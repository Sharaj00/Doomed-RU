package ru.doomedru;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Определяет, можно ли на текущем экране подменять короткие общеупотребительные
 * слова. В GUI чужих модов нельзя: их «Chloride», «Settings» или «Default Model»
 * не имеют отношения к Doomed.
 *
 * Разрешено, когда:
 *   - экран не открыт (игровой мир, HUD, подсказки предметов в мире);
 *   - экран принадлежит Doomed;
 *   - открыт ванильный список клавиш - единственное ванильное место,
 *     где встречаются подписи Doomed.
 *
 * Запрещено на экранах чужих модов, в списке модов Forge и на ванильных
 * экранах настроек: там те же слова принадлежат не нам.
 */
final class ScreenContext {
    private static Screen lastScreen;
    private static boolean lastResult = true;

    private ScreenContext() {}

    static boolean allowsGeneric() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return true;
            Screen s = mc.screen;
            if (s == null) return true;
            if (s == lastScreen) return lastResult;

            String cls = s.getClass().getName();
            boolean ok = cls.contains("doomedmatu")
                      || cls.contains("doomedru")
                      // единственное ванильное место с текстом Doomed - список клавиш
                      || cls.startsWith("net.minecraft.client.gui.screens.controls.");

            lastScreen = s;
            lastResult = ok;
            return ok;
        } catch (Throwable t) {
            return true;
        }
    }
}
