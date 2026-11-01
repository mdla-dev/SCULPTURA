package dev.sculptura.core.math;

/**
 * Deterministic 3D gradient noise & Fractal Brownian Motion (FBM) generator for terrain sculpting,
 * procedural mountains, organic structures, and noise gradients.
 */
public final class SimplexNoise3D {
    private final int[] perm = new int[512];

    private static final int[][] GRAD3 = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    public SimplexNoise3D(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        long state = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 255; i > 0; i--) {
            state = splitMix64(state);
            int j = (int) (Math.abs(state) % (i + 1));
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    public static long splitMix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static double spatialRandom01(long seed, int x, int y, int z) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h = splitMix64(h);
        h ^= (long) y * 0xBF58476D1CE4E5B9L;
        h = splitMix64(h);
        h ^= (long) z * 0x94D049BB133111EBL;
        h = splitMix64(h);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int[] g = GRAD3[hash % 12];
        return g[0] * x + g[1] * y + g[2] * z;
    }

    /**
     * Evaluates smooth 3D gradient noise in range [-1.0, 1.0].
     */
    public double sample(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);

        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        int A = perm[X] + Y;
        int AA = perm[A] + Z;
        int AB = perm[A + 1] + Z;
        int B = perm[X + 1] + Y;
        int BA = perm[B] + Z;
        int BB = perm[B + 1] + Z;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(perm[AA], x, y, z), grad(perm[BA], x - 1, y, z)),
                        lerp(u, grad(perm[AB], x, y - 1, z), grad(perm[BB], x - 1, y - 1, z))),
                lerp(v,
                        lerp(u, grad(perm[AA + 1], x, y, z - 1), grad(perm[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(perm[AB + 1], x, y - 1, z - 1), grad(perm[BB + 1], x - 1, y - 1, z - 1)))
        );
    }

    /**
     * Multi-octave Fractal Brownian Motion in range [-1.0, 1.0].
     */
    public double fbm(double x, double y, double z, int octaves, double lacunarity, double gain) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;
        int safeOctaves = Math.max(1, Math.min(octaves, 8));

        for (int i = 0; i < safeOctaves; i++) {
            total += sample(x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return maxAmplitude > 0.0 ? total / maxAmplitude : 0.0;
    }

    /**
     * Ridge multifractal noise in range [0.0, 1.0] for sharp mountain peaks and ridges.
     */
    public double ridgedFbm(double x, double y, double z, int octaves, double lacunarity, double gain) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;
        int safeOctaves = Math.max(1, Math.min(octaves, 8));

        for (int i = 0; i < safeOctaves; i++) {
            double n = 1.0 - Math.abs(sample(x * frequency, y * frequency, z * frequency));
            n = n * n;
            total += n * amplitude;
            maxAmplitude += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return maxAmplitude > 0.0 ? Math.max(0.0, Math.min(1.0, total / maxAmplitude)) : 0.0;
    }
}
