package dev.sculptura.common.platform;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.math.BlockPos3i;

/**
 * Platform-specific world adapter interface implemented by Fabric, Forge, and NeoForge modules.
 * Enforces loaded-chunk checks so server operations never force-load arbitrary chunks from client requests.
 */
public interface PlatformWorldAdapter extends VoxelWorldView {
    boolean isChunkLoaded(int chunkX, int chunkZ);

    boolean isValidBlockState(BlockStateRef state);

    void setBlockStateOnMainThread(BlockPos3i pos, BlockStateRef state);

    boolean hasEditPermission(String playerUuid, int permissionLevel);
}
