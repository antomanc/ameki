@AGENTS.md

## Agent skills

### Issue tracker

Issues live in GitHub Issues on antomanc/ameki, used through the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Live Android check

`scripts/emulator.sh` boots the `ameki_fold` AVD headless, installs the debug build and opens it; `scripts/emulator.sh stop` frees its RAM. For a few minutes after boot SystemUI may show "isn't responding": press Wait. The Fold has two displays, so `adb screencap` fails: use `agent-device screenshot`. Requests from widgets and shortcuts are `AppRequest` extras: `adb shell am start ... --el dev.antomanc.ameki.PLACE_ID 1 --el dev.antomanc.ameki.DAY_EPOCH_DAY <epoch day>`.

### Review

Reviewers apply `CODING_STANDARDS.md`. `scripts/check-conventions.py` and `scripts/wellkept-check.mjs` (both run in CI) enforce the mechanical rules; wellkept-check's per-file ratchet caps each file at the effects and `!!` it had, so a file can't gain more.

### Feature map

`docs/agents/feature-map.md` names every screen and widget and the files behind it. Use its names when talking about the UI.
