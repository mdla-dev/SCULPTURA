package dev.sculptura.core.material;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.SimplexNoise3D;
import dev.sculptura.core.math.Vec3d;

import java.util.List;

/**
 * Multi-stop block gradient supporting VERTICAL, HORIZONTAL, DIRECTIONAL, RADIAL, and NOISE gradients
 * with deterministic dithering between adjacent stops.
 */
public final class GradientMaterialPattern implements MaterialPattern {
    private final MaterialGradientMode mode;
    private final List<BlockStateRef> stops;
    private final Vec3d directionVector;
    private final double noiseFrequency;
    private final double ditherStrength;

    public GradientMaterialPattern(
            MaterialGradientMode mode,
            List<BlockStateRef> stops,
            Vec3d directionVector,
            double noiseFrequency,
            double ditherStrength
    ) {
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException("GradientMaterialPattern requires at least one stop");
        }
        this.mode = mode;
        this.stops = List.copyOf(stops);
        this.directionVector = directionVector == null || directionVector.lengthSquared() < 1e-9
                ? new Vec3d(1, 1, 0).normalize()
                : directionVector.normalize();
        this.noiseFrequency = Math.max(0.001, noiseFrequency);
        this.ditherStrength = Math.max(0.0, Math.min(1.0, ditherStrength));
    }

    public static GradientMaterialPattern vertical(List<BlockStateRef> stops, double ditherStrength) {
        return new GradientMaterialPattern(MaterialGradientMode.VERTICAL, stops, new Vec3d(0, 1, 0), 0.05, ditherStrength);
    }

    public static GradientMaterialPattern horizontal(List<BlockStateRef> stops, double ditherStrength) {
        return new GradientMaterialPattern(MaterialGradientMode.HORIZONTAL, stops, new Vec3d(1, 0, 0), 0.05, ditherStrength);
    }

    public static GradientMaterialPattern directional(List<BlockStateRef> stops, Vec3d direction, double ditherStrength) {
        return new GradientMaterialPattern(MaterialGradientMode.DIRECTIONAL, stops, direction, 0.05, ditherStrength);
    }

    public static GradientMaterialPattern radial(List<BlockStateRef> stops, double ditherStrength) {
        return new GradientMaterialPattern(MaterialGradientMode.RADIAL, stops, new Vec3d(0, 1, 0), 0.05, ditherStrength);
    }

    public static GradientMaterialPattern noise(List<BlockStateRef> stops, double frequency, double ditherStrength) {
        return new GradientMaterialPattern(MaterialGradientMode.NOISE, stops, new Vec3d(0, 1, 0), frequency, ditherStrength);
    }

    public MaterialGradientMode mode() {
        return mode;
    }

    public List<BlockStateRef> stops() {
        return stops;
    }

    @Override
    public BlockStateRef sample(BlockPos3i pos, BoundingBox3i bounds, long seed) {
        if (stops.size() == 1) {
            return stops.get(0);
        }
        double t = switch (mode) {
            case VERTICAL -> {
                double span = Math.max(1.0, bounds.maxY() - bounds.minY());
                yield (pos.y() - bounds.minY()) / span;
            }
            case HORIZONTAL -> {
                double spanX = Math.max(1.0, bounds.maxX() - bounds.minX());
                yield (pos.x() - bounds.minX()) / spanX;
            }
            case DIRECTIONAL -> {
                Vec3d minCorner = new Vec3d(bounds.minX(), bounds.minY(), bounds.minZ());
                Vec3d maxCorner = new Vec3d(bounds.maxX(), bounds.maxY(), bounds.maxZ());
                double minProj = Double.POSITIVE_INFINITY;
                double maxProj = Double.NEGATIVE_INFINITY;
                for (BlockPos3i corner : bounds.eightCorners()) {
                    double proj = new Vec3d(corner.x(), corner.y(), corner.z()).dot(directionVector);
                    minProj = Math.min(minProj, proj);
                    maxProj = Math.max(maxProj, proj);
                }
                double p = new Vec3d(pos.x(), pos.y(), pos.z()).dot(directionVector);
                double denom = Math.max(1.0e-6, maxProj - minProj);
                yield (p - minProj) / denom;
            }
            case RADIAL -> {
                Vec3d center = bounds.centerVec();
                double maxRadius = Math.max(1.0, Math.sqrt(
                        Math.pow(bounds.sizeX() * 0.5, 2)
                                + Math.pow(bounds.sizeY() * 0.5, 2)
                                + Math.pow(bounds.sizeZ() * 0.5, 2)
                ));
                double dist = pos.center().distance(center);
                yield dist / maxRadius;
            }
            case NOISE -> {
                SimplexNoise3D noise = new SimplexNoise3D(seed);
                double n = noise.fbm(
                        pos.x() * noiseFrequency,
                        pos.y() * noiseFrequency,
                        pos.z() * noiseFrequency,
                        3, 2.0, 0.5
                );
                yield (n + 1.0) * 0.5;
            }
        };

        t = Math.max(0.0, Math.min(1.0, t));
        double scaled = t * (stops.size() - 1);
        if (ditherStrength > 0.0) {
            double jitter = (SimplexNoise3D.spatialRandom01(seed ^ 0x5DEECE66DL, pos.x(), pos.y(), pos.z()) - 0.5) * ditherStrength;
            scaled = Math.max(0.0, Math.min(stops.size() - 1, scaled + jitter));
        }
        int index = (int) Math.round(scaled);
        index = Math.max(0, Math.min(stops.size() - 1, index));
        return stops.get(index);
    }
}
