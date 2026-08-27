package ru.doomedru;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DoomedRu.MODID)
public class DoomedRu {
    public static final String MODID = "doomedru";
    public static final Logger LOGGER = LoggerFactory.getLogger("DoomedRU");

    /** Опорная строка самопроверки: есть во встроенном словаре с самого начала. */
    private static final String PROBE = "SKIN";

    public DoomedRu() {
        Dict.reload();
        LOGGER.info("[DoomedRU] глобальный перехват: {}", hookStatus());
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Проверяет, что перехват LiteralContents.visit() действительно встроился.
     *
     * Component.literal(...).getString() обходит содержимое компонента ровно тем
     * же путём, что и отрисовка, поэтому результат совпадает с тем, что игрок
     * увидит на экране. Проверка нужна потому, что неудачная инъекция миксина
     * не оставляет следов: при "defaultRequire": 0 она просто не применяется,
     * и внешне это неотличимо от неполного словаря.
     */
    public static String hookStatus() {
        String direct = Dict.tr(PROBE);
        if (direct.equals(PROBE)) return "не проверить (нет опорной строки в словаре)";
        String viaComponent = Component.literal(PROBE).getString();
        return viaComponent.equals(direct) ? "работает" : "НЕ РАБОТАЕТ";
    }

    @SubscribeEvent
    public void onClientCommands(RegisterClientCommandsEvent e) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("doomedru");

        root.then(Commands.literal("reload").executes(ctx -> {
            int n = Dict.reload();
            ctx.getSource().sendSuccess(() -> Component.literal("[DoomedRU] Словарь перезагружен: " + n + " строк"), false);
            return 1;
        }));

        root.then(Commands.literal("dump").executes(ctx -> {
            try {
                var p = Dict.dump();
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[DoomedRU] Непереведённых строк: " + Dict.missCount() + " -> " + p), false);
            } catch (Exception ex) {
                ctx.getSource().sendFailure(Component.literal("[DoomedRU] Ошибка записи: " + ex));
            }
            return 1;
        }));

        root.then(Commands.literal("toggle").executes(ctx -> {
            Dict.setEnabled(!Dict.enabled());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[DoomedRU] Перевод " + (Dict.enabled() ? "включён" : "выключен")), false);
            return 1;
        }));

        root.executes(ctx -> {
            // Диагностика: если "через перехват" близко к нулю, значит миксины
            // не встроились, и дело не в словаре. Если подмен мало при большом
            // числе просмотренных строк - не хватает записей в словаре.
            boolean global = ru.doomedru.mixin.DoomedRuMixinPlugin.globalEnabled();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[DoomedRU] словарь: " + Dict.size()
                  + " | классов с перехватом: " + ru.doomedru.mixin.DoomedRuMixinPlugin.appliedCount()
                  + "\nглобальный перехват: " + (global ? "вкл" : "выкл")
                  + " -> самопроверка: " + hookStatus()
                  + " | защита общих слов: " + (Dict.guardingGeneric() ? "вкл" : "выкл")
                  + "\nчерез перехват прошло строк: " + Dict.seen()
                  + " | подменено: " + Dict.hits()
                  + " | без перевода: " + Dict.missCount()
                  + "\n/doomedru reload | dump | toggle | targets"), false);
            return 1;
        });

        root.then(Commands.literal("targets").executes(ctx -> {
            var list = ru.doomedru.mixin.DoomedRuMixinPlugin.applied();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[DoomedRU] перехват встроен в " + list.size() + " классов:\n"
                  + String.join("\n", list)), false);
            return 1;
        }));

        e.getDispatcher().register(root);
    }
}
