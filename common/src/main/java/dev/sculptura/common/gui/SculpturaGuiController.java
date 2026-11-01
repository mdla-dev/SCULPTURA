package dev.sculptura.common.gui;

import dev.sculptura.common.gizmo.TransformGizmoController;
import dev.sculptura.common.i18n.I18nManager;
import dev.sculptura.common.preview.PreviewManager;
import dev.sculptura.common.selection.SelectionController;
import dev.sculptura.core.brush.BrushSettings;
import dev.sculptura.core.brush.BrushShape;
import dev.sculptura.core.brush.BrushType;
import dev.sculptura.core.geometry.BlockStateRef;
import dev.sculptura.core.history.HistoryManager;
import dev.sculptura.core.layer.LayerManager;
import dev.sculptura.core.material.MaterialPaletteRegistry;
import dev.sculptura.core.material.MaterialPattern;
import dev.sculptura.core.shape.ShapeType;

/**
 * Professional 3D Editing GUI State & Theme Controller (Section 6).
 * Manages the state of all 11 required interface panels:
 * 1. Toolbar
 * 2. Selection tools
 * 3. Transform tools
 * 4. Brush panel
 * 5. Shape panel
 * 6. Material palette
 * 7. Operation settings
 * 8. Layers panel
 * 9. History panel
 * 10. Project browser
 * 11. Preview settings
 *
 * Uses Minecraft-compatible screen coordinates and dark-theme design tokens.
 */
public final class SculpturaGuiController {
    public enum ActiveToolCategory {
        SELECTION("sculptura.toolbar.selection"),
        TRANSFORM("sculptura.toolbar.transform"),
        BRUSH("sculptura.toolbar.brush"),
        SHAPE("sculptura.toolbar.shape"),
        MATERIAL_PALETTE("sculptura.toolbar.palette"),
        OPERATION_SETTINGS("sculptura.toolbar.operations"),
        LAYERS("sculptura.toolbar.layers"),
        HISTORY("sculptura.toolbar.history"),
        PROJECT_BROWSER("sculptura.toolbar.projects"),
        PREVIEW_SETTINGS("sculptura.toolbar.preview");

        private final String translationKey;

        ActiveToolCategory(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record DarkThemePalette(
            int backgroundPrimaryArgb,
            int panelSurfaceArgb,
            int panelBorderArgb,
            int accentPrimaryArgb,
            int accentHighlightArgb,
            int textPrimaryArgb,
            int textMutedArgb,
            int dangerArgb
    ) {
        public static DarkThemePalette professionalDark() {
            return new DarkThemePalette(
                    0xE6111318,
                    0xF21A1D24,
                    0xFF2E3440,
                    0xFF3B82F6,
                    0xFFF59E0B,
                    0xFFF3F4F6,
                    0xFF9CA3AF,
                    0xFFEF4444
            );
        }
    }

    private final SelectionController selectionController;
    private final TransformGizmoController gizmoController;
    private final PreviewManager previewManager;
    private final LayerManager layerManager;
    private final HistoryManager historyManager;
    private final MaterialPaletteRegistry paletteRegistry;
    private final I18nManager i18nManager;
    private final DarkThemePalette theme = DarkThemePalette.professionalDark();

    private ActiveToolCategory activeCategory = ActiveToolCategory.SELECTION;
    private BrushSettings activeBrush = BrushSettings.defaults(BrushType.RAISE, 8, 0.65, MaterialPattern.single(BlockStateRef.STONE));
    private ShapeType activeShapeType = ShapeType.SPHERE;
    private BlockStateRef activePrimaryBlock = BlockStateRef.STONE;
    private String blockSearchQuery = "";
    private float uiScale = 1.0f;
    private boolean hudOverlayVisible = true;

    public SculpturaGuiController(
            SelectionController selectionController,
            TransformGizmoController gizmoController,
            PreviewManager previewManager,
            LayerManager layerManager,
            HistoryManager historyManager,
            MaterialPaletteRegistry paletteRegistry,
            I18nManager i18nManager
    ) {
        this.selectionController = selectionController;
        this.gizmoController = gizmoController;
        this.previewManager = previewManager;
        this.layerManager = layerManager;
        this.historyManager = historyManager;
        this.paletteRegistry = paletteRegistry;
        this.i18nManager = i18nManager;
    }

    public DarkThemePalette theme() { return theme; }
    public ActiveToolCategory activeCategory() { return activeCategory; }
    public void setActiveCategory(ActiveToolCategory category) {
        if (category != null) {
            this.activeCategory = category;
        }
    }

    public BrushSettings activeBrush() { return activeBrush; }
    public void setActiveBrush(BrushSettings brush) {
        if (brush != null) {
            this.activeBrush = brush;
        }
    }

    public void updateBrushTypeAndShape(BrushType type, BrushShape shape, int radius, double strength) {
        this.activeBrush = new BrushSettings(
                type, shape, radius, strength,
                activeBrush.falloff(), activeBrush.falloffCurve(),
                activeBrush.spacing(), activeBrush.frequency(), activeBrush.noiseAmplitude(),
                activeBrush.targetHeight(), MaterialPattern.single(activePrimaryBlock),
                activeBrush.replaceTarget(), activeBrush.randomSeed(), activeBrush.raycastRange()
        );
    }

    public ShapeType activeShapeType() { return activeShapeType; }
    public void setActiveShapeType(ShapeType shapeType) {
        if (shapeType != null) {
            this.activeShapeType = shapeType;
        }
    }

    public BlockStateRef activePrimaryBlock() { return activePrimaryBlock; }
    public void selectPrimaryBlock(BlockStateRef block) {
        if (block != null) {
            this.activePrimaryBlock = block;
            this.paletteRegistry.recordRecentBlock(block);
        }
    }

    public String blockSearchQuery() { return blockSearchQuery; }
    public void setBlockSearchQuery(String query) {
        this.blockSearchQuery = query == null ? "" : query;
    }

    public float uiScale() { return uiScale; }
    public void setUiScale(float scale) {
        this.uiScale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    public boolean hudOverlayVisible() { return hudOverlayVisible; }
    public void setHudOverlayVisible(boolean visible) { this.hudOverlayVisible = visible; }

    public SelectionController selectionController() { return selectionController; }
    public TransformGizmoController gizmoController() { return gizmoController; }
    public PreviewManager previewManager() { return previewManager; }
    public LayerManager layerManager() { return layerManager; }
    public HistoryManager historyManager() { return historyManager; }
    public MaterialPaletteRegistry paletteRegistry() { return paletteRegistry; }
    public I18nManager i18nManager() { return i18nManager; }
}
