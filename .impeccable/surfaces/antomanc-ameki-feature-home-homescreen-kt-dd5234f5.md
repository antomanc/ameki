---
version: 1
slug: "antomanc-ameki-feature-home-homescreen-kt-dd5234f5"
primary_target: "feature/home/src/main/kotlin/dev/antomanc/ameki/feature/home/HomeScreen.kt"
related_targets: ["widget/src/main/kotlin/dev/antomanc/ameki/widget/CompactWidget.kt"]
---

# Surface brief: main weather screen and home-screen widgets

Mode: Operate. The user is glancing at a widget, or opening the app to check the hours ahead and the next ten days.

## Direction contract

THESIS: Weather read like a well-made household instrument (1960s industrial design: calm, legible, one signal color). We refuse the category default of a sky gradient behind white text.

OWN-WORLD: Material You roles on a warm neutral ground (static fallback: warm greys). There is one fixed signal color, an orange "now" marker, used only for the present moment. Temperature data runs on a fixed blue-to-amber ramp. Rain probability is drawn as dots whose diameter follows the probability. Every number uses tabular figures, with light-weight display numerals. Labels are small tracked caps. Cards are large-radius and round, with no shadows.

STORY: In one look you know the temperature now, where it sits in today's range, and when it will rain. Tapping opens the detail.

FIRST VIEWPORT: Place name and condition at the top left, the update time at the top right and the date below the condition. In the center is the dial: an arc from today's min to max, the orange marker on the current temperature, a large numeral inside, and feels-like plus min/max below it. A plain short-term rain sentence follows the dial, then the hourly card with rain dots and the 10-day card with range bars.

FORM: candidate 4 of 7 (household instrument, industrial design), seed:1625770f749424ee. Signature: the temperature dial, shared by the main screen and the Compact widget. Raises: from datamatics, tabular figures everywhere; from the ticket wallet, stale data is stamped, never hidden; from the star atlas, rain probability on a fixed dot-diameter ramp.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
