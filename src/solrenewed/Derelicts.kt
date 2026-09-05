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
import java.util.Random

/** Debris, a few Domain leftovers and specific wrecks. No random derelicts and no caches: bloat. */
object Derelicts {

    private val log = Global.getLogger(Derelicts::class.java)
    private val rng = Random()

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

    /**
     * Dresses up the gate: a defended debris field, a probe, a survey ship and a Domain-era
     * mothership. No caches: outside the sector's own generation they would come up empty. No gate hauler (vanilla has exactly one and that stays true).
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

        salvage(sys, gate, random, "derelict_probe", 650f, 2000f)
        salvage(sys, gate, random, "derelict_survey_ship", 750f, 2300f)
        val mother = salvage(sys, gate, random, "derelict_mothership", 900f, 3500f)
        mother.name = "Domain-era Mothership"
        mother.sensorProfile = 700f
        sys.addTag(Tags.THEME_DERELICT_MOTHERSHIP)
    }

    private fun salvage(sys: StarSystemAPI, focus: SectorEntityToken, random: Random, type: String, radius: Float, days: Float): SectorEntityToken {
        val e = BaseThemeGenerator.addSalvageEntity(random, sys, type, Factions.NEUTRAL)
        e.setCircularOrbit(focus, random.nextFloat() * 360f, radius, days)
        return e
    }
}
