#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Собирает эталонный список переводимых строк мода Doomed прямо из jar.

    python3 tools/extract_strings.py [путь/к/doomedmatu-X.Y.Z.jar]

Раньше known.txt получался из игровых дампов: что успели показать на экране,
то и попадало в список. Из-за этого в нём оказались служебные идентификаторы
($SwitchMap$..., пути к текстурам), а длинные тексты - записи полевого журнала,
реплики торговцев - не попадали вовсе.

Здесь список строится из самого мода и покрывает все три источника текста:

  1. Пул констант class-файлов - жёстко зашитые строки (Component.literal,
     GuiGraphics.drawString и т.п.).
  2. data/doomedmatu/doomed_journal_entries, doomed_dialogue,
     doomed_imaginary_friends - повествовательный текст в JSON датапака.
     Он доходит до экрана через Component.literal целиком, до переноса строк,
     поэтому переводится тем же словарём - но записью целиком, а не построчно.
  3. assets/doomedmatu/lang/en_us.json - ключи штатной локализации.
     В known.txt не пишутся (их переводит lang-файл), выводятся отдельно
     для сверки.

Результат:
  src/main/resources/assets/doomedru/known.txt  - фильтр для /doomedru dump
  build/doomedru-strings.json                   - полный разбор для аудита
