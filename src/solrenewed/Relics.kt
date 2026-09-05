package solrenewed

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI

/**
 * Keeps Random Assortment of Things from re-decorating our planets.
 *
 * RAT rolls its "relic" conditions (Engineered Utopia, Defensive Drones, ...) on the first game load,
 * on every planet of every derelict- or Remnant-themed system, and does not care that we already
 * hand-picked conditions for Sol. So at generation we remember which RAT conditions each planet was
 * given on purpose, and one frame into the first load we remove every other one.
 */
object Relics {

    private const val KEY_PLACED = "\$sr_rat_placed"
    private const val KEY_CLEANED = "\$sr_relics_cleaned"
    private val log = Global.getLogger(Relics::class.java)

    /** Call at the end of system generation, before RAT has had a chance to run. */
    fun snapshot(sys: StarSystemAPI) {
        for (planet in sys.planets) {
            val market = planet.market ?: continue
            val placed = market.conditions.map { it.id }.filter { it.startsWith("rat_") }
            planet.memoryWithoutUpdate.set(KEY_PLACED, ArrayList(placed))
        }
    }

    /** Registered on every game load; does its work once, then stops. */
    fun schedule() {
        if (!ModCheck.hasRAT) return
        if (Global.getSector().memoryWithoutUpdate.getBoolean(KEY_CLEANED)) return
        Global.getSector().addTransientScript(Cleanup())
    }

    private class Cleanup : EveryFrameScript {
        private var done = false

        override fun advance(amount: Float) {
            if (done) return
            done = true
            var removed = 0
            val systems = listOfNotNull(SolSystem.find(), Global.getSector().getStarSystem("Alpha Centauri"))
            for (sys in systems) {
                for (planet in sys.planets) {
                    val market = planet.market ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val placed = planet.memoryWithoutUpdate.get(KEY_PLACED) as? List<String> ?: continue
                    val strays = market.conditions.map { it.id }.filter { it.startsWith("rat_") && it !in placed }
                    for (id in strays) {
                        market.removeCondition(id)
                        planet.removeTag(id)
                        removed++
                        log.info("[SolRenewed] Removed stray RAT condition '$id' from ${planet.name}")
                    }
                    if (placed.isEmpty()) planet.removeTag("rat_relic_condition")
                }
            }
            Global.getSector().memoryWithoutUpdate.set(KEY_CLEANED, true)
            log.info("[SolRenewed] Relic cleanup done, $removed removed.")
        }

        override fun isDone(): Boolean = done
        override fun runWhilePaused(): Boolean = true
    }
}
