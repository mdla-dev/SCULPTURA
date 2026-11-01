package dev.sculptura.core;

import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.brush.BrushShape;
import dev.sculptura.core.brush.BrushType;
import dev.sculptura.core.brush.FalloffCurve;
import dev.sculptura.core.brush.SculptBrushEngine;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.material.GradientMaterialPattern;
import dev.sculptura.core.material.MaterialPaletteRegistry;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.material.WeightedPalettePattern;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.Vec3d;
import dev.sculptura.core.shape.ShapeGenerator;
import dev.sculptura.core.shape.ShapeParameters;
import dev.sculptura.core.shape.ShapeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShapeBrushesMaterialsTest {

    @Test
    void testAllSixteenProceduralShapeGeneratorsProduceValidVoxels() {
        MaterialPattern stone = MaterialPattern.single(BlockStateRef.STONE);
        BlockPos3i origin = new BlockPos3i(0, 64, 0);

        for (ShapeType type : ShapeType.values()) {
            ShapeParameters params = new ShapeParameters(
                    type, origin, 5, 5, 5, 8, 1, false, Axis3D.Y, Direction3D.NORTH, 12345L
            );
            VoxelBuffer generated = ShapeGenerator.generate(params, stone);
            assertFalse(generated.isEmpty(), "ShapeType " + type + " should generate non-empty voxels");
            assertTrue(generated.bounds().isPresent());
        }

        // Verify hollow sphere has fewer blocks than solid sphere of same radius
        VoxelBuffer solidSphere = ShapeGenerator.generate(ShapeParameters.sphere(origin, 6, false), stone);
        VoxelBuffer hollowSphere = ShapeGenerator.generate(ShapeParameters.sphere(origin, 6, true), stone);
        assertTrue(hollowSphere.size() < solidSphere.size());
        assertFalse(hollowSphere.contains(origin));
        assertTrue(solidSphere.contains(origin));
    }

    @Test
    void testSmartMaterialsWeightedPaletteAndAllFiveGradientModes() {
        WeightedPalettePattern weighted = WeightedPalettePattern.builder()
                .add(BlockStateRef.STONE, 75.0)
                .add(BlockStateRef.COBBLESTONE, 25.0)
                .build();
        assertEquals(75.0, weighted.percentageOf(BlockStateRef.STONE), 1.0e-6);
        assertEquals(25.0, weighted.percentageOf(BlockStateRef.COBBLESTONE), 1.0e-6);

        BoundingBox3i box = new BoundingBox3i(0, 60, 0, 16, 80, 16);
        // Deterministic evaluation check
        for (BlockPos3i p : box) {
            assertEquals(weighted.sample(p, box, 99L), weighted.sample(p, box, 99L));
        }

        List<BlockStateRef> ramp = List.of(BlockStateRef.DEEPSLATE, BlockStateRef.STONE, BlockStateRef.SNOW_BLOCK);
        GradientMaterialPattern vertical = GradientMaterialPattern.vertical(ramp, 0.0);
        assertEquals(BlockStateRef.DEEPSLATE, vertical.sample(new BlockPos3i(0, 60, 0), box, 1L));
        assertEquals(BlockStateRef.SNOW_BLOCK, vertical.sample(new BlockPos3i(0, 80, 0), box, 1L));

        GradientMaterialPattern horizontal = GradientMaterialPattern.horizontal(ramp, 0.0);
        assertEquals(BlockStateRef.DEEPSLATE, horizontal.sample(new BlockPos3i(0, 65, 0), box, 1L));
        assertEquals(BlockStateRef.SNOW_BLOCK, horizontal.sample(new BlockPos3i(16, 65, 0), box, 1L));

        GradientMaterialPattern directional = GradientMaterialPattern.directional(ramp, new Vec3d(1, 1, 0), 0.0);
        assertNotNull(directional.sample(new BlockPos3i(8, 70, 8), box, 1L));

        GradientMaterialPattern radial = GradientMaterialPattern.radial(ramp, 0.0);
        assertEquals(BlockStateRef.DEEPSLATE, radial.sample(box.centerBlockPos(), box, 1L));

        GradientMaterialPattern noiseGrad = GradientMaterialPattern.noise(ramp, 0.08, 0.1);
        assertNotNull(noiseGrad.sample(new BlockPos3i(5, 65, 5), box, 42L));

        MaterialPaletteRegistry registry = new MaterialPaletteRegistry();
        assertFalse(registry.searchBlocks("stone_bricks").isEmpty());
        assertFalse(registry.selectByTag("#minecraft:stone_bricks").isEmpty());
        registry.toggleFavorite(BlockStateRef.STONE);
        assertTrue(registry.isFavorite(BlockStateRef.STONE));
    }

    @Test
    void testAllTwelveTerrainSculptingBrushes() {
        // Create flat terrain at Y <= 64 with a small bump at (0, 65..67, 0)
        VoxelWorldView flatWorld = pos -> {
            if (pos.x() == 0 && pos.z() == 0 && pos.y() <= 67 && pos.y() >= -64) {
                return BlockStateRef.STONE;
            }
            if (pos.y() <= 64 && pos.y() >= -64) {
                return (pos.x() + pos.z()) % 2 == 0 ? BlockStateRef.STONE : BlockStateRef.DIRT;
            }
            return BlockStateRef.AIR;
        };

        BlockPos3i center = new BlockPos3i(0, 64, 0);
        for (BrushType type : BrushType.values()) {
            BrushSettings settings = new BrushSettings(
                    type, BrushShape.SPHERE, 4, 0.85, 0.5, FalloffCurve.SMOOTHSTEP,
                    1.0, 0.1, 2.0, 66, MaterialPattern.single(BlockStateRef.GRASS_BLOCK),
                    BlockStateRef.STONE, 777L, 120.0
            );
            VoxelBuffer stroke = SculptBrushEngine.applyBrush(center, settings, flatWorld);
            assertNotNull(stroke, "Brush " + type + " must return a valid VoxelBuffer");
            if (type == BrushType.RAISE || type == BrushType.LOWER || type == BrushType.PAINT || type == BrushType.REPLACE || type == BrushType.FLATTEN) {
                assertFalse(stroke.isEmpty(), "Brush " + type + " should produce edits on flat terrain");
            }
        }
    }
}
