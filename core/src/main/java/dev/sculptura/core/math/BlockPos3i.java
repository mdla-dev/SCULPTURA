package dev.sculptura.core.math;

import java.util.Comparator;

public record BlockPos3i(int x, int y, int z) implements Comparable<BlockPos3i> {
    public static final BlockPos3i ZERO = new BlockPos3i(0, 0, 0);

    private static final Comparator<BlockPos3i> ORDER = Comparator
            .comparingInt(BlockPos3i::y)
            .thenComparingInt(BlockPos3i::z)
            .thenComparingInt(BlockPos3i::x);

    public BlockPos3i add(int dx, int dy, int dz) {
        return new BlockPos3i(x + dx, y + dy, z + dz);
    }

    public BlockPos3i add(BlockPos3i other) {
        return new BlockPos3i(x + other.x, y + other.y, z + other.z);
    }

    public BlockPos3i subtract(BlockPos3i other) {
        return new BlockPos3i(x - other.x, y - other.y, z - other.z);
    }

    public BlockPos3i multiply(int scalar) {
        return new BlockPos3i(x * scalar, y * scalar, z * scalar);
    }

    public BlockPos3i offset(Direction3D direction, int distance) {
        return new BlockPos3i(
                x + direction.normalX() * distance,
                y + direction.normalY() * distance,
                z + direction.normalZ() * distance
        );
    }

    public BlockPos3i offset(Direction3D direction) {
        return offset(direction, 1);
    }

    public int getComponent(Axis3D axis) {
        return axis.choose(x, y, z);
    }

    public BlockPos3i withComponent(Axis3D axis, int value) {
        return switch (axis) {
            case X -> new BlockPos3i(value, y, z);
            case Y -> new BlockPos3i(x, value, z);
            case Z -> new BlockPos3i(x, y, value);
        };
    }

    public int manhattanDistance(BlockPos3i other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    public double distanceSquared(BlockPos3i other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(BlockPos3i other) {
        return Math.sqrt(distanceSquared(other));
    }

    public Vec3d center() {
        return new Vec3d(x + 0.5, y + 0.5, z + 0.5);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int sectionY() {
        return y >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public BlockPos3i snapToGrid(int gridSize) {
        if (gridSize <= 1) {
            return this;
        }
        return new BlockPos3i(
                Math.floorDiv(x, gridSize) * gridSize,
                Math.floorDiv(y, gridSize) * gridSize,
                Math.floorDiv(z, gridSize) * gridSize
        );
    }

    public static long packSubChunkKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) (chunkX & 0x3FFFFF) << 42)
                | ((long) (sectionY & 0xFFFFF) << 22)
                | ((long) (chunkZ & 0x3FFFFF));
    }

    public long subChunkKey() {
        return packSubChunkKey(chunkX(), sectionY(), chunkZ());
    }

    @Override
    public int compareTo(BlockPos3i other) {
        return ORDER.compare(this, other);
    }
}
