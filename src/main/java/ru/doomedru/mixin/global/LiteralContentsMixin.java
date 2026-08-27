package ru.doomedru.mixin.global;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.doomedru.Dict;

import java.util.Optional;

/**
 * Основной перехват текста.
 *
 * Всё, что мод показывает через Component.literal - подписи кнопок, заголовки
 * экранов, панель сортировки, подсказки предметов - проходит через
 * LiteralContents.visit() в момент отрисовки или замера ширины. Одна точка
 * покрывает и вывод строки, и перенос по словам (Font.split), и всплывающие
 * подсказки, и виджеты, которые рисует уже ванильный код.
 *
 * Почему не @Redirect на сам Component.literal внутри классов Doomed:
 * Component - интерфейс, а literal - его статический метод. Процессор
 * аннотаций Mixin разбирает такую цель не так, как обычный метод класса,
 * и перехват молча не применяется (injectors.defaultRequire = 0, поэтому
 * ошибки в логе нет). Ровно из-за этого экраны, рисующие текст только через
 * Component.literal - выбор режима, панель сортировки - оставались целиком
 * английскими, а настройки переводились наполовину: там часть подписей идёт
 * через GuiGraphics.drawString(Font, String, ...), то есть через обычный
 * виртуальный метод обычного класса, и он перехватывается нормально.
 *
 * Здесь цель - visit() у самого LiteralContents, обычный метод обычного
 * класса. Инъекция в HEAD с подменой возвращаемого значения: вызов
 * consumer.accept(...) написан обычным Java-кодом, а не точкой инъекции,
 * поэтому от переименования вообще ничего не зависит.
 *
 * Сам объект не меняется - подменяется только то, что уходит потребителю.
 */
@Mixin(LiteralContents.class)
public abstract class LiteralContentsMixin {

    /**
     * Текст берём штатным аксессором записи, а не через @Shadow: обычный вызов
     * переименовывается на этапе reobf вместе со всем остальным кодом и не
     * зависит от refmap. Вызовы consumer.accept(...) ниже - по той же причине.
     */
    private String doomedru$raw() {
        return ((LiteralContents) (Object) this).text();
    }

    @Inject(
            method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true, remap = true)
    private <T> void doomedru$plain(FormattedText.ContentConsumer<T> consumer,
                                    CallbackInfoReturnable<Optional<T>> cir) {
        String raw = doomedru$raw();
        String ru = Dict.tr(raw);
        if (!ru.equals(raw)) cir.setReturnValue(consumer.accept(ru));
    }

    @Inject(
            method = "visit(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true, remap = true)
    private <T> void doomedru$styled(FormattedText.StyledContentConsumer<T> consumer, Style style,
                                     CallbackInfoReturnable<Optional<T>> cir) {
        String raw = doomedru$raw();
        String ru = Dict.tr(raw);
        if (!ru.equals(raw)) cir.setReturnValue(consumer.accept(style, ru));
    }
}
