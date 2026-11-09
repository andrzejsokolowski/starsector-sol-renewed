package solrenewed.crisis

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetAssignment
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.ui.IntelUIAPI
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import solrenewed.Remnants
import solrenewed.SolSystem
import solrenewed.SrSettings
import java.awt.Color
import java.util.Random

/**
 * "The Deterrent": the end of the "Discovering the Past" crisis. After the first Reckoning, three boss
 * fleets each guard a Domain planetkiller: a pirate warlord at the crisis pirate base, a Luddic Path
 * fleet at the crisis Pather base, and a Remnant fleet at a new nexus in outer Sol. Each one drops its
 * planetkiller on the loot screen of the battle that destroys it. With all three, the attacks stop; a button
 * on the mission card brings them back and stops them again at any time, for players who would rather fight.
 *
 * The rules (who, how strong, which flagship, when it starts) are here; the state is in [DeterrentIntel].
 */
object Deterrent {

    /** Sector memory: the mission card. Kept after it ends: it is how the crisis knows the mission was given. */
    const val INTEL_KEY = "\$sr_deterrent_ref"
    /** Sector memory: true while Sol's deterrent holds (all three planetkillers, attacks off). */
    const val DETERRED_KEY = "\$sr_crisis_deterred"
    /** Fleet memory on a boss fleet: which boss it is. */
    const val BOSS_KEY = "\$sr_deterrentBoss"
    /** The special item each boss carries (data/campaign/special_items.csv). */
    const val ITEM = "sr_planetkiller"

    /**
     * The three bosses. Flagships come from other mods when they are installed (Moar XIV, Ship and Weapon
     * Pack, Blinding-class Super Battlecarrier); otherwise the fleet's own biggest ship leads it.
     */
    enum class Boss(val factionId: String, val fleetName: String, val flagships: List<String>, val points: Float) {
        PIRATES(Factions.PIRATES, "Sol Base Loot Armada", listOf("Endless_mk2_XIV_Doomship"), 260f),
        PATH(Factions.LUDDIC_PATH, "Unholy Relic Purge Fleet",
            listOf("swp_cathedral_for", "swp_cathedral_gra", "swp_cathedral_mob"), 260f),
        REMNANTS(Factions.REMNANTS, "Origin Found",
            listOf("kyris_blinding_fleetanchor", "kyris_blinding_siegebreaker", "kyris_blinding_orbitclearer"), 200f),
    }

    /** The first flagship variant this game has, or null for the fleet's own biggest ship. */
    @JvmStatic
    fun flagshipFor(boss: Boss, exists: (String) -> Boolean): String? = boss.flagships.firstOrNull(exists)

    /** Fleet points of a boss fleet before its flagship, scaled by the crisis strength setting (held to 0.5..3). */
    @JvmStatic
    fun points(boss: Boss, strength: Float): Float = boss.points * strength.coerceIn(0.5f, 3f)

    /** The mission starts once a Reckoning has been survived, unless the crisis is off or already deterred. */
    @JvmStatic
    fun shouldStart(cycles: Int, crisisOn: Boolean, started: Boolean, deterred: Boolean): Boolean =
        crisisOn && cycles >= 1 && !started && !deterred

    fun isDeterred(): Boolean = Global.getSector().memoryWithoutUpdate.getBoolean(DETERRED_KEY)

    fun intel(): DeterrentIntel? = Global.getSector().memoryWithoutUpdate.get(INTEL_KEY) as? DeterrentIntel

    /** Called by the crisis every few days and after a Reckoning; covers saves that are past it already. */
    fun startIfDue(crisis: CrisisIntel) {
        if (!shouldStart(crisis.cycles, SrSettings.crisis, intel() != null, isDeterred())) return
        try {
            DeterrentIntel.create(crisis)
        } catch (t: Throwable) {
            Global.getLogger(Deterrent::class.java).error("[SolRenewed] Deterrent: could not start", t)
        }
    }
}

