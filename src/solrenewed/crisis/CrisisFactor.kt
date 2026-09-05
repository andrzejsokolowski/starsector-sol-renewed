package solrenewed.crisis

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator

/**
 * One line in the crisis' "recent one-time factors" list: something the player did in Sol and the
 * attention it bought. Shows for 30 days like the game's own one-time factors.
 */
class CrisisFactor(private val desc: String, points: Int) : BaseOneTimeFactor(points) {

    override fun getDesc(intel: BaseEventIntel): String = desc

    override fun getMainRowTooltip(intel: BaseEventIntel): TooltipCreator = object : BaseFactorTooltip() {
        override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
            tooltip.addPara(
                "Every colony founded in Sol, every size a colony grows, every industry that comes online " +
                    "and the hypershunt being switched on all draw more of the Sector's attention to the system. " +
                    "Nothing lowers the meter; it only stops climbing when Sol stops growing.", 0f
            )
        }
    }
}
