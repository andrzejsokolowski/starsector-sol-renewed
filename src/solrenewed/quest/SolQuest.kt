package solrenewed.quest

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.WarningBeaconEntityPlugin
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import solrenewed.Centauri
import solrenewed.SolRenewedModPlugin
import solrenewed.SolSystem
import solrenewed.SrSettings
import java.awt.Color

/**
 * "Transferring Sol": the quest that brings Sol into the Sector. A researcher at a bar points the
 * player at Glasya-Labolas, a rogue AI on Eochu Bres, who hands over a device once the player has
 * fought the Shroud; activating it at a signal deep in the Abyss generates the Sol system.
 *
 * State lives in sector memory ($sr_quest_stage), the conversation in data/campaign/rules.csv
 * (driven by [SrQuestCMD]), the objective card in [SolQuestIntel].
 */
object SolQuest {

    enum class Stage { NONE, TALK, SHROUD, RETURN, TRAVEL, DONE, DECLINED }

    const val STAGE_KEY = "\$sr_quest_stage"
    const val INTEL_KEY = "\$sr_quest_ref"
    const val PERSON_ID = "sr_glasya_labolas"
    const val PERSON_NAME = "Glasya-Labolas"
    const val MARKET_ID = "eochu_bres"
    const val ITEM_ID = "sr_translocator"
    const val SIGNAL_TYPE = "sr_translocator_signal"
    const val SIGNAL_TAG = "sr_translocator_signal"
    const val MIN_LEVEL = 10
    /** How close (in hyperspace units) the fleet has to be to the signal before the device can be used. */
    const val SIGNAL_RANGE = 350f
    /** The signal sits this far north of the top edge of the map, in the deep Abyss. */
    const val SIGNAL_BEYOND_EDGE = 4000f

    private val log = Global.getLogger(SolQuest::class.java)

    var stage: Stage
        get() = Stage.entries.firstOrNull { it.name == Global.getSector().memoryWithoutUpdate.getString(STAGE_KEY) } ?: Stage.NONE
        private set(value) = Global.getSector().memoryWithoutUpdate.set(STAGE_KEY, value.name)

    fun intel(): SolQuestIntel? = Global.getSector().memoryWithoutUpdate.get(INTEL_KEY) as? SolQuestIntel

    /** Anything that can still end in Sol appearing: accepted and not yet done. */
    fun isActive(): Boolean = stage == Stage.TALK || stage == Stage.SHROUD || stage == Stage.RETURN || stage == Stage.TRAVEL

    fun isOffering(): Boolean = stage == Stage.NONE || stage == Stage.TALK || stage == Stage.DECLINED

    // ---------------------------------------------------------------- install

    /** Called on every game load while Sol does not exist yet. */
    fun install() {
        if (SolSystem.find() != null) return
        ensurePerson()
        ensureBarEvent()
        if (stage == Stage.TRAVEL) ensureSignal()
        Global.getSector().addTransientScript(SolQuestWatcher())
    }

    fun market(): MarketAPI? = Global.getSector().economy.getMarket(MARKET_ID)

    fun person(): PersonAPI? {
        val entry = market()?.commDirectory?.getEntryForPerson(PERSON_ID) ?: return null
        return entry.entryData as? PersonAPI
    }

    /** Glasya-Labolas, listed in the Eochu Bres comm directory. */
    fun ensurePerson() {
        val market = market()
        if (market == null) {
            log.warn("[SolRenewed] Quest: no market '$MARKET_ID' in this sector; Glasya-Labolas cannot be placed")
            return
        }
        if (person() != null) return
        val p = Global.getFactory().createPerson()
        p.id = PERSON_ID
        p.name = FullName(PERSON_NAME, "", FullName.Gender.ANY)
        p.setFaction(Factions.TRITACHYON)
        p.rankId = Ranks.CITIZEN
        p.postId = Ranks.POST_SCIENTIST
        p.portraitSprite = Global.getSettings().getSpriteName("characters", "sr_glasya_labolas")
        p.memoryWithoutUpdate.set("\$sr_glasya_labolas", true)
        market.addPerson(p)
        market.commDirectory.addPerson(p)
        Global.getSector().importantPeople.addPerson(p)
        log.info("[SolRenewed] Quest: Glasya-Labolas added to ${market.name}")
    }

    fun ensureBarEvent() {
        val data = PortsideBarData.getInstance() ?: return
        if (data.events.any { it is SolQuestBarEvent }) return
        data.addEvent(SolQuestBarEvent())
    }

    fun playerQualifiesForBarEvent(): Boolean {
        val sector = Global.getSector()
        if (stage != Stage.NONE || SolSystem.find() != null) return false
        if (sector.playerStats.level < MIN_LEVEL) return false
        return sector.playerFleet.hasAbility(Abilities.TRANSVERSE_JUMP)
    }

    // ------------------------------------------------------------ progression

    /** Accepted from the bar: go and talk to Glasya-Labolas. */
    fun start(text: TextPanelAPI?) {
        if (stage != Stage.NONE && stage != Stage.DECLINED) return
        stage = Stage.TALK
        ensurePerson()
        val intel = intel() ?: SolQuestIntel()
        Global.getSector().intelManager.addIntel(intel, false, text)
        log.info("[SolRenewed] Quest: started")
    }

    /** The player said yes (or stumbled onto Glasya-Labolas without the bar lead). */
    fun begin(text: TextPanelAPI?) {
        if (stage == Stage.NONE || stage == Stage.DECLINED) start(text)
    }

