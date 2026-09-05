package solrenewed.quest

import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.util.Misc

/**
 * Rule command used by data/campaign/rules.csv for the Glasya-Labolas conversation.
 *
 * Conditions: `SrQuestCMD stage <NAME>`, `SrQuestCMD offering`, `SrQuestCMD hasShroud`.
 * Effects: `SrQuestCMD begin | needShroud | giveDevice | decline | spawnSol`.
 */
class SrQuestCMD : BaseCommandPlugin() {

    override fun execute(ruleId: String?, dialog: InteractionDialogAPI?, params: MutableList<Misc.Token>, memoryMap: MutableMap<String, MemoryAPI>): Boolean {
        val cmd = params.getOrNull(0)?.getString(memoryMap) ?: return false
        val text = dialog?.textPanel
        return when (cmd) {
            "stage" -> SolQuest.stage.name == params.getOrNull(1)?.getString(memoryMap)
            "offering" -> SolQuest.isOffering()
            "hasShroud" -> SolQuest.hasShroud()
            "begin" -> { SolQuest.begin(text); true }
            "needShroud" -> { SolQuest.needShroud(text); true }
            "giveDevice" -> { SolQuest.giveDevice(dialog); true }
            "decline" -> { SolQuest.decline(text); true }
            "spawnSol" -> { SolQuest.spawnSol(text, skipped = true); true }
            else -> false
        }
    }
}
