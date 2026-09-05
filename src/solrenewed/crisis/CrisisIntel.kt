package solrenewed.crisis

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.StageIconSize
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import solrenewed.SolSystem
import solrenewed.SrSettings
import java.awt.Color
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Discovering the Past": the Sol colony crisis. A meter that only ever climbs when the player
 * develops Sol (colonies, population growth, industries) and that brings pirate and Luddic Path
 * bases to the neighbourhood, then raids, bombardment attempts and Remnant attacks as it crosses
 * its stages. Points scale with what was built: population growth counts people, not size steps,
 * and an industry counts its build cost, so a farm barely registers and a skunkworks does not. Built on the game's own event framework, so it lives in the intel screen
 * next to Hostile Activity with the same progress bar, stage markers and factor lists.
 *
 * Created by [CrisisWatcher] when the first player colony appears in Sol; ends if none is left.
 */
class CrisisIntel : BaseEventIntel() {

    enum class Stage { START, FOOTHOLD, RAID, ASSAULT, RECKONING }

    /** What we last saw of one Sol colony, so growth can be charged for exactly once. */
    class Snapshot(var size: Int, var industries: ArrayList<String>)

    companion object {
        const val KEY = "\$sr_crisis_ref"
        const val MAX_PROGRESS = 1000
        const val P_FOOTHOLD = 200
        const val P_RAID = 450
        const val P_ASSAULT = 700
        const val P_RECKONING = 1000
        const val RESET_AFTER_RECKONING = 450

        const val POINTS_COLONY = 10
        /** Credits of build cost per point: Farming (75k) is 8, Heavy Industry (500k) is 50, a 1M skunkworks 100. */
        const val CREDITS_PER_POINT = 10000f
        /** Growing to size 4, 5, 6, 7, 8, 9; beyond that each size is 2.5x the last. Roughly follows the head count. */
        @JvmField val GROWTH_POINTS = intArrayOf(5, 15, 40, 100, 250, 600)

        @JvmStatic
        fun growthPoints(newSize: Int): Int {
            val i = newSize - 4
            if (i < 0) return 0
            if (i < GROWTH_POINTS.size) return GROWTH_POINTS[i]
            var p = GROWTH_POINTS.last().toFloat()
            repeat(i - GROWTH_POINTS.size + 1) { p *= 2.5f }
            return p.roundToInt()
        }

        @JvmStatic
        fun industryPoints(cost: Float): Int = maxOf(1, (cost / CREDITS_PER_POINT).roundToInt())

        const val ESCALATION_STEP = 0.25f
        const val ESCALATION_MAX = 2.5f

        @JvmField val RESET_PARAM = Any()

        private val log = Global.getLogger(CrisisIntel::class.java)

        @JvmStatic
        fun get(): CrisisIntel? = Global.getSector().memoryWithoutUpdate.get(KEY) as? CrisisIntel

        @JvmStatic
        fun create(): CrisisIntel {
            val intel = CrisisIntel()
            intel.check()
            log.info("[SolRenewed] Crisis started.")
            return intel
        }
    }

    private var snapshots = HashMap<String, Snapshot>()
    /** Reckonings survived; every one makes later waves bigger. */
    var cycles = 0; private set
    private var waves = ArrayList<FleetGroupIntel>()
    private var queued = ArrayList<Stage>()
    private var reckoningActive = false
    /** Who attacked in the last wave of each stage: stage name -> faction ids. */
    private var stageFactions = HashMap<String, ArrayList<String>>()
    private var interval = IntervalUtil(2f, 4f)

    init {
        Global.getSector().memoryWithoutUpdate.set(KEY, this)
        setup()
        // Added quietly: the first "colony established" factor sends the notification.
        Global.getSector().intelManager.addIntel(this, true, null)
    }

    private fun setup() {
        factors.clear()
        stages.clear()
        setMaxProgress(MAX_PROGRESS)
        addStage(Stage.START, 0)
        addStage(Stage.FOOTHOLD, P_FOOTHOLD, true, StageIconSize.MEDIUM)
        addStage(Stage.RAID, P_RAID, true, StageIconSize.MEDIUM)
        addStage(Stage.ASSAULT, P_ASSAULT, true, StageIconSize.LARGE)
        addStage(Stage.RECKONING, P_RECKONING, true, StageIconSize.LARGE)
        for (s in stages) s.keepIconBrightWhenLaterStageReached = true
    }

    /** Hook for repairing saves from older versions once the class grows new fields. */
    protected fun readResolve(): Any = this

    /** Escalation from survived Reckonings times the LunaSettings strength knob. */
    fun strengthMult(): Float = min(ESCALATION_MAX, 1f + ESCALATION_STEP * cycles) * SrSettings.crisisStrength

    fun noteWave(stage: Stage, factionId: String) {
        stageFactions.getOrPut(stage.name) { ArrayList() }.add(factionId)
    }

