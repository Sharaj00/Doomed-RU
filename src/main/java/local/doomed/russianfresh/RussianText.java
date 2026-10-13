package local.doomed.russianfresh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.*;
import net.minecraft.resources.ResourceLocation;

public final class RussianText {
    private record Template(Pattern pattern, String replacement, int weight, boolean server, boolean joinedLabels) {}
    private static final Map<String,String> EXACT = new HashMap<>();
    private static final List<Template> TEMPLATES = new ArrayList<>();
    private static final Set<String> SERVER=new HashSet<>();
    private static final Map<String,String> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(512,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest) { return size()>8192; }
    });
    private static final String ROOT = "/assets/doomed_ru_fresh/";
    static {
        try(Reader r=new InputStreamReader(Objects.requireNonNull(RussianText.class.getResourceAsStream(ROOT+"server_messages.json")),StandardCharsets.UTF_8)) {
            for(JsonElement value:JsonParser.parseReader(r).getAsJsonArray())SERVER.add(value.getAsString());
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(RussianText.class.getResourceAsStream(ROOT + "literal_ru.json")), StandardCharsets.UTF_8)) {
            JsonObject dictionary = JsonParser.parseReader(reader).getAsJsonObject();
            dictionary.entrySet().forEach(e -> {
                String source = e.getKey(), target = e.getValue().getAsString();
                if (source.indexOf('\u0001') < 0) { EXACT.put(source,target); return; }
                StringBuilder pattern = new StringBuilder("\\A");
                int offset=0, index=0, weight=0;
                for (int i=0;i<source.length();i++) {
                    if (source.charAt(i) != '\u0001') continue;
                    String literal=source.substring(offset,i); weight+=literal.length();
                    pattern.append(Pattern.quote(literal)).append("(?<v").append(index++).append(">.*?)"); offset=i+1;
                }
                pattern.append(Pattern.quote(source.substring(offset))).append("\\z");
                weight += source.length()-offset;
                // Generic concatenation and numeric format recipes are not translation rules.
                boolean joinedLabels=source.replace('\u0001',' ').matches("[\\s/|·:–-]+");
                if (weight < 3 && !joinedLabels) return;
                TEMPLATES.add(new Template(Pattern.compile(pattern.toString(),Pattern.DOTALL),target,weight,SERVER.contains(source),joinedLabels));
            });
            TEMPLATES.sort(Comparator.comparingInt(Template::weight).reversed());
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private RussianText() {}
    public static boolean active() {
        Minecraft mc=Minecraft.getInstance();
        return mc != null && mc.options != null && "ru_ru".equals(mc.options.languageCode);
    }
    public static String translate(String source) {
        if (!active() || source == null || source.isEmpty()) return source;
        return lookup(source);
    }
    static String lookup(String source) {
        String cached=CACHE.get(source);
        if(cached!=null)return cached;
        String result=uncached(source);CACHE.put(source,result);return result;
    }
    private static String uncached(String source) {
        String exact=EXACT.get(source);
        if (exact != null) return exact;
        Matcher marked=Pattern.compile("^(.*?)(\\s+[•·])$").matcher(source);
        if(marked.matches()) {
            String label=EXACT.get(marked.group(1));
            if(label!=null)return label+marked.group(2);
        }
        if(source.endsWith(" 🔒")) {
            String label=EXACT.get(source.substring(0,source.length()-3));
            if(label!=null)return label+" 🔒";
        }
        // Category breadcrumbs join two separately audited labels at render time.
        Matcher breadcrumb=Pattern.compile("^(.+?)(\\s+[/|·]\\s+)(.+)$").matcher(source);
        if(breadcrumb.matches()) {
            String left=EXACT.get(breadcrumb.group(1)), right=EXACT.get(breadcrumb.group(3));
            if(left!=null && right!=null)return left+breadcrumb.group(2)+right;
        }
        for (Template template:TEMPLATES) {
            Matcher m=template.pattern.matcher(source);
            if (!m.matches()) continue;
            String target=template.replacement;
            // Numbered tokens permit Russian word order without exchanging dynamic fields.
            Matcher token=Pattern.compile("\\{(\\d+)\\}").matcher(target);
            StringBuilder out=new StringBuilder();
            while(token.find()) {
                int index=Integer.parseInt(token.group(1));
                if(index>=m.groupCount())return source;
                String field=m.group("v"+index);
                field=EXACT.getOrDefault(field,field);
                token.appendReplacement(out,Matcher.quoteReplacement(field));
            }
            token.appendTail(out);return out.toString();
        }
        return source;
    }
    public static Component component(Component original) {
        if (!active() || original==null) return original;
        MutableComponent result;
        if (original.getContents() instanceof LiteralContents literal) {
            result=Component.literal(translate(literal.text()));
        } else if (original.getContents() instanceof TranslatableContents translatable && translatable.getKey().contains("doomedmatu")) {
            Object[] args=translatable.getArgs().clone();
            for(int i=0;i<args.length;i++) {
                if(args[i] instanceof Component c) args[i]=component(c);
                else if(args[i] instanceof String s) args[i]=translate(s);
            }
            result=Component.translatableWithFallback(translatable.getKey(),translatable.getFallback(),args);
        } else result=original.plainCopy();
        result.setStyle(original.getStyle());
        for(Component sibling:original.getSiblings())result.append(component(sibling));
        return result;
    }
    public static Component serverComponent(Component original) {
        if(!active()||original==null)return original;
        MutableComponent result=original.plainCopy();
        if(original.getContents() instanceof LiteralContents literal) {
            String source=literal.text();boolean known=SERVER.contains(source);
            if(!known)for(Template template:TEMPLATES)if(template.server&&template.pattern.matcher(source).matches()){known=true;break;}
            if(known)result=Component.literal(translate(source));
        }
        result.setStyle(original.getStyle());
        for(Component sibling:original.getSiblings())result.append(serverComponent(sibling));
        return result;
    }
    public static ResourceLocation resource(ResourceLocation original) {
        if (!active() || !original.getNamespace().equals("doomedmatu")) return original;
        String path=original.getPath();
        if(path.equals("moodle/text.json") || path.equals("oripathy/presentation.json") || path.equals("dialogue/leuten_coded.json"))
            return new ResourceLocation("doomed_ru_fresh",path);
        return original;
    }
    public static FormattedText formatted(FormattedText text) {
        return text instanceof Component c ? component(c) : text;
    }
    public static String searchable(String english) {
        return active() ? english+" "+translate(english) : english;
    }
    public static String ruleComment(String source) {
        if(!active()||source==null||source.isEmpty())return source;
        String normalized=source.replaceAll("\\s+"," ").trim();
        String translated=EXACT.get(normalized);
        if(translated!=null)return translated;
        // Forge appends generated range metadata to the original config comment.
        Matcher suffix=Pattern.compile("^(.*?)( (?:Range|Allowed Values): .*)$",Pattern.DOTALL).matcher(normalized);
        if(suffix.matches()) {
            translated=EXACT.get(suffix.group(1));
            if(translated!=null)return translated+suffix.group(2).replace("Range:","Диапазон:").replace("Allowed Values:","Допустимые значения:");
        }
        return source;
    }
    public static String searchableRuleComment(String english) {
        return active()?english+" "+ruleComment(english):english;
    }
    public static String traitDescription(String english) {
        if(!active())return english;
        return String.join(" + ",Arrays.stream(english.split(" \\+ ")).map(RussianText::translate).toList());
    }
    public static void traitDetail(GuiGraphics gg,Object trait,int x,int y) {
        try {
            Font font=Minecraft.getInstance().font;
            Class<?> type=trait.getClass();
            String title=translate((String)type.getMethod("title").invoke(trait));
            String blurb=translate((String)type.getMethod("blurb").invoke(trait));
            String effect=translate((String)type.getMethod("effect").invoke(trait));
            boolean perk=(boolean)type.getMethod("isPerk").invoke(trait);
            gg.fill(x-3,y-4,x+369,y+54,0x66000000);
            gg.drawString(font,title,x,y,perk?-6301280:-2068132,false);
            float scale=1;List<net.minecraft.util.FormattedCharSequence> a,b;
            while(true) {
                int width=Math.max(1,(int)(366/scale));
                a=font.split(Component.literal(blurb),width);b=font.split(Component.literal(effect),width);
                if((a.size()+b.size())*9*scale+2<=43)break;
                scale*=0.94f;
            }
            gg.pose().pushPose();gg.pose().translate(x,y+11,0);gg.pose().scale(scale,scale,1);
            int cy=0;
            for(var line:a){gg.drawString(font,line,0,cy,-6643028,false);cy+=9;}
            cy+=2;
            for(var line:b){gg.drawString(font,line,0,cy,-3418925,false);cy+=9;}
            gg.pose().popPose();
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Original trait text API changed",e);}
    }
    public static String searchableFields(String title,String description,String category,String aliases) {
        return (title+" "+description+" "+category+" "+aliases+" "+translate(title)+" "+translate(description)+" "+translate(category)).toLowerCase(Locale.ROOT);
    }
    public static void codexBody(GuiGraphics gg, Font font, int x,int y,int width,int height,List<String> source) {
        if(width<=0 || height<=0)return;
        List<String> translated=new ArrayList<>();
        for(String line:source)translated.add(translate(line));
        float scale=1;
        List<net.minecraft.util.FormattedCharSequence> lines=wrapPage(font,translated,width);
        // Shrinking increases the virtual width, reducing the required number of rows.
        for(int i=0;i<60 && lines.size()*11*scale>height;i++) {
            scale*=0.93f; lines=wrapPage(font,translated,(int)(width/scale));
        }
        gg.pose().pushPose(); gg.pose().translate(x,y,0); gg.pose().scale(scale,scale,1);
        int cy=0;
        for(var line:lines) { gg.drawString(font,line,0,cy,-2569306,false);cy+=11; }
        gg.pose().popPose();
    }
    private static List<net.minecraft.util.FormattedCharSequence> wrapPage(Font font,List<String> lines,int width) {
        List<net.minecraft.util.FormattedCharSequence> out=new ArrayList<>();
        for(String line:lines)out.addAll(font.split(Component.literal(line),Math.max(1,width)));
        return out;
    }
    public static int patientText(GuiGraphics gg,Font font,Component text,int x,int y,int color) {
        if(!active())return gg.drawString(font,text,x,y,color);
        int cardX=(Minecraft.getInstance().getWindow().getGuiScaledWidth()-318)/2;
        int cardY=(Minecraft.getInstance().getWindow().getGuiScaledHeight()-218)/2;
        int offset=x-cardX;
        int width=318-offset-10;
        if(y>cardY+24 && offset>=96 && offset<196)width=offset<142?42:196-offset-4;
        else if(y<=cardY+24 && offset==74)width=156;
        int actual=font.width(text);float scale=Math.min(1,(float)Math.max(1,width)/Math.max(1,actual));
        gg.pose().pushPose();gg.pose().translate(x,y,0);gg.pose().scale(scale,scale,1);
        gg.drawString(font,text,0,0,color);gg.pose().popPose();return Math.round(actual*scale);
    }
}
