package solrenewed

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl
import com.fs.starfarer.api.util.IntervalUtil

/**
 * Keeps the Sol track playing while the player is docked at, or talking to, a planet or station in a
 * system that plays it (Sol and Alpha Centauri). Without this the game switches to the market music on
 * docking and only resumes the Sol track after undocking, so the player hears it in short pieces.
 *
 * The game has a switch for this: when a dialog opens, CampaignState skips the music change if the
 * entity's memory holds `$playLocationMusicDuringEnc = true` (vanilla uses it at the Tri-Tachyon black
 * site). Planets and custom entities (stations, relays, derelicts, the gate) get it. Fleets do not, so
 * fleet encounters and battles keep their own music. An entity that names its own music (`$musicSetId`
 * or a mission's `$musicSetIdForMission`) keeps that music.
 *
 * The flag goes into the entity's memory and so into the save. A second key marks the flags this mod set,
 * so turning the setting off removes only those.
 */
object SolMusic {

    const val KEEP_KEY = "\$playLocationMusicDuringEnc"
    const val OURS_KEY = "\$sr_keptSolMusic"

    private val log = Global.getLogger(SolMusic::class.java)

    /** True for a star system whose own music is the Sol track. */
    @JvmStatic
    fun playsSolMusic(location: LocationAPI?): Boolean =
        location is StarSystemAPI &&
            SolSystem.MUSIC == location.memoryWithoutUpdate?.getString(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY)

    /**
     * Sets (keep = true) or removes (keep = false) the flag on every planet and custom entity in the system.
     * Returns how many entities changed.
     */
    @JvmStatic
    fun apply(system: LocationAPI, keep: Boolean): Int {
        var changed = 0
        for (entity in system.allEntities) {
            if (!isVisitable(entity)) continue
            val mem = entity.memoryWithoutUpdate ?: continue
            if (mark(mem, keep && !hasOwnMusic(mem))) changed++
        }
        return changed
    }

    private fun isVisitable(entity: SectorEntityToken): Boolean =
        (entity is PlanetAPI && !entity.isStar) || entity is CustomCampaignEntityAPI

    private fun hasOwnMusic(mem: MemoryAPI): Boolean =
        mem.getString(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY) != null ||
            mem.getString(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY_MISSION) != null

    /** Returns true if the memory changed. Never touches a flag another mod or the game set. */
    private fun mark(mem: MemoryAPI, keep: Boolean): Boolean {
        val ours = mem.getBoolean(OURS_KEY)
        if (keep) {
            if (ours || mem.getBoolean(KEEP_KEY)) return false
            mem.set(KEEP_KEY, true)
            mem.set(OURS_KEY, true)
            return true
        }
        if (!ours) return false
        mem.unset(KEEP_KEY)
        mem.unset(OURS_KEY)
        return true
    }

    /** Never saved: re-added on every game load. */
    @JvmStatic
    fun install() {
        val sector = Global.getSector() ?: return
        if (!sector.hasTransientScript(Keeper::class.java)) sector.addTransientScript(Keeper())
    }

    /**
     * Every two seconds while the player is in a system with the Sol track, flags what is new there (a colony
     * station, a relay built on a stable location) or, with the setting off, removes this mod's flags.
     */
    class Keeper : EveryFrameScript {
        private val interval = IntervalUtil(2f, 2f)
        private var first = true

        override fun advance(amount: Float) {
            interval.advance(amount)
            if (!first && !interval.intervalElapsed()) return
            first = false
            try {
                val location = Global.getSector()?.playerFleet?.containingLocation ?: return
                if (!playsSolMusic(location)) return
                val changed = apply(location, SrSettings.keepSolMusicNow())
                if (changed > 0) log.info("[SolRenewed] Sol music flag changed on $changed entities in ${location.name}")
            } catch (t: Throwable) {
                log.warn("[SolRenewed] Could not update the Sol music flags", t)
            }
        }

        override fun isDone(): Boolean = false
        override fun runWhilePaused(): Boolean = false
    }
}
