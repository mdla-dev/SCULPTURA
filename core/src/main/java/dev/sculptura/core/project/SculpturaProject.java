package dev.sculptura.core.project;

import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.layer.VoxelLayer;
import dev.sculptura.core.material.MaterialPaletteRegistry;
import dev.sculptura.core.math.BlockPos3i;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Versioned SCULPTURA Project & Blueprint container (Section 5.J) holding saved layers,
 * custom palettes, brush presets, and recorded operation descriptors.
 */
public final class SculpturaProject {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public record SavedOperationPreset(String id, String operationType, String parametersSpec) {}

    private final String projectId;
    private String name;
    private int schemaVersion;
    private BlockPos3i originAnchor;
    private final List<VoxelLayer> layers;
    private final List<MaterialPaletteRegistry.CustomPalette> savedPalettes;
    private final List<BrushSettings> savedBrushes;
    private final List<SavedOperationPreset> savedOperations;

    public SculpturaProject(
            String projectId,
            String name,
            int schemaVersion,
            BlockPos3i originAnchor,
            List<VoxelLayer> layers,
            List<MaterialPaletteRegistry.CustomPalette> savedPalettes,
            List<BrushSettings> savedBrushes,
            List<SavedOperationPreset> savedOperations
    ) {
        this.projectId = projectId == null ? UUID.randomUUID().toString() : projectId;
        this.name = (name == null || name.isBlank()) ? "Untitled Project" : name.trim();
        this.schemaVersion = schemaVersion;
        this.originAnchor = originAnchor == null ? BlockPos3i.ZERO : originAnchor;
        this.layers = new ArrayList<>(layers == null ? List.of() : layers);
        this.savedPalettes = new ArrayList<>(savedPalettes == null ? List.of() : savedPalettes);
        this.savedBrushes = new ArrayList<>(savedBrushes == null ? List.of() : savedBrushes);
        this.savedOperations = new ArrayList<>(savedOperations == null ? List.of() : savedOperations);
    }

    public String projectId() {
        return projectId;
    }

    public String name() {
        return name;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be blank");
        }
        this.name = newName.trim();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public BlockPos3i originAnchor() {
        return originAnchor;
    }

    public void setOriginAnchor(BlockPos3i originAnchor) {
        this.originAnchor = originAnchor;
    }

    public List<VoxelLayer> layers() {
        return layers;
    }

    public List<MaterialPaletteRegistry.CustomPalette> savedPalettes() {
        return savedPalettes;
    }

    public List<BrushSettings> savedBrushes() {
        return savedBrushes;
    }

    public List<SavedOperationPreset> savedOperations() {
        return savedOperations;
    }

    public SculpturaProject duplicate(String newName) {
        List<VoxelLayer> copiedLayers = layers.stream()
                .map(l -> l.duplicate(l.id() + "_dup", l.name()))
                .toList();
        return new SculpturaProject(
                UUID.randomUUID().toString(),
                newName,
                CURRENT_SCHEMA_VERSION,
                originAnchor,
                copiedLayers,
                savedPalettes,
                savedBrushes,
                savedOperations
        );
    }
}
