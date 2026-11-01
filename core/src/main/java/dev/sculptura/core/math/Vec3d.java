package dev.sculptura.core.math;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d scale(double factor) {
        return new Vec3d(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3d cross(Vec3d other) {
        return new Vec3d(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3d normalize() {
        double len = length();
        if (len < 1.0e-12) {
            return ZERO;
        }
        return new Vec3d(x / len, y / len, z / len);
    }

    public double distanceSquared(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vec3d other) {
        return Math.sqrt(distanceSquared(other));
    }

    public Vec3d lerp(Vec3d target, double t) {
        return new Vec3d(
                x + (target.x - x) * t,
                y + (target.y - y) * t,
                z + (target.z - z) * t
        );
    }

    public double getComponent(Axis3D axis) {
        return axis.choose(x, y, z);
    }

    public BlockPos3i floorToBlockPos() {
        return new BlockPos3i(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z)
        );
    }

    public Vec3d rotateAroundAxis(Axis3D axis, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return switch (axis) {
            case X -> new Vec3d(x, y * cos - z * sin, y * sin + z * cos);
            case Y -> new Vec3d(x * cos + z * sin, y, -x * sin + z * cos);
            case Z -> new Vec3d(x * cos - y * sin, x * sin + y * cos, z);
        };
    }
}
