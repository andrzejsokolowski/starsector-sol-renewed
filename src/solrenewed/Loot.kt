package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.DModManager
import com.fs.starfarer.api.util.WeightedRandomPicker
import java.util.Random

/**
 * What the abandoned Gungnir Dockyard has in storage. A trimmed version of Bugatti's loot tables:
 * vanilla items, plus Ashes of the Domain materials when that mod is on. No superweapons of any kind.
 */
object Loot {

    private val log = Global.getLogger(Loot::class.java)
    private val rng = Random()

    private class Entry(val id: String, val weight: Float, val min: Int, val max: Int, val mod: String? = null) {
        val allowed: Boolean get() = mod == null || ModCheck.mod(mod)
        fun qty(): Int = if (min >= max) min else min + rng.nextInt(max - min + 1)
    }

    private val WEAPONS = listOf(
        Entry("lightmg", 100f, 1, 3), Entry("heavymg", 100f, 1, 3), Entry("lightac", 20f, 1, 3), Entry("lightmortar", 20f, 1, 3),
        Entry("heavyac", 20f, 1, 3), Entry("heavymortar", 20f, 1, 3), Entry("mininglaser", 100f, 1, 3), Entry("miningblaster", 50f, 1, 3),
        Entry("railgun", 30f, 1, 2), Entry("heavymauler", 20f, 1, 2), Entry("hveldriver", 20f, 1, 2), Entry("gauss", 5f, 1, 1),
    )
    private val COMMODITIES = listOf(
        Entry("food", 100f, 20, 100), Entry("organics", 100f, 10, 100), Entry("metals", 100f, 20, 100), Entry("ore", 100f, 20, 100),
        Entry("rare_ore", 50f, 5, 50), Entry("rare_metals", 50f, 5, 50), Entry("volatiles", 50f, 5, 50), Entry("heavy_machinery", 50f, 5, 50),
        Entry("domestic_goods", 20f, 5, 50), Entry("hand_weapons", 10f, 5, 50), Entry("luxury_goods", 10f, 5, 50),
        Entry("gamma_core", 50f, 1, 1), Entry("beta_core", 20f, 1, 1), Entry("alpha_core", 10f, 1, 1),
        Entry("advanced_components", 50f, 1, 50, "aotd_vok"), Entry("refined_metal", 50f, 1, 50, "aotd_vok"),
        Entry("purified_rare_metal", 20f, 1, 50, "aotd_vok"), Entry("research_databank", 50f, 1, 10, "aotd_vok"),
        Entry("domain_heavy_machinery", 20f, 1, 50, "aotd_vok"),
    )
    private val SPECIALS = listOf(
        Entry("corrupted_nanoforge", 20f, 1, 1), Entry("pristine_nanoforge", 10f, 1, 1), Entry("synchrotron", 10f, 1, 1),
        Entry("orbital_fusion_lamp", 10f, 1, 1), Entry("mantle_bore", 10f, 1, 1), Entry("catalytic_core", 20f, 1, 1),
        Entry("soil_nanites", 20f, 1, 1), Entry("biofactory_embryo", 20f, 1, 1), Entry("fullerene_spool", 20f, 1, 1),
        Entry("plasma_dynamo", 20f, 1, 1), Entry("cryoarithmetic_engine", 20f, 1, 1), Entry("drone_replicator", 20f, 1, 1),
        Entry("dealmaker_holosuite", 20f, 1, 1), Entry("low_tech_package", 10f, 1, 1), Entry("midline_package", 10f, 1, 1),
        Entry("high_tech_package", 10f, 1, 1), Entry("missile_package", 10f, 1, 1), Entry("XIV_package", 10f, 1, 1),
        Entry("heg_aux_package", 10f, 1, 1),
        Entry("aotd_shrouded_nanoforge", 10f, 1, 1, "aotd_vok"), Entry("aotd_shrouded_synchrotron", 10f, 1, 1, "aotd_vok"),
        Entry("aotd_atmospheric_driver", 10f, 1, 1, "aotd_vok"), Entry("aotd_shrouded_catalytic_core", 10f, 1, 1, "aotd_vok"),
    )
    private val HULLMODS = listOf(
        Entry("additional_berthing", 100f, 1, 1), Entry("augmentedengines", 100f, 1, 1), Entry("auxiliary_fuel_tanks", 100f, 1, 1),
        Entry("efficiency_overhaul", 100f, 1, 1), Entry("expanded_cargo_holds", 100f, 1, 1), Entry("hiressensors", 100f, 1, 1),
        Entry("surveying_equipment", 100f, 1, 1), Entry("insulatedengine", 20f, 1, 1), Entry("militarized_subsystems", 20f, 1, 1),
        Entry("solar_shielding", 20f, 1, 1), Entry("autorepair", 20f, 1, 1),
    )
    private val SHIPS = listOf(
        Entry("lasher_Standard", 100f, 1, 1), Entry("lasher_Assault", 100f, 1, 1), Entry("falcon_Attack", 100f, 1, 1),
        Entry("eagle_Assault", 100f, 1, 1), Entry("wolf_Assault", 50f, 1, 1), Entry("wolf_Strike", 50f, 1, 1),
        Entry("hammerhead_Balanced", 50f, 1, 1), Entry("enforcer_Assault", 50f, 1, 1), Entry("dominator_Assault", 20f, 1, 1),
    )

    /** The one abandoned station that survives from Bugatti's set: a military dockyard over Mars. */
    fun fillGungnirDockyard(station: SectorEntityToken) {
        val cargo = station.market?.getSubmarket("storage")?.cargo ?: return
        cargo.addFuel(scale(100, 300, 1.2f).toFloat())
        cargo.addSupplies(scale(50, 150, 1.2f).toFloat())
        cargo.addMarines(scale(30, 60, 1.2f))
        roll(WEAPONS, scale(4, 7, 1f)) { e -> cargo.addWeapons(e.id, e.qty()) }
        roll(COMMODITIES, scale(3, 6, 1f)) { e -> cargo.addCommodity(e.id, e.qty().toFloat()) }
        roll(SPECIALS, 1) { e -> cargo.addSpecial(SpecialItemData(e.id, null), 1f) }
        roll(HULLMODS, scale(1, 3, 1f)) { e -> cargo.addHullmods(e.id, 1) }
        roll(SHIPS, scale(1, 2, 1f)) { e -> addShip(cargo, e.id) }
        log.info("[SolRenewed] Gungnir Dockyard stocked.")
    }

    private fun addShip(cargo: CargoAPI, variantId: String) {
        cargo.addMothballedShip(FleetMemberType.SHIP, variantId, null)
        val ships = cargo.mothballedShips.membersListCopy
        if (ships.isEmpty()) return
        val fm = ships[ships.size - 1]
        DModManager.addDMods(fm, true, 3, Random())
        fm.repairTracker.cr = 0.4f
    }

    private fun roll(table: List<Entry>, count: Int, apply: (Entry) -> Unit) {
        val picker = WeightedRandomPicker<Entry>(rng)
        for (e in table) if (e.allowed) picker.add(e, e.weight)
        repeat(count) {
            val e = picker.pick() ?: return
            safe(e.id) { apply(e) }
        }
    }

    /** A bad id (a mod renamed something) must not abort system generation. */
    private fun safe(id: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log.warn("[SolRenewed] Could not add loot '$id': ${t.message}")
        }
    }

    private fun scale(min: Int, max: Int, mult: Float): Int {
        val lo = Math.round(min * mult)
        val hi = Math.round(max * mult)
        return if (hi <= lo) lo else lo + rng.nextInt(hi - lo + 1)
    }
}
