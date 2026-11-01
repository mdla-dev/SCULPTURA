package dev.sculptura.core.operation;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.BoundingBox3i;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.geometry.VoxelWorldView;
import dev.sculptura.core.history.HistoryEntry;
import dev.sculptura.core.math.BlockPos3i;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shared contract for all 14 SCULPTURA world editing operations.
 * Guarantees that holographic preview and server commit execute the exact same planning logic.
 */
public interface EditOperation {
    String operationId();

    String displayName();

    Optional<BoundingBox3i> affectedBounds();

    ValidationResult validate(OperationLimits limits, VoxelWorldView world);

    /**
     * Generates the proposed block changes without modifying the world.
     * Used by both the client holographic preview and the server commit planner.
     */
    VoxelBuffer planPreview(VoxelWorldView world, AtomicBoolean cancellationToken);

    default CostEstimate estimateCost(VoxelWorldView world) {
        VoxelBuffer planned = planPreview(world, new AtomicBoolean(false));
        int totalBlocks = planned.size();
        int nonAirPlacements = 0;
        int airRemovals = 0;
        for (BlockStateRef state : planned.entries().values()) {
            if (state.isAir()) {
                airRemovals++;
            } else {
                nonAirPlacements++;
            }
        }
        int subChunks = planned.activeSubChunkKeys().size();
        int estimatedTicks = Math.max(1, (int) Math.ceil(totalBlocks / 4096.0));
        return new CostEstimate(totalBlocks, nonAirPlacements, airRemovals, subChunks, estimatedTicks, planned.estimatedMemoryBytes());
    }

    /**
     * Executes the planned operation against a mutable block writer, recording original block states for Undo/Redo.
     */
    default ExecutionResult execute(
            VoxelWorldView world,
            BiConsumer<BlockPos3i, BlockStateRef> worldWriter,
            OperationLimits limits,
            boolean hasPermission,
            AtomicBoolean cancellationToken,
            Consumer<OperationProgress> progressCallback
    ) {
        if (!hasPermission) {
            return ExecutionResult.failure("PERMISSION_DENIED", "Player lacks permission to execute " + operationId());
        }
        ValidationResult validation = validate(limits, world);
        if (!validation.valid()) {
            return ExecutionResult.failure("VALIDATION_FAILED", String.join("; ", validation.errors()));
        }

        VoxelBuffer planned = planPreview(world, cancellationToken);
        if (cancellationToken != null && cancellationToken.get()) {
            return ExecutionResult.cancelled(0, planned.size());
        }
        if (planned.size() > limits.maxBlocksPerOperation()) {
            return ExecutionResult.failure(
                    "MAX_BLOCKS_EXCEEDED",
                    "Operation modifies " + planned.size() + " blocks, exceeding limit of " + limits.maxBlocksPerOperation()
            );
        }

        Map<BlockPos3i, BlockStateRef> beforeStates = new LinkedHashMap<>();
        Map<BlockPos3i, BlockStateRef> afterStates = new LinkedHashMap<>();
        int total = planned.size();
        int processed = 0;
        int modifiedCount = 0;

        for (Map.Entry<BlockPos3i, BlockStateRef> entry : planned.entries().entrySet()) {
            if (cancellationToken != null && cancellationToken.get()) {
                HistoryEntry partialUndo = new HistoryEntry(operationId(), displayName(), beforeStates, afterStates);
                return new ExecutionResult(false, true, modifiedCount, total, "CANCELLED", "Operation cancelled mid-execution", partialUndo);
            }
            BlockPos3i pos = entry.getKey();
            if (pos.y() < world.getMinBuildHeight() || pos.y() >= world.getMaxBuildHeight()) {
                continue;
            }
            BlockStateRef newState = entry.getValue();
            BlockStateRef oldState = world.getBlockState(pos);
            if (!oldState.equals(newState)) {
                beforeStates.put(pos, oldState);
                afterStates.put(pos, newState);
                worldWriter.accept(pos, newState);
                modifiedCount++;
            }
            processed++;
            if (progressCallback != null && (processed % 256 == 0 || processed == total)) {
                progressCallback.accept(new OperationProgress(operationId(), processed, total, false));
            }
        }

        HistoryEntry undoEntry = new HistoryEntry(operationId(), displayName(), beforeStates, afterStates);
        return ExecutionResult.success(modifiedCount, total, undoEntry);
    }

    record OperationLimits(int maxBlocksPerOperation, int maxRadius, int minBuildY, int maxBuildY) {
        public static OperationLimits defaults() {
            return new OperationLimits(262_144, 128, -64, 320);
        }
    }

    record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, List.of(message));
        }
    }

    record CostEstimate(
            int totalBlocks,
            int blockPlacements,
            int blockRemovals,
            int affectedSubChunks,
            int estimatedServerTicks,
            long estimatedMemoryBytes
    ) {}

    record OperationProgress(String operationId, int processedBlocks, int totalBlocks, boolean completed) {
        public double fraction() {
            return totalBlocks <= 0 ? 1.0 : (double) processedBlocks / totalBlocks;
        }
    }

    record ExecutionResult(
            boolean succeeded,
            boolean cancelled,
            int modifiedBlocks,
            int plannedBlocks,
            String errorCode,
            String errorMessage,
            HistoryEntry historyEntry
    ) {
        public static ExecutionResult success(int modified, int planned, HistoryEntry entry) {
            return new ExecutionResult(true, false, modified, planned, null, null, entry);
        }

        public static ExecutionResult failure(String code, String message) {
            return new ExecutionResult(false, false, 0, 0, code, message, null);
        }

        public static ExecutionResult cancelled(int modified, int planned) {
            return new ExecutionResult(false, true, modified, planned, "CANCELLED", "Cancelled by user", null);
        }
    }
}
