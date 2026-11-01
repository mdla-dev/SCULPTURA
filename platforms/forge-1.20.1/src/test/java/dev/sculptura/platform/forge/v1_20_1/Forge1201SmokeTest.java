package dev.sculptura.platform.forge.v1_20_1;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class Forge1201SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaForge1201Mod mod = new SculpturaForge1201Mod();
        assertEquals("1.20.1", mod.runtime().minecraftVersion());
        assertEquals("Forge", mod.runtime().loaderName());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(in);
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("[1.20.1,1.21)"));
        }
    }
}
