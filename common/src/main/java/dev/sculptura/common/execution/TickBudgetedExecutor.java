package dev.sculptura.common.execution;

import dev.sculptura.common.platform.PlatformWorldAdapter;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.history.HistoryEntry;
import dev.sculptura.core.history.HistoryManager;
import dev.sculptura.core.math.BlockPos3i;
import dev.sculptura.core.operation.EditOperation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Server-side Tick-Budgeted Batch Execution Engine (Sections 7 & 8).
 * Prevents unbounded main-thread stalls by splitting large validated operations across server ticks
 * within configurable block-count and nanosecond budgets per tick.
 */
public final class TickBudgetedExecutor {
    public static final class QueuedTask {
        private final String taskId;
        private final String operationId;
        private final String description;
        private final Iterator<Map.Entry<BlockPos3i, BlockStateRef>> entryIterator;
        private final int totalBlocks;
        private final Map<BlockPos3i, BlockStateRef> beforeStates = new LinkedHashMap<>();
        private final Map<BlockPos3i, BlockStateRef> afterStates = new LinkedHashMap<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Consumer<EditOperation.OperationProgress> progressListener;
        private final Consumer<HistoryEntry> completionListener;
        private int processedBlocks = 0;

        QueuedTask(
                String taskId,
                String operationId,
                String description,
                VoxelBuffer plannedChanges,
                Consumer<EditOperation.OperationProgress> progressListener,
                Consumer<HistoryEntry> completionListener
        ) {
            this.taskId = taskId;
            this.operationId = operationId;
            this.description = description;
            this.entryIterator = plannedChanges.entries().entrySet().iterator();
            this.totalBlocks = plannedChanges.size();
            this.progressListener = progressListener;
            this.completionListener = completionListener;
        }

        public String taskId() { return taskId; }
        public int processedBlocks() { return processedBlocks; }
        public int totalBlocks() { return totalBlocks; }
        public void cancel() { cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
    }

    private final int maxBlocksPerTick;
    private final long maxNanosPerTick;
    private final Deque<QueuedTask> activeQueue = new ArrayDeque<>();

    public TickBudgetedExecutor(int maxBlocksPerTick, long maxMillisPerTick) {
        this.maxBlocksPerTick = Math.max(16, maxBlocksPerTick);
        this.maxNanosPerTick = Math.max(1_000_000L, maxMillisPerTick * 1_000_000L);
    }

    public static TickBudgetedExecutor createDefault() {
        return new TickBudgetedExecutor(4096, 8L); // 4096 blocks or 8ms per server tick
    }

    public QueuedTask submitValidatedOperation(
            String taskId,
            EditOperation operation,
            PlatformWorldAdapter world,
            HistoryManager playerHistory,
            Consumer<EditOperation.OperationProgress> progressListener
    ) {
        VoxelBuffer planned = operation.planPreview(world, new AtomicBoolean(false));
        QueuedTask task = new QueuedTask(
                taskId,
                operation.operationId(),
                operation.displayName(),
                planned,
                progressListener,
                historyEntry -> {
                    if (playerHistory != null && historyEntry.affectedBlockCount() > 0) {
                        playerHistory.record(historyEntry);
                    }
                }
        );
        activeQueue.addLast(task);
        return task;
    }

    /**
     * Invoked once per server tick on the server main thread.
     * Processes queued block modifications up to maxBlocksPerTick and maxNanosPerTick.
     */
    public int tickServer(PlatformWorldAdapter world) {
        if (activeQueue.isEmpty()) {
            return 0;
        }
        long startNanos = System.nanoTime();
        int blocksPlacedThisTick = 0;

        while (!activeQueue.isEmpty() && blocksPlacedThisTick < maxBlocksPerTick) {
            if (blocksPlacedThisTick > 0 && (System.nanoTime() - startNanos) >= maxNanosPerTick) {
                break;
            }
            QueuedTask current = activeQueue.peekFirst();
            if (current.isCancelled()) {
                activeQueue.pollFirst();
                if (!current.beforeStates.isEmpty()) {
                    current.completionListener.accept(new HistoryEntry(
                            current.operationId, current.description + " (Partial/Cancelled)", current.beforeStates, current.afterStates
                    ));
                }
                continue;
            }

            if (!current.entryIterator.hasNext()) {
                activeQueue.pollFirst();
                if (current.progressListener != null) {
                    current.progressListener.accept(new EditOperation.OperationProgress(
                            current.operationId, current.processedBlocks, current.totalBlocks, true
                    ));
                }
                current.completionListener.accept(new HistoryEntry(
                        current.operationId, current.description, current.beforeStates, current.afterStates
                ));
                continue;
            }

            Map.Entry<BlockPos3i, BlockStateRef> next = current.entryIterator.next();
            BlockPos3i pos = next.getKey();
            BlockStateRef targetState = next.getValue();

            if (world.isChunkLoaded(pos.chunkX(), pos.chunkZ())
                    && pos.y() >= world.getMinBuildHeight()
                    && pos.y() < world.getMaxBuildHeight()
                    && world.isValidBlockState(targetState)) {
                BlockStateRef existing = world.getBlockState(pos);
                if (!existing.equals(targetState)) {
                    current.beforeStates.put(pos, existing);
                    current.afterStates.put(pos, targetState);
                    world.setBlockStateOnMainThread(pos, targetState);
                }
            }
            current.processedBlocks++;
            blocksPlacedThisTick++;
        }

        QueuedTask head = activeQueue.peekFirst();
        if (head != null && head.progressListener != null) {
            head.progressListener.accept(new EditOperation.OperationProgress(
                    head.operationId, head.processedBlocks, head.totalBlocks, false
            ));
        }
        return blocksPlacedThisTick;
    }

    public int pendingTaskCount() {
        return activeQueue.size();
    }
}
