package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.MarketAPI

/** Which optional mods are on, plus helpers that only apply a condition/tag/memory key when its mod is present. */
object ModCheck {

    fun mod(id: String): Boolean = Global.getSettings().modManager.isModEnabled(id)

    val hasLunaLib: Boolean get() = mod("lunalib")
    val hasRAT: Boolean get() = mod("assortment_of_things")
    val hasIndEvo: Boolean get() = mod("IndEvo")
    val hasUS: Boolean get() = mod("US")
    val hasVoK: Boolean get() = mod("aotd_vok")
    val hasDoP: Boolean get() = mod("Cryo_but_better")

    fun cond(market: MarketAPI, modId: String, vararg conditions: String) {
        if (!mod(modId)) return
        for (c in conditions) market.addCondition(c)
    }

    fun tag(entity: SectorEntityToken, modId: String, vararg tags: String) {
        if (!mod(modId)) return
        for (t in tags) entity.addTag(t)
    }

    fun mem(entity: SectorEntityToken, modId: String, key: String, value: Any) {
        if (!mod(modId)) return
        entity.memoryWithoutUpdate.set(key, value)
    }
}
