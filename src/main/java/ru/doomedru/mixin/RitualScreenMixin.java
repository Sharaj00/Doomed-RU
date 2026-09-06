package ru.doomedru.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.doomedru.Dict;

/** Переводит новый текст вступления 0.4.5 до разбиения на абзацы и строки. */
@Mixin(targets = "net.mattlives.doomedmatu.client.screen.RitualScreen", remap = false)
public abstract class RitualScreenMixin {

    /* splitNarrator статический, поэтому обработчик тоже должен быть static. */
    @ModifyVariable(
            method = "splitNarrator(Lnet/minecraft/client/gui/Font;Ljava/lang/String;I)Ljava/util/List;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false
    )
    private static String doomedru$translateBeforeSplit(String text) {
        return Dict.tr(text);
    }
}
