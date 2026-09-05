package solrenewed.quest

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent
import com.fs.starfarer.api.util.Misc
import solrenewed.SolSystem
import java.util.Random

/**
 * The lead that starts "Transferring Sol": a worried researcher who is in every bar the player
 * walks into, from level 10 and once the fleet can transverse jump, until the player accepts.
 */
class SolQuestBarEvent : BaseBarEvent() {

    private val seed = Misc.random.nextLong()

    @Transient private var person: PersonAPI? = null

    override fun isAlwaysShow(): Boolean = true

    override fun shouldShowAtMarket(market: MarketAPI): Boolean = SolQuest.playerQualifiesForBarEvent()

    override fun shouldRemoveEvent(): Boolean = SolQuest.stage != SolQuest.Stage.NONE || SolSystem.find() != null

    override fun addPromptAndOption(dialog: InteractionDialogAPI, memoryMap: MutableMap<String, MemoryAPI>) {
        super.addPromptAndOption(dialog, memoryMap)
        val random = Random(seed + (dialog.interactionTarget.market?.id?.hashCode() ?: 0))
        val p = Global.getSector().getFaction(Factions.TRITACHYON).createRandomPerson(random)
        p.rankId = Ranks.CITIZEN
        p.postId = Ranks.POST_SCIENTIST
        person = p
        dialog.textPanel.addPara("A concerned researcher reaches out to you.")
        dialog.optionPanel.addOption("See what the researcher wants", this)
    }

    override fun init(dialog: InteractionDialogAPI, memoryMap: MutableMap<String, MemoryAPI>) {
        super.init(dialog, memoryMap)
        done = false
        person?.let { dialog.visualPanel.showPersonInfo(it, true) }
        val name = Global.getSector().playerPerson.name.fullName
        text.addPara(
            "\"Hello, are you $name? I recently got news of a rogue Tri-Tachyon researcher trying experimental " +
                "hyperspace-shift technology. Please go talk with him on Eochu Bres.\""
        )
        options.clearOptions()
        options.addOption("\"I'll look into it.\"", ACCEPT)
        options.addOption("\"Not interested.\"", DECLINE)
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            ACCEPT -> {
                text.addPara("The researcher nods, already looking over your shoulder for whoever else might be listening.")
                SolQuest.start(text)
                done = true
            }
            DECLINE -> {
                text.addPara("The researcher looks like someone who will still be here tomorrow.")
                done = true
            }
        }
    }

    companion object {
        private const val ACCEPT = "sr_quest_accept"
        private const val DECLINE = "sr_quest_decline"
    }
}