    // ---------------------------------------------------------------- ticking

    override fun advanceImpl(amount: Float) {
        super.advanceImpl(amount)
        val days = Global.getSector().clock.convertToDays(amount)
        interval.advance(days)
        if (interval.intervalElapsed()) check()
        pruneWaves()
    }

    /** Charges the meter for everything new in Sol since the last look. */
    fun check() {
        if (isEnding || isEnded) return
        val sol = SolSystem.find() ?: return
        val markets = Misc.getMarketsInLocation(sol, Factions.PLAYER)
        if (markets.isEmpty()) {
            log.info("[SolRenewed] Crisis: no player colony left in Sol, ending.")
            endAfterDelay()
            return
        }
        if (!SrSettings.crisis) return
        val seen = HashSet<String>()
        for (m in markets) {
            seen.add(m.id)
            val industries = ArrayList(m.industries.filter { it.isIndustry && !it.isBuilding }.map { it.id })
            val snap = snapshots[m.id]
            if (snap == null) {
                snapshots[m.id] = Snapshot(m.size, industries)
                add(POINTS_COLONY, "Colony established: ${m.name}")
                continue
            }
            if (m.size > snap.size) {
                var pts = 0
                for (s in snap.size + 1..m.size) pts += growthPoints(s)
                add(pts, "${m.name} grew to size ${m.size}")
                snap.size = m.size
            }
            for (id in industries) {
                if (id in snap.industries) continue
                val ind = m.getIndustry(id) ?: continue
                add(industryPoints(ind.buildCost), "${ind.currentName} on ${m.name}")
            }
            snap.industries = industries
        }
        snapshots.keys.retainAll(seen)
    }

    private fun add(points: Int, desc: String) {
        val p = (points * SrSettings.crisisPace).roundToInt()
        if (p <= 0) return
        addFactor(CrisisFactor(desc, p))
    }

    // ------------------------------------------------------------------ waves

    override fun notifyStageReached(stage: EventStageData) {
        val id = stage.id as? Stage ?: return
        when (id) {
            Stage.START -> {}
            Stage.FOOTHOLD -> waves.addAll(Waves.foothold(this))
            Stage.RAID, Stage.ASSAULT, Stage.RECKONING -> {
                if (waves.isEmpty()) launch(id) else if (id !in queued) queued.add(id)
            }
        }
    }

    private fun launch(stage: Stage) {
        stageFactions.remove(stage.name)
        val started = Waves.launch(this, stage)
        waves.addAll(started)
        if (stage == Stage.RECKONING) {
            reckoningActive = true
            if (started.isEmpty()) finishReckoning()
        }
    }

    private fun pruneWaves() {
        if (waves.isEmpty()) return
        waves.removeAll { it.isEnded || it.isEnding }
        if (waves.isNotEmpty()) return
        if (reckoningActive) finishReckoning()
        if (queued.isNotEmpty()) launch(queued.removeAt(0))
    }

    /** The Reckoning is over: the Sector loses interest for a while, but next time it comes harder. */
    private fun finishReckoning() {
        reckoningActive = false
        cycles++
        setProgress(RESET_AFTER_RECKONING)
        Waves.ensureBases(this)
        sendUpdateIfPlayerHasIntel(RESET_PARAM, false)
        log.info("[SolRenewed] Crisis: Reckoning over, cycle $cycles, meter reset to $RESET_AFTER_RECKONING")
    }

    // -------------------------------------------------------------------- UI

    override fun getName(): String = "Discovering the Past"

    override fun getIcon(): String = Global.getSettings().getSpriteName("events", "sr_crisis")

    override fun isEventProgressANegativeThingForThePlayer(): Boolean = true

    override fun withMonthlyFactors(): Boolean = false

    override fun getBarColor(): Color = Misc.interpolateColor(Misc.getNegativeHighlightColor(), Color.black, 0.25f)

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> {
        val tags = super.getIntelTags(map)
        tags.add(Tags.INTEL_COLONIES)
        return tags
    }

    override fun notifyEnded() {
        super.notifyEnded()
        Global.getSector().memoryWithoutUpdate.unset(KEY)
    }

    private fun crest(factionId: String): String = Global.getSector().getFaction(factionId).crest

    override fun getStageIconImpl(stageId: Any): String {
        val stage = stageId as? Stage ?: return getIcon()
        val reached = isStageActive(stageId)
        return when (stage) {
            Stage.START -> getIcon()
            Stage.FOOTHOLD -> if (reached) crest(Factions.PIRATES) else Global.getSettings().getSpriteName("events", "stage_unknown_bad")
            Stage.RECKONING -> if (reached) crest(Factions.REMNANTS) else Global.getSettings().getSpriteName("events", "stage_unknown_bad")
            else -> {
                val f = stageFactions[stage.name]?.firstOrNull()
                if (reached && f != null) crest(f) else Global.getSettings().getSpriteName("events", "stage_unknown_bad")
            }
        }
    }

