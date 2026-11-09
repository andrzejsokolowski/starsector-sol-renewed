package solrenewed.crisis

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import solrenewed.SrSettings

/**
 * One row of the crisis' "Monthly factors" list: what the player's Sol colonies, or their industries,
 * add to the meter every month. The numbers are read from Sol by [CrisisIntel]; the tooltip lists
 * every colony or industry with its share (the rules themselves are in the panel's legend).
 */
class CrisisMonthlyFactor(private val industries: Boolean) : BaseEventFactor() {

    override fun getProgress(intel: BaseEventIntel): Int = (intel as? CrisisIntel)?.monthly(industries) ?: 0

    override fun getDesc(intel: BaseEventIntel): String = if (industries) "Industries in Sol" else "Colonies in Sol"

    override fun getMainRowTooltip(intel: BaseEventIntel): TooltipCreator = object : BaseFactorTooltip() {
        override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
            val crisis = intel as? CrisisIntel ?: return
            val h = Misc.getHighlightColor()
            var pad = 0f
            for (share in crisis.shares(industries)) {
                tooltip.addPara("%s: %s", pad, h, share.label, "+" + Misc.getRoundedValueMaxOneAfterDecimal(share.points))
                    .setHighlightColors(Misc.getTextColor(), h)
                pad = 3f
            }
            if (SrSettings.crisisPace != 1f) {
                tooltip.addPara(
                    "Crisis meter speed setting: %s.", 10f, h,
                    "x" + Misc.getRoundedValueMaxOneAfterDecimal(SrSettings.crisisPace)
                )
            }
        }
    }
}
