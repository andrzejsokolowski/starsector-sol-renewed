package solrenewed.crisis

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor

/**
 * A one-time meter gain from 0.3.7 and older, when growth in Sol was charged once instead of every
 * month. Nothing creates these any more; the class stays so older saves load, and each expires 30
 * days after it was added like the game's own one-time factors.
 */
class CrisisFactor(private val desc: String, points: Int) : BaseOneTimeFactor(points) {

    override fun getDesc(intel: BaseEventIntel): String = desc
}
