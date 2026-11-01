package dev.sculptura.platform.fabric.v1_21_11;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class Fabric12111SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaFabric12111Mod mod = new SculpturaFabric12111Mod();
        mod.onInitialize();
        mod.onInitializeClient();
        assertEquals("1.21.11", SculpturaFabric12111Mod.getRuntime().minecraftVersion());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("~1.21.11"));
        }
    }
}
