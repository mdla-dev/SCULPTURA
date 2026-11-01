package dev.sculptura.platform.forge.v1_19_2;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class Forge1192SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaForge1192Mod mod = new SculpturaForge1192Mod();
        assertEquals("1.19.2", mod.runtime().minecraftVersion());
        assertEquals("Forge", mod.runtime().loaderName());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(in);
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("[1.19.2,1.20)"));
        }
    }
}
