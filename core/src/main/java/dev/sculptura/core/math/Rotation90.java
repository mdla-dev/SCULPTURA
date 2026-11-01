package dev.sculptura.core.math;

public enum Rotation90 {
    NONE(0),
    CLOCKWISE_90(90),
    CLOCKWISE_180(180),
    COUNTERCLOCKWISE_90(270);

    private final int degrees;

    Rotation90(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    public Rotation90 inverse() {
        return switch (this) {
            case NONE -> NONE;
            case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
        };
    }

    public BlockPos3i transform(BlockPos3i pos, BlockPos3i pivot, Axis3D axis) {
        int dx = pos.x() - pivot.x();
        int dy = pos.y() - pivot.y();
        int dz = pos.z() - pivot.z();

        int rx = dx;
        int ry = dy;
        int rz = dz;

        switch (axis) {
            case Y -> {
                switch (this) {
                    case NONE -> {}
                    case CLOCKWISE_90 -> {
                        rx = -dz;
                        rz = dx;
                    }
                    case CLOCKWISE_180 -> {
                        rx = -dx;
                        rz = -dz;
                    }
                    case COUNTERCLOCKWISE_90 -> {
                        rx = dz;
                        rz = -dx;
                    }
                }
            }
            case X -> {
                switch (this) {
                    case NONE -> {}
                    case CLOCKWISE_90 -> {
                        ry = -dz;
                        rz = dy;
                    }
                    case CLOCKWISE_180 -> {
                        ry = -dy;
                        rz = -dz;
                    }
                    case COUNTERCLOCKWISE_90 -> {
                        ry = dz;
                        rz = -dy;
                    }
                }
            }
            case Z -> {
                switch (this) {
                    case NONE -> {}
                    case CLOCKWISE_90 -> {
                        rx = -dy;
                        ry = dx;
                    }
                    case CLOCKWISE_180 -> {
                        rx = -dx;
                        ry = -dy;
                    }
                    case COUNTERCLOCKWISE_90 -> {
                        rx = dy;
                        ry = -dx;
                    }
                }
            }
        }

        return new BlockPos3i(pivot.x() + rx, pivot.y() + ry, pivot.z() + rz);
    }

    public String rotateHorizontalFacing(String facing) {
        if (facing == null) {
            return null;
        }
        String normalized = facing.toLowerCase();
        String[] ring = {"north", "east", "south", "west"};
        int idx = -1;
        for (int i = 0; i < ring.length; i++) {
            if (ring[i].equals(normalized)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return facing;
        }
        int step = switch (this) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return ring[(idx + step) & 3];
    }
}
