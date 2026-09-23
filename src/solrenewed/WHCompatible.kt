package solrenewed

import com.fs.starfarer.api.Global
import org.lwjgl.util.vector.Vector2f
import org.widehorizons.api.WideHorizonsAPI

/**
 * Optional compatibility helpers for Wide Horizons and Wide Horizons Basic.
 *
 * Sol Renewed does not require either Wide Horizons variant. If neither is enabled,
 * positions are returned unchanged and no compatibility message is logged.
 *
 * Both Wide Horizons variants expose the same public scaling API. They can enlarge
 * the campaign map beyond the vanilla sector dimensions, so fixed vanilla-reference
 * coordinates need to be converted before Sol Renewed searches for free space.
 */
object WHCompatible {

    private const val STANDARD_MOD_ID = "WideHorizons"
    private const val BASIC_MOD_ID = "WideHorizonsBasic"

    private val log = Global.getLogger(WHCompatible::class.java)

    /** Returns the enabled Wide Horizons variant name, or null when neither is active. */
    private fun activeVariantName(): String? {
        val modManager = Global.getSettings().modManager
        return when {
            modManager.isModEnabled(STANDARD_MOD_ID) -> "Wide Horizons"
            modManager.isModEnabled(BASIC_MOD_ID) -> "Wide Horizons Basic"
            else -> null
        }
    }

    /**
     * Converts a vanilla-reference map position using the active Wide Horizons
     * sector scale. The original position is returned when neither variant is
     * enabled or when the active variant's API unexpectedly cannot provide a
     * scaled position.
     */
    fun scalePosition(systemName: String, position: Vector2f): Vector2f {
        val variantName = activeVariantName() ?: return position

        return try {
            val scaled = WideHorizonsAPI.getScaledPosition(position.x, position.y)

            log.info(
                String.format(
                    "[SolRenewed] %s compatibility: scaled %s reference position from (%.0f, %.0f) to (%.0f, %.0f).",
                    variantName,
                    systemName,
                    position.x,
                    position.y,
                    scaled.x,
                    scaled.y,
                )
            )

            scaled
        } catch (t: Throwable) {
            log.warn(
                "[SolRenewed] $variantName is enabled, but scaling the $systemName reference position failed; " +
                    "using the original coordinates.",
                t,
            )
            position
        }
    }
}
