package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.StarAge
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f

/**
 * The Centaurus constellation next door to Sol: Alpha Centauri (Rigil Kentaurus, Toliman and
 * Proxima, with Proxima b) and Beta Centauri (Hadar, an empty blue giant). Bugatti's radiation
 * zone around Proxima is gone; a plain Remnant nest guards the Radiant wrecks instead.
 */
object Centauri {

    private val log = Global.getLogger(Centauri::class.java)

    fun generate(sector: SectorAPI) {
        val sol = SolSystem.find()
        val alpha = if (sector.getStarSystem("Alpha Centauri") == null) alphaCentauri(sector, sol) else null
        val beta = if (sector.getStarSystem("Beta Centauri") == null) betaCentauri(sector, alpha) else null
        if (alpha != null) {
            val c = Uni.constellation(alpha, "Centaurus", StarAge.OLD)
            if (beta != null) {
                c.systems.add(beta)
                beta.constellation = c
                beta.memoryWithoutUpdate.set("\$isInConstellation", true)
            }
        }
    }

    private fun alphaCentauri(sector: SectorAPI, sol: StarSystemAPI?): StarSystemAPI {
        val sys = sector.createStarSystem("Alpha Centauri")
        sys.age = StarAge.OLD
        val preferred = if (sol != null) Uni.relativePosition(sol, 4.24f, 35f) else Vector2f(-47000f, -37000f)
        val pos = Uni.findFreeSpot(preferred, 8000f)
        sys.location.set(pos)
        log.info(String.format("[SolRenewed] Alpha Centauri placed at (%.0f, %.0f)", pos.x, pos.y))
        sys.memoryWithoutUpdate.set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, SolSystem.MUSIC)

        val center = sys.initNonStarCenter()
        center.id = "sr_centauri_center"
        val alphaA = sys.addPlanet("sr_cen_alpha_a", center, "Rigil Kentaurus", "star_yellow", 90f, 700f, 1500f, 3000f)
        sys.addCorona(alphaA, 350f, 10f, 0.4f, 1f)
        sys.star = alphaA
        val alphaB = sys.addPlanet("sr_cen_alpha_b", center, "Toliman", "star_orange", 270f, 500f, 3500f, 3000f)
        sys.addCorona(alphaB, 250f, 8f, 0.3f, 1f)
        sys.secondary = alphaB
        val proxima = sys.addPlanet("sr_cen_proxima", center, "Proxima Centauri", "star_red_dwarf", 180f, 300f, 9000f, 20000f)
        sys.addCorona(proxima, 200f, 6f, 1f, 1f)
        sys.tertiary = proxima
        sys.type = StarSystemGenerator.StarSystemType.TRINARY_1CLOSE_1FAR

        val proximaB = Uni.planet(sys, proxima, "sr_cen_proxima_b", "Proxima b", "terran-eccentric", 45f, 120f, 1100f, 90f)
        val pm = Uni.market(proximaB, "neutral", "habitable", "very_hot", "dense_atmosphere", "extreme_weather", "irradiated",
            "ore_ultrarich", "rare_ore_ultrarich")
        ModCheck.cond(pm, "IndEvo", "IndEvo_ArtilleryStationCondition")

        if (SrSettings.remnantLevel > 0) {
            Remnants.guardedNest(sys, proxima, 180f, 1900f, 120f, "Proxima Watch")
        }

        Relics.snapshot(sys)
        Uni.jumpPoint(sys, center, "sr_cen_jump", "Nadir Point", 180f, 11000f, 2000f)
        Uni.finishJumpPoints(sys)
        nebula(sys, 0f, 0f)
        Uni.hyperSweep(sys)

        sys.isProcgen = false
        sys.backgroundTextureFilename = "graphics/backgrounds/galaxy0.jpg"
        sys.addTag(Tags.THEME_DERELICT)
        sys.addTag(Tags.THEME_HIDDEN)
        sys.addTag(Tags.THEME_INTERESTING_MINOR)
        sys.addTag(Tags.THEME_RUINS)
        sys.addTag(Tags.THEME_SPECIAL)
        return sys
    }

    private fun betaCentauri(sector: SectorAPI, alpha: StarSystemAPI?): StarSystemAPI {
        val sys = sector.createStarSystem("Beta Centauri")
        sys.age = StarAge.OLD
        val preferred = if (alpha != null) Vector2f(alpha.location.x + 6000f, alpha.location.y + 3000f) else Vector2f(-52000f, -34000f)
        sys.location.set(Uni.findFreeSpot(preferred, 5000f))
        val star = sys.initStar("sr_cen_hadar", "star_blue_giant", 800f, 500f)
        star.name = "Hadar"
        nebula(sys, 0f, 0f)
        sys.isProcgen = false
        sys.addTag(Tags.THEME_INTERESTING_MINOR)
        sys.autogenerateHyperspaceJumpPoints(false, true, false)
        Uni.hyperSweep(sys)
        return sys
    }

    private fun nebula(sys: StarSystemAPI, x: Float, y: Float) {
        val n = Misc.addNebulaFromPNG("data/campaign/terrain/eos_nebula.png", 0f, 1500f, sys, "terrain", "nebula", 4, 4, StarAge.AVERAGE)
        n.setLocation(x, y)
    }
}
