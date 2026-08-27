# Doomed RU

Russian localization for **[Doomed](https://www.curseforge.com/minecraft/mc-mods/doomed)**
(`doomedmatu`) 0.3.6 — Minecraft 1.20.1, Forge.

*[Русская версия](README.ru.md)*

Client-side only. **The Doomed jar is never modified** — keep the original
`doomedmatu-0.3.6.jar` and drop this addon next to it.

---

## What it does

Doomed shows text through three different channels, and each one needs a
different approach. This addon covers all three.

| Channel | Volume | How it is translated | Status |
|---|---|---|---|
| `lang/en_us.json` — proper translation keys | 929 keys | a normal lang file | **929 / 929** |
| Hardcoded string literals in code | ~8 700 constants | runtime substitution | ~75 % |
| Datapack JSON — journal, traders, imaginary friend, advancements | 162 entries | runtime substitution, whole entries | **162 / 162** |

Item names, block names, tooltips and subtitles come from the first channel,
so they are handled by an ordinary resource-pack lang file and need no tricks.

Almost everything else — the HUD, the triage panel, the WITNESS tablet, the
settings screen, the mode-select screen — is **hardcoded English string
literals**. There are 895 `Component.literal(...)` call sites in the mod. No
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

**Secondary — `DoomedTextMixin`.** Targets 178 Doomed classes and catches
strings drawn straight to the screen, bypassing `Component`:
`GuiGraphics.drawString`, `drawCenteredString`, `Font.width`.

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

1. Build (or download) `doomedru-1.0.0.jar`.
2. Drop it into `.minecraft/mods` next to `doomedmatu-0.3.6.jar`.

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
annotation processor verifies the 178 target classes exist; it is not bundled
into the output).

```
gradlew.bat build
```

The result is `build/libs/doomedru-1.0.0.jar`.

The Gradle wrapper binary is not included — take `gradlew`, `gradlew.bat` and
`gradle/` from the official [Forge 1.20.1 MDK](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html).

---

## Translator tooling

The dictionary is verified against the mod's jar, not guessed from gameplay.

```
python3 tools/extract_strings.py     # ground truth: every string in the jar
python3 tools/audit_dict.py          # coverage, typography, term consistency
python3 tools/audit_dict.py --by-class   # what is still left, as a work queue
python3 tools/gen_targets.py         # rebuild the Mixin target list
```

`extract_strings.py` reads three sources: the constant pool of every class,
the datapack JSON, and `lang/en_us.json`. It also recovers **string-concat
recipes** — javac stores `"You are tiring (" + n + "% stamina)..."` as a
single constant with markers — and turns them into `{}` pattern keys. That is
how moodle descriptions with live values get translated.

Run all four after a Doomed update; the audit will show what changed.

---

## Current coverage

```
lang keys           929 / 929
narrative text      162 / 162   (20 354 characters)
dictionary        4 944 strings + 102 patterns
still untranslated 1 441 strings (96 583 chars) + 688 patterns
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

The Chinese `zh_cn.json` in the jar shows the effect clearly: it covers 912 of
929 lang keys — genuinely complete for that channel — yet a Chinese player
still sees an entirely English HUD, triage panel and tablet, because those
strings never pass through the lang system.

Moving the UI text to `Component.translatable(...)` keys would let every
language ship as a plain resource pack, and addons like this one could shrink
to a lang file. If that is ever interesting, the extracted string inventory in
`build/doomedru-strings.json` is a ready-made starting point: 8 700 constants
and 821 concat templates, grouped by class.

No pressure either way — the mod is excellent, and this is offered as data
rather than a complaint.

---

## License

MIT. Doomed itself belongs to its author (MattLives); this addon only ships
translated text and the code that applies it.
