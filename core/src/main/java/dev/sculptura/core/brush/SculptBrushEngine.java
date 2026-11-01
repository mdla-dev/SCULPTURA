package dev.sculptura.core.brush;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Ray3d;
import dev.sculptura.core.math.SimplexNoise3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Terrain Sculpting Engine implementing all 12 brushes in Section 5.E:
 * RAISE, LOWER, SMOOTH, FLATTEN, ERODE, TERRACE, BLEND, BLUR, PAINT, REPLACE, NOISE, VEGETATION.
 */
public final class SculptBrushEngine {
    private SculptBrushEngine() {}

    public static Optional<BlockPos3i> raycastBrushTarget(Ray3d lookRay, BrushSettings settings, VoxelWorldView world) {
        return lookRay.raycastVoxels(settings.raycastRange(), pos -> !world.isAir(pos))
                .map(Ray3d.VoxelRaycastHit::blockPos);
    }

    public static VoxelBuffer applyBrush(BlockPos3i center, BrushSettings settings, VoxelWorldView world) {
        VoxelBuffer out = new VoxelBuffer();
        int r = settings.radius();
        BoundingBox3i bounds = new BoundingBox3i(
                center.x() - r, Math.max(world.getMinBuildHeight(), center.y() - r - 6), center.z() - r,
                center.x() + r, Math.min(world.getMaxBuildHeight() - 1, center.y() + r + 8), center.z() + r
        );

        switch (settings.type()) {
            case RAISE -> applyColumnHeightDelta(center, settings, world, out, bounds, true);
            case LOWER -> applyColumnHeightDelta(center, settings, world, out, bounds, false);
            case SMOOTH -> applySmoothColumns(center, settings, world, out, bounds);
            case FLATTEN -> applyFlattenColumns(center, settings, world, out, bounds);
            case ERODE -> applyErodeColumns(center, settings, world, out, bounds);
            case TERRACE -> applyTerraceColumns(center, settings, world, out, bounds);
            case BLEND -> applyBlend(center, settings, world, out, bounds);
            case BLUR -> applyBlur3D(center, settings, world, out);
            case PAINT -> applyPaint(center, settings, world, out, bounds);
            case REPLACE -> applyReplace(center, settings, world, out, bounds);
            case NOISE -> applyNoiseColumns(center, settings, world, out, bounds);
            case VEGETATION -> applyVegetation(center, settings, world, out, bounds);
        }
        return out;
    }

    private static int sampleColumnTopY(VoxelWorldView world, int x, int z, int refY, int radius) {
        return world.findTopSolidY(x, z, refY + radius + 6, refY - radius - 8);
    }

    private static void applyColumnHeightDelta(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds, boolean raise
    ) {
        int r = s.radius();
        int maxDelta = Math.max(1, (int) Math.round(r * s.strength() * 0.65));

        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff());
                int deltaY = (int) Math.round(maxDelta * weight);
                if (deltaY <= 0) continue;

                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int topY = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (topY < world.getMinBuildHeight()) continue;

