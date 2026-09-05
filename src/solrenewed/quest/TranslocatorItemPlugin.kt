package solrenewed.quest

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc

/** The Macro-scale Translocator: worth nothing to anyone, and it finds its way back if it leaves the hold. */
class TranslocatorItemPlugin : BaseSpecialItemPlugin() {

    override fun getPrice(market: MarketAPI?, submarket: SubmarketAPI?): Int = 0

    override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, transferHandler: CargoTransferHandlerAPI?, stackSource: Any?, useGray: Boolean) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray)
        tooltip.addPara("Cannot be sold. Activate it on top of the signal Glasya-Labolas gave you the coordinates for.", Misc.getHighlightColor(), 10f)
    }
}
