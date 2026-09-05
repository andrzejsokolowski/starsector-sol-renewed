package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin
import com.fs.starfarer.api.impl.campaign.ids.Entities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import java.util.Random

/** Wrecks, caches and debris: the things that make Sol feel like a graveyard worth picking over. */
object Derelicts {

    private val log = Global.getLogger(Derelicts::class.java)
    private val rng = Random()

    /** A random recoverable wreck from a weighted mix of factions, with d-mods and a built-in s-mod. */
    fun random(sys: StarSystemAPI, focus: SectorEntityToken, angle: Float, radius: Float) {
        val params = DerelictShipEntityPlugin.createRandom(pickFaction(), null, rng) ?: return
        params.ship.addDmods = true
        params.ship.sModProb = 1f
        val wreck = BaseThemeGenerator.addSalvageEntity(rng, sys, Entities.WRECK, Factions.NEUTRAL, params)
        wreck.addTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY)
        wreck.isExpired = false
        wreck.isDiscoverable = true
        Misc.setSalvageSpecial(wreck, params)
        wreck.setCircularOrbit(focus, angle, radius, Math.max(200f, radius * 0.1f))
    }

    /** A specific hull in a given condition (used for the Radiant prizes around the Centauri nest). */
    fun specific(sys: StarSystemAPI, focus: SectorEntityToken, angle: Float, radius: Float, variantId: String, condition: ShipRecoverySpecial.ShipCondition) {
        val params = try {
            DerelictShipEntityPlugin.createVariant(variantId, rng, 1f)
        } catch (t: Throwable) {
            log.warn("[SolRenewed] No variant '$variantId' for a derelict; skipped.")
            return
        } ?: return
        params.ship.condition = condition
        params.ship.addDmods = true
        params.ship.sModProb = 1f
        val wreck = BaseThemeGenerator.addSalvageEntity(rng, sys, Entities.WRECK, Factions.NEUTRAL, params)
        wreck.addTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY)
        wreck.isExpired = false
        wreck.isDiscoverable = true
        wreck.setCircularOrbit(focus, angle, radius, Math.max(200f, radius * 0.1f))
    }

    /** `count` random wrecks spread evenly around `focus`, somewhere between the two radii. */
    fun wreckRing(sys: StarSystemAPI, focus: SectorEntityToken, count: Int, minRadius: Float, maxRadius: Float) {
        val step = 360f / Math.max(1, count)
        for (i in 0 until count) {
            random(sys, focus, i * step + rng.nextFloat() * step * 0.5f, minRadius + rng.nextFloat() * (maxRadius - minRadius))
        }
    }

    /**
     * Dresses up the gate: a defended debris field, six wrecks, two caches, a probe, a survey ship,
     * a Domain-era mothership and a gate hauler. Sol gets the full set; there is only one gate.
     */
    fun decorateGate(sys: StarSystemAPI, gate: SectorEntityToken) {
        val random = Random(gate.id.hashCode().toLong())
        val debris = DebrisFieldTerrainPlugin.DebrisFieldParams(1000f, 0.5f, 999999f, 0f)
        debris.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED
        debris.name = "Derelict Breakfield"
        debris.middleRadius = 500f
        debris.defFaction = Factions.DERELICT
        debris.defenderProb = 1f
        debris.minStr = 30
        debris.maxStr = 50
        debris.maxDefenderSize = 100
        val field = Misc.addDebrisField(sys, debris, random)
        field.addTag("debris")
        field.isDiscoverable = true
        field.memoryWithoutUpdate.set("\$hasDefenders", true)
        field.setCircularOrbit(gate, random.nextFloat() * 360f, 600f, 2600f)

        repeat(6) { random(sys, gate, random.nextFloat() * 360f, 550f + random.nextFloat() * 150f) }

        salvage(sys, gate, random, "large_cache", 500f, 2000f)
        salvage(sys, gate, random, "supply_cache", 1100f, 3000f)
        salvage(sys, gate, random, "derelict_probe", 650f, 2000f)
        salvage(sys, gate, random, "derelict_survey_ship", 750f, 2300f)
        val mother = salvage(sys, gate, random, "derelict_mothership", 900f, 3500f)
        mother.name = "Domain-era Mothership"
        mother.sensorProfile = 700f
        sys.addTag(Tags.THEME_DERELICT_MOTHERSHIP)
        val hauler = salvage(sys, gate, random, "derelict_gatehauler", 1300f, 4200f)
        hauler.name = "Domain-era Gate Hauler"
        hauler.sensorProfile = 700f
        hauler.memoryWithoutUpdate.set("\$gateHauler", true)
    }

    private fun salvage(sys: StarSystemAPI, focus: SectorEntityToken, random: Random, type: String, radius: Float, days: Float): SectorEntityToken {
        val e = BaseThemeGenerator.addSalvageEntity(random, sys, type, Factions.NEUTRAL)
        e.setCircularOrbit(focus, random.nextFloat() * 360f, radius, days)
        return e
    }

    private fun pickFaction(): String {
        val picker = WeightedRandomPicker<String>(rng)
        picker.add(Factions.PIRATES, 5f)
        picker.add(Factions.INDEPENDENT, 5f)
        picker.add(Factions.DERELICT, 4f)
        picker.add(Factions.PLAYER, 4f)
        picker.add(Factions.MERCENARY, 3f)
        picker.add(Factions.REMNANTS, 3f)
        picker.add(Factions.OMEGA, 1f)
        return picker.pick()
    }
}
