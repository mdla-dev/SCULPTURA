# SCULPTURA — Target Compatibility Matrix

Last Verified: 2026-11-01
Verification Source: Official Fabric Meta API (`meta.fabricmc.net`), Fabric Maven (`maven.fabricmc.net`), NeoForged Maven API (`maven.neoforged.net`), and MinecraftForge Promotions API (`files.minecraftforge.net`).

---

## 1. Nine Official Target Configurations

| # | Module Path | Loader | Minecraft Version | Required Java | Loader Version | API / Framework Version | Gradle Plugin | Mappings |
|---|-------------|--------|-------------------|---------------|----------------|-------------------------|---------------|----------|
| 1 | `:platforms:fabric-1.20.1` | Fabric | `1.20.1` | Java 17 | `0.20.1` | Fabric API `0.92.12+1.20.1` | Fabric Loom `1.19.3` | Official Mojang Mappings |
| 2 | `:platforms:fabric-1.21.1` | Fabric | `1.21.1` | Java 21 | `0.20.1` | Fabric API `0.116.17+1.21.1` | Fabric Loom `1.19.3` | Official Mojang Mappings |
| 3 | `:platforms:fabric-1.21.11` | Fabric | `1.21.11` | Java 21 | `0.20.1` | Fabric API `0.141.6+1.21.11` | Fabric Loom `1.19.3` | Official Mojang Mappings |
| 4 | `:platforms:forge-1.18.2` | Forge | `1.18.2` | Java 17 | `40.3.12` (rec `40.3.0`) | Minecraft Forge `1.18.2-40.3.12` | ForgeGradle `6.0.+` | Official Mojang Mappings |
| 5 | `:platforms:forge-1.19.2` | Forge | `1.19.2` | Java 17 | `43.5.2` (rec `43.5.0`) | Minecraft Forge `1.19.2-43.5.2` | ForgeGradle `6.0.+` | Official Mojang Mappings |
| 6 | `:platforms:forge-1.20.1` | Forge | `1.20.1` | Java 17 | `47.4.28` | Minecraft Forge `1.20.1-47.4.28` | ForgeGradle `6.0.+` | Official Mojang Mappings |
| 7 | `:platforms:neoforge-1.21.1` | NeoForge | `1.21.1` | Java 21 | `21.1.265` | NeoForge `21.1.265` | ModDevGradle `2.0.148` | Official Mojang Mappings |
| 8 | `:platforms:neoforge-1.21.4` | NeoForge | `1.21.4` | Java 21 | `21.4.161` | NeoForge `21.4.161` | ModDevGradle `2.0.148` | Official Mojang Mappings |
| 9 | `:platforms:neoforge-1.21.11` | NeoForge | `1.21.11` | Java 21 | `21.11.48` | NeoForge `21.11.48` | ModDevGradle `2.0.148` | Official Mojang Mappings |

---

## 2. Shared Module Bytecode Compatibility

| Shared Module | Java Language / Bytecode Target | Minecraft Dependency | Purpose |
|---------------|---------------------------------|----------------------|---------|
| `:core` | **Java 17** (`--release 17`) | **NONE** (Strictly forbidden) | 3D Math, Ray Intersection, Simplex Noise, Voxel Geometry, 16 Shape Generators, 12 Sculpting Brushes, Smart Material Palettes/Gradients, 14 Edit Operations, Layers, Conflict-Aware History, Versioned Project/Blueprint Serialization |
| `:common` | **Java 17** (`--release 17`) | **NONE** (Interfaces only) | 3D Selection & Handle System, Transformation Gizmo, Sub-Chunk Preview Cache & Frustum Culling, Server Security & Rate Limiting, Tick-Budgeted Batch Executor, GUI State Machine, Localization (`en_us`, `ru_ru`) |

Because `:core` and `:common` target **Java 17 bytecode**, they link seamlessly into both the **Java 17** targets (`1.18.2`, `1.19.2`, `1.20.1`) and the **Java 21** targets (`1.21.1`, `1.21.4`, `1.21.11`).

---

## 3. Cross-Version Technical Differences & Isolation Strategy

### A. Rendering & GUI API Differences
1. **PoseStack vs GuiGraphics**:
   - **MC 1.18.2 / 1.19.2 (Forge)**: 2D Screen rendering uses `PoseStack` directly with `Screen.fill()`, `Font.draw()`, and `RenderSystem` shader calls.
   - **MC 1.20.1 (Fabric & Forge)**: Introduces `GuiGraphics` wrapper around `PoseStack` and `MultiBufferSource`.
   - **MC 1.21.1 / 1.21.4 / 1.21.11 (Fabric & NeoForge)**: `GuiGraphics` and `VertexConsumer` pipeline updated (`BufferBuilder` methods changed from `.vertex(...).color(...).endVertex()` in 1.20.1 to `.addVertex(...).setColor(...).setNormal(...)` in 1.21+, and 1.21.4/1.21.11 further refine `RenderType` and `WorldRenderContext`).
   - **Isolation**: `:common` defines `PreviewRenderBackend` and `UiDrawContext` interfaces using primitive coordinates, ARGB colors, and `PreviewQuad` / `LineSegment3D` batches. Each platform module implements the version-specific `BufferBuilder` / `VertexConsumer` / `GuiGraphics` binding.

### B. Networking API Differences
1. **Custom Payload vs SimpleChannel**:
   - **Forge 1.18.2 / 1.19.2 / 1.20.1**: Uses `NetworkRegistry.newSimpleChannel(...)` (`SimpleChannel`) with `FriendlyByteBuf` encoders/decoders.
   - **Fabric 1.20.1**: Uses `ServerPlayNetworking` / `ClientPlayNetworking` with `PacketByteBuf` and `Identifier`.
   - **Fabric & NeoForge 1.21.1 / 1.21.4 / 1.21.11**: Uses typed `CustomPacketPayload` + `StreamCodec<FriendlyByteBuf, T>` (`PayloadTypeRegistry` on Fabric; `RegisterPayloadHandlersEvent` on NeoForge).
   - **Isolation**: `:common` serializes all SCULPTURA network packets (`OperationRequestPacket`, `UndoRedoRequestPacket`, `OperationProgressPacket`) to and from canonical length-prefixed `byte[]` buffers via `SculpturaPacketCodec` with strict max-size enforcement (`MAX_PACKET_BYTES = 262_144`). Each platform wraps this `byte[]` payload in its native channel/payload registration.

### C. ResourceLocation / Identifier & Component Differences
1. **ResourceLocation Constructors**:
   - **MC 1.18.2 / 1.19.2 / 1.20.1**: `new ResourceLocation(namespace, path)`.
   - **MC 1.21.1+**: `new ResourceLocation(...)` removed; replaced by `ResourceLocation.fromNamespaceAndPath(namespace, path)` and `ResourceLocation.tryParse(...)`.
2. **Text Components**:
   - **MC 1.18.2**: Uses `new TextComponent(...)` and `new TranslatableComponent(...)`.
   - **MC 1.19.2+**: Uses `Component.literal(...)` and `Component.translatable(...)`.

### D. Mod Metadata Files
- **Fabric (`1.20.1`, `1.21.1`, `1.21.11`)**: `src/main/resources/fabric.mod.json`
- **Forge (`1.18.2`, `1.19.2`, `1.20.1`)**: `src/main/resources/META-INF/mods.toml`
- **NeoForge (`1.21.1`, `1.21.4`, `1.21.11`)**: `src/main/resources/META-INF/neoforge.mods.toml`
