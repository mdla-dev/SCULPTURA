package dev.sculptura.common.preview;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.FrustumPlanes;
import dev.sculptura.core.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holographic Preview Manager (Section 5.C) providing:
 * - 16x16x16 sub-chunk geometry caching
 * - Incremental dirty-region mesh rebuilding with 6-neighbor internal face culling
 * - Frustum culling via FrustumPlanes
 * - Configurable opacity & depth behavior
 * - Camera-relative rendering dispatch
 * - Strict GPU resource cleanup (never leaks buffers when regions are cleared or shrunk)
 * - Read-only guarantee (never modifies the world during preview)
 */
public final class PreviewManager {
    private final PreviewRenderBackend renderBackend;
    private final VoxelBuffer activePreviewBuffer = new VoxelBuffer();
    private final Map<Long, PreviewRenderBackend.SubChunkMeshData> cachedMeshes = new HashMap<>();
    private long revisionCounter = 1L;
    private float opacity = 0.65f;
    private boolean depthTestEnabled = true;

    public PreviewManager(PreviewRenderBackend renderBackend) {
        this.renderBackend = renderBackend;
    }

    public float opacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        if (Math.abs(this.opacity - opacity) > 1.0e-4f) {
            this.opacity = Math.max(0.05f, Math.min(1.0f, opacity));
            // Mark all active sub-chunks dirty so alpha values refresh
            for (long key : activePreviewBuffer.activeSubChunkKeys()) {
                rebuildSubChunkMesh(key);
            }
        }
    }

    public boolean depthTestEnabled() {
        return depthTestEnabled;
    }

    public void setDepthTestEnabled(boolean depthTestEnabled) {
        this.depthTestEnabled = depthTestEnabled;
    }

    public VoxelBuffer activePreviewBuffer() {
        return activePreviewBuffer;
    }

    /**
     * Replaces or incrementally updates the preview buffer, rebuilding ONLY dirty 16x16x16 sub-chunks
     * and releasing GPU buffers for any sub-chunks that became empty.
     */
    public int updatePreviewIncremental(VoxelBuffer nextPreview) {
        Set<Long> previousChunks = new HashSet<>(activePreviewBuffer.activeSubChunkKeys());
        Set<Long> nextChunks = nextPreview.activeSubChunkKeys();

        // Release removed sub-chunks
        for (Long oldKey : previousChunks) {
            if (!nextChunks.contains(oldKey)) {
                cachedMeshes.remove(oldKey);
                renderBackend.releaseSubChunkGpuResource(oldKey);
            }
        }

        // Diff blocks per sub-chunk so unchanged sub-chunks are NOT rebuilt
        int rebuiltSubChunks = 0;
        for (Long chunkKey : nextChunks) {
            Collection<BlockPos3i> nextPositions = nextPreview.positionsInSubChunk(chunkKey);
            Collection<BlockPos3i> prevPositions = activePreviewBuffer.positionsInSubChunk(chunkKey);
            boolean dirty = ! cachedMeshes.containsKey(chunkKey) || nextPositions.size() != prevPositions.size();
            if (!dirty) {
                for (BlockPos3i pos : nextPositions) {
                    if (!nextPreview.getBlock(pos).equals(activePreviewBuffer.getBlock(pos))) {
                        dirty = true;
                        break;
                    }
                }
            }
            if (dirty) {
                rebuiltSubChunks++;
            }
        }

        activePreviewBuffer.clear();
        activePreviewBuffer.mergeFrom(nextPreview);

        for (Long chunkKey : nextChunks) {
            if (!cachedMeshes.containsKey(chunkKey) || activePreviewBuffer.peekDirtySubChunks().contains(chunkKey)) {
                rebuildSubChunkMesh(chunkKey);
            }
        }
        activePreviewBuffer.consumeDirtySubChunks();
        return rebuiltSubChunks;
    }

    public void applySingleVoxelDelta(BlockPos3i pos, BlockStateRef state) {
        if (state == null) {
            activePreviewBuffer.removeBlock(pos);
        } else {
            activePreviewBuffer.setBlock(pos, state);
        }
        for (long dirtyKey : activePreviewBuffer.consumeDirtySubChunks()) {
            if (activePreviewBuffer.positionsInSubChunk(dirtyKey).isEmpty()) {
                cachedMeshes.remove(dirtyKey);
                renderBackend.releaseSubChunkGpuResource(dirtyKey);
            } else {
                rebuildSubChunkMesh(dirtyKey);
            }
        }
    }

    private void rebuildSubChunkMesh(long subChunkKey) {
        Collection<BlockPos3i> positions = activePreviewBuffer.positionsInSubChunk(subChunkKey);
        if (positions.isEmpty()) {
            cachedMeshes.remove(subChunkKey);
            renderBackend.releaseSubChunkGpuResource(subChunkKey);
            return;
        }

        BlockPos3i sample = positions.iterator().next();
        int chunkX = sample.chunkX();
        int sectionY = sample.sectionY();
        int chunkZ = sample.chunkZ();

        List<PreviewRenderBackend.PreviewQuad> quads = new ArrayList<>(positions.size() * 6);
        for (BlockPos3i pos : positions) {
            BlockStateRef state = activePreviewBuffer.getBlock(pos);
            if (state == null) continue;
            int color = state.isAir() ? 0x55EF4444 : state.previewColorArgb(opacity);

            for (Direction3D face : Direction3D.values()) {
                BlockPos3i neighbor = pos.offset(face);
                BlockStateRef neighborState = activePreviewBuffer.getBlock(neighbor);
                // Cull interior faces shared between two opaque preview blocks of the same state
                if (neighborState != null && !neighborState.isAir() && !state.isAir()) {
                    continue;
                }
                quads.add(new PreviewRenderBackend.PreviewQuad(pos, face, color, state.blockId()));
            }
        }

        PreviewRenderBackend.SubChunkMeshData meshData = new PreviewRenderBackend.SubChunkMeshData(
                subChunkKey, chunkX, sectionY, chunkZ, ++revisionCounter, List.copyOf(quads)
        );
        cachedMeshes.put(subChunkKey, meshData);
        renderBackend.uploadSubChunkMesh(meshData);
    }

    /**
     * Renders only the cached sub-chunks that pass view frustum culling relative to the camera.
     * Returns the number of rendered sub-chunks.
     */
    public int renderVisibleSubChunks(Vec3d cameraPosition, FrustumPlanes frustum) {
        int rendered = 0;
        for (PreviewRenderBackend.SubChunkMeshData mesh : cachedMeshes.values()) {
            if (frustum != null && !frustum.intersectsAabb(
                    mesh.minX(), mesh.minY(), mesh.minZ(),
                    mesh.maxX(), mesh.maxY(), mesh.maxZ()
            )) {
                continue;
            }
            renderBackend.renderSubChunk(mesh.subChunkKey(), cameraPosition, opacity, depthTestEnabled);
            rendered++;
        }
        return rendered;
    }

    public int cachedSubChunkCount() {
        return cachedMeshes.size();
    }

    public void clearAndDisposeGpuResources() {
        activePreviewBuffer.clear();
        cachedMeshes.clear();
        renderBackend.disposeAllGpuResources();
    }
}
