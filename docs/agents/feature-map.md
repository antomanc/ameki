# Feature map

What the user sees, with the names to use for it and the code behind it. Paths are relative to each module's `src/main/kotlin/dev/antomanc/ameki/`. Keep this in step with the UI: a new screen, card or widget gets a row in the same change.

## App screens

Routes live in `app` (`MainActivity.kt`, `NavHost`): onboarding, home, places, settings.

| Name | What it is | How to reach it | Code |
| --- | --- | --- | --- |
| Onboarding | First run: step progress bar with back button, welcome dial, source choice (Open-Meteo or Google), location or city, add a widget | Fresh install, until finished | feature/onboarding `OnboardingScreen.kt` |
| Home | A pager of places with TopAppBar page indicator and interpolated atmosphere, one **place pane** each; on an unfolded Fold or tablet the **place list** sits beside the pane | App launch | feature/home `HomeScreen.kt` |
| Place pane | Scrolling cards for one place (see below); pull to refresh | Home, swipe between places | feature/home `PlaceWeatherPane.kt` |
| Day page | One day in detail with predictive-back transition: day chips, header, interactive Bezier temperature chart with now marker, hour rows | Tap a day in **10 days** or an hour in **Next hours**; widget taps | feature/home `DayPage.kt` |
| Places | Search for a city (with clear button), use my location, swipe-to-delete with undo, reorder, rename, local time & min/max preview | Home top bar | feature/locations `LocationsScreen.kt`, `PlaceSearch.kt` |
| Settings | Grouped cards with inline segmented controls (temperature, theme, time format), weather source, update interval, follow me with widgets, units, about | Home top bar | feature/settings `SettingsScreen.kt` |
| Google key setup | Guide to get and check a Google key, with package name and SHA-1 to copy | Settings, source Google; error cards' "Fix it" | feature/settings `GoogleKeySetup.kt` |
| Update available | AlertDialog when a newer GitHub Release exists; View opens Releases/latest; View/Later/dismiss suppress that version on cold start | Cold start after onboarding (throttled) or Settings → About → Check for updates (force) | feature/settings `UpdateAvailableDialog` / `openAmekiReleases`; `ReleaseUpdateChecker` in core/data; cold start in `MainActivity` |

## Place pane cards, top to bottom

| Name | Shows | Code (feature/home) |
| --- | --- | --- |
| Hero | Current temperature on thermal gradient dial with engraved min/max tips, condition, freshness stamp, **rain outlook** sentence; cold-start **mute dial** (`MuteTemperatureDial`) Crossfades into `TemperatureDial`; NoData ↔ content also Crossfades | `PlaceWeatherPane.kt` `Hero`, `Freshness`; `WeatherExtras.kt` `RainOutlookSummary`; core/ui `Instrument.kt` `TemperatureDial`, `MuteTemperatureDial` |
| Alerts | Severity-accented weather alerts, only when the source supplies them | `WeatherExtras.kt` `AlertsCard` |
| Next hours | Up to 48 hours with sunrise/sunset markers, scrollable | `PlaceWeatherPane.kt` `HourlyCard` |
| Hourly charts | Interactive tap/scrub Rain, Wind, Humidity, and UV tabs over 24 hours | `Details.kt` `HourlyCharts` |
| 10 days | Daily rows with thermal gradient range bar | `PlaceWeatherPane.kt` `DailyCard` |
| Detail grid | Instrument tiles: Wind compass, Humidity & dew point comfort band, UV spectrum & advice, Aneroid pressure gauge, Visibility meter, Cloud cover bar, Sun daylight arc & duration, Moon phase disc (by source capability) | `Details.kt` `DetailGrid` |
| Attribution | Data source credit | `PlaceWeatherPane.kt` `Attribution` |
| No data | Error or empty state with "Fix it" | `PlaceWeatherPane.kt` `NoData` |

What each card shows (stale stamp, the "now" hour, rain sentence) is decided by `ForecastView` in core/ui, not by the card.

## Widgets

All in the `widget` module. Each has a receiver in `AndroidManifest.xml` and a layout section in `Widgets.kt`; shared pieces (frame, freshness, messages) are in `WidgetChrome.kt`, size thresholds in `WidgetLayout`.

| Name | Sizes | Shows |
| --- | --- | --- |
| Compact | 2×1 to 2×2+ | Temperature on the dial |
| Hourly | 4×1 to 5×2 | Now and the next hours, with rain |
| Daily | 4×2 to 4×4 | The coming days with their range |
| Complete | 4×3 to 5×5 | Now, the next hours and the coming days |
| Rain | 2×2 to 4×2 | Rain sentence and nowcast dots |
| Widget message | any | No place, place gone, loading, offline, key problem, no location, failed, partial (`WidgetMessage`) |
| Widget setup | — | Place and style picker when a widget is placed (`WidgetConfigActivity.kt`) |

## Entry points

`AppRequest` (core/ui) is the only way in from outside: `Show` (place, optional day and hour), `OpenPlaces`, `Refresh`, `PinWidget`. Widgets and shortcuts send these as Intent extras; see "Live Android check" in CLAUDE.md for the adb form.
