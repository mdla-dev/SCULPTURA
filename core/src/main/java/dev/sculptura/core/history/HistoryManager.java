package dev.sculptura.core.history;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.math.BlockPos3i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Conflict-aware Undo/Redo history manager supporting:
 * - Ctrl+Z (undo) and Ctrl+Y (redo)
 * - Grouped compound operations
 * - Entry count and byte memory limits
 * - Live world conflict detection (never silently overwrites edits made by other players or mods)
 * - Partial undo reporting and cancellation
 */
public final class HistoryManager {
    public record ConflictDetail(BlockPos3i pos, BlockStateRef expectedState, BlockStateRef actualWorldState) {}

    public record UndoRedoReport(
            boolean executed,
            boolean cancelled,
            String entryDescription,
            int restoredBlocks,
            int skippedConflicts,
            List<ConflictDetail> conflicts
    ) {
        public boolean isPartial() {
            return executed && skippedConflicts > 0;
        }
    }

    private final int maxEntries;
    private final long maxMemoryBytes;
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();
    private long currentMemoryBytes = 0L;

    // Active group state
    private boolean groupActive = false;
    private String activeGroupName = null;
    private final Map<BlockPos3i, BlockStateRef> groupBefore = new LinkedHashMap<>();
    private final Map<BlockPos3i, BlockStateRef> groupAfter = new LinkedHashMap<>();

    public HistoryManager(int maxEntries, long maxMemoryBytes) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxMemoryBytes = Math.max(64_000L, maxMemoryBytes);
    }

    public static HistoryManager createDefault() {
        return new HistoryManager(100, 64L * 1024L * 1024L); // 100 steps, 64 MB budget
    }

    public void beginGroup(String groupName) {
        if (groupActive) {
            endGroup();
        }
        groupActive = true;
        activeGroupName = groupName == null || groupName.isBlank() ? "Grouped Edit" : groupName;
        groupBefore.clear();
        groupAfter.clear();
    }

    public void endGroup() {
        if (!groupActive) {
            return;
        }
        groupActive = false;
        if (!groupBefore.isEmpty()) {
            pushCommittedEntry(new HistoryEntry("grouped_operation", activeGroupName, groupBefore, groupAfter));
        }
        activeGroupName = null;
        groupBefore.clear();
        groupAfter.clear();
    }

    public void record(HistoryEntry entry) {
        if (entry == null || entry.affectedBlockCount() == 0) {
            return;
        }
        if (groupActive) {
            for (Map.Entry<BlockPos3i, BlockStateRef> e : entry.beforeStates().entrySet()) {
                groupBefore.putIfAbsent(e.getKey(), e.getValue());
            }
            groupAfter.putAll(entry.afterStates());
            return;
        }
        pushCommittedEntry(entry);
    }

    private void pushCommittedEntry(HistoryEntry entry) {
        for (HistoryEntry discardedRedo : redoStack) {
            currentMemoryBytes -= discardedRedo.estimatedMemoryBytes();
        }
        redoStack.clear();

        undoStack.push(entry);
        currentMemoryBytes += entry.estimatedMemoryBytes();
        enforceLimits();
    }

    private void enforceLimits() {
        while (undoStack.size() > maxEntries || (undoStack.size() > 1 && currentMemoryBytes > maxMemoryBytes)) {
            HistoryEntry oldest = undoStack.removeLast();
            currentMemoryBytes = Math.max(0L, currentMemoryBytes - oldest.estimatedMemoryBytes());
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int undoCount() {
        return undoStack.size();
    }

    public int redoCount() {
        return redoStack.size();
    }

    public long currentMemoryBytes() {
        return currentMemoryBytes;
    }

    public List<HistoryEntry> undoEntries() {
        return new ArrayList<>(undoStack);
    }

    public List<HistoryEntry> redoEntries() {
        return new ArrayList<>(redoStack);
    }

    /**
     * Performs Ctrl+Z (Undo). Checks current world state against entry.afterStates(); if another player
     * or mod changed a block since this operation ran, that block is reported as a conflict and skipped
     * unless forceOverwriteConflicts is true.
     */
    public UndoRedoReport undo(
            VoxelWorldView world,
            BiConsumer<BlockPos3i, BlockStateRef> worldWriter,
            boolean forceOverwriteConflicts,
            AtomicBoolean cancellationToken
    ) {
        if (undoStack.isEmpty()) {
            return new UndoRedoReport(false, false, "Nothing to undo", 0, 0, List.of());
        }
        HistoryEntry entry = undoStack.pop();
        List<ConflictDetail> conflicts = new ArrayList<>();
        int restored = 0;

        for (Map.Entry<BlockPos3i, BlockStateRef> e : entry.beforeStates().entrySet()) {
            if (cancellationToken != null && cancellationToken.get()) {
                undoStack.push(entry);
                return new UndoRedoReport(false, true, entry.description(), restored, conflicts.size(), Collections.unmodifiableList(conflicts));
            }
            BlockPos3i pos = e.getKey();
            BlockStateRef targetBefore = e.getValue();
            BlockStateRef expectedCurrent = entry.afterStates().get(pos);
            BlockStateRef actualWorld = world.getBlockState(pos);

            if (expectedCurrent != null && !actualWorld.equals(expectedCurrent) && !forceOverwriteConflicts) {
                conflicts.add(new ConflictDetail(pos, expectedCurrent, actualWorld));
                continue;
            }
            worldWriter.accept(pos, targetBefore);
            restored++;
        }

        redoStack.push(entry);
        return new UndoRedoReport(true, false, entry.description(), restored, conflicts.size(), Collections.unmodifiableList(conflicts));
    }

    /**
     * Performs Ctrl+Y (Redo) with live world conflict verification against entry.beforeStates().
     */
    public UndoRedoReport redo(
            VoxelWorldView world,
            BiConsumer<BlockPos3i, BlockStateRef> worldWriter,
            boolean forceOverwriteConflicts,
            AtomicBoolean cancellationToken
    ) {
        if (redoStack.isEmpty()) {
            return new UndoRedoReport(false, false, "Nothing to redo", 0, 0, List.of());
        }
        HistoryEntry entry = redoStack.pop();
        List<ConflictDetail> conflicts = new ArrayList<>();
        int applied = 0;

        for (Map.Entry<BlockPos3i, BlockStateRef> e : entry.afterStates().entrySet()) {
            if (cancellationToken != null && cancellationToken.get()) {
                redoStack.push(entry);
                return new UndoRedoReport(false, true, entry.description(), applied, conflicts.size(), Collections.unmodifiableList(conflicts));
            }
            BlockPos3i pos = e.getKey();
            BlockStateRef targetAfter = e.getValue();
            BlockStateRef expectedBefore = entry.beforeStates().get(pos);
            BlockStateRef actualWorld = world.getBlockState(pos);

            if (expectedBefore != null && !actualWorld.equals(expectedBefore) && !forceOverwriteConflicts) {
                conflicts.add(new ConflictDetail(pos, expectedBefore, actualWorld));
                continue;
            }
            worldWriter.accept(pos, targetAfter);
            applied++;
        }

        undoStack.push(entry);
        return new UndoRedoReport(true, false, entry.description(), applied, conflicts.size(), Collections.unmodifiableList(conflicts));
    }
}
