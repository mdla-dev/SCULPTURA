package dev.sculptura.core.brush;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.math.Ray3d;

public record BrushSettings(
        BrushType type,
        BrushShape shape,
        int radius,
        double strength,
        double falloff,
        FalloffCurve falloffCurve,
        double spacing,
        double frequency,
        double noiseAmplitude,
        int targetHeight,
        MaterialPattern material,
        BlockStateRef replaceTarget,
        long randomSeed,
        double raycastRange
) {
    public static final double DEFAULT_RAYCAST_RANGE = Ray3d.DEFAULT_SCULPT_RANGE; // 120.0 blocks

    public BrushSettings {
        type = type == null ? BrushType.RAISE : type;
        shape = shape == null ? BrushShape.SPHERE : shape;
        radius = Math.max(1, Math.min(64, radius));
        strength = Math.max(0.05, Math.min(1.0, strength));
        falloff = Math.max(0.0, Math.min(1.0, falloff));
        falloffCurve = falloffCurve == null ? FalloffCurve.SMOOTHSTEP : falloffCurve;
        spacing = Math.max(0.25, spacing);
        frequency = Math.max(0.005, frequency);
        noiseAmplitude = Math.max(0.0, noiseAmplitude);
        material = material == null ? MaterialPattern.single(BlockStateRef.STONE) : material;
        raycastRange = raycastRange <= 0.0 ? DEFAULT_RAYCAST_RANGE : raycastRange;
    }

    public static BrushSettings defaults(BrushType type, int radius, double strength, MaterialPattern material) {
        return new BrushSettings(
                type,
                BrushShape.SPHERE,
                radius,
                strength,
                0.65,
                FalloffCurve.SMOOTHSTEP,
                1.0,
                0.08,
                1.0,
                64,
                material,
                null,
                1337L,
                DEFAULT_RAYCAST_RANGE
        );
    }
}
