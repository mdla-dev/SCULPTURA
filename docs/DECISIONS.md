# SCULPTURA — Architectural & Engineering Decisions

## ADR-001: Bytecode Target Java 17 for `:core` and `:common`
- **Context**: SCULPTURA targets Minecraft 1.18.2, 1.19.2, and 1.20.1 (which run on Java 17) as well as Minecraft 1.21.1, 1.21.4, and 1.21.11 (which run on Java 21).
- **Decision**: Compile `:core` and `:common` with `JavaLanguageVersion.of(17)` (`--release 17`). Compile Java 21 platform modules (`fabric-1.21.1`, `fabric-1.21.11`, `neoforge-1.21.1`, `neoforge-1.21.4`, `neoforge-1.21.11`) with Java 21 toolchains while consuming the Java 17 `:core` and `:common` artifacts.
- **Consequence**: 100% binary compatibility across all 9 targets with zero duplicate code in `:core` or `:common`.

## ADR-002: Deterministic Spatial Hashing (`SplitMix64`) for Smart Materials & Brushes
- **Context**: Section 5.D requires that "Preview and commit must use the same operation parameters and geometry algorithms." If weighted palettes or noise gradients used `java.util.Random` sequentially, any change in iteration order or async chunk meshing would cause the committed blocks on the server to differ from the client holographic preview.
- **Decision**: All randomized material distributions, noise gradients, and brush perturbations evaluate a pure coordinate-seeded `SplitMix64` hash function `hash(seed, x, y, z)`.
- **Consequence**: Client holographic previews and server-side committed executions produce identical block states at every `(x, y, z)` coordinate regardless of evaluation order.

## ADR-003: `16x16x16` Sub-Chunk Partitioned `VoxelBuffer` and `PreviewChunkCache`
- **Context**: Rebuilding a monolithic vertex buffer for a 100,000-block sphere or brush stroke on every mouse movement causes severe frame drops and GPU memory churn.
- **Decision**: Partition `VoxelBuffer` and `PreviewChunkCache` into `16x16x16` sub-chunk regions indexed by packed chunk coordinates. Track a monotonic dirty revision per sub-chunk and perform 6-neighbor internal face culling when building preview quads.
- **Consequence**: Incremental brush strokes or face drags only rebuild the touched `16x16x16` sub-chunks, and off-screen sub-chunks are skipped via `FrustumPlanes.testAabb(...)`.

## ADR-004: Conflict-Safe Undo/Redo with Live World State Verification
- **Context**: In multiplayer servers (or when other mods modify blocks), blindly restoring old block states during `Ctrl+Z` can overwrite edits made by other players after the operation occurred.
- **Decision**: Every `HistoryEntry` stores both `beforeState` and `afterState` (the state placed by the operation) per block coordinate. During Undo, `HistoryManager` compares the current live world state at `(x, y, z)` against `afterState`. If another player or system modified that coordinate (`liveState != afterState`), the coordinate is flagged as a conflict and skipped (unless force-override is explicitly authorized), and a `PartialUndoReport` is returned to the user.

## ADR-005: Server-Authoritative Tick-Budgeted Execution & No Force-Loading Chunks
- **Context**: Large voxel edits can stall the server main thread or be abused by malicious clients to force-load thousands of chunks and crash the server.
- **Decision**: `ServerSecurityValidator` verifies that every target chunk is already loaded before execution (`PlatformWorldAdapter.isChunkLoaded`) and enforces per-player token-bucket rate limits and max block counts. `TickBudgetedExecutor` splits large validated operations across ticks using configurable block and nanosecond budgets.
