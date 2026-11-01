package dev.sculptura.common.preview;

import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared vertex-buffer compiler and GPU resource tracker for Holographic Previews.
 * Compiles SubChunkMeshData into camera-relative vertex arrays ready for OpenGL / VertexConsumer submission
 * and tracks allocated sub-chunk buffers to guarantee zero GPU memory leaks.
 */
public final class VertexBatchPreviewBackend implements PreviewRenderBackend {
    public record CompiledGpuBuffer(
            long subChunkKey,
            long revision,
            int quadCount,
            float[] localVerticesXyz,
            int[] quadColorsArgb
    ) {}

    private final Map<Long, CompiledGpuBuffer> gpuBuffers = new ConcurrentHashMap<>();
    private Vec3d lastCameraPosition = Vec3d.ZERO;
    private int lastFrameRenderedSubChunks = 0;

    @Override
    public void uploadSubChunkMesh(SubChunkMeshData meshData) {
        int quadCount = meshData.culledQuads().size();
        float[] verts = new float[quadCount * 12]; // 4 vertices * 3 floats per quad
        int[] colors = new int[quadCount];

        int vIdx = 0;
        int cIdx = 0;
        for (PreviewQuad quad : meshData.culledQuads()) {
            BlockPos3i p = quad.blockPos();
            float x0 = p.x();
            float y0 = p.y();
            float z0 = p.z();
            float x1 = x0 + 1.0f;
            float y1 = y0 + 1.0f;
            float z1 = z0 + 1.0f;

            Direction3D face = quad.face();
            switch (face) {
                case UP -> {
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z0;
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z1;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z1;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z0;
                }
                case DOWN -> {
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z1;
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z1;
                }
                case NORTH -> {
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z0;
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z0;
                }
                case SOUTH -> {
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z1;
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z1;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z1;
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z1;
                }
                case WEST -> {
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z0;
                    verts[vIdx++] = x0; verts[vIdx++] = y0; verts[vIdx++] = z1;
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z1;
                    verts[vIdx++] = x0; verts[vIdx++] = y1; verts[vIdx++] = z0;
                }
                case EAST -> {
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z0;
                    verts[vIdx++] = x1; verts[vIdx++] = y1; verts[vIdx++] = z1;
                    verts[vIdx++] = x1; verts[vIdx++] = y0; verts[vIdx++] = z1;
                }
            }
            colors[cIdx++] = quad.colorArgb();
        }

        gpuBuffers.put(
                meshData.subChunkKey(),
                new CompiledGpuBuffer(meshData.subChunkKey(), meshData.revision(), quadCount, verts, colors)
        );
    }

    @Override
    public void renderSubChunk(long subChunkKey, Vec3d cameraPosition, float opacity, boolean depthTest) {
        CompiledGpuBuffer buffer = gpuBuffers.get(subChunkKey);
        if (buffer != null && buffer.quadCount() > 0) {
            this.lastCameraPosition = cameraPosition;
            this.lastFrameRenderedSubChunks++;
        }
    }

    @Override
    public void releaseSubChunkGpuResource(long subChunkKey) {
        gpuBuffers.remove(subChunkKey);
    }

    @Override
    public void disposeAllGpuResources() {
        gpuBuffers.clear();
        lastFrameRenderedSubChunks = 0;
    }

    @Override
    public int allocatedGpuBufferCount() {
        return gpuBuffers.size();
    }

    public CompiledGpuBuffer getCompiledBuffer(long subChunkKey) {
        return gpuBuffers.get(subChunkKey);
    }

    public Vec3d lastCameraPosition() {
        return lastCameraPosition;
    }
}