    override fun addBulletPoints(info: TooltipMakerAPI, mode: ListInfoMode, isUpdate: Boolean, tc: Color, initPad: Float) {
        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) return
        val h = Misc.getHighlightColor()
        val param = listInfoParam
        if (isUpdate && param is EventStageData) {
            when (param.id) {
                Stage.FOOTHOLD -> info.addPara("Pirates and the Luddic Path move in near Sol", tc, initPad)
                Stage.RAID -> info.addPara("An attack on Sol is coming", tc, initPad)
                Stage.ASSAULT -> info.addPara("Two attacks on Sol are coming", tc, initPad)
                Stage.RECKONING -> info.addPara("Everyone is coming for Sol", tc, initPad)
            }
            return
        }
        if (isUpdate && param === RESET_PARAM) {
            info.addPara("Meter drops to %s; later waves %s bigger", initPad, tc, h, "$RESET_AFTER_RECKONING", "+${escalationPercent()}%")
            return
        }
        if (!isUpdate) {
            info.addPara("Attention on Sol: %s of %s", initPad, tc, h, "${getProgress()}", "${getMaxProgress()}")
            if (cycles > 0) info.addPara("Waves %s bigger", 0f, tc, h, "+${escalationPercent()}%")
        }
    }

    private fun escalationPercent(): Int = ((min(ESCALATION_MAX, 1f + ESCALATION_STEP * cycles) - 1f) * 100f).roundToInt()

    override fun addStageDescriptionText(info: TooltipMakerAPI, width: Float, stageId: Any) {
        addStageDesc(info, stageId, 0f)
    }

    private fun addStageDesc(info: TooltipMakerAPI, stageId: Any, initPad: Float) {
        val stage = stageId as? Stage ?: return
        val opad = 10f
        val h = Misc.getHighlightColor()
        val bad = Misc.getNegativeHighlightColor()
        val pirates = Global.getSector().getFaction(Factions.PIRATES).baseUIColor
        val pathers = Global.getSector().getFaction(Factions.LUDDIC_PATH).baseUIColor
        val remnants = Global.getSector().getFaction(Factions.REMNANTS).baseUIColor
        when (stage) {
            Stage.START -> {
                info.addPara(
                    "Sol is the cradle of humanity, and the Sector is starting to remember. Colonies, people and " +
                        "industry here draw attention.", initPad
                )
                info.addPara("The meter %s.", opad, bad, "never falls on its own")
            }
            Stage.FOOTHOLD -> {
                val label = info.addPara(
                    "Pirates and the Luddic Path set up bases near Sol. A first pirate raid follows.", initPad
                )
                label.setHighlight("Pirates", "Luddic Path")
                label.setHighlightColors(pirates, pathers)
            }
            Stage.RAID -> {
                val label = info.addPara("One of pirates, Pathers or Remnants attacks in force.", initPad)
                label.setHighlight("pirates", "Pathers", "Remnants")
                label.setHighlightColors(pirates, pathers, remnants)
                lastWaveLine(info, stage, opad)
            }
            Stage.ASSAULT -> {
                info.addPara("Two of them at once.", initPad)
                lastWaveLine(info, stage, opad)
            }
            Stage.RECKONING -> {
                info.addPara(
                    "All three at once. Afterwards the meter drops to %s and every later wave is bigger.",
                    initPad, h, "$RESET_AFTER_RECKONING"
                )
                if (cycles > 0) info.addPara("Waves are now %s bigger.", opad, h, "+${escalationPercent()}%")
            }
        }
    }

    private fun lastWaveLine(info: TooltipMakerAPI, stage: Stage, pad: Float) {
        val ids = stageFactions[stage.name] ?: return
        val factions = ids.mapNotNull { Global.getSector().getFaction(it) }
        if (factions.isEmpty()) return
        val names = factions.map { it.displayName }
        val label = info.addPara((if (waves.isNotEmpty()) "Current attackers: " else "Last attackers: ") + names.joinToString(", "), pad)
        label.setHighlight(*names.toTypedArray())
        label.setHighlightColors(*factions.map { it.baseUIColor }.toTypedArray())
    }

    override fun getStageTooltipImpl(stageId: Any): TooltipCreator? {
        val esd = getDataFor(stageId) ?: return null
        val stage = stageId as? Stage ?: return null
        if (stage == Stage.START) return null
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                val opad = 10f
                tooltip.addTitle(
                    when (stage) {
                        Stage.FOOTHOLD -> "Foothold"
                        Stage.RAID -> "Raid"
                        Stage.ASSAULT -> "Assault"
                        Stage.RECKONING -> "Reckoning"
                        else -> "Discovering the Past"
                    }
                )
                addStageDesc(tooltip, stageId, opad)
                esd.addProgressReq(tooltip, opad)
            }
        }
    }
}
