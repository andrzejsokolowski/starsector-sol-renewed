package solrenewed.quest

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.util.Misc

/** Opens by itself when the fleet reaches the signal with the translocator aboard. Ends with Sol being generated. */
class SignalDialog : InteractionDialogPlugin {

    private enum class Option { ACTIVATE, LEAVE, OPS_A, OPS_B, GO, DONE }

    private lateinit var dialog: InteractionDialogAPI
    private lateinit var text: TextPanelAPI
    private lateinit var options: OptionPanelAPI

    override fun init(dialog: InteractionDialogAPI) {
        this.dialog = dialog
        text = dialog.textPanel
        options = dialog.optionPanel
        text.addPara(
            "The signal pulses just ahead, faint and patient, exactly where Glasya-Labolas said it would be. " +
                "Your sensors officer confirms the coordinates match to the meter. The Macro-scale Translocator sits in " +
                "its crate in the hold, humming at a pitch nobody on the bridge can quite ignore."
        )
        options.addOption("Activate the Macro-scale Translocator", Option.ACTIVATE)
        options.addOption("Not yet", Option.LEAVE)
        options.setShortcut(Option.LEAVE, 1, false, false, false, true)
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        if (optionText != null) text.addParagraph(optionText, Global.getSettings().getColor("buttonText"))
        when (optionData) {
            Option.ACTIVATE -> {
                options.clearOptions()
                text.addPara(
                    "The crate opens on its own. The lattice inside unfolds, and for a heartbeat every star on the " +
                        "viewscreen is in the wrong place. The signal ahead flares, stretches into a line, and is gone."
                )
                text.addPara("Then the deck stops shaking, and the bridge is very quiet.")
                options.addOption("\"Ops, what is happening?\"", Option.OPS_A)
                options.addOption("\"I hope we didn't just doom the entire sector.\"", Option.OPS_B)
            }
            Option.OPS_A, Option.OPS_B -> {
                options.clearOptions()
                val sir = if (Global.getSector().playerPerson.gender == FullName.Gender.FEMALE) "Ma'am" else "Sir"
                text.addPara(
                    "\"$sir, I believe it worked. The signal disappeared from where we are, and we've started receiving " +
                        "a very strong signal from within the Persean Sector. I believe it worked.\""
                )
                options.addOption("\"Hopefully... Let's go check it out.\"", Option.GO)
            }
            Option.GO -> {
                options.clearOptions()
                SolQuest.spawnSol(text, skipped = false)
                text.addPara("Navigation is already plotting the course.", Misc.getPositiveHighlightColor())
                options.addOption("Leave", Option.DONE)
                options.setShortcut(Option.DONE, 1, false, false, false, true)
            }
            Option.LEAVE, Option.DONE -> dialog.dismiss()
        }
    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) {}
    override fun advance(amount: Float) {}
    override fun backFromEngagement(battleResult: EngagementResultAPI?) {}
    override fun getContext(): Any? = null
    override fun getMemoryMap(): MutableMap<String, MemoryAPI>? = null
}
