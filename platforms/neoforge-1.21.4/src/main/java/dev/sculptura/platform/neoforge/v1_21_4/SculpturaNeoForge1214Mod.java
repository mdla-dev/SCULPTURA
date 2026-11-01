package dev.sculptura.platform.neoforge.v1_21_4;

import dev.sculptura.common.platform.SculpturaRuntime;
import dev.sculptura.common.preview.VertexBatchPreviewBackend;

/**
 * SCULPTURA Entrypoint for NeoForge — Minecraft 1.21.4 (NeoForge 21.4.161, Java 21).
 */
public final class SculpturaNeoForge1214Mod {
    public static final String MOD_ID = SculpturaRuntime.MOD_ID;
    public static final String TARGET_MINECRAFT = "1.21.4";
    private final SculpturaRuntime runtime;

    public SculpturaNeoForge1214Mod() {
        this.runtime = new SculpturaRuntime("NeoForge", TARGET_MINECRAFT, new VertexBatchPreviewBackend());
    }

    public SculpturaRuntime runtime() {
        return runtime;
    }
}
