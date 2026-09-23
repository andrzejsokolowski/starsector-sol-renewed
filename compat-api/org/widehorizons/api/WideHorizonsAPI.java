package org.widehorizons.api;

import org.lwjgl.util.vector.Vector2f;

/**
 * Compile-time-only declaration of the Wide Horizons API used by Sol Renewed.
 *
 * This class is compiled in a separate Gradle source set and is never packaged
 * into SolRenewed.jar. At runtime the real implementation is supplied by either
 * Wide Horizons or Wide Horizons Basic when that mod is enabled.
 */
public final class WideHorizonsAPI {
    private WideHorizonsAPI() {}

    public static native Vector2f getScaledPosition(float originalX, float originalY);
}
