package dev.sculptura.core;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.FrustumPlanes;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Ray3d;
import dev.sculptura.core.math.Rotation90;
import dev.sculptura.core.math.SimplexNoise3D;
import dev.sculptura.core.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeometryAndMathTest {

    @Test
    void testBoundingBoxNormalizationAndFaceResize() {
        BoundingBox3i box = BoundingBox3i.fromCorners(new BlockPos3i(10, 70, 10), new BlockPos3i(0, 60, 0));
        assertEquals(0, box.minX());
        assertEquals(10, box.maxX());
        assertEquals(11, box.sizeX());
        assertEquals(11, box.sizeY());
        assertEquals(11, box.sizeZ());
        assertEquals(1331L, box.volume());

        // Expand UP face by +4 blocks
        BoundingBox3i expandedUp = box.resizeFace(Direction3D.UP, 4);
        assertEquals(74, expandedUp.maxY());
        assertEquals(60, expandedUp.minY());

        // Expand WEST face outward by +3 blocks (minX moves from 0 to -3)
        BoundingBox3i expandedWest = expandedUp.resizeFace(Direction3D.WEST, 3);
        assertEquals(-3, expandedWest.minX());
    }

    @Test
    void testRotationAndMirroringOfCoordinatesAndBlockStates() {
        BlockPos3i pivot = new BlockPos3i(0, 64, 0);
        BlockPos3i pos = new BlockPos3i(4, 64, 1);

        BlockPos3i rotated90 = Rotation90.CLOCKWISE_90.transform(pos, pivot, Axis3D.Y);
        assertEquals(new BlockPos3i(-1, 64, 4), rotated90);

        BlockPos3i restored = Rotation90.CLOCKWISE_90.inverse().transform(rotated90, pivot, Axis3D.Y);
        assertEquals(pos, restored);

        BlockStateRef stairs = BlockStateRef.parse("minecraft:oak_stairs[half=bottom,facing=north]");
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom]", stairs.canonicalString());

        BlockStateRef rotatedStairs = stairs.rotate(Rotation90.CLOCKWISE_90, Axis3D.Y);
        assertEquals("east", rotatedStairs.properties().get("facing"));

        BlockStateRef mirroredStairs = rotatedStairs.mirror(MirrorPlane.YZ);
        assertEquals("west", mirroredStairs.properties().get("facing"));
    }

    @Test
    void testRayIntersectionAnd120BlockVoxelRaycast() {
        Ray3d ray = new Ray3d(new Vec3d(0.5, 70.5, -10.0), new Vec3d(0.0, 0.0, 1.0));
        Optional<Ray3d.AabbHit> aabbHit = ray.intersectAabb(new Vec3d(0, 68, 5), new Vec3d(2, 72, 8), 120.0);
        assertTrue(aabbHit.isPresent());
        assertEquals(Direction3D.NORTH, aabbHit.get().hitFace());
        assertEquals(15.0, aabbHit.get().tNear(), 1.0e-5);

        // Voxel DDA raycast at distance 95 blocks (within 120-block default sculpt range)
        BlockPos3i solidTarget = new BlockPos3i(0, 70, 85);
        Optional<Ray3d.VoxelRaycastHit> voxelHit = ray.raycastVoxels(
                Ray3d.DEFAULT_SCULPT_RANGE,
                p -> p.equals(solidTarget)
        );
        assertTrue(voxelHit.isPresent());
        assertEquals(solidTarget, voxelHit.get().blockPos());
        assertEquals(Direction3D.NORTH, voxelHit.get().hitFace());
    }

    @Test
    void testSimplexNoiseDeterminismAndFrustumCulling() {
        SimplexNoise3D n1 = new SimplexNoise3D(987654321L);
        SimplexNoise3D n2 = new SimplexNoise3D(987654321L);
        for (int i = 0; i < 20; i++) {
            double v1 = n1.fbm(i * 0.13, i * 0.07, i * 0.19, 4, 2.0, 0.5);
            double v2 = n2.fbm(i * 0.13, i * 0.07, i * 0.19, 4, 2.0, 0.5);
            assertEquals(v1, v2, 1.0e-12);
            assertTrue(v1 >= -1.0 && v1 <= 1.0);
        }

        FrustumPlanes frustum = FrustumPlanes.fromCamera(
                new Vec3d(0, 70, 0),
                new Vec3d(0, 0, 1),
                new Vec3d(0, 1, 0),
                Math.toRadians(70.0),
                16.0 / 9.0,
                0.1,
                256.0
        );
        assertTrue(frustum.intersectsAabb(-4, 66, 20, 4, 74, 28));
        assertFalse(frustum.intersectsAabb(-4, 66, -40, 4, 74, -20));
    }

    @Test
    void testVoxelBufferSubChunkPartitioningAndDirtyTracking() {
        VoxelBuffer buffer = new VoxelBuffer();
        buffer.setBlock(0, 64, 0, BlockStateRef.STONE);
        buffer.setBlock(15, 64, 15, BlockStateRef.STONE);
        buffer.setBlock(16, 64, 0, BlockStateRef.DEEPSLATE);

        assertEquals(3, buffer.size());
        assertEquals(2, buffer.activeSubChunkKeys().size());
        Set<Long> dirty = buffer.consumeDirtySubChunks();
        assertEquals(2, dirty.size());
        assertTrue(buffer.peekDirtySubChunks().isEmpty());
    }
}
