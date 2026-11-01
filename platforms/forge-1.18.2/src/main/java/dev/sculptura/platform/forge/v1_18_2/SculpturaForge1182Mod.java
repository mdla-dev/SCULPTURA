package dev.sculptura.platform.forge.v1_18_2;

import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;

/**
 * SCULPTURA Entrypoint for Minecraft Forge — Minecraft 1.18.2 (Forge 40.3.12, Java 17).
 */
public final class SculpturaForge1182Mod {
    public static final String MOD_ID = SculpturaRuntime.MOD_ID;
    public static final String TARGET_MINECRAFT = "1.18.2";
    private final SculpturaRuntime runtime;

    public SculpturaForge1182Mod() {
        this.runtime = new SculpturaRuntime("Forge", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
    }

    public SculpturaRuntime runtime() {
        return runtime;
    }
}