    fun needShroud(text: TextPanelAPI?) {
        stage = Stage.SHROUD
        intel()?.refresh(text)
    }

    /** True once the player has destroyed any shrouded ship (the game records each kill per hull). */
    fun hasShroud(): Boolean {
        val mem = Global.getSector().playerMemoryWithoutUpdate
        if (mem.getBoolean("\$shroudedSubstrateAvailable")) return true
        return mem.keys.any { it.startsWith("\$defeatedDweller_") }
    }

    fun shroudFound() {
        if (stage != Stage.SHROUD) return
        stage = Stage.RETURN
        intel()?.refresh(null)
        log.info("[SolRenewed] Quest: Shroud data obtained, return to Glasya-Labolas")
    }

    fun giveDevice(dialog: InteractionDialogAPI?) {
        stage = Stage.TRAVEL
        ensureDevice()
        dialog?.textPanel?.let { AddRemoveCommodity.addItemGainText(SpecialItemData(ITEM_ID, null), 1, it) }
        ensureSignal()
        intel()?.refresh(dialog?.textPanel)
        log.info("[SolRenewed] Quest: translocator handed over")
    }

    fun decline(text: TextPanelAPI?) {
        stage = Stage.DECLINED
        intel()?.let {
            it.refresh(text)
            it.endAfterDelay()
        }
        Global.getSector().memoryWithoutUpdate.unset(INTEL_KEY)
        log.info("[SolRenewed] Quest: declined")
    }

    fun playerHasDevice(): Boolean =
        Global.getSector().playerFleet.cargo.getQuantity(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, SpecialItemData(ITEM_ID, null)) > 0

    /** The translocator cannot be sold (price zero, markets refuse it) and comes back if it goes missing. */
    fun ensureDevice() {
        if (playerHasDevice()) return
        Global.getSector().playerFleet.cargo.addSpecial(SpecialItemData(ITEM_ID, null), 1f)
    }

    private fun removeDevice() {
        val cargo = Global.getSector().playerFleet.cargo
        val data = SpecialItemData(ITEM_ID, null)
        val q = cargo.getQuantity(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, data)
        if (q > 0) cargo.removeItems(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, data, q)
    }

    // ------------------------------------------------------------------ signal

    fun signalLocation(): Vector2f {
        val hybrasil = Global.getSector().getStarSystem("Hybrasil")
        val x = hybrasil?.location?.x ?: -16000f
        val y = Global.getSettings().getFloat("sectorHeight") / 2f + SIGNAL_BEYOND_EDGE
        return Vector2f(x, y)
    }

    fun signal(): SectorEntityToken? = Global.getSector().hyperspace.getEntitiesWithTag(SIGNAL_TAG).firstOrNull()

    fun ensureSignal(): SectorEntityToken {
        signal()?.let { return it }
        val loc = signalLocation()
        val e = Global.getSector().hyperspace.addCustomEntity("sr_translocator_signal", "Anomalous Signal", SIGNAL_TYPE, null)
        e.setLocation(loc.x, loc.y)
        e.addTag(SIGNAL_TAG)
        e.memoryWithoutUpdate.set(WarningBeaconEntityPlugin.GLOW_COLOR_KEY, Color(200, 60, 255, 255))
        e.memoryWithoutUpdate.set(WarningBeaconEntityPlugin.PING_COLOR_KEY, Color(220, 90, 255, 255))
        e.memoryWithoutUpdate.set(WarningBeaconEntityPlugin.PING_FREQ_KEY, 1.5f)
        e.detectedRangeMod.modifyFlat("sr_signal", 6000f)
        e.setSensorProfile(1f)
        log.info(String.format("[SolRenewed] Quest: signal placed at (%.0f, %.0f)", loc.x, loc.y))
        return e
    }

    fun removeSignal() {
        val e = signal() ?: return
        Misc.fadeAndExpire(e)
    }

    fun playerAtSignal(): Boolean {
        val player = Global.getSector().playerFleet ?: return false
        if (!player.isInHyperspace) return false
        val e = signal() ?: return false
        return Misc.getDistance(player.location, e.location) <= SIGNAL_RANGE
    }

    // ------------------------------------------------------------------ finish

    /** Generates Sol (and the Centauri constellation) right now and closes the quest. */
    fun spawnSol(text: TextPanelAPI?, skipped: Boolean) {
        val sector = Global.getSector()
        SrSettings.reload()
        var sol = SolSystem.find()
        if (sol == null) {
            try {
                SolSystem.generate(sector)
                if (SrSettings.spawnCentauri) Centauri.generate(sector)
            } catch (t: Throwable) {
                log.error("[SolRenewed] Sol generation failed", t)
            }
            sol = SolSystem.find()
        }
        removeDevice()
        removeSignal()
        stage = Stage.DONE
        if (sol != null) {
            SolRenewedModPlugin.installSolRuntime(sol)
            val existing = intel()
            if (existing != null) {
                existing.refresh(text)
                existing.endAfterDelay()
            } else if (!skipped) {
                val intel = SolQuestIntel()
                sector.intelManager.addIntel(intel, false, text)
                intel.endAfterDelay()
            }
            sector.campaignUI.addMessage("Sol has arrived in the Persean Sector", Misc.getPositiveHighlightColor())
            log.info("[SolRenewed] Quest: Sol spawned" + if (skipped) " (quest skipped)" else "")
        }
        PortsideBarData.getInstance()?.events?.removeAll { it is SolQuestBarEvent }
    }
}
