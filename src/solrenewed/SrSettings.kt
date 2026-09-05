package solrenewed

import com.fs.starfarer.api.Global
import lunalib.lunaSettings.LunaSettings

/**
 * Values from the mod's LunaSettings page (`data/config/LunaSettings.csv`). The layout settings are
 * read when a new game is generated; the meteor and crisis settings are re-read on every game load. Every read
 * falls back to the built-in default so a missing LunaLib or a broken CSV never stops generation.
 */
object SrSettings {

    private const val MOD_ID = SolRenewedModPlugin.MOD_ID
    private val log = Global.getLogger(SrSettings::class.java)

    var spawnSol = true; private set
    var posX = -10700f; private set
    var posY = -28704f; private set
    var spawnCentauri = true; private set

    /** 0 = none, 1 = light, 2 = heavy, 3 = overrun. */
    var remnantLevel = 1; private set

    var hypershunt = true; private set
    var nidavellir = true; private set
    var plutoStation = true; private set
    var cryoPrison = true; private set
    var meteors = true; private set

    /** The "Discovering the Past" colony crisis: on/off, how fast the meter fills, how big the waves are. */
    var crisis = true; private set
    var crisisPace = 1f; private set
    var crisisStrength = 1f; private set

    @JvmStatic
    fun reload() {
        if (!ModCheck.hasLunaLib) {
            log.info("[SolRenewed] LunaLib not enabled; using default settings.")
            return
        }
        try {
            spawnSol = bool("sr_spawn_sol", spawnSol)
            posX = LunaSettings.getDouble(MOD_ID, "sr_pos_x")?.toFloat() ?: posX
            posY = LunaSettings.getDouble(MOD_ID, "sr_pos_y")?.toFloat() ?: posY
            spawnCentauri = bool("sr_spawn_centauri", spawnCentauri)
            remnantLevel = when (LunaSettings.getString(MOD_ID, "sr_remnant_level")?.trim()?.lowercase()) {
                "none" -> 0
                "light" -> 1
                "heavy" -> 2
                "overrun" -> 3
                else -> remnantLevel
            }
            hypershunt = bool("sr_hypershunt", hypershunt)
            nidavellir = bool("sr_nidavellir", nidavellir)
            plutoStation = bool("sr_pluto_station", plutoStation)
            cryoPrison = bool("sr_cryo_prison", cryoPrison)
            meteors = bool("sr_meteors", meteors)
            crisis = bool("sr_crisis", crisis)
            crisisPace = LunaSettings.getDouble(MOD_ID, "sr_crisis_pace")?.toFloat() ?: crisisPace
            crisisStrength = LunaSettings.getDouble(MOD_ID, "sr_crisis_strength")?.toFloat() ?: crisisStrength
        } catch (t: Throwable) {
            log.warn("[SolRenewed] Could not read LunaSettings; using defaults.", t)
        }
    }

    private fun bool(key: String, default: Boolean): Boolean = LunaSettings.getBoolean(MOD_ID, key) ?: default
}
