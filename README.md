# DoomedRU 2.0.0

Client localization addon for **Minecraft 1.20.1, Forge 47.x, Doomed 0.4.7.3**.

Version 2.0.0 renames the addon; translation changes and the translated dialogue preset are documented in `CHANGES-1.0.1.md`.

## Installation

Copy `build/libs/DoomedRU-2.0.0.jar` to the client's `mods` directory and select Russian in Minecraft's language menu. Keep the original Doomed mod installed. Use only one Russian localization addon for Doomed at a time. The addon is not needed on a dedicated server.

The delivered addon has not been installed into the ordinary DOOMED instance. All launches use the separate `production-run` directory.

## Coverage and layout

* The Russian language file has exactly the original 1,148 English keys. No keys from earlier localization attempts or unrelated addons were imported.
* Separate resources cover Moodle, oripathy and Leuten dialogue. The original empty `quips.json` remains empty; generated fallback dialogue is translated when presented.
* An audited dictionary covers original visible hardcoded text, including medical screens, Witness, character dialogue, settings, item tooltips and journal records. Identifiers, routing keys and log messages are excluded from translation selection.
* Only original `net.mattlives.doomedmatu` classes are transformation targets. There are no global Minecraft font, literal-component or rendering patches.
* Full strings are translated before the original screen wrapping, width measurement, clipping and progressive dialogue reveal. Component styles and child components are retained.
* Text pages of the defusal codex wrap and scale to their available height. Patient examination values scale inside their original columns. Cyrillic font fallback is included.
* Settings search retains English names and aliases and also searches translated titles, descriptions and category names.
* English mode restores original strings. Language-dependent resources reload when Minecraft changes language.

The translation began with a machine draft made from this exact JAR, followed by manual medical terminology, interface, subtitle and puzzle-rule corrections. A complete editorial review of every long passage is not claimed. Proper names, brands, physiological notation and puzzle identifiers may intentionally remain Latin.

## Verification

`audit/coverage.json` records resource coverage. `audit/client-result.json` records the actual isolated-client audit; screenshots are in `production-run/screenshots`. `audit/release-verification.json` records artifact hashes and package scope.

The client audit uses the installed production Forge launcher and the supplied original mod, with Doomed Guns, EMI and JEI dependencies. It checks actual transformed classes, Russian and English language switching, resource selection, nested formatting, pixel-width wrapping and truncation, bilingual search and dialogue reveal. It also creates its own creative world for Witness, status and patient-examination screenshots. This is automated isolated-profile evidence, not a manual test of the complete ordinary modpack or every rare clinical condition. Jade is absent from that profile, so its optional targets are not runtime-verified.

The audit runs only with `-Ddoomed.ru.audit=true`; ordinary installations do not create worlds or execute audit fixtures.

## Building

Use Java 17 and run `gradlew.bat build`. The checked-in resources are sufficient for a normal build; no translation service is contacted at build time or at runtime.

The `tools` directory contains the fresh extraction and resource preparation workflow. Its translation draft cache is an authoring input, not a runtime dependency. The original JAR was inspected and decompiled independently for this project. No earlier Russian localization project or memory was used.

Original artifact SHA-256: `56bb50c5dafbc3daf6a7dae407adaef9084288ab9c25b7e1c7d2c615abdb21d6`.
