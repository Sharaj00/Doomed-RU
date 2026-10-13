package local.doomed.russianfresh;

import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** An opt-in isolated-client audit. Disabled in ordinary installations. */
@Mod.EventBusSubscriber(modid="doomed_ru_fresh",value=Dist.CLIENT)
public final class ClientAudit {
    private static int phase,ticks,page;
    private static boolean pending;
    private static int checks;
    private static volatile Object patientFixture;
    private static volatile String fixtureError;
    private static final JsonObject RESULT=new JsonObject();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!Boolean.getBoolean("doomed.ru.audit")||e.phase!=TickEvent.Phase.END||pending)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(phase==0 && mc.screen instanceof TitleScreen) {
                pending=true;mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");
                mc.reloadResourcePacks().thenRun(()->mc.execute(()->{pending=false;phase=1;}));return;
            }
            if(phase==1 && mc.getOverlay()==null) {
                russian(mc);
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.DoomedSettingsScreen").getConstructor(Screen.class).newInstance((Screen)null));
                phase=3;ticks=0;return;
            }
            if(phase==2 && ++ticks>=20) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-codex-"+page+".png",mc.getMainRenderTarget(),c->{});
                if(++page<5){mc.setScreen(codexScreen(page));ticks=0;return;}
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.DiagnosticTabletScreen").getConstructor().newInstance());
                phase=9;ticks=0;return;
            }
            if(phase==3 && ++ticks>=20) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-settings.png",mc.getMainRenderTarget(),c->{});
                CreateWorldScreen.openFresh(mc,new TitleScreen());
                phase=4;ticks=0;return;
            }
            if(phase==4 && mc.screen instanceof CreateWorldScreen creation && ++ticks>=20) {
                creation.getUiState().setName("Doomed Russian Fresh Audit");
                creation.getUiState().setAllowCheats(true);
                creation.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creation.getUiState().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL);
                creation.getUiState().setSeed("12345");
                String label=Component.translatable("selectWorld.create").getString();
                for(var child:creation.children())if(child instanceof Button button && button.active && button.getMessage().getString().equals(label)) {
                    button.onPress();phase=7;ticks=0;return;
                }
                throw new IllegalStateException("World creation button unavailable");
            }
            if(phase==7) {
                if(mc.screen instanceof ConfirmScreen confirm) {
                    for(var child:confirm.children())if(child instanceof Button button && button.active){button.onPress();break;}
                }
                if(mc.player==null||mc.getSingleplayerServer()==null||++ticks<80)return;
                var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{
                    try {
                        var player=server.getPlayerList().getPlayer(uuid);
                        Class<?> util=Class.forName("net.mattlives.doomedmatu.capability.DoomedDataUtil");
                        Object data=((Optional<?>)util.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,player)).orElseThrow();
                        for(String name:List.of("setActive","setEntered","setTraitsChosen"))data.getClass().getMethod(name,boolean.class).invoke(data,true);
                        data.getClass().getMethod("resetVitals").invoke(data);
                        util.getMethod("sync",net.minecraft.server.level.ServerPlayer.class).invoke(null,player);
                        Class<?> snapshot=Class.forName("net.mattlives.doomedmatu.body.PatientSnapshot");
                        patientFixture=snapshot.getMethod("of",data.getClass()).invoke(null,data);
                    }catch(Throwable t){fixtureError=t.toString();}
                });
                phase=8;ticks=0;return;
            }
            if(phase==8) {
                if(fixtureError!=null)throw new IllegalStateException(fixtureError);
                if(patientFixture==null||++ticks<100)return;
                mc.setScreen(codexScreen(0));phase=2;ticks=0;return;
            }
            if(phase==9 && ++ticks>=180) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-witness.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.StatusPanelScreen").getConstructor().newInstance());
                Field info=mc.screen.getClass().getDeclaredField("infoOpen");info.setAccessible(true);info.setBoolean(mc.screen,false);
                phase=10;ticks=0;return;
            }
            if(phase==10 && ++ticks>=40) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-status.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.PatientExamScreen").getConstructor(patientFixture.getClass(),int.class).newInstance(patientFixture,mc.player.getId()));
                phase=11;ticks=0;return;
            }
            if(phase==11 && ++ticks>=40) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-patient.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.RulesScreen").getConstructor(Screen.class).newInstance((Screen)null));
                rules(mc.screen);phase=12;ticks=0;return;
            }
            if(phase==12 && ++ticks>=40) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-rules.png",mc.getMainRenderTarget(),c->{});
                Screen battle=(Screen)Class.forName("net.mattlives.doomedmatu.client.screen.MatuEncounterScreen")
                    .getConstructor(net.minecraft.core.BlockPos.class,long.class,long.class).newInstance(mc.player.blockPosition(),0L,73L);
                Field game=battle.getClass().getDeclaredField("game");game.setAccessible(true);battleDialogue(game.get(battle));
                mc.setScreen(battle);phase=13;ticks=0;return;
            }
            if(phase==13 && ++ticks>=35) {
                Screenshot.grab(mc.gameDirectory,"doomed-ru-battle.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen((Screen)Class.forName("net.mattlives.doomedmatu.client.screen.TraitSelectScreen").getConstructor(boolean.class).newInstance(true));
                double sx=(double)mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth();
                double sy=(double)mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),(mc.getWindow().getGuiScaledWidth()/2-150)*sx,(84+7*13+3)*sy);
                phase=14;ticks=0;return;
            }
            if(phase==14) {
                for(String[] coordinate:new String[][]{{"xpos","f_91507_"},{"ypos","f_91508_"}}) {
                    Field position;
                    try{position=mc.mouseHandler.getClass().getDeclaredField(coordinate[0]);}
                    catch(NoSuchFieldException absent){position=mc.mouseHandler.getClass().getDeclaredField(coordinate[1]);}
                    position.setAccessible(true);
                    double value=coordinate[0].equals("xpos")?mc.getWindow().getGuiScaledWidth()/2-150:84+7*13+3;
                    double scale=coordinate[0].equals("xpos")?(double)mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth():(double)mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
                    position.setDouble(mc.mouseHandler,value*scale);
                }
                if(++ticks<40)return;
                Field hovered=mc.screen.getClass().getDeclaredField("hovered");hovered.setAccessible(true);
                check(hovered.get(mc.screen)!=null,"Actual trait screen hover detail rendered");
                Screenshot.grab(mc.gameDirectory,"doomed-ru-traits.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen(new TitleScreen());pending=true;mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");
                mc.reloadResourcePacks().thenRun(()->mc.execute(()->{pending=false;phase=5;}));return;
            }
            if(phase==5){english(mc);RESULT.addProperty("checks",checks);RESULT.addProperty("status","passed");save();phase=6;mc.stop();}
        } catch(Throwable failure) {
            failure.printStackTrace();RESULT.addProperty("status","failed");RESULT.addProperty("error",failure.toString());save();phase=6;mc.stop();
        }
    }
    private static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);checks++;}
    private static void battleDialogue(Object game)throws Exception {
        Class<?> phase=Class.forName("net.mattlives.doomedmatu.minigame.MatuEncounter$Phase");
        Object menu=Arrays.stream(phase.getEnumConstants()).filter(x->x.toString().equals("MENU")).findFirst().orElseThrow();
        Method dialogue=game.getClass().getDeclaredMethod("dialogue",String[].class,boolean.class,phase);dialogue.setAccessible(true);
        dialogue.invoke(game,new String[]{"my turn."},true,menu);
        check(game.getClass().getField("text").get(game).equals(RussianText.active()?"мой ход.":"my turn."),"Battle full text translated before reveal state");
    }
    private static void rules(Screen screen)throws Exception {
        Field field=screen.getClass().getDeclaredField("all");field.setAccessible(true);List<?> entries=(List<?>)field.get(screen);
        int checked=0;
        for(Object entry:entries) {
            Method section=entry.getClass().getDeclaredMethod("section"), comment=entry.getClass().getDeclaredMethod("comment");
            section.setAccessible(true);comment.setAccessible(true);
            if(!section.invoke(entry).toString().startsWith("Doomed /"))continue;
            String english=(String)comment.invoke(entry);
            if(english.isEmpty())continue;
            String russian=RussianText.ruleComment(english);
            check(!russian.equals(english)&&russian.matches("(?s).*[А-Яа-яЁё].*"),"Original config comment translated: "+english.substring(0,Math.min(70,english.length())));
            checked++;
        }
        check(checked>100,"Original config descriptions included in world rules");RESULT.addProperty("server_rule_comments_checked",checked);
    }
    private static Screen codexScreen(int page)throws Exception {
        Screen screen=(Screen)Class.forName("net.mattlives.doomedmatu.client.screen.CodexScreen").getConstructor().newInstance();
        Field view=screen.getClass().getDeclaredField("view");view.setAccessible(true);Object value=view.get(screen);
        Field index=value.getClass().getDeclaredField("page");index.setAccessible(true);index.setInt(value,page);
        return screen;
    }
    private static Object invoke(String owner,String name,Class<?>[] types,Object... args)throws Exception {
        Method m=Class.forName(owner).getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static void russian(Minecraft mc)throws Exception {
        // Force every declared target through the real Mixin transformer and JVM verifier.
        try(Reader r=new InputStreamReader(Objects.requireNonNull(ClientAudit.class.getResourceAsStream("/doomed_ru_fresh.targets.json")),StandardCharsets.UTF_8)) {
            JsonArray targets=JsonParser.parseReader(r).getAsJsonArray();
            int loaded=0;JsonArray unavailable=new JsonArray();
            for(JsonElement target:targets) {
                String name=target.getAsString();
                String optional=name.contains(".compat.emi.")?"emi":name.contains(".compat.jei.")?"jei":name.contains(".compat.jade.")?"jade":null;
                if(optional!=null&&!net.minecraftforge.fml.ModList.get().isLoaded(optional)){unavailable.add(name);continue;}
                Class.forName(name,false,ClientAudit.class.getClassLoader());loaded++;
            }
            RESULT.addProperty("transformed_classes_loaded",loaded);RESULT.add("optional_targets_unavailable",unavailable);check(true,"All available targets verified by JVM");
        }
        check(RussianText.translate("Cardiac Arrest").equals("Остановка сердца"),"Exact medical translation");
        check(RussianText.translate("BP 120/80").contains("120/80"),"Dynamic clinical readings preserved");
        check(RussianText.translate("Unrelated external-mod text").equals("Unrelated external-mod text"),"Unknown text pass-through");
        check(RussianText.translate("VISUAL & MOTION / PRESENTATION").contains("ЗРЕНИЕ И ДВИЖЕНИЕ"),"Joined static UI labels translated");
        for(String label:List.of("VISUAL & MOTION","AUDIO","APPEARANCE","GAMEPLAY ASSISTANCE")) {
            String marked=RussianText.translate(label+"  •");
            check(!marked.startsWith(label)&&marked.endsWith("  •"),"Modified category keeps marker after translation");
        }
        for(String[] pair:new String[][]{{"BLD O+","O+"},{"CONSC","СОЗНАНИЕ"},{"INT BLEED","ВНУТР. КРВТ."},{"IMMUNE","ИММУНИТЕТ"},{"RUN","OFF"},{"Open Body Appearance","Спрайт тела"},{"UNI-HEALTH™","UNI-HEALTH™"}})
            check(RussianText.translate(pair[0]).equals(pair[1]),"Requested display label: "+pair[0]);
        check(RussianText.ruleComment("Foreign addon description.\nRange: 0 ~ 4").equals("Foreign addon description.\nRange: 0 ~ 4"),"Foreign rule comment untouched");
        battleDialogue(Class.forName("net.mattlives.doomedmatu.minigame.MatuEncounter").getConstructor(long.class,int.class).newInstance(73L,0));
        Class<?> trait=Class.forName("net.mattlives.doomedmatu.body.Trait");
        for(Object value:trait.getEnumConstants())for(String part:List.of("title","blurb","effect")) {
            String english=(String)trait.getMethod(part).invoke(value);
            check(!RussianText.translate(english).equals(english),"Trait text translated: "+value+"."+part);
        }
        check(RussianText.traitDescription("Claws + Fur").equals("Когти + Мех"),"Joined selected traits translated");
        check(RussianText.serverComponent(Component.literal("Cardiac Arrest")).getString().equals("Cardiac Arrest"),"Generic foreign system labels untouched");
        check(RussianText.serverComponent(Component.literal("You have nothing that would cut through bone.")).getString().matches("(?s).*[А-Яа-я].*"),"Original Doomed server message recognized");
        Screen npc=(Screen)Class.forName("net.mattlives.doomedmatu.client.screen.NpcDialogueScreen")
            .getConstructor(int.class,String.class,String.class,String.class,List.class)
            .newInstance(42,"External Name","intro_node","Cardiac Arrest",List.of());
        Field npcText=npc.getClass().getDeclaredField("text");npcText.setAccessible(true);
        Field npcId=npc.getClass().getDeclaredField("nodeId");npcId.setAccessible(true);
        check(npcText.get(npc).equals("Остановка сердца"),"NPC body translated before typewriter count");
        check(npcId.get(npc).equals("intro_node"),"NPC routing identifier preserved");
        Component input=Component.literal("Cardiac Arrest").withStyle(ChatFormatting.RED,ChatFormatting.BOLD)
            .append(Component.literal("Head Injury").withStyle(ChatFormatting.ITALIC));
        Component output=RussianText.component(input);
        check(output.getStyle().equals(input.getStyle()),"Parent style preserved");
        check(output.getSiblings().get(0).getStyle().equals(input.getSiblings().get(0).getStyle()),"Nested style preserved");
        check(output.getString().equals("Остановка сердцаТравма головы"),"Nested literal translation");
        check(input.getString().equals("Cardiac ArrestHead Injury"),"Original component preserved");
        check(Component.translatable("key.doomedmatu.open_codex").getString().equals("Открыть полевой справочник"),"Ordinary language key loaded");
        String moodle=(String)invoke("net.mattlives.doomedmatu.client.ClientMoodleText","resolve",new Class<?>[]{String.class,int.class,int.class,String.class},"cardiacarrest",0,0,"fallback");
        check(moodle.equals("Остановка сердца"),"Russian Moodle resource selected");
        Object lines=invoke("net.mattlives.doomedmatu.client.ClientLeutenDialogueResources","lines",new Class<?>[0]);
        List<?> greeting=(List<?>)lines.getClass().getMethod("join").invoke(lines);
        check(!greeting.isEmpty()&&greeting.get(0).toString().matches("(?s).*[А-Яа-яЁё].*"),"Leuten dialogue resource selected");
        Class<?> body=Class.forName("net.mattlives.doomedmatu.body.BodyPart");
        String part=(String)body.getMethod("displayName").invoke(body.getEnumConstants()[0]);
        check(part.equals("Голова"),"Body-part name translated on access");
        String sample="Exactly one lead is safe. Count the leads, then read down until a rule fits:";
        for(int budget:new int[]{80,160,280}) {
            List<?> wrapped=(List<?>)invoke("net.mattlives.doomedmatu.client.screen.defusal.CodexContent","wrap",new Class<?>[]{Font.class,String.class,int.class},mc.font,sample,budget);
            String full=String.join(" ",wrapped.stream().map(Object::toString).toList()).replaceAll("\\s+"," ").trim();
            check(full.equals(RussianText.translate(sample).replaceAll("\\s+"," ").trim()),"Complete translation before custom wrapping");
            for(Object line:wrapped)check(mc.font.width(line.toString())<=budget,"Russian wrap respects real font width");
        }
        for(int budget:new int[]{16,32,80,160}) {
            String fitted=(String)invoke("net.mattlives.doomedmatu.client.screen.DoomedSettingWidgets","fit",new Class<?>[]{Font.class,String.class,int.class},mc.font,"Cardiac Arrest",budget);
            check(mc.font.width(fitted)<=budget,"Russian text clipped within real pixel budget");
            check(!fitted.contains("Cardiac"),"Clipping does not precede translation");
        }
        Class<?> category=Class.forName("net.mattlives.doomedmatu.client.screen.DoomedSettingsScreen$Category");
        Class<?> entry=Class.forName("net.mattlives.doomedmatu.client.screen.DoomedSettingsScreen$SearchEntry");
        Constructor<?> constructor=entry.getDeclaredConstructor(String.class,String.class,String.class,category,String.class,BooleanSupplier.class,Runnable.class);constructor.setAccessible(true);
        Object search=constructor.newInstance("screen_shake","Screen Shake","Reduce screen motion.",category.getEnumConstants()[0],"motion",(BooleanSupplier)()->false,(Runnable)()->{});
        Method matches=entry.getDeclaredMethod("matches",String.class);matches.setAccessible(true);
        for(String query:new String[]{"тряска","shake","motion"})check((Boolean)matches.invoke(search,query),"Russian/English/alias search: "+query);
        invoke("net.mattlives.doomedmatu.client.ClientQuipStore","put",new Class<?>[]{int.class,int.class,int.class,int.class,long.class,String.class,String.class,boolean.class},42,0,0,-1,100L,"Cardiac Arrest","Head Injury",true);
        Object reveal=invoke("net.mattlives.doomedmatu.client.ClientQuipStore","currentSelfReveal",new Class<?>[]{int.class,long.class},42,102L);
        check(reveal!=null,"Quip reveal exists");
        check(reveal.getClass().getMethod("fullText").invoke(reveal).equals("Остановка сердца"),"Complete quip translated before reveal");
        check(reveal.getClass().getMethod("visibleText").invoke(reveal).toString().startsWith("О"),"Partial quip uses Cyrillic text");
        RESULT.addProperty("russian_assertions",checks);
    }
    private static void english(Minecraft mc)throws Exception {
        battleDialogue(Class.forName("net.mattlives.doomedmatu.minigame.MatuEncounter").getConstructor(long.class,int.class).newInstance(73L,0));
        check(RussianText.translate("Cardiac Arrest").equals("Cardiac Arrest"),"English pass-through");
        Class<?> body=Class.forName("net.mattlives.doomedmatu.body.BodyPart");
        String name=(String)body.getMethod("displayName").invoke(body.getEnumConstants()[0]);
        check(name.equals("Head"),"Body-part names switch back without restarting");
        String moodle=(String)invoke("net.mattlives.doomedmatu.client.ClientMoodleText","resolve",new Class<?>[]{String.class,int.class,int.class,String.class},"cardiacarrest",0,0,"fallback");
        check(moodle.equals("Cardiac Arrest"),"English Moodle resource restored after reload");
        check(Component.translatable("key.doomedmatu.open_codex").getString().equals("Open Field Codex"),"English language resource restored");
    }
    private static void save() {
        try { Files.writeString(Path.of("../audit/client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static final class CodexAuditScreen extends Screen {
        private final int page;
        CodexAuditScreen(int page){super(Component.literal("Translation audit"));this.page=page;}
        @Override public void render(GuiGraphics graphics,int mx,int my,float delta) {
            graphics.fill(0,0,width,height,0xFF10151E);
            try {
                Class<?> content=Class.forName("net.mattlives.doomedmatu.client.screen.defusal.CodexContent");
                Object pg=content.getMethod("page",int.class).invoke(null,page);
                Class<?> api=Class.forName("net.mattlives.doomedmatu.client.screen.defusal.CodexContent$Page");
                String title=(String)api.getMethod("title").invoke(pg);
                graphics.drawString(font,RussianText.translate(title),18,12,0xFFEEEEEE,false);
                api.getMethod("body",GuiGraphics.class,Font.class,int.class,int.class,int.class,int.class)
                    .invoke(pg,graphics,font,18,32,width-36,height-50);
            }catch(Exception e){throw new IllegalStateException(e);}
        }
    }
    private static final class TraitAuditScreen extends Screen {
        private Screen real;
        TraitAuditScreen(){super(Component.literal("Trait audit"));}
        @Override protected void init() {
            try {
                real=(Screen)Class.forName("net.mattlives.doomedmatu.client.screen.TraitSelectScreen").getConstructor(boolean.class).newInstance(true);
                real.init(minecraft,width,height);
            }catch(Exception e){throw new IllegalStateException(e);}
        }
        @Override public void render(GuiGraphics graphics,int mx,int my,float delta) {
            real.render(graphics,width/2-150,84+7*13+3,delta);
        }
    }
}
