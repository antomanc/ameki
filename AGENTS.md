# ameki

AI-first generic project configured with `aginit` (Native mobile, kotlin).

## Core Invariants
- Keep changes small, focused, and test-verified: define the success check before writing code, and state assumptions you could not verify.
- Prefer native language & platform capabilities over heavy external frameworks.
- Never commit broken builds, failed typechecks, or unverified changes.

## Essential Commands
- Build: `./gradlew build`
- Test: `./gradlew test`

<!-- aginit:start — managed by npx @antomanc/aginit (re-run to refresh) -->
## Bootstrap & First Session
When the user asks to "bootstrap this project" or kicks off a new initiative:
1. Understand product intent and ask 2-3 essential questions to resolve ambiguity.
2. Draft or refine canonical context in `PRODUCT.md` and `DESIGN.md` (or `docs/adr/`).
3. Plan in small verifiable increments and implement test-first using installed skills.

## Task Routing
Load only what the task needs. Skills live in `.agents/skills/` (linked into `.claude/skills/` for Claude Code).

| Task | Read / Use |
| --- | --- |
| Product intent, scope, target users | `PRODUCT.md` |
| UI/UX design system, anti-slop review | `DESIGN.md` + `.agents/skills/impeccable` |
| Automated unit & E2E testing | `docs/agents/testing.md` |
| Live UI verification (Web / Mobile) | `docs/agents/ui-verification.md` |
| Engineering workflow & skills | `docs/agents/workflows.md` |
| Prose & Copy (docs, README, UI text) | `humanizer` skill |

## Anti-Rationalization
| Excuse | Rule |
| --- | --- |
| "It's a small UI tweak, visual verification isn't needed." | Forbidden. Run agent-device on the simulator/emulator, inspect the snapshot/screenshot, and confirm no runtime errors. |
| "Unit tests pass, so the feature works." | Insufficient. Unit tests do not verify wiring or rendering. Exercise the feature end to end (integration or smoke run) before marking complete. |
<!-- aginit:end -->

<!-- wellkept:start -->
## Checks
- `scripts/verify.sh` runs every check. Run it before calling work done; CI runs the same command.
- A failing check says what to do instead. Fix the cause; an exception names the rule and says why on the same line.
- A mistake corrected twice becomes a check in `.wellkept/state.json`, not a new line here.
- `docs/agents/feature-map.md` names every screen and control with the code behind it; keep it current.
<!-- wellkept:end -->
