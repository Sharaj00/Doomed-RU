# Doomed RU

Russian localization for **[Doomed](https://www.curseforge.com/minecraft/mc-mods/doomed)**
(`doomedmatu`) 0.3.9 — Minecraft 1.20.1, Forge.

*[Русская версия](README.ru.md)*

Client-side only. **The Doomed jar is never modified** — keep the original
`doomedmatu-0.3.9.jar` and drop this addon next to it.

---

## What it does

Doomed shows text through three different channels, and each one needs a
different approach. This addon covers all three.

| Channel | Volume | How it is translated | Status |
|---|---|---|---|
| `lang/en_us.json` — proper translation keys | 1006 keys | a normal lang file | **1006 / 1006** |
| Hardcoded string literals in code | ~9 200 constants | runtime substitution | ~68 % |
| Datapack JSON — journal, traders, imaginary friend, advancements | 164 unique entries | runtime substitution, whole entries | **164 / 164** |

Item names, block names, tooltips and subtitles come from the first channel,
so they are handled by an ordinary resource-pack lang file and need no tricks.

Almost everything else — the HUD, the triage panel, the WITNESS tablet, the
settings screen, the mode-select screen — is **hardcoded English string
literals**. There are hundreds of `Component.literal(...)` call sites in the mod. No
lang file can reach them, which is why this addon exists at all.

---

## How it works

### Interception

The addon never edits Doomed. It hooks the vanilla rendering path with two
Mixin layers:

**Primary — `LiteralContents.visit()`.** Every string the mod passes to
`Component.literal(...)` flows through this method when it is drawn or
measured. One hook covers button labels, screen titles, item tooltips, word
wrapping, and text drawn by vanilla widgets on the mod's behalf.

**Secondary — `GuiGraphicsMixin` and `FontMixin`.** Catches strings drawn
straight to the screen, bypassing `Component`: `GuiGraphics.drawString`,
`drawCenteredString`, `Font.width`.

Three narrow Mixins handle WITNESS, the status panel, and animated thoughts:
full labels are translated before wrapping or clipping, while thoughts reveal
a stable Russian line during the typewriter effect. They do not redirect every
method in every Doomed class, so target-method staticness cannot conflict.

The intercepted string is looked up in a dictionary and replaced on the way to
the renderer. The `Component` object itself is never mutated, so mod logic that
compares strings keeps working.

> **Note for anyone writing a similar addon.** Redirecting `Component.literal`
> directly *does not work*. `Component` is an interface and `literal` is a
> static method on it; the Mixin annotation processor resolves such a target
> differently from an ordinary virtual method on an ordinary class, and the
> injection is silently skipped when `"defaultRequire": 0`. Hooking
> `LiteralContents.visit()` instead — a normal method on a normal class —
> is what makes this reliable.

### Lookup chain

A string on its way to the screen is tested in this order:

1. **Exact match** — `"SKIN"` → `"КОЖА"`.
2. **Numeric pattern** — `"100 BPM"` collapses to the key `"{} BPM"`, the
   number is put back. One entry covers the whole range of a gauge.
3. **Free pattern** — the same for text substitutions. The mod builds case
   notes as `String.format("%s opened.", part)`, so `"Left Foot opened."`
   matches `"{} opened."` — and the captured body part is translated too.
4. **Prefix** — Doomed types some text out character by character. A partial
   frame returns a proportional slice of the translation, so the typewriter
   effect plays in Russian instead of flickering between languages.
5. **Composite** — several sentences in a row where only some are known. The
   mod concatenates a moodle description with its live effects; the whole
   string can never be a dictionary key, so it is translated sentence by
   sentence, longest known chunk first.

Character thoughts use a stricter path: the addon translates the known full
line first and only then reveals the same fraction of its Russian text. Shared
prefixes between different lines can therefore no longer switch translations
between frames.

Every result is cached, so nothing is recomputed per frame. The cache is
capped and cleared on overflow — HUD readouts produce an unbounded stream of
distinct strings.

### Not touching other mods

The dictionary is generated from Doomed's own jar, so its keys are
Doomed-specific by construction. On top of that, short common words (`Done`,
`Combat`, up to two words) are only substituted on Doomed screens. Long
phrases are unique enough to translate anywhere.

---

## Install

1. Build (or download) `doomedru-1.1.2.jar`.
2. Drop it into `.minecraft/mods` next to `doomedmatu-0.3.9.jar`.

Client-side only — do not install it on a server.

Check it works in game:

```
/doomedru
```

It reports the dictionary size, whether the hook actually attached, and how
many strings passed through it. If the self-check says the hook is not
working, the problem is the build, not the dictionary.

| Command | |
|---|---|
| `/doomedru` | stats and hook diagnostics |
| `/doomedru reload` | re-read `config/doomedru/ru_ru.json`, no restart needed |
| `/doomedru dump` | write every untranslated string that appeared on screen |
| `/doomedru toggle` | turn translation off to compare with the original |
| `/doomedru targets` | which classes the hook attached to |

Any string can be overridden without rebuilding — put it in
`config/doomedru/ru_ru.json` and run `/doomedru reload`:

```json
{
  "strings":  { "SKIN": "ПОКРОВ" },
  "patterns": { "{} BPM": "{} уд/мин" }
}
```

---

## Build

Needs **JDK 17** and the Doomed jar in `libs/` (compile-time only — the Mixin
annotation processor verifies that the target classes exist; it is not bundled
into the output).

```
gradlew.bat build
```

The result is `build/libs/doomedru-1.1.2.jar`.

The Gradle wrapper binary is not included — take `gradlew`, `gradlew.bat` and
`gradle/` from the official [Forge 1.20.1 MDK](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html).

---

## Translator tooling

The dictionary is verified against the mod's jar, not guessed from gameplay.

```
python3 tools/extract_strings.py     # ground truth: every string in the jar
python3 tools/audit_dict.py          # coverage, typography, term consistency
python3 tools/audit_dict.py --by-class   # what is still left, as a work queue
```

`extract_strings.py` reads three sources: the constant pool of every class,
the datapack JSON, and `lang/en_us.json`. It also recovers **string-concat
recipes** — javac stores `"You are tiring (" + n + "% stamina)..."` as a
single constant with markers — and turns them into `{}` pattern keys. That is
how moodle descriptions with live values get translated.

Run extraction and the audit after a Doomed update; they will show what changed.

---

## Current coverage

```
lang keys          1006 / 1006
narrative text      164 / 164   (20 426 characters)
dictionary        5 074 strings + 278 patterns
still untranslated 1 645 strings (107 607 chars) + 596 patterns
```

Most of what remains is config descriptions — long paragraphs visible only in
one screen. The surfaces a player meets constantly (HUD, triage panel, WITNESS
tablet, journal, traders, advancements) are done.

---

## A note for the Doomed developer

This addon is a workaround, and a fairly elaborate one. The mod has 895
`Component.literal(...)` call sites against 109 `Component.translatable(...)`,
so most of the interface is hardcoded English rather than translation keys.
Every localization therefore has to intercept rendering instead of shipping a
lang file.

The Chinese `zh_cn.json` in the jar shows the effect clearly: it covers 960 of
1006 lang keys — nearly complete for that channel — yet a Chinese player
still sees an entirely English HUD, triage panel and tablet, because those
strings never pass through the lang system.

Moving the UI text to `Component.translatable(...)` keys would let every
language ship as a plain resource pack, and addons like this one could shrink
to a lang file. If that is ever interesting, the extracted string inventory in
`build/doomedru-strings.json` is a ready-made starting point: 9 191 constants
and 908 concat templates, grouped by class.

No pressure either way — the mod is excellent, and this is offered as data
rather than a complaint.

---

## License

MIT. Doomed itself belongs to its author (MattLives); this addon only ships
translated text and the code that applies it.
