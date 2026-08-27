#!/usr/bin/env python3
"""
Пересобирает список классов Doomed для DoomedTextMixin.

    python3 tools/gen_targets.py [путь/к/doomedmatu-X.Y.Z.jar]

Без аргумента берёт первый jar из папки libs/.

Находит все классы мода, которые выводят текст (Component.literal,
Component.nullToEmpty, GuiGraphics.drawString/drawCenteredString,
Font.width), и переписывает список targets в
src/main/java/ru/doomedru/mixin/DoomedTextMixin.java.

Запускать после каждого обновления Doomed, затем пересобрать мод.
"""
import re
import struct
import sys
import zipfile
from pathlib import Path

# SRG-имена методов Minecraft 1.20.1, по которым мод выводит текст
WANTED = {
    "m_237113_",  # Component.literal
    "m_130674_",  # Component.nullToEmpty
    "m_280056_",  # GuiGraphics.drawString(String)
    "m_280137_",  # GuiGraphics.drawCenteredString(String)
    "m_92895_",   # Font.width(String)
    # имена из официальных маппингов - на случай деобфусцированной сборки
    "literal", "nullToEmpty", "drawString", "drawCenteredString", "width",
}


def constant_pool_names(data: bytes):
    """Достаёт имена методов из пула констант class-файла."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return set()
    count = struct.unpack(">H", data[8:10])[0]
    i, n = 10, 1
    utf8, name_and_type = {}, []
    while n < count:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack(">H", data[i + 1:i + 3])[0]
            utf8[n] = data[i + 3:i + 3 + ln]
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            i += 3
        elif tag == 12:
            name_and_type.append(struct.unpack(">HH", data[i + 1:i + 5])[0])
            i += 5
        elif tag in (9, 10, 11):
            i += 5
        elif tag == 15:
            i += 4
        elif tag in (5, 6):
            i += 9
            n += 1
        else:
            i += 5
        n += 1
    out = set()
    for idx in name_and_type:
        raw = utf8.get(idx)
        if raw:
            try:
                out.add(raw.decode())
            except UnicodeDecodeError:
                pass
    return out


def main():
    if len(sys.argv) == 2:
        jar = Path(sys.argv[1])
    else:
        libs = sorted((Path(__file__).resolve().parent.parent / "libs").glob("*.jar"))
        if not libs:
            sys.exit(__doc__)
        jar = libs[0]
        print(f"Использую {jar.name} из libs/")
    if not jar.exists():
        sys.exit(f"Файл не найден: {jar}")

    targets = set()
    with zipfile.ZipFile(jar) as z:
        for entry in z.namelist():
            if not entry.endswith(".class") or "doomedmatu" not in entry:
                continue
            if constant_pool_names(z.read(entry)) & WANTED:
                targets.add(entry[:-6].replace("/", "."))

    if not targets:
        sys.exit("Не найдено ни одного класса с выводом текста - проверьте jar.")

    src = Path(__file__).resolve().parent.parent / "src/main/java/ru/doomedru/mixin/DoomedTextMixin.java"
    text = src.read_text(encoding="utf8")
    block = ",\n".join(f'        "{t}"' for t in sorted(targets))
    new = re.sub(r"@Mixin\(targets = \{.*?\}, remap = false\)",
                 "@Mixin(targets = {\n" + block + "\n}, remap = false)",
                 text, flags=re.S)
    src.write_text(new, encoding="utf8")
    print(f"Целей записано: {len(targets)} -> {src}")


if __name__ == "__main__":
    main()
