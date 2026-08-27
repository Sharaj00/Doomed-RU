package ru.doomedru.mixin.global;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.doomedru.Dict;

/** Ширина «сырой» строки — чтобы вёрстка считалась по переведённому тексту. */
@Mixin(Font.class)
public abstract class FontMixin {

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String doomedru$width(String text) {
        return Dict.tr(text);
    }
}
