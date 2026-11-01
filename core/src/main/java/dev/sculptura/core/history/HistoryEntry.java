package dev.sculptura.core.history;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.math.BlockPos3i;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable record of a committed world edit storing both the original pre-edit block states
 * and the committed post-edit block states for conflict-safe Undo and Redo.
 */
public final class HistoryEntry {
    private final String operationId;
    private final String description;
    private final Map<BlockPos3i, BlockStateRef> beforeStates;
    private final Map<BlockPos3i, BlockStateRef> afterStates;
    private final long timestampMillis;

    public HistoryEntry(
            String operationId,
            String description,
            Map<BlockPos3i, BlockStateRef> beforeStates,
            Map<BlockPos3i, BlockStateRef> afterStates
    ) {
        this(operationId, description, beforeStates, afterStates, System.currentTimeMillis());
    }

    public HistoryEntry(
            String operationId,
            String description,
            Map<BlockPos3i, BlockStateRef> beforeStates,
            Map<BlockPos3i, BlockStateRef> afterStates,
            long timestampMillis
    ) {
        this.operationId = operationId == null ? "operation" : operationId;
        this.description = description == null ? "Operation" : description;
        this.beforeStates = Collections.unmodifiableMap(new LinkedHashMap<>(beforeStates));
        this.afterStates = Collections.unmodifiableMap(new LinkedHashMap<>(afterStates));
        this.timestampMillis = timestampMillis;
    }

    public String operationId() {
        return operationId;
    }

    public String description() {
        return description;
    }

    public Map<BlockPos3i, BlockStateRef> beforeStates() {
        return beforeStates;
    }

    public Map<BlockPos3i, BlockStateRef> afterStates() {
        return afterStates;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public int affectedBlockCount() {
        return beforeStates.size();
    }

    public long estimatedMemoryBytes() {
        return 96L + (long) beforeStates.size() * 88L;
    }
}
