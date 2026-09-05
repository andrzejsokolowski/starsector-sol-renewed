package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.StarAge
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator
import com.fs.starfarer.api.impl.campaign.CoronalTapParticleScript
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.loading.specs.PlanetSpec
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.Random

/**
 * Builds the Sol system. Distances and sizes are gamified: moons are radius 50-75, planets 95-440,
 * Pluto at 12800 units, so the whole system is about the size of a large vanilla one.
 *
 * Layout (radius / orbit / days):
 * ```
 * Sol       840         hypershunt at 1150
 * Mercury   110  1600   80
 * Venus     170  2400  130
 * Earth     200  3300  200    Luna 55 @ 400 / 30
 * Mars      140  4200  280    Nidavellir (AOTD), Gungnir Dockyard
 * belt          5300
 * Jupiter   440  6600  450    Io 55 @ 700, Europa 55 @ 880, Ganymede 75 @ 1100, Callisto 65 @ 1360
 * Saturn    380  8300  600    Titan 70 @ 1400
 * Uranus    290  9900  800
 * Neptune   280 11400 1000
 * Pluto      95 12800 1300    Charon 50 @ 260, Pluto Mining Station (AOTD)
 * Kuiper        13800
 * ```
 */
object SolSystem {

    private val log = Global.getLogger(SolSystem::class.java)
    private const val MEM_IS_SOL = "\$sr_sol"
    const val MUSIC = "sr_music_sol"

    /** The Sol system of the current game, or null if this mod did not generate one. */
    fun find(): StarSystemAPI? = Global.getSector().starSystems.firstOrNull { it.memoryWithoutUpdate.getBoolean(MEM_IS_SOL) }

    fun generate(sector: SectorAPI) {
        val sys = sector.createStarSystem("Sol")
        sys.memoryWithoutUpdate.set(MEM_IS_SOL, true)
        val pos = Uni.findFreeSpot(Vector2f(SrSettings.posX, SrSettings.posY), 6000f)
        sys.location.set(pos)
        log.info(String.format("[SolRenewed] Sol placed at (%.0f, %.0f)", pos.x, pos.y))

        val star = makeStar(sys)
        configure(sys)
        Uni.constellation(sys, "Sol")

        val p = HashMap<String, PlanetAPI>()
        p["mercury"] = mercury(sys, star)
        p["venus"] = venus(sys, star)
        p["earth"] = earth(sys, star, p)
        p["mars"] = mars(sys, star)
        p["jupiter"] = jupiter(sys, star, p)
        p["saturn"] = saturn(sys, star, p)
        p["uranus"] = uranus(sys, star)
        p["neptune"] = neptune(sys, star)
        p["pluto"] = pluto(sys, star, p)

        belts(sys, star)
        infrastructure(sys, star, p)
        megastructures(sys, star, p)
        val gate = gate(sys, star)
        jumpPoints(sys, star)
        Derelicts.decorateGate(sys, gate)
        if (SrSettings.cryoPrison) CryoPrison.spawn(sys, star, 12200f, 270f)
        Remnants.populateSol(sys, star, p, SrSettings.remnantLevel)
        Uni.hyperSweep(sys)
    }

    // ------------------------------------------------------------------ star and system

    private fun makeStar(sys: StarSystemAPI): PlanetAPI {
        val star = sys.initStar("sr_sol_star", "star_yellow", 840f, 420f)
        star.name = "Sol"
        val spec = star.spec
        spec.setTexture(sprite("stars", "sr_sol"))
        spec.setCoronaTexture(sprite("coronas", "sr_sol_halo"))
        spec.setCloudTexture(sprite("clouds", "sr_clouds_star"))
        spec.setCloudRotation(-3f)
        spec.setRotation(0.44f)
        spec.setScaleMultStarscapeIcon(1.25f)
        spec.setIconColor(Color(240, 230, 120, 255))
        spec.setCloudColor(Color(250, 250, 50, 255))
        spec.setGlowColor(Color(248, 55, 106, 255))
        spec.setPlanetColor(Color(235, 235, 195, 255))
        spec.setUseReverseLightForGlow(false)
        (spec as? PlanetSpec)?.let {
            it.iconTexture = sprite("warroom", "sr_icon_sol")
            it.name = "G-Type Main-Sequence"
        }
        star.customDescriptionId = "sr_sol"
        star.applySpecChanges()
        return star
    }

