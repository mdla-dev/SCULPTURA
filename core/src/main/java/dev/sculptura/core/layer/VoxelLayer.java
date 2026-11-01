package dev.sculptura.core.layer;

import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.geometry.VoxelBuffer;
import dev.sculptura.core.math.BlockPos3i;

import java.util.Objects;

/**
 * Represents a visual editing layer in SCULPTURA (Section 5.H).
 * Explicitly tracks whether edits are uncommitted preview voxels or have been committed to the world.
 * Committed world edits can only be restored if their pre-commit HistoryEntry is preserved.
 */
public final class VoxelLayer {
    private final String id;
    private String name;
    private String groupName;
    private boolean visible = true;
    private boolean locked = false;
    private float previewOpacity = 0.72f;
    private boolean committedToWorld = false;
    private final VoxelBuffer voxelBuffer;

    public VoxelLayer(String id, String name, String groupName) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.groupName = groupName == null ? "Default" : groupName;
        this.voxelBuffer = new VoxelBuffer();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Layer name cannot be blank");
        }
        this.name = name.trim();
    }

    public String groupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = (groupName == null || groupName.isBlank()) ? "Default" : groupName.trim();
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean locked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public float previewOpacity() {
        return previewOpacity;
    }

    public void setPreviewOpacity(float previewOpacity) {
        this.previewOpacity = Math.max(0.05f, Math.min(1.0f, previewOpacity));
    }

    public boolean committedToWorld() {
        return committedToWorld;
    }

    public void markCommittedToWorld(boolean committed) {
        this.committedToWorld = committed;
    }

    public VoxelBuffer voxelBuffer() {
        return voxelBuffer;
    }

    public boolean putBlock(BlockPos3i pos, BlockStateRef state) {
        if (locked) {
            return false;
        }
        voxelBuffer.setBlock(pos, state);
        return true;
    }

    public VoxelLayer duplicate(String newId, String newName) {
        VoxelLayer copy = new VoxelLayer(newId, newName, this.groupName);
        copy.setVisible(this.visible);
        copy.setLocked(false);
        copy.setPreviewOpacity(this.previewOpacity);
        copy.voxelBuffer().mergeFrom(this.voxelBuffer);
        return copy;
    }
}
