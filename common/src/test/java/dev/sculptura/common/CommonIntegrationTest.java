package dev.sculptura.common;

import dev.sculptura.common.execution.TickBudgetedExecutor;
import dev.sculptura.common.gizmo.TransformGizmoController;
import dev.sculptura.common.i18n.I18nManager;
import dev.sculptura.common.network.SculpturaPacketCodec;
import dev.sculptura.common.network.ServerSecurityValidator;
import dev.sculptura.common.platform.PlatformWorldAdapter;
import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.PreviewManager;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;
import dev.sculptura.common.selection.SelectionController;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.Ray3d;
import dev.sculptura.core.math.Vec3d;
import dev.sculptura.core.operation.CoreOperations;
import dev.sculptura.core.operation.EditOperation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommonIntegrationTest {

    @Test
    void testSelectionMouseWheelFaceResizeAndGizmoRayPicking() {
        SelectionController selection = new SelectionController();
        selection.selectBox(new BlockPos3i(0, 64, 0), new BlockPos3i(4, 68, 4));
        selection.setActiveFace(Direction3D.EAST);

        // Scroll mouse wheel +2 -> expands EAST face (maxX from 4 -> 6)
        assertTrue(selection.onMouseWheelScroll(1));
        assertTrue(selection.onMouseWheelScroll(1));
        assertEquals(6, selection.bounds().orElseThrow().maxX());

        // Lock selection -> prevents further modification
        selection.setLocked(true);
        assertFalse(selection.onMouseWheelScroll(1));
        assertEquals(6, selection.bounds().orElseThrow().maxX());
        selection.setLocked(false);

        assertTrue(selection.buildVisualDescriptor().isPresent());
        assertEquals(6, selection.buildVisualDescriptor().get().faceHandles().size());

        // Transform Gizmo Ray Intersection
        TransformGizmoController gizmo = new TransformGizmoController();
        gizmo.setPivot(new Vec3d(10.0, 70.0, 10.0));
        gizmo.setMode(TransformGizmoController.GizmoMode.TRANSLATE);

        // Cast a ray aiming through the X-axis handle at (11.5, 70.0, 10.0)
        Ray3d ray = new Ray3d(new Vec3d(11.5, 70.0, 5.0), new Vec3d(0.0, 0.0, 1.0));
        Optional<TransformGizmoController.GizmoIntersection> hit = gizmo.intersectRay(ray, 50.0);
        assertTrue(hit.isPresent());
        assertEquals(TransformGizmoController.GizmoHandle.AXIS_X, hit.get().handle());
    }

    @Test
    void testHolographicPreviewSubChunkCachingFaceCullingAndGpuDisposal() {
        VertexBatchPreviewBackend backend = new VertexBatchPreviewBackend();
        PreviewManager previewManager = new PreviewManager(backend);

        // Create a 2x2x2 cube of stone blocks in sub-chunk (0, 4, 0)
        VoxelBuffer cube = new VoxelBuffer();
        for (int x = 0; x < 2; x++) {
            for (int y = 64; y < 66; y++) {
                for (int z = 0; z < 2; z++) {
                    cube.setBlock(x, y, z, BlockStateRef.STONE);
                }
            }
        }
        int rebuilt = previewManager.updatePreviewIncremental(cube);
        assertEquals(1, rebuilt);
        assertEquals(1, backend.allocatedGpuBufferCount());

        // 8 blocks in a 2x2x2 cube have 8*6=48 total faces, but 24 internal shared faces are culled -> 24 exterior quads!
        long key = new BlockPos3i(0, 64, 0).subChunkKey();
        assertEquals(24, backend.getCompiledBuffer(key).quadCount());

        // Dispose GPU resources -> zero leaked buffers
        previewManager.clearAndDisposeGpuResources();
        assertEquals(0, backend.allocatedGpuBufferCount());
    }

    @Test
    void testMultiplayerSecurityValidationUnloadedChunkRejectionAndTickBudgetedExecution() {
        Map<BlockPos3i, BlockStateRef> serverBlocks = new HashMap<>();
        PlatformWorldAdapter mockServerWorld = new PlatformWorldAdapter() {
            @Override
            public boolean isChunkLoaded(int chunkX, int chunkZ) {
                // Only chunks around origin [-2..2] are loaded
                return Math.abs(chunkX) <= 2 && Math.abs(chunkZ) <= 2;
            }

            @Override
            public boolean isValidBlockState(BlockStateRef state) {
                return state != null;
            }

            @Override
            public void setBlockStateOnMainThread(BlockPos3i pos, BlockStateRef state) {
                serverBlocks.put(pos, state);
            }

            @Override
            public boolean hasEditPermission(String playerUuid, int permissionLevel) {
                return permissionLevel >= 2;
            }

            @Override
            public BlockStateRef getBlockState(BlockPos3i pos) {
                return serverBlocks.getOrDefault(pos, BlockStateRef.AIR);
            }
        };

        ServerSecurityValidator validator = ServerSecurityValidator.createDefault();
        EditOperation validOp = new CoreOperations.SetBlocksOperation(
                new BoundingBox3i(0, 64, 0, 9, 64, 9),
                MaterialPattern.single(BlockStateRef.STONE),
                1L
        );

        // 1. Permission check fails for permissionLevel < 2
        assertFalse(validator.validateOperationRequest("p1", 0, validOp, mockServerWorld, 1000L).allowed());

        // 2. Unloaded chunk check rejects edit at chunkX=50 without force-loading
        EditOperation unloadedChunkOp = new CoreOperations.SetBlocksOperation(
                new BoundingBox3i(800, 64, 800, 805, 65, 805),
                MaterialPattern.single(BlockStateRef.STONE),
                1L
        );
        ServerSecurityValidator.SecurityDecision unloadedDecision =
                validator.validateOperationRequest("p1", 2, unloadedChunkOp, mockServerWorld, 1000L);
        assertFalse(unloadedDecision.allowed());
        assertEquals("UNLOADED_CHUNK", unloadedDecision.rejectionCode());

        // 3. Valid operation passes and executes across server ticks via TickBudgetedExecutor
        assertTrue(validator.validateOperationRequest("p1", 2, validOp, mockServerWorld, 1000L).allowed());
        TickBudgetedExecutor executor = new TickBudgetedExecutor(30, 50L); // 30 blocks per tick budget
        SculpturaRuntime runtime = new SculpturaRuntime("CommonTest", "1.21.1", new VertexBatchPreviewBackend());
        executor.submitValidatedOperation("task_1", validOp, mockServerWorld, runtime.historyManager(), null);

        // 100 blocks at 30 blocks/tick takes 4 ticks to place and 1 tick to finalize
        int ticks = 0;
        while (executor.pendingTaskCount() > 0 && ticks < 10) {
            executor.tickServer(mockServerWorld);
            ticks++;
        }
        assertEquals(100, serverBlocks.size());
        assertTrue(runtime.historyManager().canUndo());

        // 4. Packet codec roundtrip and localization check
        SculpturaPacketCodec.OperationCommitPacket pkt = new SculpturaPacketCodec.OperationCommitPacket(
                "req_1", "p1", "set_blocks", new BoundingBox3i(0, 64, 0, 2, 65, 2), BlockStateRef.STONE, 3, 42L
        );
        byte[] bytes = SculpturaPacketCodec.encodeOperationCommit(pkt);
        SculpturaPacketCodec.OperationCommitPacket decoded = SculpturaPacketCodec.decodeOperationCommit(bytes);
        assertEquals(pkt.requestId(), decoded.requestId());
        assertEquals(pkt.bounds(), decoded.bounds());

        runtime.i18nManager().setActiveLocale(I18nManager.SupportedLocale.RU_RU);
        assertTrue(runtime.i18nManager().translate("sculptura.title").contains("Визуальный"));
    }
}
