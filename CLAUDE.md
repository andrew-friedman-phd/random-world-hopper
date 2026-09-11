# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Random World Hopper++" — a RuneLite external plugin (Gradle project mirroring `runelite/example-plugin`)
that hops to a random world while tracking which worlds you've recently used, so a random hop never
sends you back to one still on cooldown. Unlike its sibling projects (`ruby-rings`, `wilderness-agility`),
this plugin is deliberately generic - no area-gating, no activity-specific item/XP tracking. It was
extracted from `ruby-rings`' random-hop-with-cooldown feature and generalized so the cooldown duration
is user-tunable per-activity instead of hardcoded. Package: `com.worldhopper`.

## Commands

- Run the dev client: `./gradlew.bat run` (Windows) or `./gradlew run` — launches `WorldHopperPluginTest`
  (in `src/test/java`), which loads this plugin as a builtin into a real RuneLite client
  (`-ea --developer-mode --debug`).
- Build a shadow jar: `./gradlew shadowJar`.
- First-time Jagex account auth in the dev client: launch with `--insecure-write-credentials` and a
  `credentials.properties` file so login doesn't have to be repeated every run.
- No lint/test suite beyond the manual dev-client run — there are no unit tests, `src/test` only
  contains the launcher.
- Client logs when debugging in-game behavior: `~/.runelite/logs/client.log` — this plugin logs under
  the `Random World Hopper++:` prefix via `@Slf4j`/`log.info`/`log.warn`.

## Architecture

**State lives in `WorldHopperPlugin`**, injected into the panel and overlay (they read plugin
getters, never hold their own state).

- `WorldHopperPlugin` — the hop state machine, world-type/ping filtering, cooldown bookkeeping,
  chat messaging, and config persistence.
- `WorldHopperPanel` — sidebar: cooldown list, the "World Cooldown" minutes spinner, and the
  "Hop to random world" button.
- `WorldHopperOverlay` — a single-line "World Hops: N" box docked under the minimap
  (`OverlayPosition.TOP_RIGHT`), visible only while the sidebar panel tab is the active one
  (`plugin.isPanelActive()`, toggled from the panel's `onActivate`/`onDeactivate`).
- `WorldHopperConfig` — ping threshold, the "World types" checkbox section, and the default/
  persisted `cooldownMinutes`.

**Hop-click ordering** (`hopToRandomWorld()`): the current world is put on cooldown
(`recordCurrentWorldCooldown()`) *before* the search for a hop target even runs, and
unconditionally — even if no candidate world turns up afterward. This matches the literal spec
this plugin was built to: "add the world they are hopping from to the cooldown list, and hop to
an available world from the remaining ones." Don't reorder this to be "only cooldown on a
successful hop" without checking that's actually wanted — it changes user-visible behavior on
the failure path.

**World hop state machine** (`hopToRandomWorld()` → `findAndHopToWorld0()` → `hopTo()` →
`progressPendingHop()`/`progressQuickHop()`, driven from `onGameTick`) exists because
`client.hopToWorld()` silently no-ops unless the World Switcher widget
(`InterfaceID.Worldswitcher.BUTTONS`) is already open — this mirrors RuneLite's own
`WorldHopperPlugin` tick-driven open-then-hop retry logic, including waiting a tick after the
"Hopping to World X..." chat message so it survives the world-hop reconnect. The button is
debounced two ways: a full `HOP_BUTTON_COOLDOWN` (2s) after a hop actually starts, or the shorter
`HOP_BUTTON_RETRY_COOLDOWN` (~1 tick) specifically when the world-list fetch itself failed (a
transient, likely network-related hiccup) — the "no candidates match filters" and "no candidates
under ping threshold" failure modes are deterministic given the current config, so they get the
full cooldown instead since retrying instantly won't help.

**Cooldown persistence** (`loadCooldowns()`/`persistCooldowns()`): `Map<Integer worldId, Instant
expiry>` is JSON-serialized (as `Map<Integer, Long>` epoch millis) into `ConfigManager` under
`worldCooldowns` in the `worldhopper` config group. `loadCooldowns()` drops already-expired
entries on load rather than keeping them around to be filtered out later.

**World-type filtering** (`matchesWorldTypeFilters`/`isNormalWorld`) reimplements RuneLite's
package-private `WorldTypeFilter` logic locally (can't be referenced from an external plugin),
and is deliberately *stricter* than RuneLite's own "normal" definition — Skill Total and Bounty
Hunter worlds get their own checkboxes rather than being lumped into "Normal".

**Skill Total level check** (`meetsSkillTotalRequirement`): `World` has no structured field for a
Skill Total world's minimum total level - it's only present as free text in `getActivity()` (e.g.
`"1250 Skill total"`), which is also all RuneLite's own `WorldSwitcherPanel` does with it (just
displays the raw string). This parses the leading number out with a regex and compares it to
`client.getTotalLevel()`, excluding the world as a candidate if the account doesn't qualify -
otherwise a Skill Total world would be a valid-looking hop target that immediately bounces you.
If the activity text doesn't parse (format change, etc.), the world is allowed through rather than
silently hidden. `allowSkillTotalWorlds` defaults to **true** specifically because this check
exists - it's safe to leave on since ineligible worlds are already filtered out.

**Session hop counter** (`sessionHopCount`) increments once per successful hop (when a
ping-eligible world is actually found and `hopTo()` is called), and resets to 0 on
`GameStateChanged` → `GameState.LOGIN_SCREEN`, so it represents hops "since you last logged in"
rather than since the client process started.

**"World Cooldown" field** (`WorldHopperConfig.cooldownMinutes`) is a real `@ConfigItem`, not
panel-local state — this way it has a sane default and survives a client restart even before the
sidebar panel is ever opened, and the panel's `JSpinner` just reads/writes it directly via
`ConfigManager` on every change (no separate synchronization step).

## RuneLite API gotchas specific to this codebase

- `net.runelite.http.api.worlds.WorldType` (used for the hop filter) is a different class from
  `net.runelite.api.WorldType` — don't confuse them.
- `net.runelite.client.plugins.worldhopper.ping.Ping` is public and reused directly;
  `WorldTypeFilter` in the same package is not, hence the local reimplementation.
- When verifying unfamiliar RuneLite API surface, fetch the real source from
  `raw.githubusercontent.com/runelite/runelite/master/...` rather than guessing signatures.
