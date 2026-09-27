package solrenewed

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI.GenericPlugin
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.plugins.BuildObjectiveTypePicker
import com.fs.starfarer.api.plugins.BuildObjectiveTypePicker.BuildObjectiveParams

/**
 * Stops a crash in the game's own patrol code. A patrol that flies to a stable location to build a
 * comm relay, nav buoy or sensor array asks again which one to build when it gets there. If another
 * patrol has meanwhile built or captured the last missing one, the answer is "none", and the game
 * then builds an object with no type and crashes. Sol makes this likely: its relay, buoy and array
 * start out neutral next to two stable locations, so a colony's patrols capture and build at once.
 *
 * This picker sits on top of the one the game (or another mod) would use and passes its answer
 * through. When the answer is "none", the stable location is marked as fading for one frame: a build
 * under way then stops at its first check and nothing is built. The mark is removed the next frame.
 */
class ObjectivePickerGuard : BuildObjectiveTypePicker, GenericPlugin {

    override fun getHandlingPriority(params: Any?): Int = if (params is BuildObjectiveParams) PRIORITY else -1

    override fun pickObjectiveToBuild(params: BuildObjectiveParams): String? {
        val type = underlying(params)?.pickObjectiveToBuild(params)
        if (type == null) holdOff(params.stableLoc)
        return type
    }

    /** The picker the game would have used without this one: the best one below our priority. */
    private fun underlying(params: BuildObjectiveParams): BuildObjectiveTypePicker? =
        Global.getSector().genericPlugins.getPluginsOfClass(BuildObjectiveTypePicker::class.java)
            .filter { it is BuildObjectiveTypePicker && it.getHandlingPriority(params) in 0 until PRIORITY }
            .maxByOrNull { it.getHandlingPriority(params) } as BuildObjectiveTypePicker?

    private fun holdOff(stableLoc: SectorEntityToken?) {
        if (stableLoc == null || stableLoc.hasTag(Tags.FADING_OUT_AND_EXPIRING)) return
        stableLoc.addTag(Tags.FADING_OUT_AND_EXPIRING)
        Global.getSector().addScript(Unmark(stableLoc))
    }

    /** Saved with the game (not transient), so the mark cannot outlive a save made in that frame. */
    private class Unmark(private val entity: SectorEntityToken) : EveryFrameScript {
        private var done = false

        override fun advance(amount: Float) {
            entity.removeTag(Tags.FADING_OUT_AND_EXPIRING)
            done = true
        }

        override fun isDone() = done

        override fun runWhilePaused() = true
    }

    companion object {
        /** Above any mod's own picker so this one wraps it; below HIGHEST, which is meant for mod packs. */
        private val PRIORITY = GenericPluginManagerAPI.MOD_SPECIFIC + 1

        /** Registered on every game load; not written into the save. */
        fun install() {
            val plugins = Global.getSector().genericPlugins
            if (!plugins.hasPlugin(ObjectivePickerGuard::class.java)) plugins.addPlugin(ObjectivePickerGuard(), true)
        }
    }
}
