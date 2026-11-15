---
name: Ameki
description: Android weather read like a household instrument.
colors:
  surface-light: "#ECEBE6"
  surface-dark: "#171816"
  now-light: "#E2501F"
  now-dark: "#FF6A36"
  rain-light: "#2F68B5"
  rain-dark: "#7AAEF5"
typography:
  display:
    fontFamily: Hanken Grotesk
    fontWeight: 300
  body:
    fontFamily: Hanken Grotesk
    fontWeight: 400
---

## Overview

Ameki is a native Android weather app whose main surface is the home-screen
widget. Its visual language comes from household instruments: a temperature
dial, clear numerals and quiet labels. Preserve this identity when extending
the app. The operating context and accessibility requirements are in PRODUCT.md.

## Colors

Material 3 roles carry surfaces, text, controls and errors. Dynamic color follows
the wallpaper; the static fallback uses warm neutrals in light and dark themes.
The orange marker identifies the present moment. Temperature uses a fixed
blue-to-amber ramp, while rain uses blue. These data colors retain their meaning
across wallpaper changes. Exact ramps and theme roles live in
`core/ui/src/main/kotlin/dev/antomanc/ameki/core/ui/theme/Color.kt`.

## Typography

Hanken Grotesk is bundled with the app. Numerals use tabular figures. The display
temperature is light; labels and controls use the Material typography roles.
The scale is defined in `core/ui/src/main/kotlin/dev/antomanc/ameki/core/ui/theme/Type.kt`.
Text uses sp and must remain readable at 200% system font size. Let containers
grow or change layout before truncating weather information.

## Layout

On compact screens, weather follows a vertical reading order: place and
condition, temperature dial, update status, hours, supporting detail and days.
Expanded layouts use the available width without stretching the dial. Preserve
the selected place and open detail across rotation and folding. Respect system
bars, cutouts and keyboard insets. Interactive targets are at least 48 dp.

## Elevation & Depth

Separate sections with spacing and Material tonal surfaces. Weather cards have
no decorative shadows. Motion must respect the system animation setting.

## Shapes

The dial, rain dots and rounded cards establish the shared geometry. Use the
existing theme shapes and components rather than introducing a second radius
system. Widgets adapt their information density to their allocated space.

## Components

- The dial shows the current temperature within today's range. Its spoken
  description includes temperature, feels-like, minimum and maximum.
- Hourly and daily forecasts keep units and min/max ordering consistent across
  app and widgets. A missing measurement stays missing instead of becoming zero.
- Update status distinguishes successful data, old data, loading and failure.
  Recovery actions name what they do.
- Rain dots encode precipitation probability or nowcast intensity in the
  appropriate surface, with a text equivalent.
- Widget configuration previews the selected style and preserves unfinished
  choices across recreation. The launcher previews distinguish widget types.
- Alerts appear only when supplied by the selected source and retain their
  validity, severity and attribution.

## Do's and Don'ts

Keep weather, place and data age readable in one glance. Use Material controls,
localized labels and complete accessibility semantics. Verify dark theme,
large fonts, narrow widgets and Fold layouts on Android. A screenshot documents
one rendering; it does not establish runtime correctness or contrast on every
wallpaper.
