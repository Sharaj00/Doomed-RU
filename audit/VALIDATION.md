# Release verification: DoomedRU 2.0.0

Artifact hashes and the final client result are recorded in `release-verification.json`. Version 2.0.0 changes only META-INF/mods.toml compared with the runtime-tested 1.0.1 JAR. Code and translation resource bytes are identical. Naming and version were verified in the rebuilt archive; no new client launch was performed for this metadata change.

The original Doomed 0.4.7.3 JAR remains unchanged: `56bb50c5dafbc3daf6a7dae407adaef9084288ab9c25b7e1c7d2c615abdb21d6`.

## Passed

* Java 17 / ForgeGradle release build and reobfuscation.
* Exactly 1,148 original language keys; no missing or additional keys.
* Resources and transformation targets remain within Doomed and the addon's own namespaces.
* Actual production client: Minecraft 1.20.1, Forge 47.4.23, original Doomed, Doomed Guns, EMI and JEI, in the separate production-run profile.
* 227 available transformed classes loaded and verified by the JVM. Final assertion count is recorded in client-result.json.
* Pixel-width wrapping and truncation, nested styles, original component preservation and unknown foreign-text pass-through.
* Russian and English search; original language resources restored after switching back to English.
* All 171 core configuration comments covered by the packaged dictionary; 164 exposed configuration comments checked against actual Forge entries in the client.
* All 22 traits: 66 manually translated titles, blurbs and effects. Original IDs, costs and gameplay calculations preserved. Joined trait names and the actual hovered detail panel checked.
* Matu battle dialogue translated before progressive character reveal; the actual bubble shows the Russian equivalent of my turn.
* All 129 requested preset lines manually translated. Original keys, array lengths, splitPerspective and limb placeholders checked against the backup.
* Screenshots reviewed at 1280x800 / GUI scale 2: codex pages, settings, Witness, health panel, patient examination, server rules, battle and traits. Requested labels fit their available space. The rule filter hint fits before the buttons.

## Scope and limits

The addon JAR has not been installed into the ordinary profile. The explicitly requested minecraft/config/doomedmatu/dialogue_presets/russian.json was updated there; its original contents are backed up in audit/backups/russian.before-1.0.1.json.

Jade was absent, so its two optional targets were not runtime-verified. The complete ordinary modpack, every tab, resource-pack font override and rare medical condition have not been manually tested. Automated isolated-client evidence does not establish all possible gameplay layouts.

The base translation uses a fresh machine draft plus manual corrections; all newly requested dialogue and trait strings were translated manually. Long narrative passages have not all received a complete editorial review. Earlier localization attempts and memory were not read or reused.
