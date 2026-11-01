package dev.sculptura.common.platform;

import dev.sculptura.common.execution.TickBudgetedExecutor;
import dev.sculptura.common.gizmo.TransformGizmoController;
import dev.sculptura.common.gui.SculpturaGuiController;
import dev.sculptura.common.i18n.I18nManager;
import dev.sculptura.common.network.ServerSecurityValidator;
import dev.sculptura.common.preview.PreviewManager;
import dev.sculptura.common.preview.PreviewRenderBackend;
import dev.sculptura.common.selection.SelectionController;
import dev.sculptura.core.history.HistoryManager;
import dev.sculptura.core.layer.LayerManager;
import dev.sculptura.core.material.MaterialPaletteRegistry;

/**
 * Unified SCULPTURA Runtime Context initialized by each of the 9 platform entrypoints
 * (Fabric 1.20.1/1.21.1/1.21.11, Forge 1.18.2/1.19.2/1.20.1, NeoForge 1.21.1/1.21.4/1.21.11).
 */
public final class SculpturaRuntime {
    public static final String MOD_ID = "sculptura";
    public static final String MOD_NAME = "SCULPTURA";
    public static final String MOD_VERSION = "1.0.0";

    private final String loaderName;
    private final String minecraftVersion;
    private final SelectionController selectionController;
    private final TransformGizmoController gizmoController;
    private final PreviewManager previewManager;
    private final LayerManager layerManager;
    private final HistoryManager historyManager;
    private final MaterialPaletteRegistry paletteRegistry;
    private final I18nManager i18nManager;
    private final SculpturaGuiController guiController;
    private final ServerSecurityValidator securityValidator;
    private final TickBudgetedExecutor tickExecutor;

    public SculpturaRuntime(
            String loaderName,
            String minecraftVersion,
            PreviewRenderBackend renderBackend
    ) {
        this.loaderName = loaderName;
        this.minecraftVersion = minecraftVersion;
        this.selectionController = new SelectionController();
        this.gizmoController = new TransformGizmoController();
        this.previewManager = new PreviewManager(renderBackend);
        this.layerManager = new LayerManager();
        this.historyManager = HistoryManager.createDefault();
        this.paletteRegistry = new MaterialPaletteRegistry();
        this.i18nManager = new I18nManager();
        this.guiController = new SculpturaGuiController(
                selectionController,
                gizmoController,
                previewManager,
                layerManager,
                historyManager,
                paletteRegistry,
                i18nManager
        );
        this.securityValidator = ServerSecurityValidator.createDefault();
        this.tickExecutor = TickBudgetedExecutor.createDefault();
    }

    public String loaderName() { return loaderName; }
    public String minecraftVersion() { return minecraftVersion; }
    public SelectionController selectionController() { return selectionController; }
    public TransformGizmoController gizmoController() { return gizmoController; }
    public PreviewManager previewManager() { return previewManager; }
    public LayerManager layerManager() { return layerManager; }
    public HistoryManager historyManager() { return historyManager; }
    public MaterialPaletteRegistry paletteRegistry() { return paletteRegistry; }
    public I18nManager i18nManager() { return i18nManager; }
    public SculpturaGuiController guiController() { return guiController; }
    public ServerSecurityValidator securityValidator() { return securityValidator; }
    public TickBudgetedExecutor tickExecutor() { return tickExecutor; }

    public void shutdownClient() {
        previewManager.clearAndDisposeGpuResources();
    }
}
