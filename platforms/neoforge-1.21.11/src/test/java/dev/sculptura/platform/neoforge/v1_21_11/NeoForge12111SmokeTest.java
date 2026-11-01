package dev.sculptura.platform.neoforge.v1_21_11;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class NeoForge12111SmokeTest {
    @Test
    void testEntrypointAndMetadata() throws Exception {
        SculpturaNeoForge12111Mod mod = new SculpturaNeoForge12111Mod();
        assertEquals("1.21.11", mod.runtime().minecraftVersion());
        assertEquals("NeoForge", mod.runtime().loaderName());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(in);
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("21.11.48"));
        }
    }
}
