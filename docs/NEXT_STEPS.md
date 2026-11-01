# SCULPTURA — Next Steps

## Immediate Next Task (Phase 8 — Native Loader Render & Event Hook Wiring)
1. **Bind Native Client World Render Events per Target**:
   - **Fabric (`1.20.1`, `1.21.1`, `1.21.11`)**: Register `WorldRenderEvents.AFTER_TRANSLUCENT` in `SculpturaFabric*Mod` to stream `VertexBatchPreviewBackend.CompiledGpuBuffer` quads and `SelectionController.SelectionVisualDescriptor` lines into `VertexConsumer` / `BufferBuilder`.
   - **Forge (`1.18.2`, `1.19.2`, `1.20.1`)**: Subscribe to `RenderLevelStageEvent` (`Stage.AFTER_TRANSLUCENT_BLOCKS`) on `MinecraftForge.EVENT_BUS`.
   - **NeoForge (`1.21.1`, `1.21.4`, `1.21.11`)**: Subscribe to `RenderLevelStageEvent` (`Stage.AFTER_TRANSLUCENT_BLOCKS`) on `NeoForge.EVENT_BUS`.

2. **Bind Native KeyMappings & Screen Overlay**:
   - Register keybindings for toggling the SCULPTURA dark-themed editor (`K`), committing previewed operations (`Enter`), cancelling (`Escape`), Undo (`Ctrl+Z`), and Redo (`Ctrl+Y`).
   - Intercept mouse wheel scroll events when a selection face or brush radius adjustment modifier is active and forward to `SelectionController.onMouseWheelScroll(delta)`.

3. **Bind Native Server Tick & Networking Channels**:
   - Hook `ServerTickEvents.END_SERVER_TICK` (Fabric) and `ServerTickEvent` (Forge/NeoForge) to invoke `SculpturaRuntime.tickExecutor().tickServer(worldAdapter)`.
   - Register the custom packet payload channel (`sculptura:main`) delegating `byte[]` payloads to `SculpturaPacketCodec` and `ServerSecurityValidator`.
