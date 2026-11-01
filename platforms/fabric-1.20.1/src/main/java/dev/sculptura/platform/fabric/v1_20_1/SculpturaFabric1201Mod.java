package dev.sculptura.platform.fabric.v1_20_1;

import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * SCULPTURA Entrypoint for Fabric — Minecraft 1.20.1 (Java 17).
 * Implements real net.fabricmc.api.ModInitializer and ClientModInitializer from Fabric Loader 0.20.1.
 */
public final class SculpturaFabric1201Mod implements ModInitializer, ClientModInitializer {
    public static final String TARGET_MINECRAFT = "1.20.1";
    private static SculpturaRuntime runtime;

    @Override
    public void onInitialize() {
        if (runtime == null) {
            runtime = new SculpturaRuntime("Fabric", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
        }
    }

    @Override
    public void onInitializeClient() {
        onInitialize();
    }

    public static SculpturaRuntime getRuntime() {
        if (runtime == null) {
            runtime = new SculpturaRuntime("Fabric", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
        }
        return runtime;
    }
}
