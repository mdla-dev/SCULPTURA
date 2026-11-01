package dev.sculptura.platform.neoforge.v1_21_1;

import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;

/**
 * SCULPTURA Entrypoint for NeoForge — Minecraft 1.21.1 (NeoForge 21.1.265, Java 21).
 */
public final class SculpturaNeoForge1211Mod {
    public static final String MOD_ID = SculpturaRuntime.MOD_ID;
    public static final String TARGET_MINECRAFT = "1.21.1";
    private final SculpturaRuntime runtime;

    public SculpturaNeoForge1211Mod() {
        this.runtime = new SculpturaRuntime("NeoForge", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
    }

    public SculpturaRuntime runtime() {
        return runtime;
    }
}
