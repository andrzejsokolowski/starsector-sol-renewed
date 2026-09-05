package solrenewed.crisis

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import solrenewed.SolSystem
import solrenewed.SrSettings

/**
 * Re-added on every game load (never saved). Waits for the player's first colony in Sol and then
 * starts the "Discovering the Past" crisis; from there the crisis intel looks after itself.
 */
class CrisisWatcher : EveryFrameScript {

    private val interval = IntervalUtil(1f, 2f)

    override fun advance(amount: Float) {
        interval.advance(Global.getSector().clock.convertToDays(amount))
        if (!interval.intervalElapsed()) return
        if (!SrSettings.crisis) return
        if (CrisisIntel.get() != null) return
        val sol = SolSystem.find() ?: return
        if (Misc.getMarketsInLocation(sol, Factions.PLAYER).isEmpty()) return
        CrisisIntel.create()
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = false
}
