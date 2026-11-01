package dev.sculptura.core.project;

import dev.sculptura.core.brush.BrushShape;
import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.brush.BrushType;
import dev.sculptura.core.brush.FalloffCurve;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.layer.VoxelLayer;
import dev.sculptura.core.material.MaterialPaletteRegistry;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.material.WeightedPalettePattern;
import dev.sculptura.core.math.BlockPos3i;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic versioned serializer & schema migrator (v1 -> v2) for SCULPTURA projects and blueprints.
 * Supports file save/load and string export/import without external library dependencies.
 */
public final class SculpturaProjectSerializer {
    private SculpturaProjectSerializer() {}

    public static String exportToString(SculpturaProject project) {
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            writeProject(project, bw);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error serializing project", e);
        }
        return sw.toString();
    }

    public static SculpturaProject importFromString(String serialized) {
        try (BufferedReader br = new BufferedReader(new StringReader(serialized))) {
            return readProject(br);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize SCULPTURA project", e);
        }
    }

    public static void saveToFile(SculpturaProject project, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeProject(project, writer);
        }
    }

    public static SculpturaProject loadFromFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readProject(reader);
        }
    }

    private static void writeProject(SculpturaProject p, BufferedWriter w) throws IOException {
        w.write("SCULPTURA_PROJECT\n");
        w.write("SCHEMA_VERSION=" + SculpturaProject.CURRENT_SCHEMA_VERSION + "\n");
        w.write("PROJECT_ID=" + escape(p.projectId()) + "\n");
        w.write("PROJECT_NAME=" + escape(p.name()) + "\n");
        BlockPos3i o = p.originAnchor();
        w.write("ORIGIN=" + o.x() + "," + o.y() + "," + o.z() + "\n");

        // Palettes
        for (MaterialPaletteRegistry.CustomPalette pal : p.savedPalettes()) {
            StringBuilder entriesSb = new StringBuilder();
            for (int i = 0; i < pal.entries().size(); i++) {
                if (i > 0) entriesSb.append(";");
                WeightedPalettePattern.WeightedEntry we = pal.entries().get(i);
                entriesSb.append(we.state().canonicalString()).append("@").append(we.weight());
            }
            w.write("PALETTE|" + escape(pal.id()) + "|" + escape(pal.name()) + "|" + entriesSb + "\n");
        }

        // Brushes
        for (BrushSettings b : p.savedBrushes()) {
            w.write("BRUSH|" + b.type().name()
                    + "|" + b.shape().name()
                    + "|" + b.radius()
                    + "|" + b.strength()
                    + "|" + b.falloff()
                    + "|" + b.falloffCurve().name()
                    + "|" + b.spacing()
                    + "|" + b.frequency()
                    + "|" + b.noiseAmplitude()
                    + "|" + b.targetHeight()
                    + "|" + b.randomSeed()
                    + "|" + b.raycastRange() + "\n");
        }

        // Saved Operations
        for (SculpturaProject.SavedOperationPreset op : p.savedOperations()) {
            w.write("OPERATION|" + escape(op.id()) + "|" + escape(op.operationType()) + "|" + escape(op.parametersSpec()) + "\n");
        }

        // Layers & Voxels
        for (VoxelLayer layer : p.layers()) {
            w.write("LAYER|" + escape(layer.id())
                    + "|" + escape(layer.name())
                    + "|" + escape(layer.groupName())
                    + "|" + layer.visible()
                    + "|" + layer.locked()
                    + "|" + layer.previewOpacity() + "\n");
            for (Map.Entry<BlockPos3i, BlockStateRef> ve : layer.voxelBuffer().entries().entrySet()) {
                BlockPos3i pos = ve.getKey();
                w.write("VOXEL|" + escape(layer.id()) + "|" + pos.x() + "," + pos.y() + "," + pos.z() + "|" + ve.getValue().canonicalString() + "\n");
            }
        }
        w.write("END_SCULPTURA_PROJECT\n");
    }

    private static SculpturaProject readProject(BufferedReader r) throws IOException {
        String header = r.readLine();
        if (header == null || !header.trim().equals("SCULPTURA_PROJECT")) {
            throw new IllegalArgumentException("Invalid SCULPTURA project header");
        }

        int schemaVersion = 1;
        String projectId = "migrated_project";
        String projectName = "Imported Project";
        BlockPos3i origin = BlockPos3i.ZERO;
        List<MaterialPaletteRegistry.CustomPalette> palettes = new ArrayList<>();
        List<BrushSettings> brushes = new ArrayList<>();
        List<SculpturaProject.SavedOperationPreset> operations = new ArrayList<>();
        List<VoxelLayer> layers = new ArrayList<>();
        Map<String, VoxelLayer> layerMap = new HashMap<>();

        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.equals("END_SCULPTURA_PROJECT")) {
                continue;
            }
            if (line.startsWith("SCHEMA_VERSION=")) {
                schemaVersion = Integer.parseInt(line.substring("SCHEMA_VERSION=".length()).trim());
            } else if (line.startsWith("PROJECT_ID=")) {
                projectId = unescape(line.substring("PROJECT_ID=".length()));
            } else if (line.startsWith("PROJECT_NAME=")) {
                projectName = unescape(line.substring("PROJECT_NAME=".length()));
            } else if (line.startsWith("ORIGIN=")) {
                origin = parsePos(line.substring("ORIGIN=".length()));
            } else if (line.startsWith("PALETTE|")) {
                String[] parts = line.split("\\|", -1);
                String id = unescape(parts[1]);
                String name = unescape(parts[2]);
                List<WeightedPalettePattern.WeightedEntry> entries = new ArrayList<>();
                if (!parts[3].isEmpty()) {
                    for (String item : parts[3].split(";")) {
                        int at = item.lastIndexOf('@');
                        BlockStateRef state = BlockStateRef.parse(item.substring(0, at));
                        double weight = Double.parseDouble(item.substring(at + 1));
                        entries.add(new WeightedPalettePattern.WeightedEntry(state, weight));
                    }
                }
                palettes.add(new MaterialPaletteRegistry.CustomPalette(id, name, entries));
            } else if (line.startsWith("BRUSH|")) {
                String[] parts = line.split("\\|", -1);
                BrushType type = BrushType.valueOf(parts[1]);
                BrushShape shape = BrushShape.valueOf(parts[2]);
                int radius = Integer.parseInt(parts[3]);
                double strength = Double.parseDouble(parts[4]);
                double falloff = Double.parseDouble(parts[5]);
                FalloffCurve curve = FalloffCurve.valueOf(parts[6]);
                double spacing = Double.parseDouble(parts[7]);
                double freq = Double.parseDouble(parts[8]);
                double noiseAmp = Double.parseDouble(parts[9]);
                int targetH = Integer.parseInt(parts[10]);
                long seed = Long.parseLong(parts[11]);
                double range = parts.length > 12 ? Double.parseDouble(parts[12]) : BrushSettings.DEFAULT_RAYCAST_RANGE;
                brushes.add(new BrushSettings(
                        type, shape, radius, strength, falloff, curve, spacing, freq, noiseAmp,
                        targetH, MaterialPattern.single(BlockStateRef.STONE), null, seed, range
                ));
            } else if (line.startsWith("OPERATION|")) {
                String[] parts = line.split("\\|", -1);
                operations.add(new SculpturaProject.SavedOperationPreset(
                        unescape(parts[1]), unescape(parts[2]), unescape(parts[3])
                ));
            } else if (line.startsWith("LAYER|")) {
                String[] parts = line.split("\\|", -1);
                String id = unescape(parts[1]);
                String name = unescape(parts[2]);
                // Schema v1 migration: v1 lacked groupName and previewOpacity
                String group = schemaVersion == 1 ? "Migrated_v1" : unescape(parts[3]);
                boolean visible = schemaVersion == 1 ? Boolean.parseBoolean(parts[3]) : Boolean.parseBoolean(parts[4]);
                boolean locked = schemaVersion == 1 ? Boolean.parseBoolean(parts[4]) : Boolean.parseBoolean(parts[5]);
                float opacity = schemaVersion == 1 ? 0.72f : Float.parseFloat(parts[6]);

                VoxelLayer layer = new VoxelLayer(id, name, group);
                layer.setVisible(visible);
                layer.setLocked(locked);
                layer.setPreviewOpacity(opacity);
                layers.add(layer);
                layerMap.put(id, layer);
            } else if (line.startsWith("VOXEL|")) {
                String[] parts = line.split("\\|", -1);
                String layerId = unescape(parts[1]);
                BlockPos3i pos = parsePos(parts[2]);
                BlockStateRef state = BlockStateRef.parse(parts[3]);
                VoxelLayer layer = layerMap.get(layerId);
                if (layer != null) {
                    layer.voxelBuffer().setBlock(pos, state);
                }
            }
        }

        // Migrate schema version 1 -> CURRENT_SCHEMA_VERSION (2)
        int finalSchema = Math.max(schemaVersion, SculpturaProject.CURRENT_SCHEMA_VERSION);
        return new SculpturaProject(projectId, projectName, finalSchema, origin, layers, palettes, brushes, operations);
    }

    private static BlockPos3i parsePos(String csv) {
        String[] c = csv.split(",");
        return new BlockPos3i(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        return s == null ? "" : s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
    }
}
