package dev.sculptura.core.shape;

import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;

public record ShapeParameters(
        ShapeType type,
        BlockPos3i origin,
        int radiusX,
        int radiusY,
        int radiusZ,
        int height,
        int wallThickness,
        boolean hollow,
        Axis3D axis,
        Direction3D facing,
        long seed
) {
    public ShapeParameters {
        radiusX = Math.max(1, radiusX);
        radiusY = Math.max(1, radiusY);
        radiusZ = Math.max(1, radiusZ);
        height = Math.max(1, height);
        wallThickness = Math.max(1, wallThickness);
        axis = axis == null ? Axis3D.Y : axis;
        facing = facing == null ? Direction3D.NORTH : facing;
    }

    public static ShapeParameters sphere(BlockPos3i center, int radius, boolean hollow) {
        return new ShapeParameters(ShapeType.SPHERE, center, radius, radius, radius, radius * 2 + 1, 1, hollow, Axis3D.Y, Direction3D.NORTH, 42L);
    }

    public static ShapeParameters cylinder(BlockPos3i baseCenter, int radius, int height, boolean hollow, Axis3D axis) {
        return new ShapeParameters(
                hollow ? ShapeType.HOLLOW_CYLINDER : ShapeType.CYLINDER,
                baseCenter, radius, height, radius, height, 1, hollow, axis, Direction3D.NORTH, 42L
        );
    }
}
