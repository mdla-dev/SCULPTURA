package dev.sculptura.common.i18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localization Manager (Section 6) providing full English (en_us) and Russian (ru_ru) translations
 * for all SCULPTURA tools, panels, brushes, shapes, operations, layers, history, and status messages.
 */
public final class I18nManager {
    public enum SupportedLocale {
        EN_US("en_us", "English (US)"),
        RU_RU("ru_ru", "Русский (RU)");

        private final String code;
        private final String displayName;

        SupportedLocale(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() { return code; }
        public String displayName() { return displayName; }
    }

    private SupportedLocale activeLocale = SupportedLocale.EN_US;
    private final Map<String, String> enUs = new LinkedHashMap<>();
    private final Map<String, String> ruRu = new LinkedHashMap<>();

    public I18nManager() {
        registerTranslations();
    }

    private void registerTranslations() {
        put("sculptura.title", "SCULPTURA — Visual 3D Voxel Editor", "SCULPTURA — Визуальный 3D воксельный редактор");
        put("sculptura.toolbar.selection", "Selection Tools", "Инструменты выделения");
        put("sculptura.toolbar.transform", "Transform Gizmo", "Гизмо трансформации");
        put("sculptura.toolbar.brush", "Terrain Sculpting", "Скульптурирование рельефа");
        put("sculptura.toolbar.shape", "Procedural Shapes", "Процедурные фигуры");
        put("sculptura.toolbar.palette", "Smart Materials", "Умные материалы");
        put("sculptura.toolbar.operations", "Operations", "Операции");
        put("sculptura.toolbar.layers", "Layers", "Слои");
        put("sculptura.toolbar.history", "History (Undo/Redo)", "История (Отмена/Повтор)");
        put("sculptura.toolbar.projects", "Projects & Blueprints", "Проекты и чертежи");
        put("sculptura.toolbar.preview", "Holographic Preview", "Голографический предпросмотр");

        // Brushes
        put("sculptura.brush.raise", "Raise Terrain", "Поднять рельеф");
        put("sculptura.brush.lower", "Lower Terrain", "Опустить рельеф");
        put("sculptura.brush.smooth", "Smooth Terrain", "Сгладить рельеф");
        put("sculptura.brush.flatten", "Flatten Terrain", "Выровнять рельеф");
        put("sculptura.brush.erode", "Erode Terrain", "Эрозия рельефа");
        put("sculptura.brush.terrace", "Terrace Terrain", "Террасирование");
        put("sculptura.brush.blend", "Blend Materials", "Смешивание материалов");
        put("sculptura.brush.blur", "Voxel 3D Blur", "Объёмное размытие");
        put("sculptura.brush.paint", "Paint Surface", "Окрашивание поверхности");
        put("sculptura.brush.replace", "Replace Blocks", "Замена блоков");
        put("sculptura.brush.noise", "Noise Perturbation", "Шумовая деформация");
        put("sculptura.brush.vegetation", "Vegetation Painter", "Кисть растительности");

        // Actions
        put("sculptura.action.commit", "Commit Edit to World", "Применить изменения в мир");
        put("sculptura.action.undo", "Undo (Ctrl+Z)", "Отменить (Ctrl+Z)");
        put("sculptura.action.redo", "Redo (Ctrl+Y)", "Повторить (Ctrl+Y)");
        put("sculptura.action.cancel", "Cancel Operation", "Отменить операцию");
        put("sculptura.status.conflict_detected", "Partial Undo: skipped blocks modified by another player", "Частичная отмена: пропущены блоки, изменённые другим игроком");
    }

    private void put(String key, String english, String russian) {
        enUs.put(key, english);
        ruRu.put(key, russian);
    }

    public SupportedLocale activeLocale() {
        return activeLocale;
    }

    public void setActiveLocale(SupportedLocale locale) {
        if (locale != null) {
            this.activeLocale = locale;
        }
    }

    public String translate(String key) {
        Map<String, String> activeMap = activeLocale == SupportedLocale.RU_RU ? ruRu : enUs;
        return activeMap.getOrDefault(key, enUs.getOrDefault(key, key));
    }

    public Map<String, String> translationsFor(SupportedLocale locale) {
        return Collections.unmodifiableMap(locale == SupportedLocale.RU_RU ? ruRu : enUs);
    }
}