    private fun configure(sys: StarSystemAPI) {
        sys.isProcgen = false
        sys.age = StarAge.AVERAGE
        sys.backgroundTextureFilename = "graphics/backgrounds/galaxy0.jpg"
        sys.lightColor = Color(255, 245, 225)
        sys.memoryWithoutUpdate.set("\$US_skipSystem", true)
        sys.memoryWithoutUpdate.set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, MUSIC)
        sys.addTag(Tags.THEME_DERELICT)
        sys.addTag(Tags.THEME_HIDDEN)
        sys.addTag(Tags.THEME_SPECIAL)
        sys.addTag(Tags.THEME_INTERESTING)
    }

    private fun sprite(category: String, key: String): String = Global.getSettings().getSpriteName(category, key)

    // ------------------------------------------------------------------ planets

    private fun mercury(sys: StarSystemAPI, star: PlanetAPI): PlanetAPI {
        val m = Uni.planet(sys, star, "sr_mercury", "Mercury", "barren-bombarded", 270f, 110f, 1600f, 80f)
        m.customDescriptionId = "sr_mercury"
        m.spec.setTexture(sprite("planets", "sr_mercury"))
        m.spec.setTilt(-0.03f)
        m.spec.setIconColor(Color(227, 151, 118, 255))
        m.spec.setRotation(0.5f)
        m.applySpecChanges()
        Uni.market(m, "neutral", "very_hot", "irradiated", "no_atmosphere", "low_gravity", "ore_ultrarich", "rare_ore_ultrarich")
        return m
    }

    private fun venus(sys: StarSystemAPI, star: PlanetAPI): PlanetAPI {
        val v = Uni.planet(sys, star, "sr_venus", "Venus", "toxic", 180f, 170f, 2400f, 130f)
        v.customDescriptionId = "sr_venus"
        v.spec.setTexture(sprite("planets", "sr_venus"))
        v.spec.setCloudTexture(sprite("clouds", "sr_clouds_heavy"))
        v.spec.setTilt(177.4f)
        v.spec.setAtmosphereColor(Color(140, 60, 80, 255))
        v.spec.setAtmosphereThickness(1.0f)
        v.spec.setAtmosphereThicknessMin(5.0f)
        v.spec.setCloudColor(Color(250, 250, 50, 25))
        v.spec.setCloudRotation(0.5f)
        v.spec.setRotation(-0.3f)
        v.applySpecChanges()
        v.addTag(Tags.NOT_RANDOM_MISSION_TARGET)
        val vm = Uni.market(v, "neutral", "very_hot", "dense_atmosphere", "toxic_atmosphere", "irradiated",
            "extreme_tectonic_activity", "ore_ultrarich", "rare_ore_ultrarich")
        ModCheck.cond(vm, "aotd_vok", "pre_collapse_facility")
        return v
    }

    private fun earth(sys: StarSystemAPI, star: PlanetAPI, p: MutableMap<String, PlanetAPI>): PlanetAPI {
        val type = if (ModCheck.hasUS) "US_continent" else "terran"
        val e = Uni.planet(sys, star, "sr_earth", "Earth", type, 90f, 200f, 3300f, 200f)
        (e.spec as? PlanetSpec)?.name = "Terrestrial"
        e.spec.setTexture(sprite("planets", "sr_earth"))
        e.spec.setCloudTexture(sprite("clouds", "sr_clouds_earth"))
        e.spec.setAtmosphereColor(Color(50, 150, 255, 150))
        e.spec.setAtmosphereThickness(1.0f)
        e.spec.setAtmosphereThicknessMin(10.0f)
        e.spec.setCloudColor(Color(230, 240, 250, 180))
        e.spec.setIconColor(Color(155, 185, 255, 255))
        e.spec.setCloudRotation(-2.5f)
        e.spec.setTilt(-23.4f)
        e.spec.setRotation(6.0f)
        e.applySpecChanges()
        e.customDescriptionId = "sr_earth"
        val em = Uni.market(e, "neutral", "habitable", "farmland_poor", "mild_climate", "pollution", "decivilized_subpop",
            "ore_ultrarich", "organics_plentiful", "ruins_vast")
        ModCheck.cond(em, "US", "US_elevator", "US_religious", "US_magnetic", "US_base")
        ModCheck.cond(em, "IndEvo", "IndEvo_RuinsCondition")
        ModCheck.mem(e, "IndEvo", "\$IndEvo_ruinsIndustryId", "IndEvo_Memorial")
        ModCheck.tag(e, "IndEvo", Tags.NOT_RANDOM_MISSION_TARGET)

        val luna = Uni.planet(sys, e, "sr_luna", "Luna", "barren", 0f, 55f, 400f, 30f)
        luna.customDescriptionId = "sr_luna"
        luna.spec.setTexture(sprite("planets", "sr_luna"))
        luna.setInteractionImage("illustrations", "sr_luna_bootprint")
        luna.spec.setTilt(-5.14f)
        luna.spec.setRotation(0.22f)
        luna.applySpecChanges()
        val lm = Uni.market(luna, "neutral", "ruins_extensive", "no_atmosphere", "low_gravity", "ore_sparse", "rare_ore_rich", "volatiles_trace")
        ModCheck.cond(lm, "US", "US_base", "US_tunnels", "US_crystals")
        ModCheck.cond(lm, "assortment_of_things", "rat_ancient_industries")
        ModCheck.tag(luna, "assortment_of_things", "rat_relic_condition", "rat_ancient_industries")
        p["luna"] = luna
        return e
    }

    private fun mars(sys: StarSystemAPI, star: PlanetAPI): PlanetAPI {
        val m = Uni.planet(sys, star, "sr_mars", "Mars", "barren-desert", 0f, 140f, 4200f, 280f)
        m.customDescriptionId = "sr_mars"
        m.spec.setTexture(sprite("planets", "sr_mars"))
        m.spec.setCloudTexture(sprite("clouds", "sr_clouds_mars"))
        m.spec.setCloudRotation(-6.3f)
        m.spec.setTilt(-25.2f)
        m.spec.setIconColor(Color(255, 68, 14, 250))
        m.spec.setRotation(11.6f)
        m.applySpecChanges()
        val mm = Uni.market(m, "neutral", "cold", "thin_atmosphere", "low_gravity", "ruins_extensive", "ore_moderate", "rare_ore_moderate",
            "volatiles_diffuse")
        if (SrSettings.nidavellir) ModCheck.cond(mm, "aotd_vok", "aotd_nidavelir_complex")
        ModCheck.cond(mm, "IndEvo", "IndEvo_RuinsCondition")
        ModCheck.mem(m, "IndEvo", "\$IndEvo_ruinsIndustryId", "IndEvo_HullDecon")
        ModCheck.cond(mm, "US", "US_elevator", "US_shrooms")
        ModCheck.cond(mm, "assortment_of_things", "rat_ancient_military_hub", "rat_kinetic_launchsystem")
        ModCheck.tag(m, "assortment_of_things", "rat_relic_condition", "rat_ancient_military_hub", "rat_kinetic_launchsystem")
        m.addTag(Tags.NOT_RANDOM_MISSION_TARGET)
        return m
    }

    private fun jupiter(sys: StarSystemAPI, star: PlanetAPI, p: MutableMap<String, PlanetAPI>): PlanetAPI {
        val type = if (ModCheck.hasUS) "US_gas_giant" else "gas_giant"
        val j = Uni.planet(sys, star, "sr_jupiter", "Jupiter", type, 150f, 440f, 6600f, 450f)
        j.customDescriptionId = "sr_jupiter"
        j.spec.setTexture(sprite("planets", "sr_jupiter"))
        j.spec.setCloudTexture(sprite("clouds", "sr_clouds_giant"))
        j.spec.setAtmosphereThickness(1.0f)
        j.spec.setAtmosphereThicknessMin(10.0f)
        j.spec.setCloudColor(Color(120, 150, 180, 100))
        j.spec.setCloudRotation(-43.3f)
        j.spec.setTilt(-3.1f)
        j.spec.setRotation(14.6f)
        j.applySpecChanges()
        j.addTag(Tags.NOT_RANDOM_MISSION_TARGET)
        val jm = Uni.market(j, "neutral", "dense_atmosphere", "high_gravity", "extreme_weather", "volatiles_plentiful", "ruins_extensive", "meteor_impacts")
        ModCheck.cond(jm, "US", "US_base", "US_floating")
        ModCheck.cond(jm, "assortment_of_things", "rat_ancient_fuel_hub")
        ModCheck.tag(j, "assortment_of_things", "rat_relic_condition", "rat_ancient_fuel_hub")
        ModCheck.cond(jm, "IndEvo", "IndEvo_ArtilleryStationCondition")
        sys.addRingBand(j, "misc", "rings_ice0", 128f, 1, Color(190, 150, 50, 255), 128f, 510f, -60f, "ring", "Amalthea Ring")
        sys.addRingBand(j, "misc", "rings_dust0", 128f, 0, Color(190, 120, 20, 255), 128f, 580f, -75f, "ring", "Thebe Ring")
        Uni.magneticField(sys, j, 490f, 1050f)

        val io = Uni.planet(sys, j, "sr_io", "Io", "lava", 120f, 55f, 700f, 6f)
        io.customDescriptionId = "sr_io"
        io.spec.setTexture(sprite("planets", "sr_io"))
        io.spec.setTilt(-1.0f)
        io.spec.setCloudRotation(-1.7f)
        io.spec.setPlanetColor(Color(238, 235, 206, 255))
        io.spec.setGlowColor(Color(250, 150, 75, 225))
        io.spec.setUseReverseLightForGlow(true)
        io.spec.setRotation(3.4f)
        io.applySpecChanges()
        Uni.market(io, "neutral", "poor_light", "very_hot", "irradiated", "thin_atmosphere", "low_gravity", "extreme_tectonic_activity",
            "ore_abundant", "rare_ore_abundant")
        p["io"] = io

        val europa = Uni.planet(sys, j, "sr_europa", "Europa", "rocky_ice", 0f, 55f, 880f, 9f)
        europa.customDescriptionId = "sr_europa"
        europa.spec.setTexture(sprite("planets", "sr_europa"))
        europa.spec.setTilt(1.8f)
        europa.spec.setCloudRotation(1.5f)
        europa.spec.setRotation(1.7f)
        europa.applySpecChanges()
        val eum = Uni.market(europa, "neutral", "cold", "irradiated", "low_gravity", "thin_atmosphere", "poor_light", "volatiles_plentiful",
            "ore_moderate", "rare_ore_moderate", "ruins_scattered")
        ModCheck.cond(eum, "assortment_of_things", "rat_bionic_plantlife")
        p["europa"] = europa

        val ganymede = Uni.planet(sys, j, "sr_ganymede", "Ganymede", "rocky_ice", 240f, 75f, 1100f, 14f)
        ganymede.customDescriptionId = "sr_ganymede"
        ganymede.spec.setTexture(sprite("planets", "sr_ganymede"))
        ganymede.spec.setTilt(-23.5f)
        ganymede.spec.setRotation(0.84f)
        ganymede.applySpecChanges()
        val gm = Uni.market(ganymede, "neutral", "cold", "thin_atmosphere", "low_gravity", "poor_light", "ore_moderate", "rare_ore_moderate",
            "ruins_widespread", "volatiles_abundant")
        ModCheck.cond(gm, "assortment_of_things", "rat_rampant_military_core")
        ModCheck.tag(ganymede, "assortment_of_things", "rat_relic_condition", "rat_rampant_military_core")
        ModCheck.cond(gm, "US", "US_bedrock")
        p["ganymede"] = ganymede

        val callisto = Uni.planet(sys, j, "sr_callisto", "Callisto", "rocky_ice", 160f, 65f, 1360f, 22f)
        callisto.customDescriptionId = "sr_callisto"
        callisto.spec.setTexture(sprite("planets", "sr_callisto"))
        callisto.spec.setTilt(-23.5f)
        callisto.spec.setRotation(0.36f)
        callisto.applySpecChanges()
        val cm = Uni.market(callisto, "neutral", "cold", "no_atmosphere", "low_gravity", "poor_light", "ore_abundant", "rare_ore_abundant",
            "ruins_widespread", "volatiles_abundant")
        ModCheck.cond(cm, "aotd_vok", "pre_collapse_facility")
        ModCheck.cond(cm, "US", "US_tunnels", "US_crystals")
        callisto.addTag(Tags.NOT_RANDOM_MISSION_TARGET)
        p["callisto"] = callisto
        return j
    }

    private fun saturn(sys: StarSystemAPI, star: PlanetAPI, p: MutableMap<String, PlanetAPI>): PlanetAPI {
        val type = if (ModCheck.hasUS) "US_gas_giantB" else "gas_giant"
        val s = Uni.planet(sys, star, "sr_saturn", "Saturn", type, 300f, 380f, 8300f, 600f)
        s.customDescriptionId = "sr_saturn"
        s.spec.setTexture(sprite("planets", "sr_saturn"))
        s.spec.setCloudTexture(sprite("clouds", "sr_clouds_giant"))
        s.spec.setCloudColor(Color(180, 160, 140, 15))
        s.spec.setIconColor(Color(220, 200, 170, 150))
        s.spec.setTilt(-26.7f)
        s.spec.setRotation(13.3f)
        s.spec.setAtmosphereColor(Color(220, 200, 170, 50))
        s.spec.setAtmosphereThickness(1.0f)
        s.spec.setAtmosphereThicknessMin(5.0f)
        s.applySpecChanges()
        val sm = Uni.market(s, "neutral", "ruins_extensive", "volatiles_plentiful", "extreme_weather", "dense_atmosphere", "high_gravity")
        ModCheck.cond(sm, "US", "US_floating", "US_fluorescent")
        ModCheck.cond(sm, "assortment_of_things", "rat_ancient_fuel_hub")
        ModCheck.tag(s, "assortment_of_things", "rat_relic_condition", "rat_ancient_fuel_hub")
        val belt = "sr_saturn_belt"
        sys.addRingBand(s, "misc", belt, 256f, 0, Color(200, 180, 150, 150), 200f, 540f, -80f, "ring", "C Ring")
        sys.addRingBand(s, "misc", belt, 256f, 1, Color(220, 200, 170, 150), 200f, 740f, -95f, "ring", "B Ring")
        sys.addRingBand(s, "misc", belt, 256f, 2, Color(210, 190, 160, 150), 200f, 940f, -110f, "ring", "A Ring")
        sys.addRingBand(s, "misc", belt, 256f, 3, Color(180, 160, 140, 150), 200f, 1140f, -125f, "ring", "Cassini Division")

        val titan = Uni.planet(sys, s, "sr_titan", "Titan", "toxic_cold", 0f, 70f, 1400f, 25f)
        titan.customDescriptionId = "sr_titan"
        titan.spec.setTexture(sprite("planets", "sr_titan"))
        titan.spec.setCloudTexture(sprite("clouds", "sr_clouds_titan"))
        titan.spec.setCloudRotation(0.2f)
        titan.spec.setAtmosphereColor(Color(235, 225, 125, 200))
        titan.spec.setTilt(-23.5f)
        titan.spec.setRotation(0.38f)
        titan.applySpecChanges()
        val tm = Uni.market(titan, "neutral", "very_cold", "dense_atmosphere", "toxic_atmosphere", "poor_light", "ore_moderate", "rare_ore_moderate",
            "volatiles_plentiful", "organics_plentiful")
        ModCheck.cond(tm, "US", "US_crash", "US_bedrock", "US_base")
        ModCheck.cond(tm, "assortment_of_things", "rat_warscape")
        ModCheck.tag(titan, "assortment_of_things", "rat_relic_condition", "rat_warscape")
        p["titan"] = titan
        return s
    }

    private fun uranus(sys: StarSystemAPI, star: PlanetAPI): PlanetAPI {
        val type = if (ModCheck.hasUS) "US_iceA" else "gas_giant"
        val u = Uni.planet(sys, star, "sr_uranus", "Uranus", type, 0f, 290f, 9900f, 800f)
        u.customDescriptionId = "sr_uranus"
        u.spec.setTexture(sprite("planets", "sr_uranus"))
        u.spec.setCloudTexture(sprite("clouds", "sr_clouds_swept"))
        u.spec.setCloudColor(Color(240, 120, 250, 30))
        u.spec.setAtmosphereColor(Color(250, 120, 250, 15))
        u.spec.setAtmosphereThickness(0.2f)
        u.spec.setAtmosphereThicknessMin(2.0f)
        u.spec.setIconColor(Color(150, 170, 180, 255))
        u.spec.setTilt(82.2f)
        u.spec.setRotation(16.7f)
        u.spec.setGlowColor(Color(150, 170, 180, 255))
        u.spec.setUseReverseLightForGlow(true)
        u.applySpecChanges()
        val um = Uni.market(u, "neutral", "poor_light", "extreme_weather", "dense_atmosphere", "toxic_atmosphere", "very_cold", "high_gravity",
            "volatiles_abundant")
        ModCheck.cond(um, "US", "US_cryosanctum")
        sys.addRingBand(u, "misc", "rings_ice0", 256f, 3, Color(250, 120, 250, 50), 128f, 400f, -45f, "ring", "Epsilon Ring")
        return u
    }

    private fun neptune(sys: StarSystemAPI, star: PlanetAPI): PlanetAPI {
        val type = if (ModCheck.hasUS) "US_iceB" else "gas_giant"
        val n = Uni.planet(sys, star, "sr_neptune", "Neptune", type, 60f, 280f, 11400f, 1000f)
        n.customDescriptionId = "sr_neptune"
        n.spec.setTexture(sprite("planets", "sr_neptune"))
        n.spec.setCloudTexture(sprite("clouds", "sr_clouds_swept"))
        n.spec.setCloudColor(Color(130, 140, 150, 20))
        n.spec.setTilt(-28.3f)
        n.spec.setRotation(8.3f)
        n.spec.setGlowColor(Color(255, 255, 255, 255))
        n.spec.setAtmosphereThicknessMin(3.25f)
        n.spec.setAtmosphereColor(Color(100, 150, 250, 100))
        n.spec.setAtmosphereThickness(1.0f)
        n.spec.setIconColor(Color(70, 70, 250, 255))
        n.applySpecChanges()
        val nm = Uni.market(n, "neutral", "dark", "very_cold", "high_gravity", "extreme_weather", "dense_atmosphere",
            "volatiles_abundant")
        ModCheck.cond(nm, "US", "US_cryosanctum")
        sys.addRingBand(n, "misc", "rings_ice0", 256f, 3, Color(140, 160, 190, 255), 128f, 380f, -60f, "ring", "Le Verrier Ring")
        sys.addRingBand(n, "misc", "rings_dust0", 256f, 2, Color(130, 150, 180, 255), 128f, 500f, -90f, "ring", "Galle Ring")
        return n
    }

    private fun pluto(sys: StarSystemAPI, star: PlanetAPI, p: MutableMap<String, PlanetAPI>): PlanetAPI {
        val pl = Uni.planet(sys, star, "sr_pluto", "Pluto", "frozen", 240f, 95f, 12800f, 1300f)
        pl.customDescriptionId = "sr_pluto"
        pl.spec.setTexture(sprite("planets", "sr_pluto"))
        pl.spec.setTilt(-122.5f)
        pl.spec.setRotation(1.9f)
        pl.applySpecChanges()
        val pm = Uni.market(pl, "neutral", "dark", "low_gravity", "very_cold", "thin_atmosphere", "ore_ultrarich", "rare_ore_ultrarich", "meteor_impacts")
        if (SrSettings.plutoStation) ModCheck.cond(pm, "aotd_vok", "aotd_pluto_station")
        pl.addTag(Tags.NEUTRINO_HIGH)
        pl.addTag(Tags.NOT_RANDOM_MISSION_TARGET)

        val charon = Uni.planet(sys, pl, "sr_charon", "Charon", "rocky_ice", 180f, 50f, 260f, 12f)
        charon.customDescriptionId = "sr_charon"
        charon.spec.setTexture(sprite("planets", "sr_charon"))
        charon.spec.setTilt(-23.5f)
        charon.spec.setRotation(0.94f)
        charon.applySpecChanges()
        val cm = Uni.market(charon, "neutral", "dark", "very_cold", "no_atmosphere", "low_gravity", "ore_ultrarich", "rare_ore_ultrarich",
            "meteor_impacts")
        ModCheck.cond(cm, "IndEvo", "IndEvo_RuinsCondition")
        ModCheck.mem(charon, "IndEvo", "\$IndEvo_ruinsIndustryId", "IndEvo_ResLab")
        p["charon"] = charon
        return pl
    }

    // ------------------------------------------------------------------ dressing

    private fun belts(sys: StarSystemAPI, star: PlanetAPI) {
        sys.addAsteroidBelt(star, 400, 5300f, 500f, 250f, 400f, Terrain_ASTEROID_BELT, "Main Belt")
        sys.addRingBand(star, "misc", "rings_dust0", 256f, 3, Color(175, 140, 100, 40), 512f, 5300f, 350f)
        sys.addOrbitalJunk(star, "orbital_junk", 120, 16f, 32f, 5300f, 400f, 250f, 400f, 100f, 200f)
        sys.addAsteroidBelt(star, 250, 13800f, 600f, 900f, 1400f, Terrain_ASTEROID_BELT, "Kuiper Belt")
        sys.addRingBand(star, "misc", "rings_ice0", 256f, 2, Color(175, 100, 75, 25), 1024f, 13800f, 1200f)
    }

    private const val Terrain_ASTEROID_BELT = "asteroid_belt"

    private fun infrastructure(sys: StarSystemAPI, star: PlanetAPI, p: Map<String, PlanetAPI>) {
        val earth = p.getValue("earth")
        // Lagrange points on Earth's orbit.
        Uni.entity(sys, star, "sr_stable1", "Leading Point", "stable_location", null, earth.circularOrbitAngle + 60f, 3300f, 200f)
        Uni.entity(sys, star, "sr_stable2", "Trailing Point", "stable_location", null, earth.circularOrbitAngle - 60f, 3300f, 200f)
        val relay = Uni.entity(sys, star, "sr_dsn", "Deep Space Network", "comm_relay", "neutral", 305f, 5000f, 330f)
        relay.customDescriptionId = "sr_dsn"
        val sensor = Uni.entity(sys, star, "sr_ssn", "Space Surveillance Network", "sensor_array", "neutral", 180f, 7600f, 520f)
        sensor.customDescriptionId = "sr_ssn"
        Uni.entity(sys, star, "sr_hubble", "Hubble Buoy", "nav_buoy", "neutral", 90f, 9100f, 700f)

        // Gungnir Dockyard: an abandoned station over Mars with the storage still full.
        val mars = p.getValue("mars")
        val dock = Uni.entity(sys, mars, "sr_gungnir", "Gungnir Dockyard", "orbital_dockyard", "neutral", 0f, mars.radius + 300f, 60f)
        dock.customDescriptionId = "sr_gungnir"
        Misc.setAbandonedStationMarket("sr_gungnir_market", dock)
        Loot.fillGungnirDockyard(dock)

        // Two sleepers: a Domain cryosleeper past Pluto, and the Prometheus ark near Neptune.
        val random = Random(sys.name.hashCode().toLong())
        val foundation = Uni.entity(sys, star, "sr_foundation", null, "derelict_cryosleeper", "derelict", 210f, 13300f, 1500f, random)
        foundation.name = foundation.name + " \"Foundation\""
        sys.addTag(Tags.THEME_DERELICT_CRYOSLEEPER)
        val neptune = p.getValue("neptune")
        if (ModCheck.hasDoP && sys.getEntitiesWithTag("aotd_cryosleeper").isEmpty()) {
            val ark = sys.addCustomEntity("sr_prometheus", "Prometheus Initiative", "ark", "neutral")
            ark.setCircularOrbit(neptune, 90f, neptune.radius + 700f, 120f)
            ark.memoryWithoutUpdate.set("\$salvageSeed", random.nextLong())
            ark.memoryWithoutUpdate.set("\$salvageSpecId", "ark")
            ark.memoryWithoutUpdate.set("\$hasDefenders", true)
        }
    }

    private fun megastructures(sys: StarSystemAPI, star: PlanetAPI, p: Map<String, PlanetAPI>) {
        if (!SrSettings.hypershunt) return
        val tap = BaseThemeGenerator.addSalvageEntity(Random(), sys, "coronal_tap", "neutral")
        tap.id = "sr_hypershunt"
        tap.setCircularOrbitPointingDown(star, 270f, 1150f, 55f)
        sys.addScript(CoronalTapParticleScript(tap))
        sys.addTag(Tags.HAS_CORONAL_TAP)
        log.info("[SolRenewed] Hypershunt added around Sol.")
    }

    private fun gate(sys: StarSystemAPI, star: PlanetAPI): SectorEntityToken {
        val gate = Uni.entity(sys, star, "sr_gate", "Sol Gate", "inactive_gate", null, 300f, 4700f, 320f)
        return gate
    }

    private fun jumpPoints(sys: StarSystemAPI, star: PlanetAPI) {
        val inner = Uni.jumpPoint(sys, star, "sr_jump_inner", "Inner System Jump-point", 45f, 2850f, 160f)
        val fringe = Uni.jumpPoint(sys, star, "sr_jump_fringe", "Fringe Jump-point", 30f, 12000f, 1150f)
        Uni.finishJumpPoints(sys)
        Uni.asteroidField(sys, inner, 45f, 180f, 60f, 6, "Newtonian Debris")
        Uni.asteroidField(sys, inner, 225f, 180f, 60f, 6, "Newtonian Debris")
        Uni.asteroidField(sys, fringe, -45f, 220f, 80f, 6, "Northern Shrapnel Field")
        Uni.asteroidField(sys, fringe, -225f, 220f, 80f, 6, "Southern Shrapnel Field")
        sys.addOrbitalJunk(inner, "orbital_junk", 6, 16f, 16f, 90f, 128f, -30f, -60f, 100f, 200f)
        sys.addOrbitalJunk(fringe, "orbital_junk", 6, 16f, 16f, 90f, 128f, -30f, -60f, 100f, 200f)
    }
}
