package dev.sculptura.core.math;

public enum MirrorPlane {
    NONE,
    XY, // Mirrors Z
    XZ, // Mirrors Y
    YZ; // Mirrors X

    public BlockPos3i transform(BlockPos3i pos, BlockPos3i pivot) {
        return switch (this) {
            case NONE -> pos;
            case XY -> new BlockPos3i(pos.x(), pos.y(), 2 * pivot.z() - pos.z());
            case XZ -> new BlockPos3i(pos.x(), 2 * pivot.y() - pos.y(), pos.z());
            case YZ -> new BlockPos3i(2 * pivot.x() - pos.x(), pos.y(), pos.z());
        };
    }

    public String mirrorFacing(String facing) {
        if (facing == null) {
            return null;
        }
        String lower = facing.toLowerCase();
        return switch (this) {
            case NONE -> facing;
            case XY -> switch (lower) {
                case "north" -> "south";
                case "south" -> "north";
                default -> facing;
            };
            case XZ -> switch (lower) {
                case "up" -> "down";
                case "down" -> "up";
                default -> facing;
            };
            case YZ -> switch (lower) {
                case "east" -> "west";
                case "west" -> "east";
                default -> facing;
            };
        };
    }
}
