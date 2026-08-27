package ru.doomedru.mixin.global;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.doomedru.Dict;

/** Строки, которые рисуются напрямую, минуя Component. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String doomedru$drawString(String text) {
        return Dict.tr(text);
    }

    @ModifyVariable(
            method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String doomedru$drawCentered(String text) {
        return Dict.tr(text);
    }
}
