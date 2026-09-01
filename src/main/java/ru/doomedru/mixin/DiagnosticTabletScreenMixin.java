package ru.doomedru.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.doomedru.Dict;

/**
 * Переводит текст WITNESS до того, как экран разобьёт его на строки или
 * обрежет многоточием.
 *
 * DiagnosticTabletScreen выполняет собственный word wrap над сырыми String.
 * Если переводить уже созданные фрагменты, первая половина строки может стать
 * русской, а остаток останется английским. Здесь полная фраза подменяется до
 * измерения ширины, поэтому разбиение и отрисовка работают с одним языком.
 */
@Mixin(targets = "net.mattlives.doomedmatu.client.screen.DiagnosticTabletScreen", remap = false)
public abstract class DiagnosticTabletScreenMixin {

    @ModifyVariable(
            method = {
                    "wrapped(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIIIF)I",
                    "wrappedHeight(Ljava/lang/String;IF)I",
                    "ellipsize(Ljava/lang/String;IF)Ljava/lang/String;"
            },
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private String doomedru$translateBeforeLayout(String text) {
        return Dict.tr(text);
    }

    @ModifyVariable(
            method = {
                    "txt(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIIF)V",
                    "txtC(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIIF)V",
                    "txtR(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIIF)I",
                    "txtW(Ljava/lang/String;F)I"
            },
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private String doomedru$translateDirectText(String text) {
        return Dict.tr(text);
    }
}