"""
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Рецепт склейки строк из invokedynamic: подстановки помечены U+0001.
CONCAT_MARK = "\u0001"

# Папки data/, где лежит повествовательный текст, и поля, которые надо взять.
NARRATIVE_DIRS = {
    "doomed_journal_entries": ("title", "pages", "text"),
    # У торговцев не только реплики: есть приветствие, список советов и целое
    # дерево разговора (nodes -> text + options[].label). Поле _nodes_comment -
    # заметка автора, её не берём.
    "doomed_dialogue": ("lines", "greeting", "advice", "text", "label"),
    "doomed_imaginary_friends": ("name", "lines"),
    # display.title / display.description достижений - обычный текст в JSON.
    # Minecraft разбирает такую строку как Component.literal, поэтому она
    # переводится тем же словарём.
    "advancements": ("title", "description"),
}

# Строки, которые заведомо не показываются игроку.
TECHNICAL = re.compile(
    r"""^(?:
          \$SwitchMap\$                                          # синтетика switch по enum
        | [a-z0-9_.]+:[a-z0-9_/.]+$                              # doomedmatu:textures/...
        | [a-z0-9_/]+\.(?:png|json|ogg|nbt|toml)$
        | (?:net|java|com|org)\.[A-Za-z0-9_.$]+$                 # имена классов
        | [A-Za-z_$][A-Za-z0-9_$]*\([^)]*\)[A-Za-z0-9_$/;\[]*$   # дескрипторы методов
        | \([^)]*\)[A-Za-z0-9_$/;\[]+$
        | [A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$                     # a.b.c - ключи и поля
      )""",
    re.X,
)


def pool_strings(data):
    """Все CONSTANT_String из class-файла."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    count = struct.unpack(">H", data[8:10])[0]
    i, n = 10, 1
    utf8, refs = {}, []
    while n < count:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack(">H", data[i + 1:i + 3])[0]
            utf8[n] = data[i + 3:i + 3 + ln]
            i += 3 + ln
        elif tag == 8:
            refs.append(struct.unpack(">H", data[i + 1:i + 3])[0])
            i += 3
        elif tag in (7, 16, 19, 20):
            i += 3
        elif tag in (9, 10, 11, 12, 17, 18, 3, 4):
            i += 5
        elif tag == 15:
            i += 4
        elif tag in (5, 6):
            i += 9
            n += 1
        else:
            raise ValueError("неизвестный тег пула констант: %d" % tag)
        n += 1
    out = []
    for r in refs:
        raw = utf8.get(r)
        if raw is not None:
            out.append(raw.decode("utf-8", "replace"))
    return out


def collect(node, fields, acc, inside=False):
    """
    Рекурсивно вытаскивает текстовые поля из JSON датапака.

    Строку берём, только если мы уже внутри одного из нужных полей (inside).
    Иначе в выборку попадают служебные списки - например requirements
    достижений с именами условий has_ingredient / has_the_recipe.
    """
    if isinstance(node, dict):
        for k, v in node.items():
            collect(v, fields, acc, inside or k in fields)
    elif isinstance(node, list):
        for v in node:
            collect(v, fields, acc, inside)
    elif isinstance(node, str) and inside:
        acc.append(node)


def displayable(s):
    """Похоже ли на строку, которую видит игрок."""
    if not (2 <= len(s) <= 4000):
        return False
    if TECHNICAL.match(s):
        return False
    return bool(re.search(r"[A-Za-z]{2}", s))


def as_pattern(s):
    """
    Рецепт склейки из invokedynamic -> ключ-шаблон словаря.

    Строку вида "Caked in grime (" + value + "%) - wounds fester..."
    компилятор кладёт в пул одной константой, пометив подстановки U+0001.
    Заменяем маркер на {} и получаем ровно тот ключ, который Dict строит из
    готовой строки, сворачивая числа. Без этого описания состояний ("Грусть",
    "Шок" и прочие) не переводились и даже не попадали в дамп: фильтр
    known.txt их не пропускал, потому что константы с U+0001 отбрасывались.
    """
    return s.replace(CONCAT_MARK, "{}") if CONCAT_MARK in s else None


def main():
    if len(sys.argv) == 2:
        jar = Path(sys.argv[1])
    else:
        jars = sorted((ROOT / "libs").glob("*.jar"))
        if not jars:
            sys.exit(__doc__)
        jar = jars[0]
        print("Использую %s из libs/" % jar.name)
    if not jar.exists():
        sys.exit("Файл не найден: %s" % jar)

    literals, patterns, narrative, lang_keys = set(), set(), {}, {}

    with zipfile.ZipFile(jar) as z:
        for entry in z.namelist():
            if entry.endswith(".class") and "doomedmatu" in entry:
                for s in pool_strings(z.read(entry)):
                    pat = as_pattern(s)
                    if pat is not None:
                        if displayable(pat):
                            patterns.add(pat)
                    elif displayable(s):
                        literals.add(s)
            elif entry.endswith(".json") and entry.startswith("data/doomedmatu/"):
                parts = entry.split("/")
                # достижения лежат во вложенных папках, поэтому смотрим весь путь
                folder = "advancements" if "advancements" in parts else parts[-2]
                fields = NARRATIVE_DIRS.get(folder)
                if not fields:
                    continue
                acc = []
                collect(json.loads(z.read(entry).decode("utf-8")), set(fields), acc)
                got = [s for s in acc if displayable(s)]
                if got:
                    narrative.setdefault(folder, {})[entry] = got
            elif entry.endswith("lang/en_us.json"):
                lang_keys = json.loads(z.read(entry).decode("utf-8"))

    narrative_all = sorted({s for f in narrative.values() for v in f.values() for s in v})

    # known.txt - фильтр для дампа. Числа сворачиваем в {}, как это делает Dict.
    known = set(literals) | set(narrative_all)
    known |= {re.sub(r"\d+(?:[.,]\d+)?", "{}", s) for s in known}
    # Шаблоны склейки уже содержат {} - именно в таком виде Dict нормализует
    # готовую строку перед проверкой, поэтому кладём их как есть.
    known |= patterns
    known = sorted(s for s in known if s)

    # Формат построчный, а страница журнала содержит переводы строк: пишем их
    # как \n, иначе одна запись растечётся на несколько строк файла и фильтр
    # начнёт пропускать обрывки. Dict.loadKnown разворачивает escape обратно.
    def escape(s):
        return s.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")

    out = ROOT / "src/main/resources/assets/doomedru/known.txt"
    out.write_text("\n".join(escape(s) for s in known) + "\n", encoding="utf-8")

    report = ROOT / "build" / "doomedru-strings.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({
        "jar": jar.name,
        "literals": sorted(literals),
        "patterns": sorted(patterns),
        "narrative": narrative,
        "narrative_all": narrative_all,
        "lang_keys": lang_keys,
    }, ensure_ascii=False, indent=1), encoding="utf-8")

    print("  зашитых строк в коде:      %d" % len(literals))
    print("  шаблонов склейки ({}):     %d" % len(patterns))
    for folder, files in sorted(narrative.items()):
        n = sum(len(v) for v in files.values())
        c = sum(len(s) for v in files.values() for s in v)
        print("  %-26s %5d строк, %d знаков" % (folder, n, c))
    print("  ключей lang/en_us.json:    %d" % len(lang_keys))
    print("\nknown.txt: %d записей -> %s" % (len(known), out))
    print("Разбор для аудита         -> %s" % report)


if __name__ == "__main__":
    main()
