package dev.sculptura.core.geometry;

import dev.sculptura.core.math.BlockPos3i;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spatial sub-chunk (16x16x16) partitioned voxel container supporting dirty-region tracking,
 * incremental preview updates, and fast bounding-box calculation.
 */
public final class VoxelBuffer {
    private final Map<BlockPos3i, BlockStateRef> blocks = new LinkedHashMap<>();
    private final Map<Long, Set<BlockPos3i>> subChunkIndex = new HashMap<>();
    private final Set<Long> dirtySubChunks = new HashSet<>();
    private BoundingBox3i cachedBounds;
    private boolean boundsDirty = false;

    public void setBlock(BlockPos3i pos, BlockStateRef state) {
        if (pos == null || state == null) {
            return;
        }
        BlockStateRef previous = blocks.put(pos, state);
        if (!state.equals(previous)) {
            long subChunkKey = pos.subChunkKey();
            subChunkIndex.computeIfAbsent(subChunkKey, k -> new HashSet<>()).add(pos);
            dirtySubChunks.add(subChunkKey);
            if (cachedBounds == null) {
                cachedBounds = BoundingBox3i.ofSingleBlock(pos);
            } else if (!boundsDirty) {
                cachedBounds = cachedBounds.encompass(pos);
            }
        }
    }

    public void setBlock(int x, int y, int z, BlockStateRef state) {
        setBlock(new BlockPos3i(x, y, z), state);
    }

    public boolean removeBlock(BlockPos3i pos) {
        BlockStateRef removed = blocks.remove(pos);
        if (removed != null) {
            long key = pos.subChunkKey();
            Set<BlockPos3i> subSet = subChunkIndex.get(key);
            if (subSet != null) {
                subSet.remove(pos);
                if (subSet.isEmpty()) {
                    subChunkIndex.remove(key);
                }
            }
            dirtySubChunks.add(key);
            boundsDirty = true;
            return true;
        }
        return false;
    }

    public BlockStateRef getBlock(BlockPos3i pos) {
        return blocks.get(pos);
    }

    public boolean contains(BlockPos3i pos) {
        return blocks.containsKey(pos);
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public Map<BlockPos3i, BlockStateRef> entries() {
        return Collections.unmodifiableMap(blocks);
    }

    public Set<Long> activeSubChunkKeys() {
        return Collections.unmodifiableSet(subChunkIndex.keySet());
    }

    public Collection<BlockPos3i> positionsInSubChunk(long subChunkKey) {
        Set<BlockPos3i> set = subChunkIndex.get(subChunkKey);
        return set == null ? ListEmpty.INSTANCE : Collections.unmodifiableSet(set);
    }

    public Set<Long> consumeDirtySubChunks() {
        Set<Long> copy = new HashSet<>(dirtySubChunks);
        dirtySubChunks.clear();
        return copy;
    }

    public Set<Long> peekDirtySubChunks() {
        return Collections.unmodifiableSet(dirtySubChunks);
    }

    public Optional<BoundingBox3i> bounds() {
        if (blocks.isEmpty()) {
            return Optional.empty();
        }
        if (boundsDirty || cachedBounds == null) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos3i p : blocks.keySet()) {
                if (p.x() < minX) minX = p.x();
                if (p.y() < minY) minY = p.y();
                if (p.z() < minZ) minZ = p.z();
                if (p.x() > maxX) maxX = p.x();
                if (p.y() > maxY) maxY = p.y();
                if (p.z() > maxZ) maxZ = p.z();
            }
            cachedBounds = new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
            boundsDirty = false;
        }
        return Optional.of(cachedBounds);
    }

    public void mergeFrom(VoxelBuffer other) {
        for (Map.Entry<BlockPos3i, BlockStateRef> entry : other.blocks.entrySet()) {
            setBlock(entry.getKey(), entry.getValue());
        }
    }

    public VoxelBuffer copy() {
        VoxelBuffer copy = new VoxelBuffer();
        copy.mergeFrom(this);
        copy.consumeDirtySubChunks();
        return copy;
    }

    public void clear() {
        dirtySubChunks.addAll(subChunkIndex.keySet());
        blocks.clear();
        subChunkIndex.clear();
        cachedBounds = null;
        boundsDirty = false;
    }

    public long estimatedMemoryBytes() {
        return (long) blocks.size() * 64L + (long) subChunkIndex.size() * 48L;
    }

    private static final class ListEmpty {
        private static final Set<BlockPos3i> INSTANCE = Collections.emptySet();
    }
}
