# AGENTS.md

BetterBose: Android companion app for the Bose QC Ultra Headphones (2nd gen). Kotlin, Jetpack Compose, Glance widgets. Talks to the headphones over BMAP (RFCOMM), no network.

## Layout

- `bmap/` frame codec and blocking RFCOMM client
- `data/` headset state, session, repository, prefs
- `ui/` Compose screens (custom components, no Material)
- `alerts/` low battery notification and 15 min background job
- `widget/` Glance widgets
- `docs/PROTOCOL.md` verified BMAP commands

## Rules

- Never add the INTERNET permission.
- Only send BMAP commands listed in `docs/PROTOCOL.md`. Never probe unknown functions (some can reset the headphones).
- Keep files under ~250 lines. No trivial comments, no magic numbers.
- UI copy: short and natural, no em dashes.
- Conventional Commits, one logical change per commit.

## Skills

- `.opencode/skills/build` build, install, demo data
- `.opencode/skills/release` publish a GitHub release
