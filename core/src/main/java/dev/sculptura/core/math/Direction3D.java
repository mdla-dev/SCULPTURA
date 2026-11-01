package dev.sculptura.core.math;

public enum Direction3D {
    DOWN(Axis3D.Y, -1, 0, -1, 0),
    UP(Axis3D.Y, 1, 0, 1, 0),
    NORTH(Axis3D.Z, -1, 0, 0, -1),
    SOUTH(Axis3D.Z, 1, 0, 0, 1),
    WEST(Axis3D.X, -1, -1, 0, 0),
    EAST(Axis3D.X, 1, 1, 0, 0);

    private final Axis3D axis;
    private final int axisDirection;
    private final int normalX;
    private final int normalY;
    private final int normalZ;

    Direction3D(Axis3D axis, int axisDirection, int normalX, int normalY, int normalZ) {
        this.axis = axis;
        this.axisDirection = axisDirection;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    public Axis3D axis() {
        return axis;
    }

    public int axisDirection() {
        return axisDirection;
    }

    public int normalX() {
        return normalX;
    }

    public int normalY() {
        return normalY;
    }

    public int normalZ() {
        return normalZ;
    }

    public Vec3d normalVector() {
        return new Vec3d(normalX, normalY, normalZ);
    }

    public BlockPos3i normalOffset() {
        return new BlockPos3i(normalX, normalY, normalZ);
    }

    public Direction3D opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public static Direction3D fromAxisAndSign(Axis3D axis, int sign) {
        boolean positive = sign >= 0;
        return switch (axis) {
            case X -> positive ? EAST : WEST;
            case Y -> positive ? UP : DOWN;
            case Z -> positive ? SOUTH : NORTH;
        };
    }
}
