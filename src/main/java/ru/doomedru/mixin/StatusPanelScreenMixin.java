package ru.doomedru.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.doomedru.Dict;

/**
 * Переводит подписи панели состояния до измерения ширины и обрезки.
 *
 * StatusPanelScreen сокращает длинные подписи собственным fitTriageText().
 * Если общий перехват срабатывает уже при отрисовке, исходное "EXT BLEED"
 * успевает превратиться в "EXT B..." и больше не совпадает со словарём.
 */
@Mixin(targets = "net.mattlives.doomedmatu.client.screen.StatusPanelScreen", remap = false)
public abstract class StatusPanelScreenMixin {

    @ModifyVariable(
            method = "fitTriageText(Ljava/lang/String;I)Ljava/lang/String;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false
    )
    private String doomedru$translateBeforeFit(String text) {
        return Dict.tr(text);
    }
}
