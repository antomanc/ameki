# Ameki

A weather app whose home-screen widgets are the main product. It shows forecasts for the places a user follows, from the data source they choose.

## Places

**Place**:
A location the user follows, with a name and coordinates. Shown in the UI as *località*.
_Avoid_: Location, city, saved location

**Current location**:
The single special Place whose coordinates come from the device rather than from a search. Shown as *posizione attuale*.
_Avoid_: GPS place, my location

## Weather data

**Source**:
The provider of weather data a user picks: Google or Open-Meteo. Shown as *fonte*.
_Avoid_: Provider (in user-facing text), backend, API

**Snapshot**:
The latest known weather for one Place from one Source: what the app and widgets display.
_Avoid_: Forecast (for the whole thing), weather data, cache entry

**Part**:
One of the four independently refreshed sections of a Snapshot: current, hourly, daily, alerts.
_Avoid_: Section, block

**Refresh**:
Fetching one or more Parts of a Snapshot from its Source over the network.
_Avoid_: Sync, update (for network fetches)

**Alert**:
An official weather warning issued by an authority for an area that includes a Place.
_Avoid_: Warning, notification

## Home screen

**Widget instance**:
One widget placed on the home screen, with its own Place, style and background opacity. Shown as *widget*.
_Avoid_: Widget config, appwidget
