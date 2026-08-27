package ru.doomedru.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.doomedru.Dict;

/**
 * Точечный перехват вывода текста ВНУТРИ классов Doomed.
 *
 * Список целей сгенерирован из doomedmatu 0.3.6: это все классы мода,
 * которые обращаются к Component.literal, GuiGraphics.drawString или
 * Font.width. Ни один чужой мод сюда не попадает по построению, поэтому
 * подмена строк физически не может задеть их интерфейс.
 *
 * Обработчики статические: так они годятся и для статических, и для
 * обычных методов цели. require = 0 - класс, где нужного вызова нет,
 * просто пропускается.
 *
 * При обновлении Doomed список пересобирается скриптом tools/gen_targets.py.
 */
@Mixin(targets = {
        "net.mattlives.doomedmatu.block.LifepodConsoleBlock",
        "net.mattlives.doomedmatu.block.WashStationBlock",
        "net.mattlives.doomedmatu.body.BodyAlert",
        "net.mattlives.doomedmatu.body.ConsentGate",
        "net.mattlives.doomedmatu.body.CoopMedical",
        "net.mattlives.doomedmatu.body.DefibService",
        "net.mattlives.doomedmatu.body.DismemberService",
        "net.mattlives.doomedmatu.body.DrugService",
        "net.mattlives.doomedmatu.body.EyeLossAlert",
        "net.mattlives.doomedmatu.body.ImaginaryFriend",
        "net.mattlives.doomedmatu.body.KuruHandler",
        "net.mattlives.doomedmatu.body.ShockSensitiveHandler",
        "net.mattlives.doomedmatu.body.TourniquetHandler",
        "net.mattlives.doomedmatu.body.TreatmentService",
        "net.mattlives.doomedmatu.client.ChemTooltips",
        "net.mattlives.doomedmatu.client.ClientForgeEvents",
        "net.mattlives.doomedmatu.client.ClientHooks",
        "net.mattlives.doomedmatu.client.DeepMawGrabClient",
        "net.mattlives.doomedmatu.client.QuipRenderer",
        "net.mattlives.doomedmatu.client.WearInventoryButton$GearButton",
        "net.mattlives.doomedmatu.client.firearm.v2.FirearmGeometry$FaceUv",
        "net.mattlives.doomedmatu.client.firearm.v2.FirearmGeometryRenderer",
        "net.mattlives.doomedmatu.client.hud.CoopNoticeOverlay",
        "net.mattlives.doomedmatu.client.hud.DoomedHudOverlay",
        "net.mattlives.doomedmatu.client.hud.DoomedHudOverlay$ArtSize",
        "net.mattlives.doomedmatu.client.hud.DoomedHudOverlay$MoodleBounds",
        "net.mattlives.doomedmatu.client.hud.DoomedTitleCard",
        "net.mattlives.doomedmatu.client.hud.LastStandOverlay",
        "net.mattlives.doomedmatu.client.hud.PanelFractureOverlay",
        "net.mattlives.doomedmatu.client.hud.QuipOverlay",
        "net.mattlives.doomedmatu.client.hud.RadiationOverlay",
        "net.mattlives.doomedmatu.client.hud.TerminalFractureLayout$CrackSegment",
        "net.mattlives.doomedmatu.client.hud.VitalsCircleOverlay",
        "net.mattlives.doomedmatu.client.render.SurfaceWoundRenderer",
        "net.mattlives.doomedmatu.client.screen.AedScreen",
        "net.mattlives.doomedmatu.client.screen.AmputationScreen",
        "net.mattlives.doomedmatu.client.screen.ArrowExtractionScreen",
        "net.mattlives.doomedmatu.client.screen.BandageScreen",
        "net.mattlives.doomedmatu.client.screen.BloodColorScreen",
        "net.mattlives.doomedmatu.client.screen.CauterizeScreen",
        "net.mattlives.doomedmatu.client.screen.ChatColorScreen",
        "net.mattlives.doomedmatu.client.screen.CodexScreen",
        "net.mattlives.doomedmatu.client.screen.CprScreen",
        "net.mattlives.doomedmatu.client.screen.CraftingScreen",
        "net.mattlives.doomedmatu.client.screen.DeathScreen",
        "net.mattlives.doomedmatu.client.screen.DeepMawVisualSettingsScreen",
        "net.mattlives.doomedmatu.client.screen.DeepMawVisualSettingsScreen$IntensitySlider",
        "net.mattlives.doomedmatu.client.screen.DefibScreen",
        "net.mattlives.doomedmatu.client.screen.DespairScreen",
        "net.mattlives.doomedmatu.client.screen.DiagnosticTabletScreen",
        "net.mattlives.doomedmatu.client.screen.DialogueEditorScreen",
        "net.mattlives.doomedmatu.client.screen.DirectPressureScreen",
        "net.mattlives.doomedmatu.client.screen.DoomedColorEditorScreen",
        "net.mattlives.doomedmatu.client.screen.DoomedColorEditorScreen$ColorSwatchButton",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$CategoryButton",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$CycleOption",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$NavigationOption",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$SectionLabel",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$SliderOption",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets$ToggleOption",
        "net.mattlives.doomedmatu.client.screen.DoomedSettingsScreen",
        "net.mattlives.doomedmatu.client.screen.DropCapsuleScreen",
        "net.mattlives.doomedmatu.client.screen.DropCapsuleScreen$CableRun",
        "net.mattlives.doomedmatu.client.screen.ExtendingDoomedHelpScreen",
        "net.mattlives.doomedmatu.client.screen.FailureWarningPanel",
        "net.mattlives.doomedmatu.client.screen.FieldJournalScreen",
        "net.mattlives.doomedmatu.client.screen.GiveUpButton",
        "net.mattlives.doomedmatu.client.screen.GrindScreen",
        "net.mattlives.doomedmatu.client.screen.HealthPanelBackdrop",
        "net.mattlives.doomedmatu.client.screen.HsvColorPicker$HueWidget",
        "net.mattlives.doomedmatu.client.screen.HsvColorPicker$SaturationValueWidget",
        "net.mattlives.doomedmatu.client.screen.HudCustomizationScreen",
        "net.mattlives.doomedmatu.client.screen.HudCustomizationScreen$OffsetSlider",
        "net.mattlives.doomedmatu.client.screen.HudCustomizationScreen$ScaleSlider",
        "net.mattlives.doomedmatu.client.screen.InterfaceColorScreen",
        "net.mattlives.doomedmatu.client.screen.JawReductionScreen",
        "net.mattlives.doomedmatu.client.screen.KeypadScreen",
        "net.mattlives.doomedmatu.client.screen.LandmineDefusalScreen",
        "net.mattlives.doomedmatu.client.screen.LastStandScreen",
        "net.mattlives.doomedmatu.client.screen.LockpickScreen",
        "net.mattlives.doomedmatu.client.screen.MachineGuiTheme",
        "net.mattlives.doomedmatu.client.screen.MachineScreen",
        "net.mattlives.doomedmatu.client.screen.MachineTabButton",
        "net.mattlives.doomedmatu.client.screen.MatuOSScreen",
        "net.mattlives.doomedmatu.client.screen.ModeSelectScreen",
        "net.mattlives.doomedmatu.client.screen.NpcDialogueScreen",
        "net.mattlives.doomedmatu.client.screen.PanelInfoCard",
        "net.mattlives.doomedmatu.client.screen.PatientExamScreen",
        "net.mattlives.doomedmatu.client.screen.PuppetEditorScreen",
        "net.mattlives.doomedmatu.client.screen.PuppetEditorScreen$Slide",
        "net.mattlives.doomedmatu.client.screen.ReduceDislocationScreen",
        "net.mattlives.doomedmatu.client.screen.RefusalScreen",
        "net.mattlives.doomedmatu.client.screen.RemoveShrapnelScreen",
        "net.mattlives.doomedmatu.client.screen.RitualScreen",
        "net.mattlives.doomedmatu.client.screen.RulesScreen",
        "net.mattlives.doomedmatu.client.screen.SeveredExaminationScreen",
        "net.mattlives.doomedmatu.client.screen.SplintScreen",
        "net.mattlives.doomedmatu.client.screen.StatusPanelScreen",
        "net.mattlives.doomedmatu.client.screen.StitchScreen",
        "net.mattlives.doomedmatu.client.screen.SyringeScreen",
        "net.mattlives.doomedmatu.client.screen.TradeScreen",
        "net.mattlives.doomedmatu.client.screen.TraitSelectScreen",
        "net.mattlives.doomedmatu.client.screen.WearScreen",
        "net.mattlives.doomedmatu.client.screen.WitnessCustomizationScreen",
        "net.mattlives.doomedmatu.client.screen.WitnessCustomizationScreen$PercentSlider",
        "net.mattlives.doomedmatu.client.screen.WitnessPatientScanScreen",
        "net.mattlives.doomedmatu.client.screen.defusal.CodexContent",
        "net.mattlives.doomedmatu.client.screen.defusal.CodexContent$1",
        "net.mattlives.doomedmatu.client.screen.defusal.CodexContent$GlyphColumnsPage",
        "net.mattlives.doomedmatu.client.screen.defusal.CodexView",
        "net.mattlives.doomedmatu.client.screen.defusal.EchoModule",
        "net.mattlives.doomedmatu.client.screen.defusal.ValveModule",
        "net.mattlives.doomedmatu.client.screen.defusal.WiresModule",
        "net.mattlives.doomedmatu.client.screen.surgery.SurgeryScreen",
        "net.mattlives.doomedmatu.client.screen.surgery.TransplantScreen",
        "net.mattlives.doomedmatu.command.DoomCapsuleCommand",
        "net.mattlives.doomedmatu.command.DoomCommand",
        "net.mattlives.doomedmatu.command.DoomDespairCommand",
        "net.mattlives.doomedmatu.command.DoomJournalCommand",
        "net.mattlives.doomedmatu.command.DoomStatCommand",
        "net.mattlives.doomedmatu.command.DoomTransformCommand",
        "net.mattlives.doomedmatu.command.DoomVisionCommand",
        "net.mattlives.doomedmatu.command.DoomWoundCommand",
        "net.mattlives.doomedmatu.command.DoomedHealCommand",
        "net.mattlives.doomedmatu.compat.emi.EmiChemRecipe",
        "net.mattlives.doomedmatu.compat.jade.FluidBarrelProvider",
        "net.mattlives.doomedmatu.compat.jade.MachineProvider",
        "net.mattlives.doomedmatu.compat.jei.ChemCategory",
        "net.mattlives.doomedmatu.entity.DeepMawCavePlanner",
        "net.mattlives.doomedmatu.entity.DeepMawCavePlanner$BodyEnvelope",
        "net.mattlives.doomedmatu.entity.DeepMawCavePlanner$CollisionProbe",
        "net.mattlives.doomedmatu.entity.DeepMawEntity",
        "net.mattlives.doomedmatu.entity.DoomedTrader",
        "net.mattlives.doomedmatu.inventory.TradeMenu",
        "net.mattlives.doomedmatu.item.AedItem",
        "net.mattlives.doomedmatu.item.AntibioticItem",
        "net.mattlives.doomedmatu.item.AntidepressantItem",
        "net.mattlives.doomedmatu.item.AntimicrobialItem",
        "net.mattlives.doomedmatu.item.BagItem",
        "net.mattlives.doomedmatu.item.BatteryItem",
        "net.mattlives.doomedmatu.item.BloodBagItem",
        "net.mattlives.doomedmatu.item.BraingrowItem",
        "net.mattlives.doomedmatu.item.CadaverTorsoItem",
        "net.mattlives.doomedmatu.item.CanteenItem",
        "net.mattlives.doomedmatu.item.CigaretteItem",
        "net.mattlives.doomedmatu.item.CleaningItem",
        "net.mattlives.doomedmatu.item.DefibrillatorItem",
        "net.mattlives.doomedmatu.item.DisposableDustMaskItem",
        "net.mattlives.doomedmatu.item.DryingClothItem",
        "net.mattlives.doomedmatu.item.EmptyBloodBagItem",
        "net.mattlives.doomedmatu.item.FilterCartridgeItem",
        "net.mattlives.doomedmatu.item.FluidSyringeItem",
        "net.mattlives.doomedmatu.item.ForagingKnifeItem",
        "net.mattlives.doomedmatu.item.GasMaskItem",
        "net.mattlives.doomedmatu.item.GunItem",
        "net.mattlives.doomedmatu.item.HumanFleshItem",
        "net.mattlives.doomedmatu.item.LightItem",
        "net.mattlives.doomedmatu.item.LimbMedItem",
        "net.mattlives.doomedmatu.item.MagazineItem",
        "net.mattlives.doomedmatu.item.MatuOSItem",
        "net.mattlives.doomedmatu.item.MedDrinkItem",
        "net.mattlives.doomedmatu.item.MindwipeItem",
        "net.mattlives.doomedmatu.item.NeuralBoosterItem",
        "net.mattlives.doomedmatu.item.ProstheticItem",
        "net.mattlives.doomedmatu.item.ProstheticLubricantItem",
        "net.mattlives.doomedmatu.item.RebreatherItem",
        "net.mattlives.doomedmatu.item.RevivolItem",
        "net.mattlives.doomedmatu.item.SalineItem",
        "net.mattlives.doomedmatu.item.SuppressantItem",
        "net.mattlives.doomedmatu.item.SyringeItem",
        "net.mattlives.doomedmatu.item.TransplantOrganItem",
        "net.mattlives.doomedmatu.item.WearableItem",
        "net.mattlives.doomedmatu.network.C2SCraftRecipePacket",
        "net.mattlives.doomedmatu.network.C2SHarvestOrganPacket",
        "net.mattlives.doomedmatu.registry.DoomedItems$1",
        "net.mattlives.doomedmatu.registry.DoomedItems$2",
        "net.mattlives.doomedmatu.transform.RefusalService"
}, remap = false)
public abstract class DoomedTextMixin {

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0, expect = 0, remap = true)
    private static MutableComponent doomedru$literal(String text) {
        return Component.literal(Dict.tr(text));
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/network/chat/Component;nullToEmpty(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"),
            require = 0, expect = 0, remap = true)
    private static Component doomedru$nullToEmpty(String text) {
        return Component.nullToEmpty(Dict.tr(text));
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
            require = 0, expect = 0, remap = true)
    private static int doomedru$drawStringShadow(GuiGraphics gfx, Font font, String text, int x, int y, int colour, boolean shadow) {
        return gfx.drawString(font, Dict.tr(text), x, y, colour, shadow);
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
            require = 0, expect = 0, remap = true)
    private static int doomedru$drawString(GuiGraphics gfx, Font font, String text, int x, int y, int colour) {
        return gfx.drawString(font, Dict.tr(text), x, y, colour);
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
            require = 0, expect = 0, remap = true)
    private static void doomedru$drawCentered(GuiGraphics gfx, Font font, String text, int x, int y, int colour) {
        gfx.drawCenteredString(font, Dict.tr(text), x, y, colour);
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", remap = true,
                     target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"),
            require = 0, expect = 0, remap = true)
    private static int doomedru$width(Font font, String text) {
        return font.width(Dict.tr(text));
    }
}
