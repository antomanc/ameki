# ameki

AI-first generic project configured with `aginit` (Native mobile, kotlin).

## Core Invariants
- Keep changes small, focused, and test-verified: define the success check before writing code, and state assumptions you could not verify.
- Prefer native language & platform capabilities over heavy external frameworks.
- Never commit broken builds, failed typechecks, or unverified changes.

## Essential Commands
- Build: `./gradlew build`
- Test: `./gradlew test`


## Editing
- Use the Edit tool for Kotlin and text edits. Do not rewrite files with a `python3` heredoc: a failed match is retried and burns context. If Edit fails, read the exact lines and retry with a unique anchor.

## Checks
- `scripts/verify.sh` runs every check. Run it before calling work done; CI runs the same command.
- A failing check says what to do instead. Fix the cause; an exception names the rule and says why on the same line.
- A mistake corrected twice becomes a check in the conventions script or the linter, not a new line here.
- If a test contradicts the task, stop and report the conflict. Never change an expected value, skip a test or regenerate a snapshot just to get a green build.
- `docs/agents/feature-map.md` names every screen and control with the code behind it; keep it current.
