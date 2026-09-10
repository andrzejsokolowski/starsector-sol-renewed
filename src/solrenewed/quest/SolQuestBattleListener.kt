package solrenewed.quest

import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags

/** Record enemy Shroud casualties before salvage, including kills during a lost engagement. */
class SolQuestBattleListener : BaseCampaignEventListener(false) {

    override fun reportPlayerEngagement(result: EngagementResultAPI) {
        if (SolQuest.stage == SolQuest.Stage.DONE) return
        val enemy = if (result.didPlayerWin()) result.loserResult else result.winnerResult
        if (enemy.isPlayer) return
        if (enemy.destroyed.any { it.hullSpec.hasTag(Tags.DWELLER) } ||
            enemy.disabled.any { it.hullSpec.hasTag(Tags.DWELLER) }) {
            SolQuest.recordShroudKill()
        }
    }
}
