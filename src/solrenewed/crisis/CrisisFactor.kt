package solrenewed.crisis

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc

/**
 * One line in the crisis' "recent one-time factors" list: something the player did in Sol and the
 * attention it bought. Shows for 30 days like the game's own one-time factors.
 */
class CrisisFactor(private val desc: String, points: Int) : BaseOneTimeFactor(points) {

    override fun getDesc(intel: BaseEventIntel): String = desc

    override fun getMainRowTooltip(intel: BaseEventIntel): TooltipCreator = object : BaseFactorTooltip() {
        override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
            val h = Misc.getHighlightColor()
            tooltip.addPara("A new colony in Sol is worth %s.", 0f, h, "+${CrisisIntel.POINTS_COLONY}")
            tooltip.addPara(
                "Growing a size counts people, not steps: size 4 is %s, size 6 %s, size 8 %s.", 10f, h,
                "+${CrisisIntel.growthPoints(4)}", "+${CrisisIntel.growthPoints(6)}", "+${CrisisIntel.growthPoints(8)}"
            )
            tooltip.addPara(
                "An industry counts its build cost, %s per point: farms barely register, heavy industry and " +
                    "anything beyond it does.", 10f, h, Misc.getDGSCredits(CrisisIntel.CREDITS_PER_POINT)
            )
            tooltip.addPara("Nothing lowers the meter.", 10f)
        }
    }
}
