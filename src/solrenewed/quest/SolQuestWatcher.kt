package solrenewed.quest

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.IntervalUtil
import solrenewed.SolSystem

/**
 * Re-added on every game load while Sol does not exist. Notices the first Shroud kill, keeps the
 * translocator and the signal in place, and opens the activation dialog when the fleet reaches the signal.
 */
class SolQuestWatcher : EveryFrameScript {

    private val interval = IntervalUtil(0.05f, 0.1f)
    private var wasAtSignal = false
    private var finished = false

    override fun advance(amount: Float) {
        if (finished) return
        interval.advance(Global.getSector().clock.convertToDays(amount))
        if (!interval.intervalElapsed()) return
        if (SolSystem.find() != null) {
            finished = true
            return
        }
        when (SolQuest.stage) {
            SolQuest.Stage.NONE -> SolQuest.ensureBarEvent()
            SolQuest.Stage.SHROUD -> if (SolQuest.hasShroud()) SolQuest.shroudFound()
            SolQuest.Stage.TRAVEL -> travel()
            else -> {}
        }
    }

    private fun travel() {
        SolQuest.ensureSignal()
        SolQuest.ensureDevice()
        val ui = Global.getSector().campaignUI
        val atSignal = SolQuest.playerAtSignal()
        if (atSignal && !wasAtSignal && !ui.isShowingDialog && !ui.isShowingMenu) {
            val signal = SolQuest.signal()
            if (signal != null) ui.showInteractionDialog(SignalDialog(), signal)
        }
        wasAtSignal = atSignal
    }

    override fun isDone(): Boolean = finished
    override fun runWhilePaused(): Boolean = false
}
