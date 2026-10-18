package solrenewed.crisis

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.StageIconSize
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.loading.IndustrySpecAPI
import com.fs.starfarer.api.ui.Alignment
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
 * "Discovering the Past": the Sol colony crisis. A meter that climbs every month by how developed the
 * player's Sol colonies are (every colony size, every industry by its build cost) and that brings
 * pirate and Luddic Path bases to the neighbourhood, then raids, bombardment attempts and Remnant
 * attacks as it crosses its stages. A Sol that stops growing still draws attention, it just stops
 * drawing more of it. Built on the game's own event framework, so it lives in the intel screen next
 * to Hostile Activity with the same progress bar, stage markers and monthly factor list.
 *
 * Created by [CrisisWatcher] when the first player colony appears in Sol; ends if none is left.
 */
class CrisisIntel : BaseEventIntel() {

    enum class Stage { START, FOOTHOLD, RAID, ASSAULT, RECKONING }

    /** Saves from 0.3.7 and older hold these (growth was charged once back then); kept so they load. */
    class Snapshot(var size: Int, var industries: ArrayList<String>)

    /** One line of a monthly factor's breakdown: a colony or an industry and what it adds a month. */
    class Share(val label: String, val points: Float)

    companion object {
        const val KEY = "\$sr_crisis_ref"
        const val MAX_PROGRESS = 1000
        const val P_FOOTHOLD = 200
        const val P_RAID = 450
        const val P_ASSAULT = 700
        const val P_RECKONING = 1000
        const val RESET_AFTER_RECKONING = 450

        /** Every size of every player colony in Sol adds this much to the meter each month. */
        const val POINTS_PER_SIZE = 2
        /** Build cost per monthly point: Farming (75k) is 1.5, Heavy Industry (500k) 10, a 1M skunkworks 20. */
        const val CREDITS_PER_POINT = 50000f

        /** Monthly points for one colony. */
        @JvmStatic
        fun colonyPoints(size: Int): Float = (POINTS_PER_SIZE * size).toFloat()

        /** Monthly points for one industry: its build cost plus the cost of everything it was upgraded from. */
        @JvmStatic
        fun industryPoints(spec: IndustrySpecAPI): Float {
            var cost = 0f
            var curr: IndustrySpecAPI? = spec
            val seen = HashSet<String>()
            while (curr != null && seen.add(curr.id)) {
                cost += curr.cost
                val down = curr.downgrade
                curr = if (down.isNullOrEmpty()) null else try {
                    Global.getSettings().getIndustrySpec(down)
                } catch (t: Throwable) {
                    null
                }
            }
            return cost / CREDITS_PER_POINT
        }

        /**
         * What counts: finished industries, not structures like spaceports or defenses. One being upgraded
         * keeps counting as what it is until the upgrade is done.
         */
        @JvmStatic
        fun counted(market: MarketAPI): List<Industry> =
            market.industries.filter { it.isIndustry && (!it.isBuilding || it.isUpgrading) }

        @JvmStatic
        fun colonyShares(markets: List<MarketAPI>): List<Share> =
            markets.map { Share("${it.name}, size ${it.size}", colonyPoints(it.size)) }

        @JvmStatic
        fun industryShares(markets: List<MarketAPI>): List<Share> =
            markets.flatMap { m -> counted(m).map { Share("${it.currentName} on ${m.name}", industryPoints(it.spec)) } }

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

    @Suppress("unused") private var snapshots = HashMap<String, Snapshot>()
    /** Reckonings survived; every one makes later waves bigger. */
    var cycles = 0; private set
    private var waves = ArrayList<FleetGroupIntel>()
    private var queued = ArrayList<Stage>()
    private var reckoningActive = false
    /** Who attacked in the last wave of each stage: stage name -> faction ids. */
    private var stageFactions = HashMap<String, ArrayList<String>>()
    private var interval = IntervalUtil(2f, 4f)
    /** The breakdown behind the two monthly factors; rebuilt every few days and after every load. */
    @Transient private var colonies: List<Share>? = null
    @Transient private var industries: List<Share>? = null

    init {
        Global.getSector().memoryWithoutUpdate.set(KEY, this)
        setup()
        Global.getSector().intelManager.addIntel(this, false, null)
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
        addMonthlyFactors()
    }

    private fun addMonthlyFactors() {
        if (factors.any { it is CrisisMonthlyFactor }) return
        factors.add(CrisisMonthlyFactor(false))
        factors.add(CrisisMonthlyFactor(true))
    }

    /** Repairs saves from older versions: 0.3.7 and older charged growth once and had no monthly factors. */
    protected fun readResolve(): Any {
        addMonthlyFactors()
        return this
    }

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

    /** Re-reads Sol's colonies for the monthly factors; ends the crisis once none is left. */
    fun check() {
        if (isEnding || isEnded) return
        val sol = SolSystem.find() ?: return
        val markets = Misc.getMarketsInLocation(sol, Factions.PLAYER)
        if (markets.isEmpty()) {
            log.info("[SolRenewed] Crisis: no player colony left in Sol, ending.")
            endAfterDelay()
            return
        }
        refresh(markets)
    }

    private fun refresh(markets: List<MarketAPI>) {
        colonies = colonyShares(markets)
        industries = industryShares(markets)
    }

    /** The colonies' or their industries' share of the monthly gain, before the pace setting. */
    fun shares(ofIndustries: Boolean): List<Share> {
        if (colonies == null || industries == null) {
            refresh(SolSystem.find()?.let { Misc.getMarketsInLocation(it, Factions.PLAYER) } ?: emptyList())
        }
        return (if (ofIndustries) industries else colonies) ?: emptyList()
    }

    /** What the colonies or their industries add to the meter this month, after the pace setting. */
    fun monthly(ofIndustries: Boolean): Int {
        if (!SrSettings.crisis || isEnding || isEnded) return 0
        return (shares(ofIndustries).sumOf { it.points.toDouble() } * SrSettings.crisisPace).roundToInt()
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

    override fun withOneTimeFactors(): Boolean = false

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
            info.addPara("Rising %s a month", 0f, tc, h, "+$monthlyProgress")
            if (cycles > 0) info.addPara("Waves %s bigger", 0f, tc, h, "+${escalationPercent()}%")
        }
    }

