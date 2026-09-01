package ru.doomedru.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Решает, какие миксины применять.
 *
 * doomedru.global.mixins.json  - перехват в ванильных классах. Основной режим:
 *                                LiteralContents.visit() покрывает весь текст,
 *                                который мод отдаёт через Component.literal,
 *                                включая подписи виджетов и подсказки.
 *                                Выключается флагом -Ddoomedru.globalHook=false.
 * doomedru.mixins.json         - узкие исправления для конкретных механизмов
 *                                Doomed: перевод WITNESS и панели состояния
 *                                до обрезки, стабильная выдача мыслей.
 */
public class DoomedRuMixinPlugin implements IMixinConfigPlugin {

    /** Ресурс, а не класс: Class.forName здесь загрузил бы класс мода раньше,
     *  чем готов трансформер миксинов, и тот класс остался бы без перехвата. */
    private static final String DOOMED_MARKER =
            "net/mattlives/doomedmatu/client/hud/DoomedHudOverlay.class";

    /** Классы, в которые перехват реально встроился - для /doomedru. */
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();

    private boolean globalConfig;
    private boolean doomedPresent;

    /** Глобальный перехват включён, пока его явно не выключили. */
    public static boolean globalEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("doomedru.globalHook"));
    }

    public static int appliedCount() { return APPLIED.size(); }

    public static List<String> applied() { return List.copyOf(APPLIED); }

    @Override
    public void onLoad(String mixinPackage) {
        globalConfig = mixinPackage.endsWith(".global");
        doomedPresent = DoomedRuMixinPlugin.class.getClassLoader()
                                                 .getResource(DOOMED_MARKER) != null;
        if (!doomedPresent) {
            System.out.println("[DoomedRU] Мод Doomed не найден - перевод интерфейса отключён.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!doomedPresent) return false;
        return globalConfig ? globalEnabled() : true;
    }

    @Override
    public void postApply(String targetClassName, ClassNode node, String mixinClassName, IMixinInfo info) {
        APPLIED.add(targetClassName);
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode n, String m, IMixinInfo i) { }
}
