# nembo

AI-first generic project configured with `aginit` (Native mobile, kotlin).

## Core Invariants
- Keep changes small, focused, and test-verified.
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

## Guardrails (karpathy-guidelines)
- **Think before coding**: state assumptions and trade-offs; ask instead of guessing.
- **Simplicity first**: minimum code that solves the problem; no speculative features or abstractions.
- **Surgical changes**: touch only what the task needs; no silent scope creep or drive-by refactors.
- **Goal-driven execution**: define a verifiable success check first, then loop until it passes.

## Task Routing
Load only what the task needs. Skills live in `.agents/skills/` (linked into `.claude/skills/` for Claude Code).

| Task | Read / Use |
| --- | --- |
| Product intent, scope, target users | `PRODUCT.md` |
| UI/UX design system, anti-slop review | `DESIGN.md` + `.agents/skills/impeccable` |
| Automated unit & E2E testing | `docs/agents/testing.md` |
| Live UI verification (Web / Mobile) | `docs/agents/ui-verification.md` |
| Engineering workflow & skills | `docs/agents/workflows.md` |
| Scoping or simplifying a change | `ponytail` skill |
| Prose & Copy (docs, README, UI text) | `humanizer` skill |

## Anti-Rationalization
| Excuse | Rule |
| --- | --- |
| "It's a small UI tweak, visual verification isn't needed." | Forbidden. Run agent-device on the simulator/emulator, inspect the snapshot/screenshot, and confirm no runtime errors. |
| "Unit tests pass, so the feature works." | Insufficient. Unit tests do not verify wiring or rendering. Exercise the feature end to end (integration or smoke run) before marking complete. |
<!-- aginit:end -->
