package dev.sculptura.core.brush;

public enum FalloffCurve {
    CONSTANT,
    LINEAR,
    SMOOTHSTEP,
    GAUSSIAN,
    SHARP;

    /**
     * Evaluates brush weight in [0.0, 1.0] given normalized distance u in [0.0, 1.0] and falloff parameter in [0.0, 1.0].
     */
    public double evaluate(double normalizedDistance, double falloffFactor) {
        if (normalizedDistance > 1.0) {
            return 0.0;
        }
        if (normalizedDistance <= 0.0 || falloffFactor <= 0.0) {
            return 1.0;
        }
        double innerCore = 1.0 - Math.min(1.0, falloffFactor);
        if (normalizedDistance <= innerCore) {
            return 1.0;
        }
        double t = (normalizedDistance - innerCore) / Math.max(1.0e-6, 1.0 - innerCore);
        t = Math.max(0.0, Math.min(1.0, t));
        return switch (this) {
            case CONSTANT -> 1.0;
            case LINEAR -> 1.0 - t;
            case SMOOTHSTEP -> 1.0 - (t * t * (3.0 - 2.0 * t));
            case GAUSSIAN -> Math.exp(-4.5 * t * t);
            case SHARP -> Math.pow(1.0 - t, 3.0);
        };
    }
}
