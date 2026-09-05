package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetAssignment
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantStationFleetManager
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import com.fs.starfarer.api.util.Misc
import java.util.Random

/**
 * Remnant presence, modelled on Solsector: "nests" (Remnant battlestations with a fleet manager that
 * keeps spawning patrols around them) plus one-shot big patrol fleets. Fleet-manager sizes are in
 * points; the game multiplies them by 8 to get fleet points, so 10-20 points is an 80-160 FP fleet.
 */
object Remnants {

    private val log = Global.getLogger(Remnants::class.java)

    const val STANDARD = "remnant_station2_Standard"
    const val DAMAGED = "remnant_station2_Damaged"
    const val PLATFORM = "remnant_weapon_platform1_Standard"

    fun populateSol(sys: StarSystemAPI, star: PlanetAPI, p: Map<String, PlanetAPI>, level: Int) {
        if (level <= 0) {
            log.info("[SolRenewed] Remnants: none.")
            return
        }
        val neptune = p.getValue("neptune")
        val jupiter = p.getValue("jupiter")
        val saturn = p.getValue("saturn")
        val mercury = p.getValue("mercury")
        val mars = p.getValue("mars")
        val titan = p.getValue("titan")
        val callisto = p.getValue("callisto")
        val ganymede = p.getValue("ganymede")
        val uranus = p.getValue("uranus")
        val pluto = p.getValue("pluto")

        sys.addTag(Tags.THEME_REMNANT)
        sys.addTag(Tags.THEME_REMNANT_SECONDARY)

        // Light: the outer system only. Inner planets stay free to settle.
        nest(sys, neptune, DAMAGED, 200f, neptune.radius + 450f, 90f, maxFleets = 2, minPts = 4, maxPts = 10, core = Commodities.BETA_CORE)
        nest(sys, star, STANDARD, 120f, 16600f, 1660f, maxFleets = 3, minPts = 6, maxPts = 14)

        if (level >= 2) {
            sys.addTag(Tags.THEME_REMNANT_RESURGENT)
            nest(sys, jupiter, STANDARD, 300f, jupiter.radius + 900f, 60f, maxFleets = 5, minPts = 10, maxPts = 20)
            nest(sys, saturn, DAMAGED, 180f, saturn.radius + 900f, 70f, maxFleets = 3, minPts = 6, maxPts = 12, core = Commodities.BETA_CORE)
            nest(sys, mercury, STANDARD, 180f, mercury.radius + 220f, 20f, maxFleets = 4, minPts = 12, maxPts = 24)
            patrol(sys, jupiter, 150f)
        } else {
            sys.addTag(Tags.THEME_REMNANT_SUPPRESSED)
        }

        if (level >= 3) {
            sys.addTag(Tags.THEME_REMNANT_MAIN)
            for (a in listOf(45f, 135f, 225f, 315f)) {
                nest(sys, star, STANDARD, a, 1400f, 65f, maxFleets = 5, minPts = 20, maxPts = 40)
            }
            nest(sys, mercury, STANDARD, 60f, mercury.radius + 220f, 20f, maxFleets = 5, minPts = 30, maxPts = 50)
            nest(sys, mercury, DAMAGED, 300f, mercury.radius + 220f, 20f, maxFleets = 5, minPts = 5, maxPts = 35, core = Commodities.BETA_CORE)
            nest(sys, mars, STANDARD, 180f, mars.radius + 250f, 40f, maxFleets = 5, minPts = 20, maxPts = 30)
            nest(sys, titan, STANDARD, 72f, titan.radius + 200f, 25f, maxFleets = 3, minPts = 20, maxPts = 30)
            nest(sys, callisto, STANDARD, 72f, callisto.radius + 200f, 22f, maxFleets = 3, minPts = 20, maxPts = 30)
            nest(sys, uranus, PLATFORM, 180f, uranus.radius + 400f, 80f, maxFleets = 1, minPts = 20, maxPts = 30, core = Commodities.BETA_CORE)
            nest(sys, pluto, PLATFORM, 0f, pluto.radius + 500f, 60f, maxFleets = 1, minPts = 20, maxPts = 30, core = Commodities.BETA_CORE)
            patrol(sys, mars, 250f)
            patrol(sys, mercury, 400f)
            patrol(sys, ganymede, 300f)
            patrol(sys, mercury, 800f)
        }

        val beacon = if (level >= 2) RemnantThemeGenerator.RemnantSystemType.RESURGENT else RemnantThemeGenerator.RemnantSystemType.SUPPRESSED
        RemnantThemeGenerator.addBeacon(sys, beacon)
        log.info("[SolRenewed] Remnants: level $level.")
    }

