package dev.sculptura.core.shape;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.SimplexNoise3D;

/**
 * Generates all 16 procedural 3D shapes specified in Section 5.F into a VoxelBuffer.
 */
public final class ShapeGenerator {
    private ShapeGenerator() {}

    public static VoxelBuffer generate(ShapeParameters params, MaterialPattern material) {
        VoxelBuffer buffer = new VoxelBuffer();
        BlockPos3i o = params.origin();
        int rx = params.radiusX();
        int ry = params.radiusY();
        int rz = params.radiusZ();
        int h = params.height();
        int thickness = params.wallThickness();
        boolean hollow = params.hollow();
        long seed = params.seed();

        BoundingBox3i approxBounds = new BoundingBox3i(
                o.x() - rx - 2, o.y() - ry - 2, o.z() - rz - 2,
                o.x() + rx + 2, o.y() + Math.max(ry, h) + 2, o.z() + rz + 2
        );

        switch (params.type()) {
            case CUBE -> generateCube(buffer, o, rx, ry, rz, hollow, thickness, material, approxBounds, seed);
            case SPHERE -> generateEllipsoid(buffer, o, rx, rx, rx, hollow, thickness, false, material, approxBounds, seed);
            case ELLIPSOID -> generateEllipsoid(buffer, o, rx, ry, rz, hollow, thickness, false, material, approxBounds, seed);
            case HEMISPHERE -> generateEllipsoid(buffer, o, rx, ry, rz, hollow, thickness, true, material, approxBounds, seed);
            case CYLINDER -> generateCylinder(buffer, o, rx, rz, h, false, thickness, params.axis(), material, approxBounds, seed);
            case HOLLOW_CYLINDER -> generateCylinder(buffer, o, rx, rz, h, true, thickness, params.axis(), material, approxBounds, seed);
            case CONE -> generateCone(buffer, o, rx, rz, h, hollow, thickness, material, approxBounds, seed);
            case PYRAMID -> generatePyramid(buffer, o, rx, h, hollow, thickness, material, approxBounds, seed);
            case DOME -> generateDome(buffer, o, rx, ry, rz, thickness, material, approxBounds, seed);
            case ARCH -> generateArch(buffer, o, rx, h, thickness, material, approxBounds, seed);
            case RING -> generateRing(buffer, o, rx, Math.max(1, thickness), material, approxBounds, seed);
            case TUNNEL -> generateTunnel(buffer, o, rx, ry, h, thickness, material, approxBounds, seed);
            case STAIRS -> generateStairs(buffer, o, rx, h, params, material, approxBounds, seed);
            case WALL -> generateWall(buffer, o, rx, h, thickness, material, approxBounds, seed);
            case PROCEDURAL_MOUNTAIN -> generateProceduralMountain(buffer, o, rx, rz, h, seed, material, approxBounds);
            case ORGANIC_STRUCTURE -> generateOrganicStructure(buffer, o, rx, h, seed, material, approxBounds);
        }
        return buffer;
    }

    private static void put(VoxelBuffer buf, int x, int y, int z, MaterialPattern mat, BoundingBox3i bounds, long seed) {
        BlockPos3i pos = new BlockPos3i(x, y, z);
        buf.setBlock(pos, mat.sample(pos, bounds, seed));
    }

    private static void generateCube(
            VoxelBuffer buf, BlockPos3i o, int rx, int ry, int rz,
            boolean hollow, int thickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        for (int y = -ry; y <= ry; y++) {
            for (int z = -rz; z <= rz; z++) {
                for (int x = -rx; x <= rx; x++) {
                    if (hollow) {
                        boolean onShell = Math.abs(x) > rx - thickness
                                || Math.abs(y) > ry - thickness
                                || Math.abs(z) > rz - thickness;
                        if (!onShell) continue;
                    }
                    put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                }
            }
        }
    }

