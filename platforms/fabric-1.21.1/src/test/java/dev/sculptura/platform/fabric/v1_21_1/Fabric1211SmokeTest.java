package dev.sculptura.platform.fabric.v1_21_1;

import dev.sculptura.common.platform.SculpturaRuntime;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Fabric1211SmokeTest {

    @Test
    void testFabric1211EntrypointAndMetadataInitialization() throws Exception {
        SculpturaFabric1211Mod mod = new SculpturaFabric1211Mod();
        mod.onInitialize();
        mod.onInitializeClient();

        SculpturaRuntime runtime = SculpturaFabric1211Mod.getRuntime();
        assertNotNull(runtime);
        assertEquals("Fabric", runtime.loaderName());
        assertEquals("1.21.1", runtime.minecraftVersion());

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json must be packaged in resources");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"id\": \"sculptura\""));
            assertTrue(json.contains("SculpturaFabric1211Mod"));
        }
    }
}
