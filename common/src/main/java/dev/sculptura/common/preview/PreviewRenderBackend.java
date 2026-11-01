package dev.sculptura.common.preview;

import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.Vec3d;

import java.util.List;

/**
 * Platform-independent rendering interface for Holographic Ghost Block Previews (Section 5.C).
 * Implemented by each Fabric, Forge, and NeoForge platform adapter to bind native OpenGL / BufferBuilder resources.
 */
public interface PreviewRenderBackend {
    record PreviewQuad(
            BlockPos3i blockPos,
            Direction3D face,
            int colorArgb,
            String blockId
    ) {}

    record SubChunkMeshData(
            long subChunkKey,
            int chunkX,
            int sectionY,
            int chunkZ,
            long revision,
            List<PreviewQuad> culledQuads
    ) {
        public double minX() { return chunkX * 16.0; }
        public double minY() { return sectionY * 16.0; }
        public double minZ() { return chunkZ * 16.0; }
        public double maxX() { return chunkX * 16.0 + 16.0; }
        public double maxY() { return sectionY * 16.0 + 16.0; }
        public double maxZ() { return chunkZ * 16.0 + 16.0; }
    }

    /**
     * Uploads or updates a 16x16x16 sub-chunk preview mesh on the GPU render thread.
     */
    void uploadSubChunkMesh(SubChunkMeshData meshData);

    /**
     * Renders a previously uploaded sub-chunk mesh relative to the active camera position with depth testing.
     */
    void renderSubChunk(long subChunkKey, Vec3d cameraPosition, float opacity, boolean depthTest);

    /**
     * Frees the GPU vertex buffer associated with a single 16x16x16 sub-chunk.
     */
    void releaseSubChunkGpuResource(long subChunkKey);

    /**
     * Frees all allocated GPU buffers when clearing previews, switching worlds, or shutting down.
     */
    void disposeAllGpuResources();

    int allocatedGpuBufferCount();
}
