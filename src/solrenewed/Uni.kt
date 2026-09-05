package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.JumpPointAPI
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.procgen.Constellation
import com.fs.starfarer.api.impl.campaign.procgen.NameGenData
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld
import com.fs.starfarer.api.impl.campaign.procgen.StarAge
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.Random

/** Small building blocks shared by the Sol and Centauri generators. */
object Uni {

    private val log = Global.getLogger(Uni::class.java)

    /** Adds a planet whose market starts unsurveyed, like any procgen world. */
    fun planet(
        sys: StarSystemAPI, focus: SectorEntityToken, id: String, name: String, type: String,
        angle: Float, radius: Float, orbitRadius: Float, orbitDays: Float,
    ): PlanetAPI {
        val p = sys.addPlanet(id, focus, name, type, angle, radius, orbitRadius, orbitDays)
        p.market?.surveyLevel = MarketAPI.SurveyLevel.NONE
        // AOTD's megastructure conditions only build themselves once the planet's memory is non-empty.
        p.memoryWithoutUpdate.set("\$sr_body", true)
        return p
    }

    /** Gives a planet an uncolonized "conditions only" market with the listed conditions. */
    fun market(planet: PlanetAPI, factionId: String, vararg conditions: String): MarketAPI {
        val market = Global.getFactory().createMarket(planet.id + "_market", planet.name, 0)
        market.factionId = factionId
        market.primaryEntity = planet
        market.isPlanetConditionMarketOnly = true
        for (c in conditions) market.addCondition(c)
        planet.market = market
        return market
    }

    /**
     * Adds a custom entity in a circular orbit. Types with a salvage spec (caches, derelicts, the
     * hypershunt, cryosleepers) go through the game's own salvage-entity path so they get a seed,
     * the right discoverability and their defenders; everything else is a plain entity.
     */
    fun entity(
        sys: StarSystemAPI, focus: SectorEntityToken, id: String?, name: String?, type: String, faction: String?,
        angle: Float, orbitRadius: Float, orbitDays: Float, random: Random = Random(),
    ): SectorEntityToken {
        val e: SectorEntityToken = if (SalvageEntityGeneratorOld.hasSalvageSpec(type)) {
            val s = BaseThemeGenerator.addSalvageEntity(random, sys, type, faction ?: "neutral")
            if (id != null) s.id = id
            if (name != null) s.name = name
            s
        } else {
            sys.addCustomEntity(id, name, type, faction)
        }
        e.setCircularOrbitPointingDown(focus, angle, orbitRadius, orbitDays)
        return e
    }

    /** A plain entity that has to be found with sensors (used for the cryo prison and the ark). */
    fun discoverable(e: SectorEntityToken, xp: Float = 2000f) {
        MiscellaneousThemeGenerator.makeDiscoverable(e, 1000f, xp)
        e.isDiscoverable = true
    }

    fun jumpPoint(sys: StarSystemAPI, focus: SectorEntityToken, id: String, name: String, angle: Float, orbitRadius: Float, orbitDays: Float): JumpPointAPI {
        val jp = Global.getFactory().createJumpPoint(id, name)
        jp.setStandardWormholeToHyperspaceVisual()
        jp.orbit = Global.getFactory().createCircularOrbit(focus, angle, orbitRadius, orbitDays)
        sys.addEntity(jp)
        return jp
    }

    /** Creates the hyperspace side of every jump point added so far. Call once per system. */
    fun finishJumpPoints(sys: StarSystemAPI) = sys.autogenerateHyperspaceJumpPoints(false, false, false)

    fun magneticField(sys: StarSystemAPI, planet: PlanetAPI, inner: Float, outer: Float) {
        val params = MagneticFieldTerrainPlugin.MagneticFieldParams(
            outer - inner, (inner + outer) / 2f, planet, inner, outer,
            Color(70, 30, 120, 50), 0.5f,
            Color(100, 40, 55, 195), Color(130, 10, 220, 200), Color(120, 80, 85, 185), Color(85, 100, 120, 180),
            Color(95, 30, 165, 195), Color(110, 60, 155, 185), Color(140, 155, 100, 170), Color(80, 170, 255, 35),
        )
        val field = sys.addTerrain("magnetic_field", params)
        field.setCircularOrbit(planet, 0f, 0f, 100f)
    }

