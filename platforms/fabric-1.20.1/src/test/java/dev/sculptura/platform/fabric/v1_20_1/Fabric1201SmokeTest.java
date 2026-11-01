package dev.sculptura.platform.fabric.v1_20_1;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class Fabric1201SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaFabric1201Mod mod = new SculpturaFabric1201Mod();
        mod.onInitialize();
        mod.onInitializeClient();
        assertEquals("1.20.1", SculpturaFabric1201Mod.getRuntime().minecraftVersion());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("~1.20.1"));
        }
    }
}
