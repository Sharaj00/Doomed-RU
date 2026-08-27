#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Проверяет словарь перевода по эталону из jar.

    python3 tools/extract_strings.py     # сначала собрать эталон
    python3 tools/audit_dict.py          # затем проверить словарь
    python3 tools/audit_dict.py --fix    # и убрать заведомый мусор
    python3 tools/audit_dict.py --by-class   # очередь работ: что осталось

Что проверяется:

  1. lang-файл  - все ли ключи en_us.json переведены, совпадают ли
     подстановки %s/%d/%%, нет ли лишних ключей.
  2. Словарь строк - есть ли для каждого ключа такая строка в моде.
     Часть подписей мод собирает на лету, их в пуле констант нет - это норма.
     Настоящий мусор виден по обрыву посреди слова ("Reset Sec"): так
     получается только при отрисовке, то есть ключ пришёл из дампа.
  3. Покрытие строк мода - главный раздел: сколько текста Doomed ещё
     не переведено. Имена констант перечислений (CARDIAC_ARREST) не
     считаются: игроку они не показываются.
  4. Покрытие повествования - журнал, торговцы, воображаемый друг,
     достижения.
  5. Типографика русских значений - дефис вместо тире, прямые кавычки,
     пробел перед знаком препинания.
  6. Единство терминов - один и тот же английский термин, переведённый
     по-разному (без учёта регистра).

С --by-class печатается только остаток работ и выгружается полный список
в build/doomedru-gap.json - его удобно переводить пачками.

С --fix из словаря удаляются только обрывки посреди слова. Всё остальное
показывается, но не трогается - решение за переводчиком.
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DICT = ROOT / "src/main/resources/assets/doomedru/ru_ru.json"
LANG = ROOT / "src/main/resources/assets/doomedmatu/lang/ru_ru.json"
REF = ROOT / "build/doomedru-strings.json"

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-+ 0,#]*\d*(?:\.\d+)?[a-zA-Z%]")


def load(p):
    return json.loads(p.read_text(encoding="utf-8"))


def section(title):
    print("\n" + title)
    print("-" * len(title))


def looks_like_ui(s):
    """Похоже ли на подпись, которую видит игрок."""
    if not (3 <= len(s) <= 4000):
        return False
    if not re.search(r"[A-Za-z]{2}", s):
        return False
    if re.fullmatch(r"[a-z0-9_]+", s):
        return False
    if re.fullmatch(r"[A-Z][A-Z0-9_]{3,}", s):
        return False          # имена констант перечислений: CARDIAC_ARREST
    return " " in s or s.isupper() or s[:1].isupper()


def by_class():
    """
    Очередь работ: сколько непереведённого текста осталось в каждом классе.

    Нужен разбор из tools/extract_strings.py и распакованный jar рядом с ним;
    без карты классов выводится только общий итог.
    """
    ref = load(REF)
    d = load(DICT)
    S, P = d["strings"], d["patterns"]
    gap_s = sorted(s for s in ref["literals"] if looks_like_ui(s) and s not in S)
    gap_p = sorted(s for s in ref.get("patterns", []) if looks_like_ui(s) and s not in P)

    out = ROOT / "build" / "doomedru-gap.json"
    out.write_text(json.dumps({"strings": gap_s, "patterns": gap_p},
                              ensure_ascii=False, indent=1), encoding="utf-8")
    print("Осталось перевести")
    print("  строк:   %5d  (%d знаков)" % (len(gap_s), sum(len(s) for s in gap_s)))
    print("  шаблонов:%5d  (%d знаков)" % (len(gap_p), sum(len(s) for s in gap_p)))
    print("\nСписок для работы -> %s" % out)
    print("\nСамые длинные непереведённые строки:")
    for s in sorted(gap_s, key=len, reverse=True)[:10]:
        print("  %4d  %s..." % (len(s), s[:70]))
    return 0


