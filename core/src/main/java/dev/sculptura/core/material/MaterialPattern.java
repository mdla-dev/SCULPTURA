package dev.sculptura.core.material;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.math.BlockPos3i;

/**
 * Deterministic spatial material pattern evaluated identically during holographic preview and server commit.
 */
@FunctionalInterface
public interface MaterialPattern {
    BlockStateRef sample(BlockPos3i pos, BoundingBox3i regionBounds, long seed);

    static MaterialPattern single(BlockStateRef state) {
        return (pos, bounds, seed) -> state;
    }
}
