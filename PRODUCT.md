# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

A broad Android audience from day one, technical or not, found through GitHub and F-Droid.
The core case: someone on a non-Pixel phone (the reference device is a Samsung Galaxy Z Fold 8)
who wants home-screen weather widgets as good as Pixel's and finds One UI's At a Glance too thin.
They glance at a widget many times a day and open the app only to check the hours ahead, the
next ten days, or an alert.

## Product Purpose

Ameki (あめき · 雨木, "rain tree") is an open source Android weather app whose widgets are the
main product. It shows Google's forecasts (Weather API, WeatherNext 2) when the user brings their
own API key, and Open-Meteo with no setup at all. Success: widgets people keep on their home
screen because they are beautiful, readable on any wallpaper, and always up to date.

## Positioning

Widgets first. Ameki aims to have the most beautiful and dependable weather widgets on any
Android phone, Samsung first. The app complements them. Google's forecast data is an optional
premium source, and no other open source app offers it.

## Operating Context

- Home-screen widgets on One UI (Fold outer and inner screen, different grids) and Pixel Launcher.
- Foldable continuity: the app moves between a one-column outer screen and a two-column inner one without losing state.
- Background refresh through one shared WorkManager job. Widgets read only from the local cache.
- Offline: show the last data with its time, within each provider's retention limits.

## Capabilities and Constraints

- Kotlin, Jetpack Compose, Material 3, dynamic color, Glance widgets. minSdk 31.
- Data sources: Google Maps Platform Weather API (each user supplies their own key; none ships with the app) and Open-Meteo (free, non-commercial, CC BY 4.0).
- Google's Service Specific Terms let you cache current conditions, hourly forecasts and alerts for 1 hour at most, and daily forecasts for 24 hours.
- Google's terms (section 21.1) forbid using Weather API content in "a weather app ... whose primary purpose is to provide weather information". This is an **open decision**: whether and how the Google provider ships, and which provider is the default.
- No tracking, no analytics, no key in the repo or APK. Must be compatible with F-Droid.
- Interface languages: Italian and English from the start, with strings ready for Weblate/Crowdin.
- Licence: GPL-3.0.
- Weather icons: Meteocons (MIT, basmilius/meteocons). The colored fill style and Lottie animations go in the app; the monochrome style, tinted with Material You colors, goes in the widgets.

## Brand Commitments

- Name: Ameki (あめき · 雨木).
- Voice: warm and clear. Short sentences, no technical jargon, like Google/Pixel apps. Setup
  steps (such as creating a Google key) are explained so that a non-technical user can follow them.

## Evidence on Hand

- `HANDOFF.md`: requirements, the widget catalogue and the v1 definition of done.
- No screenshots, users, reviews or press yet. Do not invent them.

## Product Principles

1. Widgets are the product. Every trade-off favors the widget experience.
2. Defaults over options: setup in a few steps, few settings, each one clear.
3. One look whatever the data source: the UI knows only the domain model and hides what the source lacks, without empty cards.
4. Honest data: always show when the data was last updated, and never show data that has expired.
5. Private by design: location stays on the device, and is sent only to the chosen source.

## Accessibility & Inclusion

WCAG 2.1 AA contrast; font scaling up to 200%; full TalkBack support in the app and the widgets.