    private fun escalationPercent(): Int = ((min(ESCALATION_MAX, 1f + ESCALATION_STEP * cycles) - 1f) * 100f).roundToInt()

    /**
     * Only the opening blurb gets a row of its own; every stage past it is explained by the tooltip on
     * its marker above the bar, so repeating all of them down the panel is just noise. A stage whose
     * text adds nothing has its whole row skipped by the game.
     */
    override fun addStageDescriptionText(info: TooltipMakerAPI, width: Float, stageId: Any) {
        if (stageId !== Stage.START) return
        addStageDesc(info, stageId, 0f)
    }

    /** Scaled the same way [monthly] scales real points, so the legend matches what the meter will actually do. */
    private fun scaled(points: Float): String = Misc.getRoundedValueMaxOneAfterDecimal(points * SrSettings.crisisPace)

    private fun industryExample(id: String): Pair<String, String>? = try {
        val spec = Global.getSettings().getIndustrySpec(id)
        if (spec == null) null else spec.name to scaled(industryPoints(spec))
    } catch (t: Throwable) {
        null
    }

    /** The legend: everything that moves the meter, with the numbers this game is actually using. */
    override fun afterStageDescriptions(main: TooltipMakerAPI) {
        val opad = 10f
        val faction = factionForUIColors
        val width = (barWidth - opad) / 2f
        val ptsW = 46f
        val text = Misc.getTextColor()
        val h = Misc.getHighlightColor()

        val sub = main.beginSubTooltip(width)
        sub.addSectionHeading("What raises attention", faction.baseUIColor, faction.darkUIColor, Alignment.MID, opad)
            .position.setXAlignOffset(0f)
        sub.beginTable2(faction, 20f, false, false, "In Sol, every month", width - ptsW - 3f, "Points", ptsW)
        sub.addRow(Alignment.LMID, text, "Each size of each colony", Alignment.RMID, h, "+${scaled(POINTS_PER_SIZE.toFloat())}")
        // Expressed as credits-per-point rather than points-per-50k so the pace setting never rounds this to zero.
        val perPoint = CREDITS_PER_POINT / maxOf(0.01f, SrSettings.crisisPace)
        sub.addRow(Alignment.LMID, text, "Industry, per ${Misc.getDGSCredits(perPoint)} to build", Alignment.RMID, h, "+1")
        sub.addTable("", -1, opad)
        sub.prev.position.setXAlignOffset(-5f)
        main.endSubTooltip()
        main.addCustom(sub, opad)

        val examples = listOfNotNull(
            industryExample("farming"), industryExample("heavyindustry"), industryExample("orbitalworks")
        )
        if (examples.isNotEmpty()) {
            val label = main.addPara(
                "For example, a month: " + examples.joinToString(", ") { "${it.first} +${it.second}" } +
                    ". Upgrades also count what they were built on.", opad
            )
            label.setHighlight(*examples.map { "+${it.second}" }.toTypedArray())
            label.setHighlightColors(*examples.map { h }.toTypedArray())
        }
        main.addPara("Nothing else raises it. Hover a marker above the bar to see what that stage brings.", opad)
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
                    "Sol is the cradle of humanity, and the Sector is starting to remember. Every month, the " +
                        "colonies, people and industry here draw more attention.", initPad
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
