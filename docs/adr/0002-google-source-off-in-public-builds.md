# The Google source is off in public builds

Google Maps Platform's Service Specific Terms (section 21.1, Weather API) forbid using Weather API
content in "a weather app ... whose primary purpose is to provide weather information", and
each user's own API key does not change that. Public builds (GitHub Releases, F-Droid) ship with
Open-Meteo only, and Open-Meteo is the default Source. The Google source stays in the codebase,
fully working and tested. It is compiled in only with `-Pameki.googleSource=true` (BuildConfig
`GOOGLE_SOURCE_ENABLED`), and the README explains how to build it for personal use. We will ask
Google for a written clarification and revisit this decision when the answer arrives.

## Considered options

- Ship Google as a user-selectable source with a disclaimer: rejected because Google can revoke keys, F-Droid may object, and we would knowingly ship something its terms forbid.
- Remove Google entirely: rejected because the code is cheap to keep behind a flag and the decision may change.

## Consequences

- Public builds have no Alert data, because Open-Meteo has no alerts. Alerts need another Source or a later decision.
- Onboarding cannot assume there is a choice of Source.
- Google's caching limits (1 h for current, hourly and alerts; 24 h for daily) still apply in flagged builds and are enforced in the repository.
