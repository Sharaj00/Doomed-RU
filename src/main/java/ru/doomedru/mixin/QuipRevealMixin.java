package ru.doomedru.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.doomedru.Dict;

/**
 * Переводит анимированные мысли по полной фразе, а не по видимому префиксу.
 *
 * У многих реплик совпадает начало. Обычный префиксный поиск при каждом новом
 * символе мог выбирать другую полную реплику, из-за чего русский текст прыгал
 * или на один кадр возвращался к английскому. Reveal уже хранит полный текст и
 * точное число открытых кодовых точек, поэтому здесь можно один раз однозначно
 * перевести фразу и открыть ту же долю русского варианта.
 */
@Mixin(targets = "net.mattlives.doomedmatu.client.ClientQuipStore$Reveal", remap = false)
public abstract class QuipRevealMixin {
    @Shadow @Final private String fullText;
    @Shadow @Final private int revealedCodePoints;

    @Inject(method = "visibleText()Ljava/lang/String;", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void doomedru$stableTranslatedReveal(CallbackInfoReturnable<String> cir) {
        String translated = Dict.tr(fullText);
        if (translated.equals(fullText)) return;

        int sourceLength = fullText.codePointCount(0, fullText.length());
        int translatedLength = translated.codePointCount(0, translated.length());
        if (sourceLength == 0 || translatedLength == 0 || revealedCodePoints <= 0) {
            cir.setReturnValue("");
            return;
        }

        int visible = (int) Math.ceil(
                translatedLength * Math.min(revealedCodePoints, sourceLength) / (double) sourceLength);
        visible = Math.max(1, Math.min(translatedLength, visible));
        int end = translated.offsetByCodePoints(0, visible);
        cir.setReturnValue(translated.substring(0, end));
    }
}