def main():
    fix = "--fix" in sys.argv
    if not REF.exists():
        sys.exit("Нет %s - сначала запустите tools/extract_strings.py" % REF)
    if "--by-class" in sys.argv:
        return by_class()

    ref = load(REF)
    literals = set(ref["literals"])
    narrative = ref["narrative_all"]
    en = ref["lang_keys"]
    ru = load(LANG)
    d = load(DICT)
    strings, patterns = d["strings"], d["patterns"]

    problems = 0

    section("1. lang-файл (assets/doomedmatu/lang/ru_ru.json)")
    missing = sorted(set(en) - set(ru))
    extra = sorted(set(ru) - set(en))
    identical = sorted(k for k in ru if k in en and ru[k] == en[k])
    mismatch = [k for k in ru if k in en
                and PLACEHOLDER.findall(en[k]) != PLACEHOLDER.findall(ru[k])]
    print("  ключей: %d из %d" % (len(ru), len(en)))
    for label, items in (("не переведено", missing),
                         ("лишние ключи", extra),
                         ("расходятся подстановки", mismatch)):
        print("  %-24s %d" % (label, len(items)))
        problems += len(items)
        for k in items[:10]:
            print("      %s" % k)
    print("  %-24s %d  (%s)" % ("совпадает с оригиналом", len(identical),
                                ", ".join(identical[:4]) or "-"))

    section("2. Словарь строк: ключи, которых нет в моде")
    # Ключ без точного совпадения - не обязательно мусор: мод собирает часть
    # подписей на лету (в верхнем регистре, из кусков), и такие строки в пуле
    # констант не лежат. Однозначно лишний только обрыв посреди слова: такое
    # рождается лишь при отрисовке, а значит попало в словарь из дампа.
    upper = {s.upper() for s in literals}
    unknown = [k for k in strings if k not in literals and k not in narrative]
    truncated, composed, foreign = [], [], []
    for k in unknown:
        cut = (" " in k and k[-1:].isalpha()
               and any(s.startswith(k) and len(s) > len(k) and s[len(k)].isalpha()
                       for s in literals))
        if cut:
            truncated.append(k)
        elif k.upper() in upper or any(k in s for s in literals):
            composed.append(k)
        else:
            foreign.append(k)
    print("  всего ключей:                %d" % len(strings))
    print("  нет точного совпадения:      %d" % len(unknown))
    print("    обрыв посреди слова:       %d  <- мусор из дампа" % len(truncated))
    print("    собирается на лету:        %d  <- норма" % len(composed))
    print("    в моде не найдено:         %d  <- проверить вручную" % len(foreign))
    for k in sorted(truncated):
        print("      обрыв: %r" % k)
    for k in sorted(foreign)[:15]:
        print("      %r" % k)
    problems += len(truncated)

    section("3. Покрытие строк мода переводом")
    # Главная проверка: что из текста Doomed вообще не переведено.
    # Технические константы (пути, идентификаторы, одиночные слова без
    # признаков подписи) отсеиваем - переводить их не нужно.
    ref_patterns = set(ref.get("patterns", []))

    for label, pile, table in (("строки", literals, strings),
                               ("шаблоны", ref_patterns, patterns)):
        ui = sorted(s for s in pile if looks_like_ui(s))
        gap = [s for s in ui if s not in table]
        print("  %-9s похожих на подписи: %5d | без перевода: %d" % (
            label, len(ui), len(gap)))
        for s in gap[:25]:
            print("      %r" % (s[:88] + "..." if len(s) > 88 else s))
        if len(gap) > 25:
            print("      ... ещё %d" % (len(gap) - 25))
        problems += len(gap)

    section("4. Покрытие повествовательного текста")
    covered = [s for s in narrative if s in strings]
    print("  переведено %d из %d (%d из %d знаков)" % (
        len(covered), len(narrative),
        sum(len(s) for s in covered), sum(len(s) for s in narrative)))
    for s in narrative:
        if s not in strings:
            print("      %r" % (s[:70] + "..." if len(s) > 70 else s))
            problems += 1
            if problems > 400:
                break

    section("5. Типографика русских значений")
    # Пара (английский оригинал, русский перевод). Для lang-файла оригинал -
    # это значение из en_us.json, а не ключ: ключ там технический идентификатор.
    values = ([(k, v) for k, v in strings.items()]
              + [(k, v) for k, v in patterns.items()]
              + [(en.get(k, ""), v) for k, v in ru.items()])
    checks = (
        ("дефис вместо тире", lambda s, v: " - " in v and " - " not in s),
        ("прямые кавычки", lambda s, v: '"' in v and '"' not in s),
        ("пробел перед знаком", lambda s, v: re.search(r"\s[,.;:!?](?:\s|$)", v)
                                             and not re.search(r"\s[,.;:!?](?:\s|$)", s)),
        ("два пробела подряд", lambda s, v: "  " in v and "  " not in s),
    )
    for label, pred in checks:
        hits = [(k, v) for k, v in values if pred(k, v)]
        print("  %-22s %d" % (label, len(hits)))
        for k, v in hits[:5]:
            print("      %r -> %r" % (k[:45], v[:60]))
        problems += len(hits)

    section("6. Единство терминов")
    groups = defaultdict(dict)
    for k, v in strings.items():
        if len(k.split()) <= 3 and re.fullmatch(r"[A-Za-z /&+'-]+", k):
            groups[k.lower().strip()][k] = v
    clashes = {k: m for k, m in groups.items()
               if len({v.lower().strip() for v in m.values()}) > 1}
    print("  терминов с разным переводом: %d" % len(clashes))
    for k in sorted(clashes)[:25]:
        print("      %-24s %s" % (k, sorted(set(clashes[k].values()))))

    if fix and truncated:
        for k in truncated:
            del strings[k]
        DICT.write_text(json.dumps(d, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8")
        print("\nУдалено обрывков: %d -> %s" % (len(truncated), DICT))

    print("\nИтого замечаний: %d" % problems)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
