package dev.sculptura.core.material;

import dev.sculptura.core.geometry.BlockStateRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Smart Material Palette Registry managing block search, favorites, recent blocks,
 * tag-based selection (#minecraft:stone_bricks, #minecraft:logs, etc.), and custom named palettes.
 */
public final class MaterialPaletteRegistry {
    public record BlockCatalogEntry(BlockStateRef state, String displayName, Set<String> tags) {}

    public record CustomPalette(String id, String name, List<WeightedPalettePattern.WeightedEntry> entries) {}

    private static final int MAX_RECENT_BLOCKS = 24;

    private final Map<String, BlockCatalogEntry> catalog = new LinkedHashMap<>();
    private final Map<String, Set<BlockStateRef>> tagIndex = new LinkedHashMap<>();
    private final Set<BlockStateRef> favorites = new LinkedHashSet<>();
    private final Deque<BlockStateRef> recentBlocks = new ArrayDeque<>();
    private final Map<String, CustomPalette> customPalettes = new LinkedHashMap<>();

    public MaterialPaletteRegistry() {
        registerDefaultCatalog();
        registerDefaultPalettes();
    }

    private void registerDefaultCatalog() {
        registerBlock("minecraft:stone", "Stone", Set.of("#minecraft:base_stone_overworld", "#minecraft:stone"));
        registerBlock("minecraft:cobblestone", "Cobblestone", Set.of("#minecraft:stone_crafting_materials", "#minecraft:stone"));
        registerBlock("minecraft:mossy_cobblestone", "Mossy Cobblestone", Set.of("#minecraft:stone", "#minecraft:mossy"));
        registerBlock("minecraft:stone_bricks", "Stone Bricks", Set.of("#minecraft:stone_bricks", "#minecraft:stone"));
        registerBlock("minecraft:mossy_stone_bricks", "Mossy Stone Bricks", Set.of("#minecraft:stone_bricks", "#minecraft:mossy"));
        registerBlock("minecraft:cracked_stone_bricks", "Cracked Stone Bricks", Set.of("#minecraft:stone_bricks", "#minecraft:stone"));
        registerBlock("minecraft:andesite", "Andesite", Set.of("#minecraft:base_stone_overworld", "#minecraft:stone"));
        registerBlock("minecraft:tuff", "Tuff", Set.of("#minecraft:base_stone_overworld", "#minecraft:stone"));
        registerBlock("minecraft:deepslate", "Deepslate", Set.of("#minecraft:base_stone_overworld", "#minecraft:deepslate"));
        registerBlock("minecraft:cobbled_deepslate", "Cobbled Deepslate", Set.of("#minecraft:deepslate"));
        registerBlock("minecraft:deepslate_bricks", "Deepslate Bricks", Set.of("#minecraft:deepslate"));
        registerBlock("minecraft:grass_block", "Grass Block", Set.of("#minecraft:dirt", "#minecraft:terrain"));
        registerBlock("minecraft:dirt", "Dirt", Set.of("#minecraft:dirt", "#minecraft:terrain"));
        registerBlock("minecraft:coarse_dirt", "Coarse Dirt", Set.of("#minecraft:dirt", "#minecraft:terrain"));
        registerBlock("minecraft:moss_block", "Moss Block", Set.of("#minecraft:dirt", "#minecraft:mossy", "#minecraft:flora"));
        registerBlock("minecraft:sand", "Sand", Set.of("#minecraft:sand", "#minecraft:terrain"));
        registerBlock("minecraft:sandstone", "Sandstone", Set.of("#minecraft:sand", "#minecraft:terrain"));
        registerBlock("minecraft:snow_block", "Snow Block", Set.of("#minecraft:snow", "#minecraft:terrain"));
        registerBlock("minecraft:calcite", "Calcite", Set.of("#minecraft:stone", "#minecraft:light"));
        registerBlock("minecraft:oak_log", "Oak Log", Set.of("#minecraft:logs", "#minecraft:oak_logs"));
        registerBlock("minecraft:spruce_log", "Spruce Log", Set.of("#minecraft:logs", "#minecraft:spruce_logs"));
        registerBlock("minecraft:oak_planks", "Oak Planks", Set.of("#minecraft:planks"));
        registerBlock("minecraft:spruce_planks", "Spruce Planks", Set.of("#minecraft:planks"));
        registerBlock("minecraft:oak_leaves", "Oak Leaves", Set.of("#minecraft:leaves", "#minecraft:flora"));
        registerBlock("minecraft:spruce_leaves", "Spruce Leaves", Set.of("#minecraft:leaves", "#minecraft:flora"));
        registerBlock("minecraft:short_grass", "Short Grass", Set.of("#minecraft:flora", "#minecraft:replaceable_plants"));
        registerBlock("minecraft:fern", "Fern", Set.of("#minecraft:flora", "#minecraft:replaceable_plants"));
    }

    private void registerDefaultPalettes() {
        saveCustomPalette(new CustomPalette(
                "weathered_ruins",
                "Weathered Stone Ruins",
                List.of(
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:stone_bricks"), 50.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:cracked_stone_bricks"), 25.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:mossy_stone_bricks"), 15.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:cobblestone"), 10.0)
                )
        ));
        saveCustomPalette(new CustomPalette(
                "alpine_cliff",
                "Alpine Cliff Stratification",
                List.of(
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:deepslate"), 30.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:tuff"), 25.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:stone"), 30.0),
                        new WeightedPalettePattern.WeightedEntry(BlockStateRef.parse("minecraft:andesite"), 15.0)
                )
        ));
    }

    public void registerBlock(String blockStateString, String displayName, Set<String> tags) {
        BlockStateRef ref = BlockStateRef.parse(blockStateString);
        Set<String> normalizedTags = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                String t = tag.trim().toLowerCase(Locale.ROOT);
                if (!t.startsWith("#")) {
                    t = "#" + t;
                }
                normalizedTags.add(t);
                tagIndex.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(ref);
            }
        }
        catalog.put(ref.canonicalString(), new BlockCatalogEntry(ref, displayName, Collections.unmodifiableSet(normalizedTags)));
    }

    public List<BlockCatalogEntry> searchBlocks(String query) {
        if (query == null || query.isBlank()) {
            return List.copyOf(catalog.values());
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.startsWith("#")) {
            Set<BlockStateRef> tagged = selectByTag(q);
            return catalog.values().stream()
                    .filter(e -> tagged.contains(e.state()))
                    .toList();
        }
        return catalog.values().stream()
                .filter(entry -> entry.state().canonicalString().contains(q)
                        || entry.displayName().toLowerCase(Locale.ROOT).contains(q)
                        || entry.tags().stream().anyMatch(t -> t.contains(q)))
                .toList();
    }

    public Set<BlockStateRef> selectByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return Set.of();
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        Set<BlockStateRef> matched = tagIndex.get(normalized);
        return matched == null ? Set.of() : Collections.unmodifiableSet(matched);
    }

    public WeightedPalettePattern patternFromTag(String tag) {
        Set<BlockStateRef> blocks = selectByTag(tag);
        if (blocks.isEmpty()) {
            return new WeightedPalettePattern(List.of(new WeightedPalettePattern.WeightedEntry(BlockStateRef.STONE, 1.0)));
        }
        List<WeightedPalettePattern.WeightedEntry> entries = blocks.stream()
                .map(b -> new WeightedPalettePattern.WeightedEntry(b, 1.0))
                .toList();
        return new WeightedPalettePattern(entries);
    }

    public void toggleFavorite(BlockStateRef state) {
        if (!favorites.remove(state)) {
            favorites.add(state);
        }
    }

    public boolean isFavorite(BlockStateRef state) {
        return favorites.contains(state);
    }

    public Set<BlockStateRef> favorites() {
        return Collections.unmodifiableSet(favorites);
    }

    public void recordRecentBlock(BlockStateRef state) {
        recentBlocks.remove(state);
        recentBlocks.addFirst(state);
        while (recentBlocks.size() > MAX_RECENT_BLOCKS) {
            recentBlocks.removeLast();
        }
    }

    public List<BlockStateRef> recentBlocks() {
        return new ArrayList<>(recentBlocks);
    }

    public void saveCustomPalette(CustomPalette palette) {
        customPalettes.put(palette.id(), palette);
    }

    public Optional<CustomPalette> getCustomPalette(String id) {
        return Optional.ofNullable(customPalettes.get(id));
    }

    public Map<String, CustomPalette> customPalettes() {
        return Collections.unmodifiableMap(customPalettes);
    }
}