    private static void generateEllipsoid(
            VoxelBuffer buf, BlockPos3i o, int rx, int ry, int rz,
            boolean hollow, int thickness, boolean upperHalfOnly,
            MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        double invRx = 1.0 / (rx + 0.5);
        double invRy = 1.0 / (ry + 0.5);
        double invRz = 1.0 / (rz + 0.5);

        double innerRx = Math.max(0.5, rx + 0.5 - thickness);
        double innerRy = Math.max(0.5, ry + 0.5 - thickness);
        double innerRz = Math.max(0.5, rz + 0.5 - thickness);

        int startY = upperHalfOnly ? 0 : -ry;
        for (int y = startY; y <= ry; y++) {
            for (int z = -rz; z <= rz; z++) {
                for (int x = -rx; x <= rx; x++) {
                    double dOuter = (x * invRx) * (x * invRx) + (y * invRy) * (y * invRy) + (z * invRz) * (z * invRz);
                    if (dOuter <= 1.0) {
                        if (hollow) {
                            double dInner = (x / innerRx) * (x / innerRx) + (y / innerRy) * (y / innerRy) + (z / innerRz) * (z / innerRz);
                            if (dInner < 1.0 && (!upperHalfOnly || y >= thickness)) {
                                continue;
                            }
                        }
                        put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generateCylinder(
            VoxelBuffer buf, BlockPos3i o, int rx, int rz, int height,
            boolean hollow, int thickness, Axis3D axis,
            MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        double outerRx = rx + 0.5;
        double outerRz = rz + 0.5;
        double innerRx = Math.max(0.5, outerRx - thickness);
        double innerRz = Math.max(0.5, outerRz - thickness);

        for (int h = 0; h < height; h++) {
            for (int u = -rx; u <= rx; u++) {
                for (int v = -rz; v <= rz; v++) {
                    double dOuter = (u / outerRx) * (u / outerRx) + (v / outerRz) * (v / outerRz);
                    if (dOuter <= 1.0) {
                        if (hollow) {
                            double dInner = (u / innerRx) * (u / innerRx) + (v / innerRz) * (v / innerRz);
                            if (dInner < 1.0) continue;
                        }
                        int wx = switch (axis) {
                            case Y -> o.x() + u;
                            case X -> o.x() + h;
                            case Z -> o.x() + u;
                        };
                        int wy = switch (axis) {
                            case Y -> o.y() + h;
                            case X -> o.y() + u;
                            case Z -> o.y() + v;
                        };
                        int wz = switch (axis) {
                            case Y -> o.z() + v;
                            case X -> o.z() + v;
                            case Z -> o.z() + h;
                        };
                        put(buf, wx, wy, wz, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generateCone(
            VoxelBuffer buf, BlockPos3i o, int rx, int rz, int height,
            boolean hollow, int thickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        for (int y = 0; y < height; y++) {
            double progress = 1.0 - ((double) y / Math.max(1.0, height));
            double curRx = Math.max(0.5, (rx + 0.5) * progress);
            double curRz = Math.max(0.5, (rz + 0.5) * progress);
            double innerRx = Math.max(0.0, curRx - thickness);
            double innerRz = Math.max(0.0, curRz - thickness);

            for (int z = -rz; z <= rz; z++) {
                for (int x = -rx; x <= rx; x++) {
                    double d = (x / curRx) * (x / curRx) + (z / curRz) * (z / curRz);
                    if (d <= 1.0) {
                        if (hollow && y > 0 && innerRx > 0.5 && innerRz > 0.5) {
                            double dIn = (x / innerRx) * (x / innerRx) + (z / innerRz) * (z / innerRz);
                            if (dIn < 1.0) continue;
                        }
                        put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generatePyramid(
            VoxelBuffer buf, BlockPos3i o, int baseRadius, int height,
            boolean hollow, int thickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        for (int y = 0; y < height; y++) {
            double t = 1.0 - ((double) y / Math.max(1.0, height));
            int layerR = Math.max(0, (int) Math.round(baseRadius * t));
            for (int z = -layerR; z <= layerR; z++) {
                for (int x = -layerR; x <= layerR; x++) {
                    if (hollow && y > 0) {
                        if (Math.abs(x) <= layerR - thickness && Math.abs(z) <= layerR - thickness) {
                            continue;
                        }
                    }
                    put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                }
            }
        }
    }

    private static void generateDome(
            VoxelBuffer buf, BlockPos3i o, int rx, int ry, int rz,
            int thickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        generateEllipsoid(buf, o, rx, ry, rz, true, Math.max(1, thickness), true, mat, bounds, seed);
    }

    private static void generateArch(
            VoxelBuffer buf, BlockPos3i o, int spanRadius, int totalHeight,
            int thickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        int pillarHeight = Math.max(1, totalHeight - spanRadius);
        int depth = Math.max(1, thickness);
        // Left & right pillars
        for (int y = 0; y < pillarHeight; y++) {
            for (int z = 0; z < depth; z++) {
                for (int t = 0; t < thickness; t++) {
                    put(buf, o.x() - spanRadius + t, o.y() + y, o.z() + z, mat, bounds, seed);
                    put(buf, o.x() + spanRadius - t, o.y() + y, o.z() + z, mat, bounds, seed);
                }
            }
        }
        // Semicircular upper arch crown
        double outerR = spanRadius + 0.5;
        double innerR = Math.max(0.5, outerR - thickness);
        for (int y = 0; y <= spanRadius; y++) {
            for (int x = -spanRadius; x <= spanRadius; x++) {
                double rSq = x * x + y * y;
                if (rSq <= outerR * outerR && rSq >= innerR * innerR) {
                    for (int z = 0; z < depth; z++) {
                        put(buf, o.x() + x, o.y() + pillarHeight + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generateRing(
            VoxelBuffer buf, BlockPos3i o, int majorRadius, int tubeRadius,
            MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        int extent = majorRadius + tubeRadius + 1;
        for (int y = -tubeRadius; y <= tubeRadius; y++) {
            for (int z = -extent; z <= extent; z++) {
                for (int x = -extent; x <= extent; x++) {
                    double radial = Math.sqrt(x * x + z * z) - majorRadius;
                    if (radial * radial + y * y <= (tubeRadius + 0.35) * (tubeRadius + 0.35)) {
                        put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generateTunnel(
            VoxelBuffer buf, BlockPos3i o, int widthRadius, int heightRadius, int length,
            int wallThickness, MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        double outerRx = widthRadius + wallThickness + 0.5;
        double outerRy = heightRadius + wallThickness + 0.5;
        double innerRx = widthRadius + 0.5;
        double innerRy = heightRadius + 0.5;

        for (int z = 0; z < length; z++) {
            for (int y = 0; y <= heightRadius + wallThickness; y++) {
                for (int x = -(widthRadius + wallThickness); x <= widthRadius + wallThickness; x++) {
                    double dOut = (x / outerRx) * (x / outerRx) + (y / outerRy) * (y / outerRy);
                    double dIn = (x / innerRx) * (x / innerRx) + (y / innerRy) * (y / innerRy);
                    if (dOut <= 1.0 && (dIn >= 1.0 || y == 0)) {
                        put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
    }

    private static void generateStairs(
            VoxelBuffer buf, BlockPos3i o, int width, int steps, ShapeParameters params,
            MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        int halfW = Math.max(0, width / 2);
        for (int step = 0; step < steps; step++) {
            for (int w = -halfW; w <= halfW; w++) {
                BlockPos3i stepPos = switch (params.facing()) {
                    case NORTH, UP, DOWN -> new BlockPos3i(o.x() + w, o.y() + step, o.z() - step);
                    case SOUTH -> new BlockPos3i(o.x() + w, o.y() + step, o.z() + step);
                    case EAST -> new BlockPos3i(o.x() + step, o.y() + step, o.z() + w);
                    case WEST -> new BlockPos3i(o.x() - step, o.y() + step, o.z() + w);
                };
                buf.setBlock(stepPos, mat.sample(stepPos, bounds, seed));
            }
        }
    }

    private static void generateWall(
            VoxelBuffer buf, BlockPos3i o, int lengthRadius, int height, int thickness,
            MaterialPattern mat, BoundingBox3i bounds, long seed
    ) {
        int halfT = thickness / 2;
        for (int x = -lengthRadius; x <= lengthRadius; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -halfT; z <= halfT; z++) {
                    put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                }
            }
            // Crenellations / battlements along top edge
            if (Math.floorMod(x, 2) == 0) {
                put(buf, o.x() + x, o.y() + height, o.z() - halfT, mat, bounds, seed);
                put(buf, o.x() + x, o.y() + height, o.z() + halfT, mat, bounds, seed);
            }
        }
    }

    private static void generateProceduralMountain(
            VoxelBuffer buf, BlockPos3i o, int rx, int rz, int peakHeight,
            long seed, MaterialPattern mat, BoundingBox3i bounds
    ) {
        SimplexNoise3D noise = new SimplexNoise3D(seed);
        for (int z = -rz; z <= rz; z++) {
            for (int x = -rx; x <= rx; x++) {
                double nx = (double) x / Math.max(1.0, rx);
                double nz = (double) z / Math.max(1.0, rz);
                double radialDistSq = nx * nx + nz * nz;
                if (radialDistSq > 1.0) {
                    continue;
                }
                double falloff = Math.pow(1.0 - radialDistSq, 1.6);
                double ridge = noise.ridgedFbm((o.x() + x) * 0.045, 0.0, (o.z() + z) * 0.045, 4, 2.1, 0.5);
                double detail = (noise.fbm((o.x() + x) * 0.12, 10.0, (o.z() + z) * 0.12, 2, 2.0, 0.5) + 1.0) * 0.5;
                int columnHeight = (int) Math.round(peakHeight * falloff * (0.72 * ridge + 0.28 * detail));

                for (int y = 0; y <= columnHeight; y++) {
                    BlockPos3i pos = new BlockPos3i(o.x() + x, o.y() + y, o.z() + z);
                    BlockStateRef state = mat.sample(pos, bounds, seed);
                    buf.setBlock(pos, state);
                }
            }
        }
    }

    private static void generateOrganicStructure(
            VoxelBuffer buf, BlockPos3i o, int radius, int height,
            long seed, MaterialPattern mat, BoundingBox3i bounds
    ) {
        SimplexNoise3D noise = new SimplexNoise3D(seed ^ 0xCAFEBABEL);
        // Organic trunk + twisting branches + cellular canopy
        int branches = 4;
        for (int y = 0; y < height; y++) {
            double t = (double) y / Math.max(1.0, height);
            double swayX = noise.sample(y * 0.15, 0.0, 0.0) * (radius * 0.25);
            double swayZ = noise.sample(0.0, 0.0, y * 0.15) * (radius * 0.25);
            double trunkR = Math.max(1.0, radius * 0.35 * (1.0 - t * 0.65));

            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    double dx = x - swayX;
                    double dz = z - swayZ;
                    if (dx * dx + dz * dz <= trunkR * trunkR) {
                        put(buf, o.x() + x, o.y() + y, o.z() + z, mat, bounds, seed);
                    }
                }
            }
        }
        // Organic canopy / root Tendrils
        int canopyCenterY = o.y() + height - Math.max(1, radius / 3);
        for (int b = 0; b < branches; b++) {
            double angle = (Math.PI * 2.0 * b) / branches;
            int bx = o.x() + (int) Math.round(Math.cos(angle) * (radius * 0.55));
            int bz = o.z() + (int) Math.round(Math.sin(angle) * (radius * 0.55));
            int lobeR = Math.max(2, radius / 2);
            for (int y = -lobeR; y <= lobeR; y++) {
                for (int z = -lobeR; z <= lobeR; z++) {
                    for (int x = -lobeR; x <= lobeR; x++) {
                        double dist = Math.sqrt(x * x + y * y * 1.4 + z * z);
                        double n = noise.sample((bx + x) * 0.25, (canopyCenterY + y) * 0.25, (bz + z) * 0.25) * 0.8;
                        if (dist + n <= lobeR) {
                            put(buf, bx + x, canopyCenterY + y, bz + z, mat, bounds, seed);
                        }
                    }
                }
            }
        }
    }
}
