package local.doomed.russianfresh;

import java.util.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

/** Only original Doomed classes are transformed. Minecraft and other mods are never targets. */
public final class TextPlugin implements IMixinConfigPlugin {
    private static final String HELPER="local/doomed/russianfresh/RussianText";
    private static final String STRING="Ljava/lang/String;";
    private static final String COMPONENT="Lnet/minecraft/network/chat/Component;";
    private static final String FORMATTED="Lnet/minecraft/network/chat/FormattedText;";
    private static final Map<String,Set<String>> ENTRIES=new HashMap<>();
    @Override public void onLoad(String mixinPackage) {
        try(Reader r=new InputStreamReader(Objects.requireNonNull(TextPlugin.class.getResourceAsStream("/doomed_ru_fresh.entries.json")),StandardCharsets.UTF_8)) {
            JsonParser.parseReader(r).getAsJsonObject().entrySet().forEach(e->{
                Set<String> signatures=new HashSet<>();for(JsonElement s:e.getValue().getAsJsonArray())signatures.add(s.getAsString());
                ENTRIES.put(e.getKey(),signatures);
            });
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        // The client configuration is not applied on a dedicated server.
        return target.startsWith("net.mattlives.doomedmatu.");
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){transform(node);}
    public static void transform(ClassNode node) {
        if(!node.name.startsWith("net/mattlives/doomedmatu/"))throw new IllegalArgumentException("Foreign translation target: "+node.name);
        for(MethodNode method:node.methods) {
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            if(node.name.endsWith("/CodexContent$1") && method.name.equals("body")) { codex(node,method); continue; }
            if(node.name.endsWith("/TraitSelectScreen") && (method.name.equals("m_88315_")||method.name.equals("render")))traitDetails(node,method);
            if(node.name.endsWith("/DoomedSettingsScreen$SearchEntry") && method.name.equals("matches")) bilingualSearch(node,method);
            if(node.name.endsWith("/NpcDialogueScreen") && method.name.equals("<init>")) {
                // Keep packet routing IDs and the speaker untouched. Translate the full body before reveal counts.
                InsnList edit=new InsnList();edit.add(new VarInsnNode(Opcodes.ALOAD,4));edit.add(translation(STRING));edit.add(new VarInsnNode(Opcodes.ASTORE,4));
                method.instructions.insert(edit);method.maxStack+=1;
            }
            if(ENTRIES.getOrDefault(node.name,Set.of()).contains(method.name+method.desc))translateEntry(method);
            for(AbstractInsnNode insn:method.instructions.toArray()) {
                if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
                    && node.name.endsWith("/minigame/MatuEncounter") && field.name.equals("text") && field.desc.equals(STRING)) {
                    method.instructions.insertBefore(insn,translation(STRING));
                }
                if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETSTATIC
                    && (node.name.endsWith("/ClientMoodleText") || node.name.endsWith("/ClientOripathyPresentation") || node.name.endsWith("/ClientLeutenDialogueResources"))
                    && (field.name.equals("FILE")||field.name.equals("COPY"))) {
                    method.instructions.insert(insn,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"resource","(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceLocation;",false));
                }
                if(!(insn instanceof MethodInsnNode call))continue;
                if(node.name.contains("/client/") && call.owner.equals("net/mattlives/doomedmatu/body/Trait")) {
                    if(Set.of("title","blurb","effect").contains(call.name))method.instructions.insert(call,translation(STRING));
                    else if(call.name.equals("describe"))method.instructions.insert(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"traitDescription","("+STRING+")"+STRING,false));
                }
                if(node.name.endsWith("/RulesScreen") && call.owner.endsWith("/RulesScreen$Entry") && call.name.equals("comment")) {
                    String helper=method.name.equals("visible")?"searchableRuleComment":"ruleComment";
                    method.instructions.insert(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,helper,"("+STRING+")"+STRING,false));
                }
                boolean component=call.owner.equals("net/minecraft/network/chat/Component")&&(call.name.equals("m_237113_")||call.name.equals("literal"));
                boolean font=call.owner.equals("net/minecraft/client/gui/Font");
                boolean graphics=call.owner.equals("net/minecraft/client/gui/GuiGraphics");
                boolean format=call.owner.equals("java/lang/String")&&call.name.equals("format");
                if((component && !method.name.equals("<clinit>"))||font||graphics||(format&&!method.name.equals("<clinit>")))translateCall(method,call);
                if((call.owner.equals("net/minecraft/network/chat/Component")||call.owner.equals("net/minecraft/network/chat/MutableComponent"))
                    && (call.name.equals("m_7532_")||call.name.equals("getVisualOrderText"))) {
                    InsnList edit=new InsnList();edit.add(translation(COMPONENT));
                    if(call.owner.endsWith("/MutableComponent"))edit.add(new TypeInsnNode(Opcodes.CHECKCAST,call.owner));
                    method.instructions.insertBefore(call,edit);
                }
                if(node.name.endsWith("/PatientExamScreen") && graphics && (call.name.equals("m_280430_")||call.name.equals("drawString"))
                    && call.desc.equals("(Lnet/minecraft/client/gui/Font;"+COMPONENT+"III)I")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);call.owner=HELPER;call.name="patientText";
                    call.desc="(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;"+COMPONENT+"III)I";call.itf=false;
                }
            }
            if(node.name.endsWith("/BodyPart")&&method.name.equals("displayName")) {
                for(AbstractInsnNode insn:method.instructions.toArray())if(insn.getOpcode()==Opcodes.ARETURN)
                    method.instructions.insertBefore(insn,translation(STRING));
            }
        }
    }
    private static MethodInsnNode translation(String descriptor) {
        String name=descriptor.equals(STRING)?"translate":descriptor.equals(FORMATTED)?"formatted":"component";
        return new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,name,"("+descriptor+")"+descriptor,false);
    }
    private static boolean text(Type type){String d=type.getDescriptor();return d.equals(STRING)||d.equals(COMPONENT)||d.equals(FORMATTED);}
    private static void translateCall(MethodNode method,MethodInsnNode call) {
        Type[] args=Type.getArgumentTypes(call.desc);
        if(Arrays.stream(args).noneMatch(TextPlugin::text))return;
        InsnList edit=new InsnList();int[] locals=new int[args.length];
        for(int i=args.length-1;i>=0;i--) {
            locals[i]=method.maxLocals;method.maxLocals+=args[i].getSize();
            edit.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE),locals[i]));
        }
        for(int i=0;i<args.length;i++) {
            edit.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),locals[i]));
            if(text(args[i]))edit.add(translation(args[i].getDescriptor()));
        }
        method.instructions.insertBefore(call,edit);method.maxStack+=2;
    }
    private static void translateEntry(MethodNode method) {
        InsnList edit=new InsnList();int local=(method.access&Opcodes.ACC_STATIC)!=0?0:1;
        for(Type type:Type.getArgumentTypes(method.desc)) {
            if(type.getDescriptor().equals(STRING)) {
                edit.add(new VarInsnNode(Opcodes.ALOAD,local));edit.add(translation(STRING));edit.add(new VarInsnNode(Opcodes.ASTORE,local));
            }
            local+=type.getSize();
        }
        method.instructions.insert(edit);method.maxStack+=1;
    }
    private static void bilingualSearch(ClassNode owner,MethodNode method) {
        // Preserve the original fields and English aliases. Extend the local haystack only.
        for(AbstractInsnNode insn:method.instructions.toArray()) {
            if(!(insn instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.ASTORE||store.var!=2)continue;
            InsnList edit=new InsnList();
            for(String field:List.of("title","description")) {
                edit.add(new VarInsnNode(Opcodes.ALOAD,0));edit.add(new FieldInsnNode(Opcodes.GETFIELD,owner.name,field,STRING));
            }
            edit.add(new VarInsnNode(Opcodes.ALOAD,0));
            edit.add(new FieldInsnNode(Opcodes.GETFIELD,owner.name,"category","Lnet/mattlives/doomedmatu/client/screen/DoomedSettingsScreen$Category;"));
            edit.add(new FieldInsnNode(Opcodes.GETFIELD,"net/mattlives/doomedmatu/client/screen/DoomedSettingsScreen$Category","label",STRING));
            edit.add(new VarInsnNode(Opcodes.ALOAD,0));edit.add(new FieldInsnNode(Opcodes.GETFIELD,owner.name,"aliases",STRING));
            edit.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"searchableFields","("+STRING+STRING+STRING+STRING+")"+STRING,false));
            // Replace the computed English haystack on the stack, leaving the subsequent matching loop intact.
            edit.insert(new InsnNode(Opcodes.POP));method.instructions.insertBefore(store,edit);method.maxStack+=4;return;
        }
        throw new IllegalStateException("Doomed settings search shape changed");
    }
    private static void traitDetails(ClassNode owner,MethodNode method) {
        int x=-1,y=-1;JumpInsnNode guard=null;
        for(AbstractInsnNode insn:method.instructions.toArray()) {
            if(insn instanceof MethodInsnNode call && call.owner.equals(owner.name) && call.getNext() instanceof VarInsnNode store && store.getOpcode()==Opcodes.ISTORE) {
                if(call.name.equals("leftX"))x=store.var;
                if(call.name.equals("detailTop"))y=store.var;
            }
            if(insn instanceof JumpInsnNode jump && jump.getOpcode()==Opcodes.IFNULL && jump.getPrevious() instanceof FieldInsnNode field && field.name.equals("hovered"))guard=jump;
        }
        if(guard==null||x<0||y<0)throw new IllegalStateException("Trait detail layout changed");
        AbstractInsnNode cursor=guard.getNext();LabelNode detailEnd=null;
        while(cursor!=guard.label) {
            AbstractInsnNode next=cursor.getNext();
            if(cursor instanceof JumpInsnNode jump && jump.getOpcode()==Opcodes.GOTO)detailEnd=jump.label;
            method.instructions.remove(cursor);cursor=next;
        }
        if(detailEnd==null)throw new IllegalStateException("Trait detail branch changed");
        InsnList edit=new InsnList();edit.add(new VarInsnNode(Opcodes.ALOAD,1));edit.add(new VarInsnNode(Opcodes.ALOAD,0));
        edit.add(new FieldInsnNode(Opcodes.GETFIELD,owner.name,"hovered","Lnet/mattlives/doomedmatu/body/Trait;"));
        edit.add(new VarInsnNode(Opcodes.ILOAD,x));edit.add(new VarInsnNode(Opcodes.ILOAD,y));
        edit.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"traitDetail","(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)V",false));
        edit.add(new JumpInsnNode(Opcodes.GOTO,detailEnd));
        method.instructions.insert(guard,edit);method.localVariables=null;method.maxStack+=1;
    }
    private static void codex(ClassNode owner,MethodNode method) {
        FieldNode lines=owner.fields.stream().filter(f->f.desc.equals("Ljava/util/List;")).findFirst().orElseThrow();
        method.instructions.clear();method.tryCatchBlocks.clear();
        if(method.localVariables!=null)method.localVariables.clear();
        // Invoke the height-aware renderer with the original stored page; it remains language-neutral.
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
        for(int local=3;local<=6;local++)method.instructions.add(new VarInsnNode(Opcodes.ILOAD,local));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,owner.name,lines.name,lines.desc));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"codexBody","(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIILjava/util/List;)V",false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));method.maxLocals=7;method.maxStack=7;
    }
}
