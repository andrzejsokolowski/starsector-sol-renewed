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
 * develops Sol (colonies, colony sizes, industries, the hypershunt) and that brings pirate and
 * Luddic Path bases to the neighbourhood, then raids, bombardment attempts and Remnant attacks as
 * it crosses its stages. Built on the game's own event framework, so it lives in the intel screen
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

        const val POINTS_COLONY = 60
        const val POINTS_PER_SIZE = 10
        const val POINTS_INDUSTRY = 40
        const val POINTS_HYPERSHUNT = 100

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
    private var hypershuntCounted = false
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
                for (s in snap.size + 1..m.size) pts += POINTS_PER_SIZE * s
                add(pts, "${m.name} grew to size ${m.size}")
                snap.size = m.size
            }
            for (id in industries) {
                if (id in snap.industries) continue
                val name = m.getIndustry(id)?.currentName ?: id
                add(POINTS_INDUSTRY, "$name built on ${m.name}")
            }
            snap.industries = industries
        }
        snapshots.keys.retainAll(seen)
        if (!hypershuntCounted) {
            for (tap in sol.getEntitiesWithTag(Tags.CORONAL_TAP)) {
                if (tap.memoryWithoutUpdate.getBoolean("\$usable")) {
                    hypershuntCounted = true
                    add(POINTS_HYPERSHUNT, "Hypershunt activated")
                    break
                }
            }
        }
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
                Stage.FOOTHOLD -> info.addPara("Pirates and the Luddic Path are moving in on Sol", tc, initPad)
                Stage.RAID -> info.addPara("An attack on your Sol colonies is being organized", tc, initPad)
                Stage.ASSAULT -> info.addPara("A two-pronged assault on your Sol colonies is being organized", tc, initPad)
                Stage.RECKONING -> info.addPara("Everyone who wants Sol is coming at once", tc, initPad)
            }
            return
        }
        if (isUpdate && param === RESET_PARAM) {
            info.addPara("The Sector's attention drifts; the meter drops to %s", initPad, tc, h, "$RESET_AFTER_RECKONING")
            info.addPara("Future waves are %s bigger", 0f, tc, h, "+${escalationPercent()}%")
            return
        }
        if (!isUpdate) {
            info.addPara("Attention on Sol: %s of %s", initPad, tc, h, "${getProgress()}", "${getMaxProgress()}")
            if (cycles > 0) info.addPara("Waves are %s bigger", 0f, tc, h, "+${escalationPercent()}%")
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
        val reached = isStageActive(stageId)
        when (stage) {
            Stage.START -> {
                info.addPara(
                    "Sol is the cradle of humanity, and a Sector that had forgotten it is starting to remember. " +
                        "Every colony founded here, every size a colony grows, every industry that comes online and " +
                        "the hypershunt being switched on draw more attention: scavengers who want what the Domain left " +
                        "behind, zealots who call the return to Old Earth a blasphemy, and the Remnant nests that stir " +
                        "as traffic through the system grows.", initPad
                )
                info.addPara("The meter %s. It only stops climbing when Sol stops growing.", opad, bad, "never falls on its own")
            }
            Stage.FOOTHOLD -> {
                if (!reached) {
                    val label = info.addPara(
                        "Pirates and the Luddic Path each set up a base within a dozen light-years of Sol, and the " +
                            "pirates send a first raiding party to test your defences.", initPad
                    )
                    label.setHighlight("Pirates", "Luddic Path")
                    label.setHighlightColors(pirates, pathers)
                } else {
                    val label = info.addPara(
                        "Pirates and the Luddic Path have bases near Sol. Destroying a base sends its raiders home " +
                            "until a new one is founded, but it does not lower the meter.", initPad
                    )
                    label.setHighlight("Pirates", "Luddic Path")
                    label.setHighlightColors(pirates, pathers)
                }
            }
            Stage.RAID -> {
                val label = info.addPara(
                    "One of the three comes in force: pirates to seize the system's relays and plunder the colonies, " +
                        "Pathers or Remnants to saturation-bombard one of them.", initPad
                )
                label.setHighlight("pirates", "Pathers", "Remnants", "saturation-bombard")
                label.setHighlightColors(pirates, pathers, remnants, bad)
                lastWaveLine(info, stage, opad)
            }
            Stage.ASSAULT -> {
                info.addPara("Two of them at once.", initPad)
                lastWaveLine(info, stage, opad)
            }
            Stage.RECKONING -> {
                val label = info.addPara(
                    "Pirates, the Luddic Path and the Remnants all at once. Survive it and the Sector loses interest for " +
                        "a while: the meter drops to %s and the bases return if you had cleared them, but every wave after " +
                        "that is bigger.", initPad, h, "$RESET_AFTER_RECKONING"
                )
                label.setHighlight("Pirates", "Luddic Path", "Remnants", "$RESET_AFTER_RECKONING")
                label.setHighlightColors(pirates, pathers, remnants, h)
                if (cycles > 0) {
                    info.addPara("Reckonings survived: %s. Waves are currently %s bigger.", opad, h, "$cycles", "+${escalationPercent()}%")
                }
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
