package dev.sculptura.platform.forge.v1_19_2;

import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;

/**
 * SCULPTURA Entrypoint for Minecraft Forge — Minecraft 1.19.2 (Forge 43.5.2, Java 17).
 */
public final class SculpturaForge1192Mod {
    public static final String MOD_ID = SculpturaRuntime.MOD_ID;
    public static final String TARGET_MINECRAFT = "1.19.2";
    private final SculpturaRuntime runtime;

    public SculpturaForge1192Mod() {
        this.runtime = new SculpturaRuntime("Forge", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
    }

    public SculpturaRuntime runtime() {
        return runtime;
    }
}
