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
 * player's Sol colonies are (colony size on a steep curve, every industry by its own build cost) and
 * that brings pirate and Luddic Path bases to the neighbourhood, then one attack per stage, each
 * stronger than the last (see [Waves]). A Sol that stops growing still draws attention, it just stops
 * drawing more of it. Every Reckoning survived makes the meter and every mark on it longer (the
 * "Crisis meter growth per Reckoning" setting), because a Sol that only grows would otherwise be
 * attacked faster and faster. Built on the game's own event framework, so it lives in the intel screen next
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

        /**
         * Monthly points by colony size: nothing up to size 3, 15 and 25 at sizes 5 and 6, 100 from size 10 on
         * (Ashes of the Domain sizes past 10 stay at 100). A size 10 colony holds tens of billions, a size 4 ten
         * thousand, so the curve climbs steeply.
         */
        @JvmField val SIZE_POINTS = intArrayOf(0, 0, 0, 0, 5, 15, 25, 40, 60, 80, 100)

        /** Own build cost per monthly point: Farming (75k) is 0.4, Heavy Industry (500k) 2.5, a 1M building 5. */
        const val CREDITS_PER_POINT = 200000f

        /** Monthly points for one colony. */
        @JvmStatic
        fun colonyPoints(size: Int): Float = SIZE_POINTS[size.coerceIn(0, SIZE_POINTS.size - 1)].toFloat()

        /**
         * Monthly points for one industry: its own build cost only. What it was upgraded from does not count,
         * because Ashes of the Domain upgrade chains are long.
         */
        @JvmStatic
        fun industryPoints(spec: IndustrySpecAPI): Float = spec.cost / CREDITS_PER_POINT

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

        /** One Reckoning never stretches the meter more than this, and the meter never gets longer than [MAX_LENGTH] times 1000. */
        const val STRETCH_MAX = 4f
        const val MAX_LENGTH = 8f

        /** How many times its base 1000 points the meter is after [cycles] Reckonings, each stretching it [stretch] times. */
        @JvmStatic
        fun lengthFor(cycles: Int, stretch: Float): Float {
            val s = stretch.coerceIn(1f, STRETCH_MAX)
            var length = 1f
            repeat(cycles) { length = min(MAX_LENGTH, length * s) }
            return length
        }

        /** Where a stage's mark sits on the base 1000 point meter. */
        @JvmStatic
        fun baseMark(stageId: Any?): Int? = when (stageId) {
            Stage.START -> 0
            Stage.FOOTHOLD -> P_FOOTHOLD
            Stage.RAID -> P_RAID
            Stage.ASSAULT -> P_ASSAULT
            Stage.RECKONING -> P_RECKONING
            else -> null
        }

        @JvmStatic
        fun markAt(base: Int, length: Float): Int = (base * length).roundToInt()

        /**
         * The end of a Reckoning: puts every mark and the top of the meter at [length] times their base and drops
         * the meter to the Raid mark. Fires no stage, however far the marks move: the meter first goes to the new
         * top (past the Reckoning mark, which is reached already), and only then do the marks move. The climb
         * that follows fires Assault and Reckoning again at their new marks.
         */
        @JvmStatic
        fun stretchAndDrop(intel: BaseEventIntel, length: Float) {
            val top = markAt(MAX_PROGRESS, length)
            intel.setMaxProgress(top)
            intel.setProgress(top)
            for (stage in intel.getStages()) {
                stage.progress = markAt(baseMark(stage.id) ?: continue, length)
            }
            intel.setProgress(markAt(RESET_AFTER_RECKONING, length))
        }

        @JvmField val RESET_PARAM = Any()
        @JvmField val DETERRED_PARAM = Any()
        @JvmField val RESUMED_PARAM = Any()

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
    /** The faction of the last attack, any stage; it is less likely to come again next. Null in older saves. */
    var lastAttacker: String? = null; private set
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

    /** How many times its base 1000 points the meter is now. */
    fun length(): Float = getMaxProgress() / MAX_PROGRESS.toFloat()

    /** Escalation from survived Reckonings times the LunaSettings strength knob. */
    fun strengthMult(): Float = min(ESCALATION_MAX, 1f + ESCALATION_STEP * cycles) * SrSettings.crisisStrength

    fun noteWave(stage: Stage, factionId: String) {
        stageFactions[stage.name] = arrayListOf(factionId)
        lastAttacker = factionId
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
        Deterrent.startIfDue(this)
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
        if (!SrSettings.crisis || isEnding || isEnded || Deterrent.isDeterred()) return 0
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

    /** Next queued attack once the last one is over; held while Sol's deterrent holds, sent when it lapses. */
    private fun pruneWaves() {
        if (waves.isNotEmpty()) {
            waves.removeAll { it.isEnded || it.isEnding }
            if (waves.isNotEmpty()) return
            if (reckoningActive) finishReckoning()
        }
        if (queued.isNotEmpty() && !Deterrent.isDeterred()) launch(queued.removeAt(0))
    }

    /**
     * The Reckoning is over: the meter gets longer, so the next attacks need more attention, and drops back to
     * the Raid mark. The next waves come harder.
     */
    private fun finishReckoning() {
        reckoningActive = false
        cycles++
        stretchAndDrop(this, lengthFor(cycles, SrSettings.crisisStretchNow()))
        Waves.ensureBases(this)
        sendUpdateIfPlayerHasIntel(RESET_PARAM, false)
        Deterrent.startIfDue(this)
        log.info("[SolRenewed] Crisis: Reckoning over, cycle $cycles, meter now ${getMaxProgress()} long, back at ${getProgress()}")
    }

    /**
     * Sol's deterrent holds (all three planetkillers, attacks switched off on the Deterrent card): the meter stops
     * where it is and no new attack starts. Attacks already under way finish. [resume] picks it up again.
     */
    fun deter() {
        if (isEnding || isEnded) return
        sendUpdateIfPlayerHasIntel(DETERRED_PARAM, false)
        log.info("[SolRenewed] Crisis: deterred, meter held at ${getProgress()}")
    }

    /** The player let the Sector forget: the meter climbs again from where it stopped. */
    fun resume() {
        if (isEnding || isEnded) return
        sendUpdateIfPlayerHasIntel(RESUMED_PARAM, false)
        log.info("[SolRenewed] Crisis: resumed at ${getProgress()}")
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

    /** Every stage has its own picture (graphics/icons/crisis/), like the game's Hyperspace Topography event. */
    override fun getStageIconImpl(stageId: Any): String {
        val stage = stageId as? Stage ?: return getIcon()
        return Global.getSettings().getSpriteName("events", "sr_crisis_" + stage.name)
    }

    override fun addBulletPoints(info: TooltipMakerAPI, mode: ListInfoMode, isUpdate: Boolean, tc: Color, initPad: Float) {
        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) return
        val h = Misc.getHighlightColor()
        val param = listInfoParam
        if (isUpdate && param is EventStageData) {
            when (param.id) {
                Stage.FOOTHOLD -> info.addPara("Raiders make camp near Sol", tc, initPad)
                Stage.RAID -> info.addPara("A strike force sets course for Sol", tc, initPad)
                Stage.ASSAULT -> info.addPara("A war fleet sets course for Sol", tc, initPad)
                Stage.RECKONING -> info.addPara("An armada sets course for Sol", tc, initPad)
            }
            return
        }
        if (isUpdate && param === DETERRED_PARAM) {
            info.addPara("No one will strike at Sol again", tc, initPad)
            return
        }
        if (isUpdate && param === RESUMED_PARAM) {
            info.addPara("The Sector has forgotten what Sol holds", tc, initPad)
            return
        }
        if (isUpdate && param === RESET_PARAM) {
            info.addPara("The attackers fall back, for now", tc, initPad)
            info.addPara("Attention on Sol: %s of %s", 0f, tc, h, "${getProgress()}", "${getMaxProgress()}")
            return
        }
        if (!isUpdate) {
            info.addPara("Attention on Sol: %s of %s", initPad, tc, h, "${getProgress()}", "${getMaxProgress()}")
            if (Deterrent.isDeterred()) info.addPara("Sol's deterrent holds", tc, 0f)
            else info.addPara("Rising %s a month", 0f, tc, h, "+$monthlyProgress")
            if (cycles > 0) info.addPara("Attacks %s stronger", 0f, tc, h, "+${escalationPercent()}%")
        }
    }

    private fun escalationPercent(): Int = ((min(ESCALATION_MAX, 1f + ESCALATION_STEP * cycles) - 1f) * 100f).roundToInt()

    /**
     * Only the opening blurb gets a row of its own; every later stage has its text in the tooltip on its
     * marker above the bar. A stage whose text adds nothing has its whole row skipped by the game.
     */
    override fun addStageDescriptionText(info: TooltipMakerAPI, width: Float, stageId: Any) {
        if (stageId !== Stage.START) return
        addStageDesc(info, stageId, 0f)
    }

    /** Scaled the same way [monthly] scales real points, so the legend matches what the meter will actually do. */
    private fun scaled(points: Float): String = Misc.getRoundedValueMaxOneAfterDecimal(points * SrSettings.crisisPace)

    /** The legend: what moves the meter, with the numbers this game is actually using. */
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
        val last = SIZE_POINTS.size - 1
        for (size in SIZE_POINTS.indices) {
            if (SIZE_POINTS[size] <= 0) continue
            val label = if (size == last) "Colony, size $size and up" else "Colony, size $size"
            sub.addRow(Alignment.LMID, text, label, Alignment.RMID, h, "+${scaled(SIZE_POINTS[size].toFloat())}")
        }
        // Expressed as credits-per-point rather than points-per-credit so the pace setting never rounds this to zero.
        val perPoint = CREDITS_PER_POINT / maxOf(0.01f, SrSettings.crisisPace)
        sub.addRow(Alignment.LMID, text, "Industry, per ${Misc.getDGSCredits(perPoint)} to build", Alignment.RMID, h, "+1")
        sub.addTable("", -1, opad)
        sub.prev.position.setXAlignOffset(-5f)
        main.endSubTooltip()
        main.addCustom(sub, opad)
    }

    private fun addStageDesc(info: TooltipMakerAPI, stageId: Any, initPad: Float) {
        val stage = stageId as? Stage ?: return
        val opad = 10f
        val h = Misc.getHighlightColor()
        val pirates = Global.getSector().getFaction(Factions.PIRATES).baseUIColor
        val pathers = Global.getSector().getFaction(Factions.LUDDIC_PATH).baseUIColor
        when (stage) {
            Stage.START -> {
                info.addPara(
                    "Sol is the cradle of humanity, and the Sector is starting to remember. Every colony, every " +
                        "soul and every factory here draws more eyes to it.", initPad
                )
                info.addPara(
                    "Beat back these ever stronger attacks, and in time the factions of the Persean Sector will " +
                        "learn that taking Sol is a futile endeavour" +
                        (if (SrSettings.crisisStretch > 1f) ", and they will come less often" else "") +
                        ". But they will not stop. Not until Sol has a deterrent.", opad
                )
                if (Deterrent.isDeterred()) {
                    info.addPara("Now it has one. The Sector knows what Sol holds, and no fleet comes.", opad)
                }
            }
            Stage.FOOTHOLD -> {
                val label = info.addPara(
                    "Pirate crews and Pather cells make camp in the dark systems around Sol. The first raiders " +
                        "come to test its defences.", initPad
                )
                label.setHighlight("Pirate", "Pather")
                label.setHighlightColors(pirates, pathers)
                lastWaveLine(info, stage, opad)
            }
            Stage.RAID -> {
                info.addPara("Word of Sol's wealth travels. Someone sends a strike force to take a closer look.", initPad)
                lastWaveLine(info, stage, opad)
            }
            Stage.ASSAULT -> {
                info.addPara("What came to probe now comes to conquer: a war fleet, larger than the last.", initPad)
                lastWaveLine(info, stage, opad)
            }
            Stage.RECKONING -> {
                info.addPara("The Sector gives its answer to the cradle of humanity: an armada.", initPad)
                lastWaveLine(info, stage, opad)
                if (cycles > 0) {
                    info.addPara(
                        "Reckonings survived: %s. Attacks %s stronger.", opad, h,
                        "$cycles", "+${escalationPercent()}%"
                    )
                }
            }
        }
    }

    private fun lastWaveLine(info: TooltipMakerAPI, stage: Stage, pad: Float) {
        val id = stageFactions[stage.name]?.lastOrNull() ?: return
        val faction = Global.getSector().getFaction(id) ?: return
        val name = faction.displayName
        val label = info.addPara((if (waves.isNotEmpty()) "Attacking now: " else "Last attacker: ") + name, pad)
        label.setHighlight(name)
        label.setHighlightColors(faction.baseUIColor)
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
