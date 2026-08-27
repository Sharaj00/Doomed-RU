package ru.doomedru;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Словарь подстановки. Три механизма, в порядке применения:
 *   1) точное совпадение   "SKIN" -> "КОЖА"
 *   2) числовой шаблон     "100 BPM" -> ключ "{} BPM", числа возвращаются на место
 *   3) префикс             "EXT BLE..." и посимвольная анимация набора текста
 *
 * Формат файла:
 *   { "strings": {"en":"ru"}, "patterns": {"{} BPM":"{} уд/мин"} }
 * Плоский объект тоже принимается - считается секцией strings.
 */
public final class Dict {
    /**
     * Готовый к работе словарь. Собирается целиком при загрузке и подставляется
     * одной записью в volatile-поле: поток отрисовки видит либо старую версию,
     * либо новую, но никогда наполовину заполненную таблицу. Раньше `/doomedru
     * reload` чистил и заполнял те же самые HashMap, по которым в этот момент
     * читал рендер, - гонка вплоть до зависания на resize таблицы.
     *
     * @param exact      точные соответствия
     * @param pattern    шаблоны с {}
     * @param prefixable длинные фразы для префиксного режима: [en, ru]
     * @param regexes    скомпилированные шаблоны: [regex, русский шаблон, строгий ли]
     */
    private record Table(Map<String, String> exact,
                         Map<String, String> pattern,
                         List<String[]> prefixable,
                         List<Object[]> regexes) {
        static final Table EMPTY = new Table(Map.of(), Map.of(), List.of(), List.of());
    }

    private static volatile Table table = Table.EMPTY;

    /** Кэш всех запросов, включая промахи, чтобы не пересчитывать каждый кадр. */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    /**
     * Показания HUD дают бесконечный поток разных строк ("HR 88 // BP 120/80"),
     * поэтому кэш ограничен: при переполнении он сбрасывается целиком.
     */
    private static final int CACHE_LIMIT = 20000;
    /** Метка промаха. Сравнивается по ссылке, поэтому обычная строка её не подделает. */
    private static final String MISS = new String(" miss ");

    private static final Set<String> MISSES = ConcurrentHashMap.newKeySet();
    /** Строки, реально встречающиеся в коде Doomed. Всё остальное в дамп не попадает. */
    private static final Set<String> KNOWN = new HashSet<>();
    private static final Pattern NUM = Pattern.compile("\\d+(?:[.,]\\d+)?");
    /** Хвост курсора или многоточия у анимированного текста. */
    private static final Pattern TAIL = Pattern.compile("(\\.\\.\\.|[|_])$");
    /** Минимальная длина строки, для которой вообще рассматривается префикс. */
    private static final int PREFIX_MIN = 6;
    /**
     * Разделители составных подписей вида "Действие - Зона". Английский дефис
     * при сборке заменяется на тире: в русском тексте между словами стоит "—".
     */
    private static final String[][] SEPARATORS = {
            {" - ", " — "}, {" — ", " — "}, {" · ", " · "}, {": ", ": "}, {" / ", " / "}};

    private static volatile boolean enabled = true;
    /**
     * Защита коротких общих слов от подмены в GUI чужих модов.
     * Нужна при глобальном перехвате: он видит текст всей игры, а слова
     * вроде "Done" или "Combat" встречаются не только в Doomed. Длинные
     * фразы уникальны и подменяются всегда.
     * Выключается через config/doomedru/ru_ru.json: {"options":{"guardGeneric":false}}
     */
    private static volatile boolean guardGeneric =
            !"false".equalsIgnoreCase(System.getProperty("doomedru.globalHook"));
    private static volatile boolean collect = true;

    /** Счётчики для /doomedru: сколько строк прошло через перехват и сколько подменено. */
    private static final java.util.concurrent.atomic.AtomicLong SEEN = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong HITS = new java.util.concurrent.atomic.AtomicLong();

    private Dict() {}