                BlockStateRef topBlock = world.getBlockState(wx, topY, wz);
                if (raise) {
                    for (int y = topY + 1; y <= Math.min(world.getMaxBuildHeight() - 1, topY + deltaY); y++) {
                        BlockPos3i p = new BlockPos3i(wx, y, wz);
                        BlockStateRef mat = y == topY + deltaY && !topBlock.isAir()
                                ? s.material().sample(p, bounds, s.randomSeed())
                                : s.material().sample(p, bounds, s.randomSeed());
                        out.setBlock(p, mat);
                    }
                } else {
                    for (int y = topY; y > Math.max(world.getMinBuildHeight(), topY - deltaY); y--) {
                        out.setBlock(wx, y, wz, BlockStateRef.AIR);
                    }
                }
            }
        }
    }

    private static void applySmoothColumns(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        int kernel = Math.max(1, Math.min(3, (int) Math.ceil(s.strength() * 3.0)));
        int[][] heights = new int[2 * (r + kernel) + 1][2 * (r + kernel) + 1];
        int offset = r + kernel;

        for (int dz = -offset; dz <= offset; dz++) {
            for (int dx = -offset; dx <= offset; dx++) {
                heights[dx + offset][dz + offset] = sampleColumnTopY(world, c.x() + dx, c.z() + dz, c.y(), r);
            }
        }

        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff()) * s.strength();

                double weightedSum = 0.0;
                double totalW = 0.0;
                for (int kz = -kernel; kz <= kernel; kz++) {
                    for (int kx = -kernel; kx <= kernel; kx++) {
                        int h = heights[dx + kx + offset][dz + kz + offset];
                        if (h >= world.getMinBuildHeight()) {
                            double kw = 1.0 / (1.0 + kx * kx + kz * kz);
                            weightedSum += h * kw;
                            totalW += kw;
                        }
                    }
                }
                if (totalW <= 0.0) continue;
                int currentY = heights[dx + offset][dz + offset];
                double avgH = weightedSum / totalW;
                int targetY = (int) Math.round(currentY + (avgH - currentY) * weight);
                updateColumnToTargetY(world, out, bounds, s, c.x() + dx, c.z() + dz, currentY, targetY);
            }
        }
    }

    private static void applyFlattenColumns(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        int planeY = s.targetHeight();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff()) * s.strength();
                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int currentY = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (currentY < world.getMinBuildHeight()) continue;
                int newY = (int) Math.round(currentY + (planeY - currentY) * weight);
                updateColumnToTargetY(world, out, bounds, s, wx, wz, currentY, newY);
            }
        }
    }

    private static void applyErodeColumns(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff()) * s.strength();
                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int centerH = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (centerH < world.getMinBuildHeight()) continue;

                int lowerNeighbors = 0;
                int higherNeighbors = 0;
                int minNeighbor = centerH;
                int maxNeighbor = centerH;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
                for (int[] d : dirs) {
                    int nh = sampleColumnTopY(world, wx + d[0], wz + d[1], c.y(), r);
                    if (nh < centerH) {
                        lowerNeighbors++;
                        minNeighbor = Math.min(minNeighbor, nh);
                    } else if (nh > centerH) {
                        higherNeighbors++;
                        maxNeighbor = Math.max(maxNeighbor, nh);
                    }
                }
                int targetY = centerH;
                if (lowerNeighbors >= 5) {
                    targetY = centerH - Math.max(1, (int) Math.round((centerH - minNeighbor) * 0.5 * weight));
                } else if (higherNeighbors >= 6) {
                    targetY = centerH + Math.max(1, (int) Math.round((maxNeighbor - centerH) * 0.4 * weight));
                }
                updateColumnToTargetY(world, out, bounds, s, wx, wz, centerH, targetY);
            }
        }
    }

    private static void applyTerraceColumns(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        int stepHeight = Math.max(2, (int) Math.round(2 + (1.0 - s.frequency()) * 4));
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff()) * s.strength();
                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int currentY = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (currentY < world.getMinBuildHeight()) continue;

                int quantizedY = Math.floorDiv(currentY, stepHeight) * stepHeight;
                int newY = (int) Math.round(currentY + (quantizedY - currentY) * weight);
                updateColumnToTargetY(world, out, bounds, s, wx, wz, currentY, newY);
            }
        }
    }

    private static void applyBlend(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!s.shape().containsNormalizedOffset(dx, dy, dz, r)) continue;
                    BlockPos3i pos = c.add(dx, dy, dz);
                    if (world.isAir(pos)) continue;

                    Map<BlockStateRef, Integer> counts = new HashMap<>();
                    List<BlockStateRef> neighborStates = new ArrayList<>();
                    for (int nz = -1; nz <= 1; nz++) {
                        for (int ny = -1; ny <= 1; ny++) {
                            for (int nx = -1; nx <= 1; nx++) {
                                BlockStateRef ns = world.getBlockState(pos.add(nx, ny, nz));
                                if (!ns.isAir()) {
                                    counts.merge(ns, 1, Integer::sum);
                                    neighborStates.add(ns);
                                }
                            }
                        }
                    }
                    if (!neighborStates.isEmpty()) {
                        double rnd = SimplexNoise3D.spatialRandom01(s.randomSeed(), pos.x(), pos.y(), pos.z());
                        if (rnd <= s.strength()) {
                            int pickIdx = (int) (SimplexNoise3D.spatialRandom01(s.randomSeed() ^ 0x99L, pos.x(), pos.y(), pos.z()) * neighborStates.size());
                            pickIdx = Math.min(neighborStates.size() - 1, pickIdx);
                            out.setBlock(pos, neighborStates.get(pickIdx));
                        }
                    }
                }
            }
        }
    }

    private static void applyBlur3D(BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out) {
        int r = s.radius();
        BoundingBox3i bounds = new BoundingBox3i(c.x() - r, c.y() - r, c.z() - r, c.x() + r, c.y() + r, c.z() + r);
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!s.shape().containsNormalizedOffset(dx, dy, dz, r)) continue;
                    BlockPos3i pos = c.add(dx, dy, dz);
                    int solidNeighbors = 0;
                    for (int nz = -1; nz <= 1; nz++) {
                        for (int ny = -1; ny <= 1; ny++) {
                            for (int nx = -1; nx <= 1; nx++) {
                                if (!world.isAir(pos.add(nx, ny, nz))) {
                                    solidNeighbors++;
                                }
                            }
                        }
                    }
                    boolean currentlySolid = !world.isAir(pos);
                    if (currentlySolid && solidNeighbors <= 8) {
                        out.setBlock(pos, BlockStateRef.AIR);
                    } else if (!currentlySolid && solidNeighbors >= 18) {
                        out.setBlock(pos, s.material().sample(pos, bounds, s.randomSeed()));
                    }
                }
            }
        }
    }

    private static void applyPaint(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!s.shape().containsNormalizedOffset(dx, dy, dz, r)) continue;
                    BlockPos3i pos = c.add(dx, dy, dz);
                    if (!world.isAir(pos)) {
                        out.setBlock(pos, s.material().sample(pos, bounds, s.randomSeed()));
                    }
                }
            }
        }
    }

    private static void applyReplace(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        BlockStateRef target = s.replaceTarget();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!s.shape().containsNormalizedOffset(dx, dy, dz, r)) continue;
                    BlockPos3i pos = c.add(dx, dy, dz);
                    BlockStateRef existing = world.getBlockState(pos);
                    if (existing.isAir()) continue;
                    if (target == null || existing.equals(target) || existing.blockId().equals(target.blockId())) {
                        out.setBlock(pos, s.material().sample(pos, bounds, s.randomSeed()));
                    }
                }
            }
        }
    }

    private static void applyNoiseColumns(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        SimplexNoise3D noise = new SimplexNoise3D(s.randomSeed());
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff());
                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int currentY = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (currentY < world.getMinBuildHeight()) continue;

                double n = noise.fbm(wx * s.frequency(), 0.0, wz * s.frequency(), 3, 2.0, 0.5);
                int delta = (int) Math.round(n * s.noiseAmplitude() * s.strength() * weight * 4.0);
                updateColumnToTargetY(world, out, bounds, s, wx, wz, currentY, currentY + delta);
            }
        }
    }

    private static void applyVegetation(
            BlockPos3i c, BrushSettings s, VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds
    ) {
        int r = s.radius();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double normDist = s.shape().normalizedDistance(dx, 0, dz, r);
                if (normDist > 1.0) continue;
                double weight = s.falloffCurve().evaluate(normDist, s.falloff()) * s.strength();
                int wx = c.x() + dx;
                int wz = c.z() + dz;
                int surfaceY = sampleColumnTopY(world, wx, wz, c.y(), r);
                if (surfaceY < world.getMinBuildHeight() || surfaceY + 1 >= world.getMaxBuildHeight()) continue;

                double densityRoll = SimplexNoise3D.spatialRandom01(s.randomSeed(), wx, surfaceY, wz);
                if (densityRoll <= weight * 0.65) {
                    BlockPos3i above = new BlockPos3i(wx, surfaceY + 1, wz);
                    if (world.isAir(above)) {
                        BlockStateRef flora = s.material().sample(above, bounds, s.randomSeed());
                        if (flora.equals(BlockStateRef.STONE)) {
                            flora = BlockStateRef.parse("minecraft:short_grass");
                        }
                        out.setBlock(above, flora);
                    }
                }
            }
        }
    }

    private static void updateColumnToTargetY(
            VoxelWorldView world, VoxelBuffer out, BoundingBox3i bounds, BrushSettings s,
            int wx, int wz, int currentY, int targetY
    ) {
        if (targetY > currentY) {
            for (int y = currentY + 1; y <= Math.min(world.getMaxBuildHeight() - 1, targetY); y++) {
                BlockPos3i p = new BlockPos3i(wx, y, wz);
                out.setBlock(p, s.material().sample(p, bounds, s.randomSeed()));
            }
        } else if (targetY < currentY) {
            for (int y = currentY; y > Math.max(world.getMinBuildHeight(), targetY); y--) {
                out.setBlock(wx, y, wz, BlockStateRef.AIR);
            }
        }
    }
}
