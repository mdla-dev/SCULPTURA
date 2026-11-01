package dev.sculptura.core.geometry;

import dev.sculptura.core.math.BlockPos3i;

/**
 * Read-only view of voxel world blocks used by preview generators, sculpting brushes, and operations.
 * Strictly prohibits world mutation during preview computation.
 */
public interface VoxelWorldView {
    BlockStateRef getBlockState(BlockPos3i pos);

    default BlockStateRef getBlockState(int x, int y, int z) {
        return getBlockState(new BlockPos3i(x, y, z));
    }

    default boolean isAir(BlockPos3i pos) {
        return getBlockState(pos).isAir();
    }

    default int getMinBuildHeight() {
        return -64;
    }

    default int getMaxBuildHeight() {
        return 320;
    }

    default int findTopSolidY(int x, int z, int searchFromY, int searchMinY) {
        int top = Math.min(getMaxBuildHeight() - 1, searchFromY);
        int bottom = Math.max(getMinBuildHeight(), searchMinY);
        for (int y = top; y >= bottom; y--) {
            if (!getBlockState(x, y, z).isAir()) {
                return y;
            }
        }
        return bottom - 1;
    }
}
