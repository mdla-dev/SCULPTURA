package dev.sculptura.core.geometry;

import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.Direction3D;
import dev.sculptura.core.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Normalized inclusive 3D integer bounding box [minX..maxX, minY..maxY, minZ..maxZ].
 */
public record BoundingBox3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements Iterable<BlockPos3i> {
    public BoundingBox3i {
        if (minX > maxX) {
            int t = minX;
            minX = maxX;
            maxX = t;
        }
        if (minY > maxY) {
            int t = minY;
            minY = maxY;
            maxY = t;
        }
        if (minZ > maxZ) {
            int t = minZ;
            minZ = maxZ;
            maxZ = t;
        }
    }

    public static BoundingBox3i fromCorners(BlockPos3i a, BlockPos3i b) {
        return new BoundingBox3i(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()),
                Math.max(a.z(), b.z())
        );
    }

    public static BoundingBox3i ofSingleBlock(BlockPos3i pos) {
        return new BoundingBox3i(pos.x(), pos.y(), pos.z(), pos.x(), pos.y(), pos.z());
    }

    public BlockPos3i min() {
        return new BlockPos3i(minX, minY, minZ);
    }

    public BlockPos3i max() {
        return new BlockPos3i(maxX, maxY, maxZ);
    }

    public int sizeX() {
        return (maxX - minX) + 1;
    }

    public int sizeY() {
        return (maxY - minY) + 1;
    }

    public int sizeZ() {
        return (maxZ - minZ) + 1;
    }

    public int size(Axis3D axis) {
        return axis.choose(sizeX(), sizeY(), sizeZ());
    }

    public long volume() {
        return (long) sizeX() * (long) sizeY() * (long) sizeZ();
    }

    public Vec3d centerVec() {
        return new Vec3d(
                (minX + maxX + 1.0) * 0.5,
                (minY + maxY + 1.0) * 0.5,
                (minZ + maxZ + 1.0) * 0.5
        );
    }

    public BlockPos3i centerBlockPos() {
        return new BlockPos3i(
                (minX + maxX) / 2,
                (minY + maxY) / 2,
                (minZ + maxZ) / 2
        );
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos3i pos) {
        return contains(pos.x(), pos.y(), pos.z());
    }

    public boolean intersects(BoundingBox3i other) {
        return this.maxX >= other.minX && this.minX <= other.maxX
                && this.maxY >= other.minY && this.minY <= other.maxY
                && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }

    public BoundingBox3i translate(int dx, int dy, int dz) {
        return new BoundingBox3i(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public BoundingBox3i translate(BlockPos3i offset) {
        return translate(offset.x(), offset.y(), offset.z());
    }

    public BoundingBox3i expandAll(int amount) {
        return new BoundingBox3i(
                minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount
        );
    }

    /**
     * Expands or shrinks the specified face along its outward normal axis (used by face dragging and mouse wheel).
     * Never collapses below 1x1x1 volume.
     */
    public BoundingBox3i resizeFace(Direction3D face, int deltaAmount) {
        int nMinX = minX;
        int nMinY = minY;
        int nMinZ = minZ;
        int nMaxX = maxX;
        int nMaxY = maxY;
        int nMaxZ = maxZ;

        switch (face) {
            case EAST -> nMaxX = Math.max(nMinX, nMaxX + deltaAmount);
            case WEST -> nMinX = Math.min(nMaxX, nMinX - deltaAmount);
            case UP -> nMaxY = Math.max(nMinY, nMaxY + deltaAmount);
            case DOWN -> nMinY = Math.min(nMaxY, nMinY - deltaAmount);
            case SOUTH -> nMaxZ = Math.max(nMinZ, nMaxZ + deltaAmount);
            case NORTH -> nMinZ = Math.min(nMaxZ, nMinZ - deltaAmount);
        }
        return new BoundingBox3i(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
    }

    public Vec3d faceCenter(Direction3D face) {
        Vec3d c = centerVec();
        return switch (face) {
            case WEST -> new Vec3d(minX, c.y(), c.z());
            case EAST -> new Vec3d(maxX + 1.0, c.y(), c.z());
            case DOWN -> new Vec3d(c.x(), minY, c.z());
            case UP -> new Vec3d(c.x(), maxY + 1.0, c.z());
            case NORTH -> new Vec3d(c.x(), c.y(), minZ);
            case SOUTH -> new Vec3d(c.x(), c.y(), maxZ + 1.0);
        };
    }

    public List<BlockPos3i> eightCorners() {
        List<BlockPos3i> corners = new ArrayList<>(8);
        corners.add(new BlockPos3i(minX, minY, minZ));
        corners.add(new BlockPos3i(maxX, minY, minZ));
        corners.add(new BlockPos3i(minX, maxY, minZ));
        corners.add(new BlockPos3i(maxX, maxY, minZ));
        corners.add(new BlockPos3i(minX, minY, maxZ));
        corners.add(new BlockPos3i(maxX, minY, maxZ));
        corners.add(new BlockPos3i(minX, maxY, maxZ));
        corners.add(new BlockPos3i(maxX, maxY, maxZ));
        return corners;
    }

    public BoundingBox3i encompass(BlockPos3i pos) {
        return new BoundingBox3i(
                Math.min(minX, pos.x()),
                Math.min(minY, pos.y()),
                Math.min(minZ, pos.z()),
                Math.max(maxX, pos.x()),
                Math.max(maxY, pos.y()),
                Math.max(maxZ, pos.z())
        );
    }

    public BoundingBox3i union(BoundingBox3i other) {
        return new BoundingBox3i(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ)
        );
    }

    @Override
    public Iterator<BlockPos3i> iterator() {
        return new Iterator<>() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public BlockPos3i next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                BlockPos3i result = new BlockPos3i(x, y, z);
                if (x < maxX) {
                    x++;
                } else {
                    x = minX;
                    if (z < maxZ) {
                        z++;
                    } else {
                        z = minZ;
                        if (y < maxY) {
                            y++;
                        } else {
                            hasNext = false;
                        }
                    }
                }
                return result;
            }
        };
    }
}
