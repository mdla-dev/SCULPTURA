package dev.sculptura.core.layer;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.math.BlockPos3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages ordered voxel layers (Section 5.H):
 * Create, Rename, Delete, Duplicate, Hide, Show, Lock, Reorder, Group, and Preview Opacity composition.
 */
public final class LayerManager {
    private final List<VoxelLayer> layers = new ArrayList<>();
    private String activeLayerId;

    public LayerManager() {
        VoxelLayer base = createLayer("Base Layer", "Main");
        this.activeLayerId = base.id();
    }

    public VoxelLayer createLayer(String name, String groupName) {
        String id = "layer_" + (layers.size() + 1) + "_" + UUID.randomUUID().toString().substring(0, 6);
        VoxelLayer layer = new VoxelLayer(id, name, groupName);
        layers.add(layer);
        activeLayerId = id;
        return layer;
    }

    public void addLoadedLayer(VoxelLayer layer) {
        layers.add(layer);
        if (activeLayerId == null) {
            activeLayerId = layer.id();
        }
    }

    public Optional<VoxelLayer> findLayer(String id) {
        return layers.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public Optional<VoxelLayer> activeLayer() {
        return findLayer(activeLayerId);
    }

    public void setActiveLayerId(String id) {
        if (findLayer(id).isPresent()) {
            this.activeLayerId = id;
        }
    }

    public boolean renameLayer(String id, String newName) {
        Optional<VoxelLayer> layer = findLayer(id);
        layer.ifPresent(l -> l.setName(newName));
        return layer.isPresent();
    }

    public boolean deleteLayer(String id) {
        if (layers.size() <= 1) {
            return false; // Always preserve at least one layer
        }
        Optional<VoxelLayer> target = findLayer(id);
        if (target.isEmpty() || target.get().locked()) {
            return false;
        }
        layers.remove(target.get());
        if (id.equals(activeLayerId)) {
            activeLayerId = layers.get(layers.size() - 1).id();
        }
        return true;
    }

    public Optional<VoxelLayer> duplicateLayer(String id) {
        Optional<VoxelLayer> src = findLayer(id);
        if (src.isEmpty()) {
            return Optional.empty();
        }
        String newId = "layer_copy_" + UUID.randomUUID().toString().substring(0, 6);
        VoxelLayer copy = src.get().duplicate(newId, src.get().name() + " (Copy)");
        int idx = layers.indexOf(src.get());
        layers.add(idx + 1, copy);
        activeLayerId = copy.id();
        return Optional.of(copy);
    }

    public boolean reorderLayer(String id, int newIndex) {
        Optional<VoxelLayer> target = findLayer(id);
        if (target.isEmpty()) {
            return false;
        }
        int clamped = Math.max(0, Math.min(layers.size() - 1, newIndex));
        layers.remove(target.get());
        layers.add(clamped, target.get());
        return true;
    }

    public void setGroupVisibility(String groupName, boolean visible) {
        for (VoxelLayer layer : layers) {
            if (layer.groupName().equalsIgnoreCase(groupName)) {
                layer.setVisible(visible);
            }
        }
    }

    public List<VoxelLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    public void clearAndReset(List<VoxelLayer> replacement) {
        layers.clear();
        if (replacement == null || replacement.isEmpty()) {
            VoxelLayer def = createLayer("Base Layer", "Main");
            activeLayerId = def.id();
        } else {
            layers.addAll(replacement);
            activeLayerId = layers.get(0).id();
        }
    }

    /**
     * Composites all visible layers in bottom-to-top order into a unified holographic preview buffer.
     */
    public VoxelBuffer composeVisiblePreview() {
        VoxelBuffer composite = new VoxelBuffer();
        for (VoxelLayer layer : layers) {
            if (!layer.visible()) {
                continue;
            }
            for (Map.Entry<BlockPos3i, BlockStateRef> entry : layer.voxelBuffer().entries().entrySet()) {
                composite.setBlock(entry.getKey(), entry.getValue());
            }
        }
        return composite;
    }
}