/** The mission card in the intel screen, the three boss fleets, and what happens when each one dies. */
class DeterrentIntel : BaseIntelPlugin(), FleetEventListener {

    /** One boss: its fleet while it lives, where it waits, and whether its planetkiller is Sol's. */
    class Step(val boss: Deterrent.Boss) {
        var fleet: CampaignFleetAPI? = null
        var home: SectorEntityToken? = null
        var system: StarSystemAPI? = null
        var homeX = 0f
        var homeY = 0f
        var done = false
    }

    private val steps = ArrayList<Step>()
    private val interval = IntervalUtil(1f, 1f)
    private var finished = false
    /** Days until a boss that is missing (could not be placed, or vanished without a fight) is placed again. */
    private var retryDays = RETRY_DAYS

    companion object {
        private val log = Global.getLogger(DeterrentIntel::class.java)
        @JvmField val FINISHED_PARAM = Any()
        @JvmField val BUTTON_TOGGLE = Any()
        const val RETRY_DAYS = 30f

        fun create(crisis: CrisisIntel): DeterrentIntel? {
            val sol = SolSystem.find() ?: return null
            val intel = DeterrentIntel()
            for (boss in Deterrent.Boss.values()) intel.steps.add(Step(boss))
            Global.getSector().memoryWithoutUpdate.set(Deterrent.INTEL_KEY, intel)
            val random = Random(crisis.getRandom().nextLong())
            for (step in intel.steps) {
                try {
                    intel.spawn(step, crisis, sol, random)
                } catch (t: Throwable) {
                    log.error("[SolRenewed] Deterrent: could not place the ${step.boss} boss", t)
                }
            }
            intel.isImportant = true
            Global.getSector().intelManager.addIntel(intel)
            Global.getSector().addScript(intel)
            log.info("[SolRenewed] Deterrent started: " + intel.steps.joinToString { "${it.boss} in ${it.system?.name}" })
            return intel
        }
    }

    fun allDone(): Boolean = steps.isNotEmpty() && steps.all { it.done }

    fun recovered(): Int = steps.count { it.done }

    // ----------------------------------------------------------------- bosses

    private fun spawn(step: Step, crisis: CrisisIntel, sol: StarSystemAPI, random: Random) {
        // Placed again after it went missing: the same base or nexus, while it stands.
        val home = step.home?.takeIf { it.isAlive && it.containingLocation != null }
            ?: homeFor(step.boss, crisis, sol, random) ?: return
        val fleet = bossFleet(step.boss, home, random) ?: return
        step.fleet = fleet
        step.home = home
        step.system = home.starSystem
        step.homeX = home.location.x
        step.homeY = home.location.y
    }

    /** The pirate and Pather bases near Sol (planted if missing), or a new Remnant nexus out by Neptune. */
    private fun homeFor(boss: Deterrent.Boss, crisis: CrisisIntel, sol: StarSystemAPI, random: Random): SectorEntityToken? {
        fun base(): SectorEntityToken? = when (boss) {
            Deterrent.Boss.PIRATES -> Waves.nearestPirateBase(sol, Waves.BASE_RANGE_LY)?.market?.primaryEntity
            Deterrent.Boss.PATH -> Waves.nearestPatherBase(sol, Waves.BASE_RANGE_LY)?.market?.primaryEntity
            Deterrent.Boss.REMNANTS -> null
        }
        if (boss == Deterrent.Boss.REMNANTS) {
            val focus = sol.getEntityById("sr_neptune") ?: sol.star ?: return null
            return Remnants.nest(sol, focus, Remnants.STANDARD, random.nextFloat() * 360f, 900f, 60f,
                maxFleets = 3, minPts = 10, maxPts = 20, name = "Origin Nexus")
        }
        base()?.let { return it }
        Waves.ensureBases(crisis)
        base()?.let { return it }
        // No hiding place near Sol at all: the fleet waits at Sol's outer jump point instead.
        return sol.getEntityById("sr_jump_fringe") ?: sol.jumpPoints.lastOrNull()
    }