    /**
     * A Remnant battlestation in orbit that keeps up to `maxFleets` patrols alive around itself.
     * Follows the game's own recipe for Remnant system stations.
     */
    fun nest(
        sys: StarSystemAPI, focus: SectorEntityToken, variant: String, angle: Float, radius: Float, period: Float,
        maxFleets: Int, minPts: Int, maxPts: Int, core: String = Commodities.ALPHA_CORE, name: String? = null,
    ): CampaignFleetAPI {
        val random = Random()
        val fleet = FleetFactoryV3.createEmptyFleet(Factions.REMNANTS, FleetTypes.BATTLESTATION, null)
        val member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
        fleet.fleetData.addFleetMember(member)
        fleet.fleetData.setFlagship(member)

        val mem = fleet.memoryWithoutUpdate
        mem.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true)
        mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true)
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true)
        fleet.addTag(Tags.NEUTRINO_HIGH)
        fleet.isStationMode = true
        RemnantThemeGenerator.addRemnantStationInteractionConfig(fleet)
        sys.addEntity(fleet)

        fleet.clearAbilities()
        fleet.addAbility(Abilities.TRANSPONDER)
        fleet.getAbility(Abilities.TRANSPONDER).activate()
        fleet.detectedRangeMod.modifyFlat("gen", 1000f)
        fleet.setAI(null)
        fleet.setCircularOrbitWithSpin(focus, angle, radius, period, 3f, 8f)

        val damaged = variant.lowercase().contains("damaged")
        if (damaged) {
            mem.set("\$damagedStation", true)
            fleet.name = fleet.name + " (Damaged)"
        }
        if (name != null) fleet.name = name

        val commander = Misc.getAICoreOfficerPlugin(core).createPerson(core, Factions.REMNANTS, random)
        fleet.commander = commander
        fleet.flagship.captain = commander
        if (!damaged) {
            RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(fleet.flagship)
            RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, null, 3, random)
        }
        member.repairTracker.cr = member.repairTracker.maxCR

        sys.addScript(RemnantStationFleetManager(fleet, 1f, 0, maxFleets, if (damaged) 25f else 15f, minPts, maxPts))
        return fleet
    }

    /** One big Remnant fleet that patrols the system forever (until someone kills it). */
    fun patrol(sys: StarSystemAPI, source: SectorEntityToken, fleetPoints: Float): CampaignFleetAPI? {
        val params = FleetParamsV3(null, source.locationInHyperspace, Factions.REMNANTS, 1f, FleetTypes.PATROL_LARGE,
            fleetPoints, 0f, 0f, 0f, 0f, 0f, 0f)
        val fleet = FleetFactoryV3.createFleet(params) ?: return null
        sys.addEntity(fleet)
        fleet.setLocation(source.location.x, source.location.y)
        RemnantSeededFleetManager.initRemnantFleetProperties(Random(), fleet, false)
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, source, 1000000f)
        return fleet
    }

    /** A guarded nest with a cache and three Radiant wrecks around it (the Centauri prize). */
    fun guardedNest(sys: StarSystemAPI, focus: SectorEntityToken, angle: Float, radius: Float, period: Float, name: String): CampaignFleetAPI {
        val station = nest(sys, focus, STANDARD, angle, radius, period, maxFleets = 6, minPts = 10, maxPts = 22, name = name)
        val commander = station.commander
        commander.name = FullName("Ion", "Core", FullName.Gender.ANY)
        Uni.entity(sys, station, "sr_remnant_cache", null, "weapons_cache_remnant", "neutral", 0f, 200f, 100f)
        Derelicts.specific(sys, station, 60f, 320f, "radiant_Standard", ShipRecoverySpecial.ShipCondition.AVERAGE)
        Derelicts.specific(sys, station, 150f, 420f, "radiant_Assault", ShipRecoverySpecial.ShipCondition.GOOD)
        Derelicts.specific(sys, station, 220f, 520f, "radiant_Strike", ShipRecoverySpecial.ShipCondition.PRISTINE)
        return station
    }
}
