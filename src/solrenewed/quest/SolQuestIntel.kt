package solrenewed.quest

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import solrenewed.SolSystem
import java.awt.Color

/** The "Transferring Sol" card in the intel screen: one line for the current objective, a marker on the map. */
class SolQuestIntel : BaseIntelPlugin() {

    init {
        Global.getSector().memoryWithoutUpdate.set(SolQuest.INTEL_KEY, this)
    }

    fun refresh(text: TextPanelAPI?) {
        sendUpdateIfPlayerHasIntel(null, text)
    }

    override fun getName(): String = "Transferring Sol"

    override fun getIcon(): String = Global.getSettings().getSpriteName("events", "sr_crisis")

    override fun getSortString(): String = "Transferring Sol"

    private fun objective(): String = when (SolQuest.stage) {
        SolQuest.Stage.TALK -> "Talk to Glasya-Labolas on Eochu Bres, in the Hybrasil system"
        SolQuest.Stage.SHROUD -> "Go into the Abyss and destroy a shrouded ship"
        SolQuest.Stage.RETURN -> "Return to Glasya-Labolas on Eochu Bres"
        SolQuest.Stage.TRAVEL -> "Travel to the given coordinates and activate the translocator device"
        SolQuest.Stage.DONE -> "Sol has arrived in the Persean Sector"
        SolQuest.Stage.DECLINED -> "Declined"
        SolQuest.Stage.NONE -> ""
    }

    override fun addBulletPoints(info: TooltipMakerAPI, mode: ListInfoMode, isUpdate: Boolean, tc: Color, initPad: Float) {
        val line = objective()
        if (line.isNotEmpty()) info.addPara(line, tc, initPad)
    }

    override fun hasSmallDescription(): Boolean = true

    override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        val opad = 10f
        val h = Misc.getHighlightColor()
        info.addImage(getIcon(), 64f, 64f, opad)
        info.addPara(
            "A concerned researcher pointed you at a rogue Tri-Tachyon AI on Eochu Bres. " +
                "Glasya-Labolas claims to have found Sol, the system humanity came from, transmitting from a region of " +
                "abyssal hyperspace too dense to jump into, and to have a way of moving it somewhere more convenient.", opad
        )
        when (SolQuest.stage) {
            SolQuest.Stage.SHROUD -> info.addPara(
                "The device needs data the AI's sniffer can only collect while your fleet fights the Shroud. " +
                    "Destroy any shrouded ship in the Abyss, then go back.", opad
            )
            SolQuest.Stage.TRAVEL -> {
                val loc = SolQuest.signalLocation()
                info.addPara(
                    "The Macro-scale Translocator is in your cargo hold. Take it to the coordinates %s, far north of the " +
                        "edge of the map in the deep Abyss, and be directly on top of the signal before activating it.",
                    opad, h, String.format("(%.0f, %.0f)", loc.x, loc.y)
                )
            }
            SolQuest.Stage.DONE -> info.addPara("It worked. Sol is in the Persean Sector now.", opad, Misc.getPositiveHighlightColor(), "Sol")
            SolQuest.Stage.DECLINED -> info.addPara("You told the AI you did not care. It said Sol would wait.", opad)
            else -> {}
        }
        val line = objective()
        if (line.isNotEmpty() && SolQuest.stage != SolQuest.Stage.DONE && SolQuest.stage != SolQuest.Stage.DECLINED) {
            info.addPara("Objective: %s", opad, h, line)
        }
    }

    override fun getMapLocation(map: SectorMapAPI?): SectorEntityToken? = when (SolQuest.stage) {
        SolQuest.Stage.TALK, SolQuest.Stage.RETURN -> SolQuest.market()?.primaryEntity
        SolQuest.Stage.TRAVEL -> SolQuest.signal()
        SolQuest.Stage.DONE -> SolSystem.find()?.star
        else -> null
    }

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> {
        val tags = super.getIntelTags(map)
        tags.add(Tags.INTEL_MISSIONS)
        tags.add(Tags.INTEL_ACCEPTED)
        tags.add(Tags.INTEL_STORY)
        return tags
    }

    override fun notifyEnded() {
        super.notifyEnded()
        if (Global.getSector().memoryWithoutUpdate.get(SolQuest.INTEL_KEY) === this) {
            Global.getSector().memoryWithoutUpdate.unset(SolQuest.INTEL_KEY)
        }
    }
}
