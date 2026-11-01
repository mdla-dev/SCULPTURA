# SCULPTURA — Development Roadmap

## Phase 1 — Research & Architecture [COMPLETED]
- [x] Inspect Google AI Studio workspace capabilities (Linux root container, JDK 21 & JDK 17, Gradle 8.12.1, Maven connectivity).
- [x] Verify all 9 Minecraft target configurations against official Fabric, Forge, and NeoForge repositories.
- [x] Design three-tier modular architecture (`:core`, `:common`, `:platforms:*`).
- [x] Document compatibility matrix and cross-version API isolation strategies.

## Phase 2 — Project Foundation [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Configure multi-project Gradle build (`settings.gradle`, `build.gradle`, `gradle.properties`, `./gradlew`).
- [x] Configure all 9 platform modules (`fabric-1.20.1`, `fabric-1.21.1`, `fabric-1.21.11`, `forge-1.18.2`, `forge-1.19.2`, `forge-1.20.1`, `neoforge-1.21.1`, `neoforge-1.21.4`, `neoforge-1.21.11`).
- [x] Create loader metadata (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`) and localization files (`en_us.json`, `ru_ru.json`).

## Phase 3 — Core Engine (`:core`) [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Implement 3D Mathematics (`BlockPos3i`, `Vec3d`, `Axis3D`, `Direction3D`, `Rotation90`, `MirrorPlane`, `Ray3d`, `SimplexNoise3D`, `FrustumPlanes`).
- [x] Implement Voxel Geometry (`BoundingBox3i`, `BlockStateRef`, `VoxelBuffer`).
- [x] Implement all 16 Procedural Shape Generators (`ShapeGenerator`).
- [x] Implement all 12 Terrain Sculpting Brushes (`SculptBrushEngine`).
- [x] Implement Smart Material System (`MaterialPattern`, weighted palettes, 5 gradient types, `MaterialPaletteRegistry`).
- [x] Implement all 14 Edit Operations (`EditOperation` hierarchy).
- [x] Implement Layers (`LayerManager`), Conflict-Aware History (`HistoryManager`), and Versioned Project Serialization (`SculpturaProjectSerializer`).
- [x] Write comprehensive JUnit 5 unit and integration tests.

## Phase 4 — Selection, Gizmo & Rendering (`:common`) [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Implement 3D Selection System (`SelectionController` with face/edge/corner handles & mouse-wheel face resizing).
- [x] Implement Transformation Gizmo (`TransformGizmoController` with axis, plane, and ring ray intersections).
- [x] Implement Holographic Preview Manager (`PreviewManager`, `PreviewChunkCache`, frustum culling, dirty-region meshing, GPU resource lifecycle).

## Phase 5 — GUI & Localization (`:common`) [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Implement dark-themed UI state & layout models (`SculpturaGuiController`) for Toolbar, Selection, Transform, Brush, Shape, Material Palette, Layers, History, Project Browser, and Preview settings.
- [x] Implement English (`en_us`) and Russian (`ru_ru`) localization (`I18nManager`).

## Phase 6 — World Editing, Networking & Multiplayer Security (`:common`) [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Implement `SculpturaPacketCodec` with packet size validation.
- [x] Implement `ServerSecurityValidator` (permissions, rate limits, world bounds, loaded-chunk verification).
- [x] Implement `TickBudgetedExecutor` (server tick-budgeted batch execution, progress reporting, cancellation, undo state capture).

## Phase 7 — Project System [IN PROGRESS -> COMPLETING THIS SESSION]
- [x] Implement project/blueprint save, load, rename, duplicate, export, import, and schema v1->v2 migration.

## Phase 8 — Platform Adapters & Build Verification [NEXT]
- [ ] Bind native Minecraft rendering hooks (`WorldRenderEvents` / `RenderLevelStageEvent`) to `PreviewRenderBackend` across all 9 loader versions.
- [ ] Run external graphical client smoke tests across all 9 Minecraft installations.

## Phase 9 — Optimization & Release [FUTURE]
- [ ] Profile large-scale (1M+ block) preview mesh generation under live client frame budgets.
- [ ] Package signed multi-loader release artifacts for Modrinth / CurseForge.