    private fun bossFleet(boss: Deterrent.Boss, home: SectorEntityToken, random: Random): CampaignFleetAPI? {
        val faction = Global.getSector().getFaction(boss.factionId)
        val params = FleetParamsV3(null, home.locationInHyperspace, boss.factionId, 1f, FleetTypes.TASK_FORCE,
            Deterrent.points(boss, SrSettings.crisisStrength), 0f, 0f, 0f, 0f, 0f, 0f)
        params.random = random
        params.qualityOverride = 1f
        if (boss != Deterrent.Boss.REMNANTS) params.averageSMods = 2
        params.officerNumberMult = 2f
        params.officerLevelBonus = 3
        val doctrine = faction.doctrine.clone()
        doctrine.shipSize = 5
        params.doctrineOverride = doctrine
        params.flagshipVariantId = Deterrent.flagshipFor(boss) { Global.getSettings().doesVariantExist(it) }
        val fleet = FleetFactoryV3.createFleet(params) ?: return null

        fleet.name = boss.fleetName
        fleet.isNoFactionInName = true
        home.containingLocation.addEntity(fleet)
        fleet.setLocation(home.location.x, home.location.y)

        val mem = fleet.memoryWithoutUpdate
        mem.set(Deterrent.BOSS_KEY, boss.name)
        mem.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true)
        mem.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true)
        mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true)
        mem.set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true)
        if (boss == Deterrent.Boss.REMNANTS) RemnantSeededFleetManager.initRemnantFleetProperties(random, fleet, false)

        fleet.flagship?.let { flagship ->
            val variant: ShipVariantAPI = flagship.variant.clone()
            variant.source = VariantSource.REFIT
            variant.addTag(Tags.VARIANT_ALWAYS_RECOVERABLE)
            flagship.setVariant(variant, false, true)
            flagship.repairTracker.cr = flagship.repairTracker.maxCR
        }
        fleet.fleetData.setSyncNeeded()
        fleet.fleetData.syncIfNeeded()

        val loot = Global.getFactory().createCargo(true)
        loot.addSpecial(SpecialItemData(Deterrent.ITEM, null), 1f)
        BaseSalvageSpecial.addExtraSalvage(fleet, loot)

        Misc.makeImportant(fleet, "sr_deterrent")
        fleet.addEventListener(this)
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, home, 1e9f, "guarding a planetkiller")
        return fleet
    }

    /** Runs every frame inside the game's loop, so nothing in it may throw. */
    override fun advanceImpl(amount: Float) {
        try {
            tick(amount)
        } catch (t: Throwable) {
            log.error("[SolRenewed] Deterrent: tick failed", t)
        }
    }

    private fun tick(amount: Float) {
        val days = Global.getSector().clock.convertToDays(amount)
        interval.advance(days)
        if (!interval.intervalElapsed()) return
        if (steps.any { !it.done && it.fleet?.isAlive != true }) {
            retryDays -= interval.intervalDuration
            if (retryDays <= 0f) {
                retryDays = RETRY_DAYS
                respawnMissing()
            }
        }
        for (step in steps) {
            val fleet = step.fleet ?: continue
            if (step.done || !fleet.isAlive) continue
            // The base it guarded is gone: it holds the spot where the base was.
            if (fleet.currentAssignment == null) {
                val sys = step.system ?: continue
                val spot = step.home?.takeIf { it.isAlive } ?: sys.createToken(step.homeX, step.homeY)
                fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, spot, 1e9f, "guarding a planetkiller")
            }
        }
    }

    private fun respawnMissing() {
        val crisis = CrisisIntel.get() ?: return
        val sol = SolSystem.find() ?: return
        val random = Random(crisis.getRandom().nextLong())
        for (step in steps) {
            if (step.done || step.fleet?.isAlive == true) continue
            try {
                spawn(step, crisis, sol, random)
                log.info("[SolRenewed] Deterrent: ${step.boss} boss placed again in ${step.system?.name}")
            } catch (t: Throwable) {
                log.error("[SolRenewed] Deterrent: could not place the ${step.boss} boss again", t)
            }
        }
    }

    // ------------------------------------------------------------ boss deaths

    override fun reportFleetDespawnedToListener(fleet: CampaignFleetAPI?, reason: FleetDespawnReason?, param: Any?) {
        val step = steps.firstOrNull { it.fleet === fleet } ?: return
        if (step.done) return
        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE) {
            step.fleet = null
            return
        }
        val battle = param as? BattleAPI
        step.done = true
        step.fleet = null
        log.info("[SolRenewed] Deterrent: ${step.boss} boss destroyed, ${recovered()} of ${steps.size}")
        // Called from the end of a battle: a failure here must not take the game down with it.
        try {
            if (battle == null || !battle.isPlayerInvolved) leaveInWreckage(fleet)
            if (allDone()) finish() else sendUpdateIfPlayerHasIntel(step, false)
        } catch (t: Throwable) {
            log.error("[SolRenewed] Deterrent: could not finish the ${step.boss} step", t)
        }
    }

    override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {}

    /** Someone else killed the boss, so its loot screen never came up: the planetkiller drifts in pods there. */
    private fun leaveInWreckage(fleet: CampaignFleetAPI?) {
        val where = fleet?.containingLocation ?: return
        val pods = Misc.addCargoPods(where, fleet.location)
        pods.cargo.addSpecial(SpecialItemData(Deterrent.ITEM, null), 1f)
        (pods.customPlugin as? CargoPodsEntityPlugin)?.setNeverExpire(true)
        Misc.makeImportant(pods, "sr_deterrent")
    }

    /** All three: the attacks stop. The card stays, with the button that brings them back. */
    private fun finish() {
        if (finished) return
        finished = true
        setDeterred(true)
        sendUpdateIfPlayerHasIntel(FINISHED_PARAM, false)
        isImportant = false
        log.info("[SolRenewed] Deterrent: all three planetkillers recovered, attacks stop")
    }

    /** Attacks off (true) or on again (false), from [finish] or the card's button. */
    private fun setDeterred(on: Boolean) {
        if (on) Global.getSector().memoryWithoutUpdate.set(Deterrent.DETERRED_KEY, true)
        else Global.getSector().memoryWithoutUpdate.unset(Deterrent.DETERRED_KEY)
        val crisis = CrisisIntel.get()
        if (on) crisis?.deter() else crisis?.resume()
    }

    override fun doesButtonHaveConfirmDialog(buttonId: Any?): Boolean = buttonId === BUTTON_TOGGLE || super.doesButtonHaveConfirmDialog(buttonId)

    override fun createConfirmationPrompt(buttonId: Any?, prompt: TooltipMakerAPI) {
        if (buttonId !== BUTTON_TOGGLE) return super.createConfirmationPrompt(buttonId, prompt)
        if (Deterrent.isDeterred()) {
            prompt.addPara(
                "Let word of the planetkillers fade. In time the Sector will forget what Sol holds, and its fleets will " +
                    "come again.", 0f
            )
        } else {
            prompt.addPara(
                "Make sure every captain in the Sector knows what Sol holds. No fleet will come while they remember.", 0f
            )
        }
    }

    override fun getConfirmText(buttonId: Any?): String =
        if (buttonId !== BUTTON_TOGGLE) super.getConfirmText(buttonId)
        else if (Deterrent.isDeterred()) "Let them come" else "Remind them"

    override fun buttonPressConfirmed(buttonId: Any?, ui: IntelUIAPI) {
        if (buttonId === BUTTON_TOGGLE && finished) {
            setDeterred(!Deterrent.isDeterred())
            ui.updateUIForItem(this)
            return
        }
        super.buttonPressConfirmed(buttonId, ui)
    }

    // --------------------------------------------------------------------- UI

    override fun getName(): String = "The Deterrent"

    override fun getIcon(): String = Global.getSettings().getSpecialItemSpec(Deterrent.ITEM).iconName

    override fun getSortString(): String = "The Deterrent"

    private fun where(step: Step): String = when (step.boss) {
        Deterrent.Boss.PIRATES -> "the pirate base in " + (step.system?.nameWithLowercaseTypeShort ?: "the dark")
        Deterrent.Boss.PATH -> "the Pather base in " + (step.system?.nameWithLowercaseTypeShort ?: "the dark")
        Deterrent.Boss.REMNANTS -> "a nexus in the outer reaches of Sol"
    }

    private fun color(step: Step): Color = Global.getSector().getFaction(step.boss.factionId).baseUIColor

    override fun addBulletPoints(info: TooltipMakerAPI, mode: ListInfoMode, isUpdate: Boolean, tc: Color, initPad: Float) {
        val h = Misc.getHighlightColor()
        val param = listInfoParam
        if (isUpdate && param === FINISHED_PARAM) {
            info.addPara("Sol holds all three planetkillers", tc, initPad)
            return
        }
        if (finished) {
            info.addPara(if (Deterrent.isDeterred()) "Sol's deterrent holds" else "The Sector has forgotten", tc, initPad)
            return
        }
        if (isUpdate && param is Step) {
            info.addPara("%s destroyed", initPad, tc, color(param), param.boss.fleetName)
            info.addPara("Planetkillers: %s of %s", 0f, tc, h, "${recovered()}", "${steps.size}")
            return
        }
        var pad = initPad
        for (step in steps) {
            if (step.done) continue
            info.addPara("%s", pad, tc, color(step), step.boss.fleetName)
            pad = 0f
        }
    }

    override fun hasSmallDescription(): Boolean = true

    override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        val opad = 10f
        val h = Misc.getHighlightColor()
        info.addImage(getIcon(), 64f, 64f, opad)
        info.addPara(
            "Three Domain planetkillers survived the Collapse within reach of Sol. A pirate warlord keeps one in the " +
                "vault of the base near Sol. The Luddic Path carries another as a holy relic, waiting for a world that " +
                "has strayed far enough from God. The third lies with something in the outer dark of Sol, older than " +
                "the Remnants around it.", opad
        )
        info.addPara(
            "Hold all three, and no one in the Sector will strike at a world that can answer with a planet's death.", opad
        )
        for (step in steps) {
            if (step.done) {
                info.addPara("%s: planetkiller recovered", opad, color(step), step.boss.fleetName)
            } else {
                info.addPara("%s, at %s", opad, color(step), step.boss.fleetName, where(step))
            }
        }
        if (finished) {
            if (Deterrent.isDeterred()) {
                info.addPara("Sol holds all three, and the Sector knows it. No fleet comes.", opad, h, "all three")
                addGenericButton(info, width, "Let the Sector forget", BUTTON_TOGGLE)
            } else {
                info.addPara("Sol holds all three, but the Sector has forgotten. Its fleets come again.", opad, h, "all three")
                addGenericButton(info, width, "Remind the Sector", BUTTON_TOGGLE)
            }
        }
    }

    override fun getMapLocation(map: SectorMapAPI?): SectorEntityToken? {
        val next = steps.firstOrNull { !it.done } ?: return SolSystem.find()?.star
        return next.fleet ?: next.home ?: next.system?.center
    }

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> {
        val tags = super.getIntelTags(map)
        tags.add(Tags.INTEL_MISSIONS)
        tags.add(Tags.INTEL_ACCEPTED)
        tags.add(Tags.INTEL_STORY)
        return tags
    }
}
