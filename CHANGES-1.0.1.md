# Version 1.0.1

Requested display changes:

* Blood type is shown without the BLD prefix.
* CONSC becomes СОЗНАНИЕ; INT BLEED becomes ВНУТР. КРВТ.; IMMUNE becomes ИММУНИТЕТ.
* The collar button shows OFF, without changing its action.
* Open Body Appearance becomes Спрайт тела.
* UNI-HEALTH retains its original brand spelling, including the trademark.

Missing translations:

* Category and option titles now translate with the original modified marker intact.
* All 171 core configuration comments, including string-array descriptions, enter the translation inventory. Forge-generated range metadata is translated when attached to a recognized core description. Numeric ranges, option paths and editable values remain unchanged.
* The server-rules filter includes Russian descriptions alongside the original English text.
* Matu encounter dialogue is translated when the complete display text is assigned, before character reveal, line wrapping and bubble sizing. Fight schedules, action identifiers and events are not modified.
* All 22 perks and flaws have manually translated titles, short descriptions and effects. The detail panel wraps the complete text and fits it to its available height instead of silently dropping everything after the third effect line. Trait IDs, costs, bitmasks and gameplay calculations stay unchanged.

The requested `minecraft/config/doomedmatu/dialogue_presets/russian.json` was directly updated. All 129 dialogue lines were translated manually, retaining the original keys, array lengths, splitPerspective flag and limb placeholders. Its original contents are backed up at `audit/backups/russian.before-1.0.1.json`; a distributable translated copy is at `configuration/russian.json`.

Replace the previous addon JAR with the version 1.0.1 JAR; do not load both versions simultaneously. The updated JAR was not installed into the ordinary profile automatically.
