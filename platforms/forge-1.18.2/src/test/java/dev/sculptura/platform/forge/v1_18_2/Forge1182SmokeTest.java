package dev.sculptura.platform.forge.v1_18_2;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class Forge1182SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaForge1182Mod mod = new SculpturaForge1182Mod();
        assertEquals("1.18.2", mod.runtime().minecraftVersion());
        assertEquals("Forge", mod.runtime().loaderName());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(in);
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("[1.18.2,1.19)"));
        }
    }
}
