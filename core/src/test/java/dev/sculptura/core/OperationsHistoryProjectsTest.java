package dev.sculptura.core;

import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.brush.BrushType;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.history.HistoryManager;
import dev.sculptura.core.layer.LayerManager;
import dev.sculptura.core.layer.VoxelLayer;
import dev.sculptura.core.material.GradientMaterialPattern;
import dev.sculptura.core.material.MaterialPaletteRegistry;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Rotation90;
import dev.sculptura.core.operation.CoreOperations;
import dev.sculptura.core.operation.EditOperation;
import dev.sculptura.core.project.SculpturaProject;
import dev.sculptura.core.project.SculpturaProjectSerializer;
import dev.sculptura.core.shape.ShapeParameters;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OperationsHistoryProjectsTest {

    private static final class MutableTestWorld implements VoxelWorldView {
        private final Map<BlockPos3i, BlockStateRef> map = new HashMap<>();

        @Override
        public BlockStateRef getBlockState(BlockPos3i pos) {
            return map.getOrDefault(pos, pos.y() <= 60 ? BlockStateRef.STONE : BlockStateRef.AIR);
        }

        public void setBlock(BlockPos3i pos, BlockStateRef state) {
            map.put(pos, state);
        }
    }

    @Test
    void testAllFourteenOperationsPreviewCommitEquivalenceAndConflictSafeUndoRedo() {
        MutableTestWorld world = new MutableTestWorld();
        HistoryManager history = HistoryManager.createDefault();
        EditOperation.OperationLimits limits = EditOperation.OperationLimits.defaults();

        BoundingBox3i box = new BoundingBox3i(0, 61, 0, 2, 62, 2);
        MaterialPattern mat = MaterialPattern.single(BlockStateRef.COBBLESTONE);

        EditOperation setOp = new CoreOperations.SetBlocksOperation(box, mat, 42L);
        VoxelBuffer preview = setOp.planPreview(world, new AtomicBoolean(false));
        assertEquals(18, preview.size());

        EditOperation.ExecutionResult execResult = setOp.execute(
                world, world::setBlock, limits, true, new AtomicBoolean(false), null
        );
        assertTrue(execResult.succeeded());
        assertEquals(18, execResult.modifiedBlocks());
        history.record(execResult.historyEntry());

        // Verify preview and committed world states match identically
        for (Map.Entry<BlockPos3i, BlockStateRef> e : preview.entries().entrySet()) {
            assertEquals(e.getValue(), world.getBlockState(e.getKey()));
        }

        // Simulate another player modifying (0, 61, 0) to DEEPSLATE after our operation
        BlockPos3i conflictedPos = new BlockPos3i(0, 61, 0);
        world.setBlock(conflictedPos, BlockStateRef.DEEPSLATE);

        // Undo should detect the conflict at (0, 61, 0), skip overwriting it, and restore the other 17 blocks!
        HistoryManager.UndoRedoReport undoReport = history.undo(world, world::setBlock, false, new AtomicBoolean(false));
        assertTrue(undoReport.executed());
        assertTrue(undoReport.isPartial());
        assertEquals(17, undoReport.restoredBlocks());
        assertEquals(1, undoReport.skippedConflicts());
        assertEquals(BlockStateRef.DEEPSLATE, world.getBlockState(conflictedPos));
        assertEquals(BlockStateRef.AIR, world.getBlockState(new BlockPos3i(1, 61, 1)));

        // Seed a 3-block terrain spire at (0, 61..63, 0) so SmoothOperation and FlattenOperation act on uneven terrain
        world.setBlock(new BlockPos3i(0, 61, 0), BlockStateRef.STONE);
        world.setBlock(new BlockPos3i(0, 62, 0), BlockStateRef.STONE);
        world.setBlock(new BlockPos3i(0, 63, 0), BlockStateRef.STONE);

        // Verify remaining operations instantiate, validate, and plan previews cleanly
        List<EditOperation> otherOps = List.of(
                new CoreOperations.ReplaceBlocksOperation(new BoundingBox3i(0, 58, 0, 2, 60, 2), BlockStateRef.STONE, mat, 1L),
                new CoreOperations.FillShapeOperation(ShapeParameters.sphere(new BlockPos3i(0, 65, 0), 3, false), mat),
                new CoreOperations.SphereOperation(new BlockPos3i(0, 65, 0), 3, true, mat, 1L),
                new CoreOperations.CylinderOperation(new BlockPos3i(0, 65, 0), 3, 4, false, Axis3D.Y, mat),
                new CoreOperations.StackOperation(new BoundingBox3i(0, 59, 0, 1, 60, 1), Direction3D.UP, 2, 0, false),
                new CoreOperations.CopyOperation(new BoundingBox3i(0, 59, 0, 1, 60, 1), new BlockPos3i(5, 0, 0), false),
                new CoreOperations.MoveOperation(new BoundingBox3i(0, 59, 0, 1, 60, 1), new BlockPos3i(0, 4, 0)),
                new CoreOperations.RotateOperation(new BoundingBox3i(0, 59, 0, 2, 60, 1), new BlockPos3i(0, 60, 0), Rotation90.CLOCKWISE_90, Axis3D.Y, false),
                new CoreOperations.MirrorOperation(new BoundingBox3i(0, 59, 0, 2, 60, 1), new BlockPos3i(0, 60, 0), MirrorPlane.YZ, false),
                new CoreOperations.PaintOperation(new BoundingBox3i(0, 59, 0, 2, 60, 2), mat, true, 1L),
                new CoreOperations.SmoothOperation(new BlockPos3i(0, 60, 0), 3, 0.85, mat),
                new CoreOperations.FlattenOperation(new BlockPos3i(0, 60, 0), 3, 62, 0.85, mat),
                new CoreOperations.GradientOperation(
                        new BoundingBox3i(0, 58, 0, 2, 60, 2),
                        GradientMaterialPattern.vertical(List.of(BlockStateRef.DEEPSLATE, BlockStateRef.STONE), 0.0),
                        true,
                        1L
                )
        );
        for (EditOperation op : otherOps) {
            assertTrue(op.validate(limits, world).valid(), "Operation " + op.operationId() + " should validate");
            assertFalse(op.planPreview(world, new AtomicBoolean(false)).isEmpty(), "Operation " + op.operationId() + " should plan blocks");
        }
    }

    @Test
    void testLayersAndVersionedProjectSerializationWithSchemaMigration() {
        LayerManager layerManager = new LayerManager();
        VoxelLayer base = layerManager.activeLayer().orElseThrow();
        base.putBlock(new BlockPos3i(1, 64, 1), BlockStateRef.STONE);

        VoxelLayer second = layerManager.createLayer("Roof Detail", "Architecture");
        second.putBlock(new BlockPos3i(1, 65, 1), BlockStateRef.COBBLESTONE);
        assertEquals(2, layerManager.composeVisiblePreview().size());

        second.setVisible(false);
        assertEquals(1, layerManager.composeVisiblePreview().size());
        second.setVisible(true);

        SculpturaProject project = new SculpturaProject(
                "proj_01",
                "Cathedral Blueprint",
                SculpturaProject.CURRENT_SCHEMA_VERSION,
                new BlockPos3i(100, 64, 200),
                layerManager.layers(),
                List.of(),
                List.of(BrushSettings.defaults(BrushType.SMOOTH, 6, 0.5, MaterialPattern.single(BlockStateRef.STONE))),
                List.of(new SculpturaProject.SavedOperationPreset("op1", "sphere", "r=6"))
        );

        String serialized = SculpturaProjectSerializer.exportToString(project);
        SculpturaProject restored = SculpturaProjectSerializer.importFromString(serialized);

        assertEquals("Cathedral Blueprint", restored.name());
        assertEquals(SculpturaProject.CURRENT_SCHEMA_VERSION, restored.schemaVersion());
        assertEquals(new BlockPos3i(100, 64, 200), restored.originAnchor());
        assertEquals(2, restored.layers().size());
        assertEquals(1, restored.savedBrushes().size());
        assertEquals(1, restored.savedOperations().size());

        // Test schema v1 -> v2 migration
        String legacyV1 = """
                SCULPTURA_PROJECT
                SCHEMA_VERSION=1
                PROJECT_ID=legacy_1
                PROJECT_NAME=Legacy Castle
                ORIGIN=0,64,0
                LAYER|l1|Walls|true|false
                VOXEL|l1|2,64,2|minecraft:stone_bricks
                END_SCULPTURA_PROJECT
                """;
        SculpturaProject migrated = SculpturaProjectSerializer.importFromString(legacyV1);
        assertEquals(SculpturaProject.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals("Migrated_v1", migrated.layers().get(0).groupName());
        assertEquals(BlockStateRef.parse("minecraft:stone_bricks"), migrated.layers().get(0).voxelBuffer().getBlock(new BlockPos3i(2, 64, 2)));
    }
}
