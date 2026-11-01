# SCULPTURA — Project State (Primary Source of Truth)

Last Updated: 2026-11-01T14:06:18Z

## 1. Current Project Phase
- **Completed Phases**:
  - Phase 1 (Research & Architecture)
  - Phase 2 (Gradle Multi-Project Foundation & 9 Platform Targets)
  - Phase 3 (`:core` Engine — Math, Geometry, 16 Shapes, 12 Brushes, Smart Materials, 14 Operations, Layers, Conflict-Aware History, Project Serialization)
  - Phase 4 (`:common` — 3D Selection System, Transform Gizmo, Sub-Chunk Holographic Preview Manager & GPU Cache)
  - Phase 5 (`:common` — GUI State Controller & EN/RU Localization)
  - Phase 6 (`:common` — Packet Codec, Server Security Validator, Tick-Budgeted Batch Executor)
  - Phase 7 (`:core` — Versioned Project & Blueprint System with Schema v1->v2 Migration)
- **Active Phase**: Entering Phase 8 (Binding native Minecraft client render hooks, keybindings, and server event buses across all 9 loader targets).

## 2. Completed Features
1. **`:core` Module (`dev.sculptura.core.*`)**:
   - `BlockPos3i`, `Vec3d`, `Axis3D`, `Direction3D`, `Rotation90`, `MirrorPlane`
   - `Ray3d` (exact Ray-AABB, Ray-Plane, Ray-SegmentTube, Ray-RotationRing, and Amanatides-Woo 3D DDA voxel raycasting with 120-block default range)
   - `SimplexNoise3D` (3D gradient noise, FBM, Ridged FBM, deterministic `SplitMix64` coordinate hashing)
   - `FrustumPlanes` (6-plane camera frustum extraction and AABB culling)
   - `BlockStateRef`, `BoundingBox3i`, `VoxelBuffer` (`16x16x16` sub-chunk spatial partitioning & dirty tracking), `VoxelWorldView`
   - `ShapeGenerator` (all 16 procedural shapes: Cube, Sphere, Ellipsoid, Hemisphere, Cylinder, Hollow Cylinder, Cone, Pyramid, Dome, Arch, Ring, Tunnel, Stairs, Wall, Procedural Mountain, Organic Structure)
   - `SculptBrushEngine` (all 12 terrain sculpting brushes: Raise, Lower, Smooth, Flatten, Erode, Terrace, Blend, Blur, Paint, Replace, Noise, Vegetation; 5 brush shapes; 5 falloff curves)
   - `MaterialPattern`, `WeightedPalettePattern`, `GradientMaterialPattern` (Vertical, Horizontal, Directional, Radial, Noise), `MaterialPaletteRegistry` (search, favorites, recent blocks, tag selection, custom palettes)
   - `EditOperation` & `CoreOperations` (all 14 operations sharing identical preview & commit planning)
   - `VoxelLayer` & `LayerManager` (create, rename, delete, duplicate, hide, show, lock, reorder, group, opacity)
   - `HistoryEntry` & `HistoryManager` (Ctrl+Z, Ctrl+Y, grouped operations, memory limits, live world conflict detection & partial undo reporting, cancellation)
   - `SculpturaProject` & `SculpturaProjectSerializer` (save, load, rename, duplicate, export, import, schema v1->v2 migration)

2. **`:common` Module (`dev.sculptura.common.*`)**:
   - `SelectionController` (point, box, face, edge, corner handles, mouse-wheel face expansion/contraction along active normal, grid snapping, locking, 3D outline/handle/axis/label descriptors)
   - `TransformGizmoController` (Translate, Scale, Rotate modes; axis, plane, ring ray intersections; World/Local coordinates; snapping)
   - `PreviewRenderBackend`, `PreviewManager`, `VertexBatchPreviewBackend` (`16x16x16` sub-chunk mesh caching, 6-neighbor internal face culling, frustum culling, deterministic GPU buffer cleanup)
   - `SculpturaPacketCodec` (binary packet serialization with 256 KB max packet size validation)
   - `ServerSecurityValidator` (permissions, per-player rate limiting, world vertical bounds, max blocks, loaded-chunk verification without force-loading)
   - `TickBudgetedExecutor` (tick-budgeted server batch execution, progress reporting, cancellation, undo state capture)
   - `SculpturaGuiController` & `I18nManager` (dark theme UI state for all 11 panels; English `en_us` and Russian `ru_ru` localizations)

3. **Nine Platform Modules (`:platforms:*`)**:
   - Fabric: `fabric-1.20.1`, `fabric-1.21.1`, `fabric-1.21.11` (implementing `net.fabricmc.api.ModInitializer` and `ClientModInitializer` with `fabric.mod.json`)
   - Forge: `forge-1.18.2`, `forge-1.19.2`, `forge-1.20.1` (with `META-INF/mods.toml`)
   - NeoForge: `neoforge-1.21.1`, `neoforge-1.21.4`, `neoforge-1.21.11` (with `META-INF/neoforge.mods.toml`)

## 3. Incomplete Features
- Native Minecraft client rendering hooks (`WorldRenderEvents` / `RenderLevelStageEvent`) and `Screen` widget event bindings inside each of the 9 platform modules.
- Graphical client smoke tests in an external desktop environment with OpenGL display.

## 4. Current Build & Compiler Status
- **Status**: `BUILD SUCCESSFUL` across all 11 Gradle projects (`./gradlew test build`).
- **Compiler Errors**: `0`
- **Runtime / Test Errors**: `0` (22 / 22 JUnit 5 tests passing).

## 5. Last Completed Task
- Implemented `:core`, `:common`, and all 9 `:platforms:*` modules, generated `./gradlew`, compiled all 9 target mod JARs, and verified all 22 JUnit 5 unit, integration, and platform smoke tests.

## 6. Exact Next Task
- Proceed to Phase 8 as documented in `/docs/NEXT_STEPS.md`: bind native loader rendering events (`WorldRenderEvents` on Fabric, `RenderLevelStageEvent` on Forge/NeoForge), keybindings, and server tick/network listeners in each platform module.