    public static Path configDir() { return FMLPaths.CONFIGDIR.get().resolve("doomedru"); }

    public static synchronized int reload() {
        Map<String, String> exact = new HashMap<>();
        Map<String, String> pattern = new HashMap<>();
        if (KNOWN.isEmpty()) loadKnown();
        loadResource("/assets/doomedru/ru_ru.json", exact, pattern);
        loadFile(configDir().resolve("ru_ru.json"), exact, pattern);

        List<String[]> prefixable = new ArrayList<>();
        for (Map.Entry<String, String> e : exact.entrySet()) {
            if (e.getKey().length() >= 8) prefixable.add(new String[]{e.getKey(), e.getValue()});
        }
        prefixable.sort((a, b) -> b[0].length() - a[0].length());

        table = new Table(Map.copyOf(exact), Map.copyOf(pattern),
                          List.copyOf(prefixable), List.copyOf(buildRegexes(pattern)));
        CACHE.clear();
        DoomedRu.LOGGER.info("[DoomedRU] строк: {}, шаблонов: {}", exact.size(), pattern.size());
        return exact.size() + pattern.size();
    }

    /** Шаблон "HR {} // BP {}/{}" превращается в регулярное выражение с захватом подстановок. */
    private static List<Object[]> buildRegexes(Map<String, String> pattern) {
        List<Object[]> regexes = new ArrayList<>();
        List<Map.Entry<String, String>> list = new ArrayList<>(pattern.entrySet());
        // сначала самые «длинные» шаблоны, чтобы общий не перехватил частный
        list.sort((a, b) -> b.getKey().replace("{}", "").length() - a.getKey().replace("{}", "").length());
        for (Map.Entry<String, String> e : list) {
            String k = e.getKey();
            if (!k.contains("{}")) continue;
            StringBuilder rx = new StringBuilder("^");
            int i = 0;
            while (true) {
                int p = k.indexOf("{}", i);
                if (p < 0) { rx.append(Pattern.quote(k.substring(i))); break; }
                if (p > i) rx.append(Pattern.quote(k.substring(i, p)));
                rx.append("(.*?)");
                i = p + 2;
            }
            rx.append("$");
            try {
                // строгий вариант: на месте {} только число - для показаний HUD
                Pattern strict = Pattern.compile(rx.toString().replace("(.*?)", "(\\d+(?:[.,]\\d+)?)"), Pattern.DOTALL);
                regexes.add(new Object[]{strict, e.getValue(), Boolean.TRUE});
                // свободный вариант допускаем только для достаточно «длинных» шаблонов,
                // иначе короткий вроде "HR {}" перехватит целую строку показаний.
                // Порог 5, а не 8: иначе не проходили заметки по делу вида
                // "{} opened." - у них постоянная часть всего 7 знаков.
                if (k.replace("{}", "").trim().length() >= 5) {
                    regexes.add(new Object[]{Pattern.compile(rx.toString(), Pattern.DOTALL), e.getValue(), Boolean.FALSE});
                }
            } catch (Exception ignored) { }
        }
        return regexes;
    }