    /** A small clump of asteroids parked in orbit (used to dress up the jump points). */
    fun asteroidField(sys: StarSystemAPI, focus: SectorEntityToken, angle: Float, distance: Float, radius: Float, count: Int, name: String) {
        val params = AsteroidFieldTerrainPlugin.AsteroidFieldParams(radius, radius + 100f, count, count, 4f, 16f, name)
        val field = sys.addTerrain("asteroid_field", params)
        field.setCircularOrbit(focus, angle, distance, 99999f)
    }

    fun constellation(sys: StarSystemAPI, name: String, age: StarAge = StarAge.AVERAGE): Constellation {
        val c = Constellation(Constellation.ConstellationType.NORMAL, age)
        val data = NameGenData(name, name)
        data.addTag("constellation")
        c.namePick = ProcgenUsedNames.NamePick(data, name, name)
        c.systems.add(sys)
        sys.constellation = c
        sys.memoryWithoutUpdate.set("\$isInConstellation", true)
        return c
    }

    /** Clears hyperspace storms around the system so the approach is calm, like the game does for its own systems. */
    fun hyperSweep(sys: StarSystemAPI) {
        val plugin = Misc.getHyperspaceTerrain().plugin as HyperspaceTerrainPlugin
        val editor = NebulaEditor(plugin as BaseTiledTerrain)
        val r = sys.maxRadiusInHyperspace + plugin.tileSize * 1.5f
        editor.clearArc(sys.location.x, sys.location.y, 0f, r, 0f, 360f)
        editor.clearArc(sys.location.x, sys.location.y, 0f, r, 0f, 360f, 0.25f)
    }

    /** Map position `lightYears` away from `anchor` at `angleDeg` (2000 units per light-year). */
    fun relativePosition(anchor: StarSystemAPI, lightYears: Float, angleDeg: Float): Vector2f {
        val d = lightYears * 2000f
        val rad = Math.toRadians(angleDeg.toDouble())
        return Vector2f(anchor.location.x + (Math.cos(rad) * d).toFloat(), anchor.location.y + (Math.sin(rad) * d).toFloat())
    }

    /** Nudges `preferred` away from any existing system until it has `clearance` units of free space around it. */
    fun findFreeSpot(preferred: Vector2f, clearance: Float): Vector2f {
        val sector = Global.getSector()
        val test = Vector2f(preferred)
        repeat(100) { attempt ->
            var clear = true
            for (existing in sector.starSystems) {
                val loc = existing.location ?: continue
                val dist = Misc.getDistance(test, loc)
                val required = clearance + existing.maxRadiusInHyperspace
                if (dist < required) {
                    clear = false
                    val angle = Misc.getAngleInDegrees(loc, test)
                    val push = required - dist + 500f
                    val rad = Math.toRadians(angle.toDouble())
                    test.x += (Math.cos(rad) * push).toFloat()
                    test.y += (Math.sin(rad) * push).toFloat()
                    break
                }
            }
            if (clear) {
                log.info(String.format("[SolRenewed] Free map spot at (%.0f, %.0f) after %d nudges", test.x, test.y, attempt))
                return test
            }
        }
        // Spiral outwards as a last resort.
        var angle = 0f
        var radius = clearance
        repeat(200) {
            val rad = Math.toRadians(angle.toDouble())
            val t = Vector2f(preferred.x + (Math.cos(rad) * radius).toFloat(), preferred.y + (Math.sin(rad) * radius).toFloat())
            val clear = sector.starSystems.none { s ->
                s.location != null && Misc.getDistance(t, s.location) < clearance + s.maxRadiusInHyperspace
            }
            if (clear) return t
            angle += 30f
            radius += 50f
        }
        log.warn("[SolRenewed] Could not find free map space; overlapping.")
        return Vector2f(preferred.x + clearance * 3f, preferred.y + clearance * 3f)
    }
}
