# Coding standards

Judgement rules for review. Mechanical rules live in `scripts/check-conventions.py` and `.ratchet/state.json` (checked by `scripts/ratchet-check.mjs`), not here.

## Where a decision lives

- **What a snapshot shows** (stale stamp, the "now" hour and its temperature, which days and hours are listed, rain sentence and dots) is decided by `ForecastView` in core/ui. The app and the widgets render it; a new display rule goes there, with a JVM test, never into a composable or a Glance layout.
- **Source differences stay behind the `WeatherSource` seam.** UI and view models never branch on Google vs Open-Meteo; they read what the repository exposes (for example `PlaceWeather.dayHours`). A Source-specific comment in feature code is a sign of a leak.
- **Retention and freshness** belong to `WeatherSnapshot` (`retainedAt`, `nextChange`, `partIsFresh`) and `RefreshPolicy`. Google caching limits must hold for every hour stored, including on-demand hours.
- **Navigation requests** are `AppRequest`s. Deciding where a request leads is `nextStep` (pure, tested); screens only apply a `Navigation`.
- **Widget size thresholds** are in `WidgetLayout`, tested; a widget layout reads them instead of comparing dp values inline.
- **Widget instances** are owned by `WidgetInstances` in core/data.
