package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener
import com.fs.starfarer.api.util.Misc
import indevo.exploration.meteor.MeteorSwarmManager

/**
 * Industrial Evolution meteor showers on a seasonal schedule. Only ever touched when IndEvo is on,
 * so its classes are never loaded otherwise. Registered as a transient listener on every game load.
 */
object MeteorShowers {

    private val log = Global.getLogger(MeteorShowers::class.java)

    fun install(sol: StarSystemAPI) {
        val lm = Global.getSector().listenerManager
        if (lm.hasListenerOfClass(Listener::class.java)) return
        lm.addListener(Listener(sol), true)
        log.info("[SolRenewed] Meteor showers armed.")
    }

    class Listener(private val sol: StarSystemAPI) : EconomyTickListener {
        override fun reportEconomyTick(iterIndex: Int) {}

        override fun reportEconomyMonthEnd() {
            if (!SrSettings.meteors) return
            if (!playerNearSol()) return
            val month = Global.getSector().clock.month
            val chance = when (month) { 1 -> 0.6f; 4 -> 0.7f; 7 -> 0.5f; 10 -> 0.8f; else -> 0.1f }
            if (Math.random() > chance) return
            val intensity = when (month) { 1 -> 2f; 4 -> 3f; 7 -> 1.5f; 10 -> 2.5f; else -> 1f }
            try {
                MeteorSwarmManager.getInstance().spawnShower(sol, intensity, MeteorSwarmManager.MeteroidShowerType.ASTEROID)
                log.info("[SolRenewed] Meteor shower over Sol (month $month, intensity $intensity)")
            } catch (t: Throwable) {
                log.error("[SolRenewed] Meteor shower failed", t)
            }
        }

        private fun playerNearSol(): Boolean {
            val current = Global.getSector().currentLocation ?: return false
            if (current === sol) return true
            if (current.isHyperspace) {
                val player = Global.getSector().playerFleet.locationInHyperspace
                val anchor = sol.hyperspaceAnchor?.location ?: return false
                return Misc.getDistance(player, anchor) < sol.maxRadiusInHyperspace + 400f
            }
            return false
        }
    }
}
