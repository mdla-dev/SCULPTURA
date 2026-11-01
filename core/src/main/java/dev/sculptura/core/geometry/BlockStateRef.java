package dev.sculptura.core.geometry;

import dev.sculptura.core.math.Axis3D;
import dev.sculptura.core.math.MirrorPlane;
import dev.sculptura.core.math.Rotation90;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Platform-independent canonical representation of a Minecraft block state
 * (e.g. "minecraft:stone" or "minecraft:oak_stairs[facing=north,half=bottom]").
 */
public final class BlockStateRef {
    public static final BlockStateRef AIR = BlockStateRef.parse("minecraft:air");
    public static final BlockStateRef STONE = BlockStateRef.parse("minecraft:stone");
    public static final BlockStateRef COBBLESTONE = BlockStateRef.parse("minecraft:cobblestone");
    public static final BlockStateRef DEEPSLATE = BlockStateRef.parse("minecraft:deepslate");
    public static final BlockStateRef GRASS_BLOCK = BlockStateRef.parse("minecraft:grass_block");
    public static final BlockStateRef DIRT = BlockStateRef.parse("minecraft:dirt");
    public static final BlockStateRef SNOW_BLOCK = BlockStateRef.parse("minecraft:snow_block");

    private final String namespace;
    private final String path;
    private final Map<String, String> properties;
    private final String canonicalString;

    public BlockStateRef(String namespace, String path, Map<String, String> properties) {
        this.namespace = Objects.requireNonNull(namespace, "namespace").trim().toLowerCase();
        this.path = Objects.requireNonNull(path, "path").trim().toLowerCase();
        if (this.namespace.isEmpty() || this.path.isEmpty()) {
            throw new IllegalArgumentException("BlockStateRef namespace and path must be non-empty");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        if (properties != null) {
            for (Map.Entry<String, String> e : properties.entrySet()) {
                sorted.put(e.getKey().trim().toLowerCase(), e.getValue().trim().toLowerCase());
            }
        }
        this.properties = Collections.unmodifiableMap(sorted);
        this.canonicalString = buildCanonical(this.namespace, this.path, this.properties);
    }

    private static String buildCanonical(String ns, String path, Map<String, String> props) {
        if (props.isEmpty()) {
            return ns + ":" + path;
        }
        StringBuilder sb = new StringBuilder(ns).append(':').append(path).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    public static BlockStateRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("BlockStateRef string cannot be null or blank");
        }
        String trimmed = raw.trim();
        int bracketStart = trimmed.indexOf('[');
        String idPart = bracketStart >= 0 ? trimmed.substring(0, bracketStart) : trimmed;
        int colon = idPart.indexOf(':');
        String ns = colon >= 0 ? idPart.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? idPart.substring(colon + 1) : idPart;

        Map<String, String> props = new TreeMap<>();
        if (bracketStart >= 0) {
            int bracketEnd = trimmed.lastIndexOf(']');
            if (bracketEnd <= bracketStart) {
                throw new IllegalArgumentException("Invalid block state property syntax: " + raw);
            }
            String propBody = trimmed.substring(bracketStart + 1, bracketEnd).trim();
            if (!propBody.isEmpty()) {
                String[] pairs = propBody.split(",");
                for (String pair : pairs) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0 || eq == pair.length() - 1) {
                        throw new IllegalArgumentException("Invalid property key=value in: " + raw);
                    }
                    props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return new BlockStateRef(ns, path, props);
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public String blockId() {
        return namespace + ":" + path;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public String canonicalString() {
        return canonicalString;
    }

    public boolean isAir() {
        return "minecraft".equals(namespace)
                && ("air".equals(path) || "cave_air".equals(path) || "void_air".equals(path));
    }

    public BlockStateRef withProperty(String key, String value) {
        TreeMap<String, String> updated = new TreeMap<>(properties);
        updated.put(key.trim().toLowerCase(), value.trim().toLowerCase());
        return new BlockStateRef(namespace, path, updated);
    }

    public BlockStateRef rotate(Rotation90 rotation, Axis3D axis) {
        if (rotation == Rotation90.NONE || properties.isEmpty()) {
            return this;
        }
        TreeMap<String, String> updated = new TreeMap<>(properties);
        if (axis == Axis3D.Y && updated.containsKey("facing")) {
            updated.put("facing", rotation.rotateHorizontalFacing(updated.get("facing")));
        }
        if (axis == Axis3D.Y && updated.containsKey("axis")
                && (rotation == Rotation90.CLOCKWISE_90 || rotation == Rotation90.COUNTERCLOCKWISE_90)) {
            String curAxis = updated.get("axis");
            if ("x".equals(curAxis)) {
                updated.put("axis", "z");
            } else if ("z".equals(curAxis)) {
                updated.put("axis", "x");
            }
        }
        return new BlockStateRef(namespace, path, updated);
    }

    public BlockStateRef mirror(MirrorPlane plane) {
        if (plane == MirrorPlane.NONE || properties.isEmpty()) {
            return this;
        }
        TreeMap<String, String> updated = new TreeMap<>(properties);
        if (updated.containsKey("facing")) {
            updated.put("facing", plane.mirrorFacing(updated.get("facing")));
        }
        if (plane == MirrorPlane.XZ && updated.containsKey("half")) {
            String half = updated.get("half");
            if ("top".equals(half)) {
                updated.put("half", "bottom");
            } else if ("bottom".equals(half)) {
                updated.put("half", "top");
            }
        }
        return new BlockStateRef(namespace, path, updated);
    }

    /**
     * Deterministic ARGB preview color derived from block identifier for holographic ghost rendering.
     */
    public int previewColorArgb(float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0f)));
        int rgb = switch (path) {
            case "stone", "cobblestone", "andesite", "stone_bricks" -> 0x8F9396;
            case "deepslate", "cobbled_deepslate", "deepslate_bricks" -> 0x4B4D52;
            case "grass_block", "moss_block", "oak_leaves" -> 0x5E9D34;
            case "dirt", "coarse_dirt", "rooted_dirt" -> 0x866043;
            case "sand", "sandstone" -> 0xDBD3A0;
            case "snow_block", "white_concrete", "quartz_block", "calcite" -> 0xF0F4F5;
            case "oak_planks", "spruce_planks", "oak_stairs" -> 0xA2824E;
            case "water" -> 0x3F76E4;
            case "lava" -> 0xD45A12;
            default -> {
                int h = canonicalString.hashCode();
                int r = 90 + (Math.abs(h & 0xFF) % 130);
                int g = 90 + (Math.abs((h >> 8) & 0xFF) % 130);
                int b = 90 + (Math.abs((h >> 16) & 0xFF) % 130);
                yield (r << 16) | (g << 8) | b;
            }
        };
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockStateRef that)) return false;
        return canonicalString.equals(that.canonicalString);
    }

    @Override
    public int hashCode() {
        return canonicalString.hashCode();
    }

    @Override
    public String toString() {
        return canonicalString;
    }
}
