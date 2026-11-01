package dev.sculptura.core.math;

public enum Axis3D {
    X(1, 0, 0),
    Y(0, 1, 0),
    Z(0, 0, 1);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Axis3D(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public int choose(int x, int y, int z) {
        return switch (this) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public double choose(double x, double y, double z) {
        return switch (this) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public Vec3d unitVector() {
        return new Vec3d(stepX, stepY, stepZ);
    }

    public Axis3D[] orthogonalAxes() {
        return switch (this) {
            case X -> new Axis3D[]{Y, Z};
            case Y -> new Axis3D[]{X, Z};
            case Z -> new Axis3D[]{X, Y};
        };
    }
}
