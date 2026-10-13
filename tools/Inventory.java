import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;
import java.util.*;
import java.util.zip.*;
import java.nio.file.*;

public class Inventory {
    public static void main(String[] args) throws Exception {
        JsonArray constants = new JsonArray();
        JsonArray methods = new JsonArray();
        JsonArray classes = new JsonArray();
        try (ZipFile jar = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().startsWith("net/mattlives/doomedmatu/") || !entry.getName().endsWith(".class")) continue;
                ClassNode c = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(c, 0);
                boolean relevant = false;
                for (MethodNode m : c.methods) {
                    JsonObject method = new JsonObject();
                    method.addProperty("owner", c.name); method.addProperty("name", m.name);
                    method.addProperty("desc", m.desc); method.addProperty("access", m.access);
                    JsonArray calls = new JsonArray();
                    for (AbstractInsnNode insn : m.instructions) {
                        String text = null; boolean recipe = false;
                        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) text = s;
                        if (insn instanceof InvokeDynamicInsnNode indy && indy.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && indy.bsmArgs.length > 0) {
                            text = (String) indy.bsmArgs[0]; recipe = true;
                        }
                        if (text != null) {
                            JsonObject o = new JsonObject(); o.addProperty("owner", c.name); o.addProperty("method", m.name);
                            o.addProperty("text", text); o.addProperty("recipe", recipe); constants.add(o);
                        }
                        if (insn instanceof MethodInsnNode call) {
                            calls.add(call.owner + "." + call.name + call.desc);
                            if ((call.owner.equals("net/minecraft/network/chat/Component") && (call.name.equals("m_237113_") || call.name.equals("literal")))
                                || call.owner.equals("net/minecraft/client/gui/Font") || call.owner.equals("net/minecraft/client/gui/GuiGraphics")) relevant = true;
                        }
                    }
                    method.add("calls", calls); methods.add(method);
                }
                if (relevant && (c.access & Opcodes.ACC_INTERFACE) == 0) classes.add(c.name.replace('/', '.'));
            }
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(Path.of(args[1], "constants.json"), gson.toJson(constants));
        Files.writeString(Path.of(args[1], "methods.json"), gson.toJson(methods));
        Files.writeString(Path.of(args[1], "classes.json"), gson.toJson(classes));
    }
}
