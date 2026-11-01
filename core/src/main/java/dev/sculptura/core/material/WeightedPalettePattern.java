package dev.sculptura.core.material;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.math.SimplexNoise3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Weighted & percentage-mixed block palette using deterministic coordinate hashing (SplitMix64).
 */
public final class WeightedPalettePattern implements MaterialPattern {
    public record WeightedEntry(BlockStateRef state, double weight) {
        public WeightedEntry {
            if (weight <= 0.0 || Double.isNaN(weight)) {
                throw new IllegalArgumentException("Palette entry weight must be positive");
            }
        }
    }

    private final List<WeightedEntry> entries;
    private final double totalWeight;

    public WeightedPalettePattern(List<WeightedEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("WeightedPalettePattern requires at least one entry");
        }
        this.entries = List.copyOf(entries);
        double sum = 0.0;
        for (WeightedEntry entry : this.entries) {
            sum += entry.weight();
        }
        this.totalWeight = sum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<WeightedEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public double percentageOf(BlockStateRef state) {
        double matching = 0.0;
        for (WeightedEntry e : entries) {
            if (e.state().equals(state)) {
                matching += e.weight();
            }
        }
        return (matching / totalWeight) * 100.0;
    }

    @Override
    public BlockStateRef sample(BlockPos3i pos, BoundingBox3i regionBounds, long seed) {
        if (entries.size() == 1) {
            return entries.get(0).state();
        }
        double roll = SimplexNoise3D.spatialRandom01(seed, pos.x(), pos.y(), pos.z()) * totalWeight;
        double cumulative = 0.0;
        for (WeightedEntry entry : entries) {
            cumulative += entry.weight();
            if (roll <= cumulative) {
                return entry.state();
            }
        }
        return entries.get(entries.size() - 1).state();
    }

    public static final class Builder {
        private final List<WeightedEntry> list = new ArrayList<>();

        public Builder add(BlockStateRef state, double weight) {
            list.add(new WeightedEntry(state, weight));
            return this;
        }

        public WeightedPalettePattern build() {
            return new WeightedPalettePattern(list);
        }
    }
}