    private static void loadKnown() {
        try (InputStream in = Dict.class.getResourceAsStream("/assets/doomedru/known.txt")) {
            if (in == null) return;
            try (var r = new java.io.BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) if (!line.isEmpty()) KNOWN.add(unescape(line));
            }
            DoomedRu.LOGGER.info("[DoomedRU] известных строк Doomed: {}", KNOWN.size());
        } catch (Exception e) {
            DoomedRu.LOGGER.warn("[DoomedRU] known.txt: {}", e.toString());
        }
    }

    /**
     * Обратная операция к escape() из tools/extract_strings.py: одна запись
     * known.txt занимает ровно одну строку файла, поэтому переводы строк
     * внутри неё записаны как \n. Страница полевого журнала - как раз такой
     * случай: она приходит на отрисовку целым многоабзацным текстом.
     */
    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { b.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n'  -> b.append('\n');
                case 'r'  -> b.append('\r');
                case '\\' -> b.append('\\');
                default   -> b.append('\\').append(n);
            }
        }
        return b.toString();
    }

    private static void loadResource(String path, Map<String, String> exact, Map<String, String> pattern) {
        try (InputStream in = Dict.class.getResourceAsStream(path)) {
            if (in != null) {
                parse(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class),
                      exact, pattern);
            }
        } catch (Exception e) {
            DoomedRu.LOGGER.warn("[DoomedRU] встроенный словарь: {}", e.toString());
        }
    }

    private static void loadFile(Path p, Map<String, String> exact, Map<String, String> pattern) {
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, "{\n  \"strings\": {},\n  \"patterns\": {}\n}\n", StandardCharsets.UTF_8);
                return;
            }
            try (var r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                parse(new Gson().fromJson(r, JsonObject.class), exact, pattern);
            }
        } catch (Exception e) {
            DoomedRu.LOGGER.warn("[DoomedRU] config/doomedru/ru_ru.json: {}", e.toString());
        }
    }

    private static void parse(JsonObject o, Map<String, String> exact, Map<String, String> pattern) {
        if (o == null) return;
        if (o.has("options")) {
            JsonObject opt = o.getAsJsonObject("options");
            if (opt != null && opt.has("guardGeneric")) {
                try { guardGeneric = opt.get("guardGeneric").getAsBoolean(); } catch (Exception ignored) { }
            }
        }
        if (o.has("strings") || o.has("patterns")) {
            if (o.has("strings"))  put(o.getAsJsonObject("strings"),  exact);
            if (o.has("patterns")) put(o.getAsJsonObject("patterns"), pattern);
        } else {
            put(o, exact);
        }
    }

    private static void put(JsonObject o, Map<String, String> into) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            String v = e.getValue().getAsString();
            if (!v.isEmpty() && !v.equals(e.getKey())) into.put(e.getKey(), v);
        }
    }

    // -------------------------------------------------------------- вход

    public static String tr(String in) {
        if (!enabled || in == null || in.length() < 2) return in;
        SEEN.incrementAndGet();

        String c = CACHE.get(in);
        if (c != null) {
            //noinspection StringEquality - MISS опознаётся по ссылке, а не по содержимому
            if (c == MISS) return in;
            // короткое общее слово подменяем только там, где это точно Doomed
            if (isGeneric(in) && !ScreenContext.allowsGeneric()) return in;
            HITS.incrementAndGet();
            return c;
        }

        String out = compute(in);
        // показания HUD порождают неограниченный поток ключей - кэш не должен расти вечно
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(in, out == null ? MISS : out);
        if (out == null) {
            if (collect) miss(in);
            return in;
        }
        if (isGeneric(in) && !ScreenContext.allowsGeneric()) return in;
        HITS.incrementAndGet();
        return out;
    }

    /**
     * Короткая строка из одного-двух слов может принадлежать чужому моду:
     * "Settings", "Default", "Combat", "Chloride". Длинные фразы уникальны,
     * их подменяем всегда.
     */
    private static boolean isGeneric(String s) {
        if (!guardGeneric) return false;
        if (s.length() > 20) return false;
        if (s.contains("Doomed") || s.contains("DOOMED") || s.contains("WITNESS")) return false;
        int words = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ' ') words++;
        return words <= 2;
    }

    private static String compute(String in) {
        // 0. обрамляющие пробелы сохраняем, переводим ядро
        int a = 0, b = in.length();
        while (a < b && in.charAt(a) == ' ') a++;
        while (b > a && in.charAt(b - 1) == ' ') b--;
        if (a != 0 || b != in.length()) {
            String inner = compute(in.substring(a, b));
            return inner == null ? null : in.substring(0, a) + inner + in.substring(b);
        }

        String whole = simple(in);
        return whole != null ? whole : composite(in);
    }

    /**
     * Перевод строки целиком: точное совпадение, шаблоны, префикс, разделитель.
     * Не пытается разбирать текст на части - этим занимается composite().
     */
    private static String simple(String in) {
        // таблицу читаем один раз: перезагрузка словаря не должна менять её по ходу разбора
        Table t = table;

        // 1. точное совпадение
        String hit = t.exact().get(in);
        if (hit != null) return hit;

        // 2. числовой шаблон
        if (!t.pattern().isEmpty()) {
            Matcher m = NUM.matcher(in);
            List<String> nums = new ArrayList<>(4);
            StringBuilder key = new StringBuilder();
            int last = 0;
            while (m.find()) {
                key.append(in, last, m.start()).append("{}");
                nums.add(m.group());
                last = m.end();
            }
            if (!nums.isEmpty()) {
                key.append(in, last, in.length());
                String tpl = t.pattern().get(key.toString());
                if (tpl != null) return fill(tpl, nums);
            }
        }

        // 2б. шаблон с подстановкой: сперва строгие числовые, затем свободные
        for (int pass = 0; pass < 2; pass++) {
            boolean strictPass = pass == 0;
            for (Object[] r : t.regexes()) {
                if ((Boolean) r[2] != strictPass) continue;
                Matcher m = ((Pattern) r[0]).matcher(in);
                if (!m.matches()) continue;
                List<String> caught = new ArrayList<>(m.groupCount());
                for (int g = 1; g <= m.groupCount(); g++) caught.add(m.group(g));
                // В {} должно попадать значение, а не проза. Свободный шаблон
                // с ".*?" иначе съедает всё, что стоит перед ним: описание
                // состояния целиком уходило в подстановку шаблона
                // "{}% of what you try simply won't happen." и оставалось
                // английским, хотя в словаре оно есть.
                if (!strictPass && spansSentence(caught)) continue;
                return fill((String) r[1], caught);
            }
        }

        // 3. префикс: анимация набора текста и обрезка многоточием
        Matcher tail = TAIL.matcher(in);
        String core = in;
        String suffix = "";
        if (tail.find()) {
            core = in.substring(0, tail.start());
            suffix = tail.group();
        }
        if (!suffix.isEmpty()) {
            String whole = t.exact().get(core);
            if (whole != null) return whole + suffix;      // "EXT BLEED..." -> перевод + "..."
        }
        // Префикс годится, только если строка ЗАВЕДОМО оборвана:
        //   - есть хвост "..." или курсор;
        //   - или такой строки в моде нет вовсе (known.txt), а значит она
        //     родилась при отрисовке - это кадр анимации набора;
        //   - или обрыв пришёлся посреди слова.
        // Если же строка есть в моде как самостоятельная, резать перевод
        // нельзя. Именно из-за этого название мода "Doomed" превращалось в
        // "Снаряже": оно оказалось началом "Doomed gear is FOUND, ...".
        //
        // Проверка по known.txt заодно убирает мерцание: раньше кадр,
        // оборвавшийся на границе слова, показывался по-английски, а
        // следующий - по-русски, и текст моргал по мере набора. Теперь
        // все промежуточные кадры одинаково считаются обрывками.
        boolean fragment = !suffix.isEmpty()
                        || (!KNOWN.isEmpty() && !KNOWN.contains(core));
        if (core.length() >= PREFIX_MIN) {
            for (String[] pair : t.prefixable()) {
                if (pair[0].length() > core.length() && pair[0].startsWith(core)
                        && (fragment
                            || Character.isLetterOrDigit(pair[0].charAt(core.length())))) {
                    int cut = Math.round(pair[1].length() * (core.length() / (float) pair[0].length()));
                    cut = Math.max(1, Math.min(pair[1].length(), cut));
                    return pair[1].substring(0, cut) + suffix;
                }
            }
        }

        // 4. составные строки: "Действие - Зона", "Слева · Справа".
        //    Собираем перевод русским разделителем, а не английским.
        for (String[] sep : SEPARATORS) {
            int p = in.indexOf(sep[0]);
            if (p <= 0 || p + sep[0].length() >= in.length()) continue;
            String l = t.exact().get(in.substring(0, p));
            String r = t.exact().get(in.substring(p + sep[0].length()));
            if (l != null && r != null) return l + sep[1] + r;
        }
        return null;
    }

    /**
     * Составной текст: несколько предложений подряд, из которых словарю
     * известны не все.
     *
     * Мод склеивает описание состояния с текущими эффектами:
     *   "You are sitting in the dark of your own mind. Comfort and rest still
     *    pull you out... 48% of what you try simply won't happen. You move at
     *    59% speed."
     * Целиком такой строки в словаре нет и быть не может - число сочетаний
     * бесконечно, - поэтому раньше не переводилось ничего, включая описание,
     * которое в словаре есть.
     *
     * Идём слева направо и на каждом шаге берём самый длинный кусок, который
     * заканчивается на границе предложения и переводится целиком. Так
     * двухпредложное описание находится одним куском, а эффекты - каждый
     * своим. Непереведённые куски переносятся как есть.
     */
    private static String composite(String in) {
        int[] bounds = sentenceBounds(in);
        if (bounds.length < 2) return null;

        StringBuilder out = new StringBuilder(in.length() + 16);
        boolean any = false;
        int i = 0, from = 0;
        while (from < in.length()) {
            String hit = null;
            int next = -1;
            // От длинного куска к короткому: у состояния есть и краткое
            // описание, и полное из двух предложений, и брать надо полное.
            // Проглотить лишнее длинный кусок больше не может - захват через
            // границу предложения отсекается в simple().
            for (int j = bounds.length - 1; j >= i; j--) {
                if (bounds[j] <= from) break;
                String piece = in.substring(from, bounds[j]);
                String core = piece.strip();
                String ru = core.isEmpty() ? null : simple(core);
                if (ru != null) {
                    int lead = piece.indexOf(core);
                    hit = piece.substring(0, lead) + ru + piece.substring(lead + core.length());
                    next = j;
                    break;
                }
            }
            if (hit != null) {
                out.append(hit);
                any = true;
                from = bounds[next];
                i = next + 1;
            } else {
                int end = i < bounds.length ? bounds[i] : in.length();
                out.append(in, from, end);
                from = end;
                i++;
            }
        }
        return any ? out.toString() : null;
    }

    /** Позиции концов предложений: после ". ", "! ", "? " и перевода строки. */
    private static int[] sentenceBounds(String s) {
        List<Integer> b = new ArrayList<>();
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\n') { b.add(i + 1); continue; }
            if (c != '.' && c != '!' && c != '?') continue;
            if (s.charAt(i + 1) != ' ') continue;
            int j = i + 1;
            while (j < s.length() && s.charAt(j) == ' ') j++;
            b.add(j);
            i = j - 1;
        }
        b.add(s.length());
        int[] out = new int[b.size()];
        for (int i = 0; i < out.length; i++) out[i] = b.get(i);
        return out;
    }

    /**
     * Подставляет захваченные куски в русский шаблон.
     *
     * Сама подстановка тоже переводится, если словарь её знает: мод строит
     * заметки по делу как String.format("%s opened.", part), поэтому в {}
     * попадает название части тела. Без этого получалось "Left Foot: вскрытие".
     * Числа и короткие куски не трогаем.
     */
    private static String fill(String tpl, List<String> nums) {
        StringBuilder sb = new StringBuilder();
        int i = 0, n = 0;
        while (i < tpl.length()) {
            int p = tpl.indexOf("{}", i);
            if (p < 0) { sb.append(tpl, i, tpl.length()); break; }
            sb.append(tpl, i, p);
            if (n < nums.size()) {
                String v = nums.get(n++);
                String ru = translatable(v) ? simple(v) : null;
                sb.append(ru != null ? ru : v);
            }
            i = p + 2;
        }
        return sb.toString();
    }

    /** Захват пересёк границу предложения - значит шаблон подобран неверно. */
    private static boolean spansSentence(List<String> caught) {
        for (String c : caught) {
            for (int i = 0; i + 1 < c.length(); i++) {
                char ch = c.charAt(i);
                if ((ch == '.' || ch == '!' || ch == '?') && c.charAt(i + 1) == ' ') return true;
                if (ch == '\n') return true;
            }
        }
        return false;
    }

    /** Стоит ли пытаться переводить захваченную подстановку. */
    private static boolean translatable(String v) {
        if (v.length() < 3) return false;
        for (int i = 0; i < v.length(); i++) {
            if (Character.isLetter(v.charAt(i))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------- дампы

    private static void miss(String s) {
        // Верхняя граница совпадает с tools/extract_strings.py. Прежние 400 знаков
        // отсекали именно то, что переводить важнее всего: страница полевого
        // журнала доходит до Component.literal целиком и бывает длиннее 1000.
        if (s.length() < 3 || s.length() > 4000) return;
        int letters = 0, digits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) return;              // уже кириллица
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
        }
        if (letters < 3 || digits >= letters) return;             // числовые показатели
        if (s.indexOf('§') >= 0) return;                     // чужие оверлеи с цветовыми кодами
        if (s.indexOf(':') > 0 && s.indexOf(' ') < 0) return;     // идентификаторы
        String norm = NUM.matcher(s).replaceAll("{}");            // числа сворачиваем в шаблон
        // Чужие моды и ванильные экраны в дамп не пишем. Пустой список известных
        // строк раньше означал «пропускать всё»; при глобальном перехвате это
        // сразу давало мусор - до Dict.reload() успевают отрисоваться экран
        // загрузки Forge и чужие настройки, и весь их текст попадал в дамп.
        if (!KNOWN.contains(s) && !KNOWN.contains(norm)) return;
        MISSES.add(norm);
        if (MISSES.size() > 20000) collect = false;
    }

    public static Path dump() throws IOException {
        Path out = configDir().resolve("untranslated.json");
        Files.createDirectories(out.getParent());

        List<String> all = new ArrayList<>(MISSES);
        Collections.sort(all);
        // выбрасываем промежуточные кадры анимации: строку, являющуюся началом другой
        List<String> keys = new ArrayList<>();
        for (String s : all) {
            boolean prefixOfNext = false;
            for (String t : all) {
                if (t.length() > s.length() && t.length() - s.length() <= 2 && t.startsWith(s)) {
                    prefixOfNext = true;
                    break;
                }
            }
            if (!prefixOfNext) keys.add(s);
        }

        List<String> plain = new ArrayList<>();
        List<String> pat = new ArrayList<>();
        for (String k : keys) {
            if (k.contains("{}")) pat.add(k); else plain.add(k);
        }

        StringBuilder sb = new StringBuilder("{\n  \"strings\": {\n");
        for (int i = 0; i < plain.size(); i++) {
            sb.append("    ").append(quote(plain.get(i))).append(": \"\"").append(i + 1 < plain.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"patterns\": {\n");
        for (int i = 0; i < pat.size(); i++) {
            sb.append("    ").append(quote(pat.get(i))).append(": \"\"").append(i + 1 < pat.size() ? ",\n" : "\n");
        }
        sb.append("  }\n}\n");
        Files.writeString(out, sb, StandardCharsets.UTF_8);
        return out;
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    public static int missCount() { return MISSES.size(); }
    public static int size()      { Table t = table; return t.exact().size() + t.pattern().size(); }
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; CACHE.clear(); }
    public static boolean collecting() { return collect; }
    public static void setCollecting(boolean v) { collect = v; }
    public static long seen() { return SEEN.get(); }
    public static long hits() { return HITS.get(); }
    public static boolean guardingGeneric() { return guardGeneric; }
}
