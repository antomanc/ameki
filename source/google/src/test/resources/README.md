# Google Weather API fixtures

- `real/` holds real responses (Fara in Sabina, 2026-11-14), including `error_alerts_units.json`: publicAlerts rejects `unitsSystem`.
- `error_invalid_key.json` is a real response, captured from `currentConditions:lookup` with an invalid key.
- The other files follow the documented REST schema
  (https://developers.google.com/maps/documentation/weather/reference/rest) with realistic values.
  Replace them with real captures when a key is available:
  `curl -H "X-Goog-Api-Key: $KEY" "https://weather.googleapis.com/v1/currentConditions:lookup?location.latitude=41.9&location.longitude=12.5&languageCode=it"`.
  Strip nothing sensitive: responses contain no key.
