# SCULPTURA — System Architecture

## 1. Architectural Overview

SCULPTURA is structured as a strict three-tier multi-project Gradle build designed to support nine Minecraft configurations (Fabric 1.20.1/1.21.1/1.21.11, Forge 1.18.2/1.19.2/1.20.1, NeoForge 1.21.1/1.21.4/1.21.11) from a single shared algorithmic and orchestration core.

```text
+-----------------------------------------------------------------------------------+
|                                 PLATFORM MODULES                                  |
|  fabric-1.20.1 | fabric-1.21.1 | fabric-1.21.11                                   |
|  forge-1.18.2  | forge-1.19.2  | forge-1.20.1                                     |
|  neoforge-1.21.1 | neoforge-1.21.4 | neoforge-1.21.11                             |
+-----------------------------------------+-----------------------------------------+
                                          | implements SPI interfaces
                                          v
+-----------------------------------------------------------------------------------+
|                                :common (Java 17)                                  |
|  - SelectionController (Point, Box, Face, Edge, Corner, MouseWheel Resize, Lock)  |
|  - TransformGizmoController (Translate, Scale, Rotate, Axis/Plane/Ring Raycast)   |
|  - PreviewManager & PreviewChunkCache (16x16x16 Sub-Chunks, Frustum Culling)      |
|  - ServerSecurityValidator (Permissions, Rate Limits, Bounds, Loaded-Chunk Check) |
|  - TickBudgetedExecutor (Server Tick-Budgeted Batching, Progress & Cancellation)  |
|  - SculpturaGuiController & I18nManager (Toolbar, Panels, EN/RU Localization)     |
|  - SculpturaPacketCodec (Version-Agnostic Binary Packet Serialization & Limits)   |
+-----------------------------------------+-----------------------------------------+
                                          | orchestrates pure domain models
                                          v
+-----------------------------------------------------------------------------------+
|                                 :core (Java 17)                                   |
|  - math: BlockPos3i, Vec3d, Axis3D, Direction3D, Ray3d, SimplexNoise3D, Frustum   |
|  - geometry: BoundingBox3i, BlockStateRef, VoxelBuffer (16^3 Spatial Chunks)      |
|  - shape: ShapeGenerator (16 Procedural Shapes: Sphere, Arch, Mountain, Organic)  |
|  - material: MaterialPattern, WeightedPalette, 5 Gradient Modes, PaletteRegistry  |
|  - brush: SculptBrushEngine (12 Terrain Brushes, 5 Brush Shapes, 5 Falloff Modes) |
|  - operation: 14 EditOperations (Shared Preview/Commit Pipeline, Cost, Validation)|
|  - layer: LayerManager & VoxelLayer (Visibility, Lock, Opacity, Reorder, Groups)  |
|  - history: HistoryManager (Grouped Undo/Redo, Memory Cap, Conflict Detection)    |
|  - project: SculpturaProjectSerializer (Versioned Schema v1->v2 Migration, I/O)   |
+-----------------------------------------------------------------------------------+
```

---

## 2. Module Dependency Rules

1. **`:core` Isolation Rule**:
   - `:core` MUST NOT import `net.minecraft.*`, `com.mojang.*`, `net.fabricmc.*`, `net.minecraftforge.*`, or `net.neoforged.*`.
   - All calculations in `:core` are deterministic, thread-safe for off-thread preview planning, and testable in pure JUnit 5 in milliseconds.

2. **`:common` Isolation Rule**:
   - `:common` depends only on `:core` and standard Java 17 libraries.
   - Platform interactions are defined exclusively through SPI contracts in `dev.sculptura.common.platform`:
     - `PlatformWorldAdapter`: Read/write block states, query chunk load state (`isChunkLoaded(chunkX, chunkZ)`), query world height bounds (`getMinBuildHeight()`, `getMaxBuildHeight()`).
     - `PreviewRenderBackend`: Upload and dispose cached `16x16x16` sub-chunk preview geometry meshes on the GPU render thread, draw selection wireframes and gizmo handles.
     - `PlatformNetworkBridge`: Send client-to-server operation/undo requests and server-to-client progress/result packets.

3. **Platform Adapter Rule**:
   - Each of the 9 `:platforms:*` modules provides the loader entrypoint (`ModInitializer` / `ClientModInitializer` on Fabric, `@Mod` on Forge and NeoForge), mod descriptor metadata (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`), localization assets (`en_us.json`, `ru_ru.json`), and concrete adapter implementations for that Minecraft version.

---

## 3. Data Flow: Preview vs. Server-Authoritative Execution

1. **Client-First Visual Preview**:
   - When the user adjusts a 3D selection handle, drags a transformation gizmo axis/ring, configures a procedural shape, or moves a sculpting brush cursor (`Ray3d` up to 120 blocks), the client constructs an `EditOperation` instance.
   - `EditOperation.generatePreview(VoxelWorldView)` computes a read-only `VoxelBuffer` of proposed block changes without mutating the world.
   - `PreviewManager` diffs the new `VoxelBuffer` against cached `16x16x16` sub-chunks (`PreviewChunkCache`), marks only modified sub-chunks dirty, frustum-culls visible sub-chunks using `FrustumPlanes`, and submits dirty meshes to `PreviewRenderBackend`.

2. **Server-Authoritative Commit**:
   - When the user commits an edit, the client serializes the `OperationDescriptor` via `SculpturaPacketCodec` and sends it to the server.
   - The server NEVER trusts client-computed block diffs directly for arbitrary world mutation without validation. Instead, `ServerSecurityValidator` validates:
     - Packet size (`<= 256 KB`)
     - Player permission level and creative/builder capability
     - Token-bucket rate limit (operations per second and blocks per minute)
     - Operation parameters and maximum block count (`<= maxBlocksPerOperation`)
     - World vertical boundaries (`minY <= y < maxY`)
     - Chunk availability (`isChunkLoaded` — unloaded chunks are rejected, never force-loaded)
   - Once validated, `TickBudgetedExecutor` schedules the operation across server ticks within a strict millisecond/block budget per tick, recording original block states into the player's server-side `HistoryManager` for conflict-safe Undo/Redo.
