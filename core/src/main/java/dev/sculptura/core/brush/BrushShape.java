package dev.sculptura.core.brush;

public enum BrushShape {
    SPHERE,
    DISC,
    CIRCLE,
    CYLINDER,
    CUBE;

    public boolean containsNormalizedOffset(double dx, double dy, double dz, double radius) {
        double r = Math.max(0.5, radius);
        return switch (this) {
            case SPHERE -> (dx * dx + dy * dy + dz * dz) <= r * r;
            case DISC -> Math.abs(dy) <= 1.5 && (dx * dx + dz * dz) <= r * r;
            case CIRCLE -> Math.abs(dy) <= 0.5 && (dx * dx + dz * dz) <= r * r;
            case CYLINDER -> Math.abs(dy) <= r && (dx * dx + dz * dz) <= r * r;
            case CUBE -> Math.abs(dx) <= r && Math.abs(dy) <= r && Math.abs(dz) <= r;
        };
    }

    public double normalizedDistance(double dx, double dy, double dz, double radius) {
        double r = Math.max(0.5, radius);
        return switch (this) {
            case SPHERE -> Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
            case DISC, CIRCLE, CYLINDER -> Math.sqrt(dx * dx + dz * dz) / r;
            case CUBE -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / r;
        };
    }
}
