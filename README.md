# Random World Hopper++

A RuneLite plugin for hopping to a random world while keeping track of worlds you've already
used, so you don't land back on one before it's had time to reset - useful for any activity
where worlds "run out" for a while (loot respawns, spawn timers, instance-like farming spots,
etc.), not tied to any specific location or activity.

## Features

- **Hop to random world**: a sidebar button that records the world you're currently on as
  "on cooldown," then hops you to a random world that isn't on cooldown and matches your ping
  and world-type filters. If nothing qualifies, a `[RWH]` chat message explains why (world list
  couldn't be fetched, every candidate is filtered out or on cooldown, or none are under your
  ping threshold) - the tag is purple, the message itself is normal chat color.
- **Worlds on cooldown list**: the sidebar panel shows every world currently on cooldown with a
  live mm:ss countdown, newest first, clearing itself out as entries expire.
- **World Cooldown field**: a number field right in the sidebar panel to tune how long a world
  stays on cooldown (in minutes) - turn it down for a fast-paced activity, up for a slower one,
  without digging into the plugin's settings screen. Persists across client restarts.
- **World Hops counter**: while the sidebar panel is open, a small "World Hops" counter appears
  on screen under the minimap, showing how many times you've hopped since you last logged in.

## Configuration

- **World Cooldown** - default cooldown duration (minutes) for a world after you hop away from
  it. Also editable directly from the sidebar panel.
- **Max ping for hop (ms)** - the random-hop button only considers worlds at or below this ping.
- **World types** - which world types (Normal, Deadman, Seasonal/Tournament, Quest Speedrunning,
  Fresh Start, PVP, Skill Total, High Risk, Bounty Hunter) and F2P/members worlds the random-hop
  button is allowed to pick from. Skill Total worlds are on by default - the plugin reads each
  one's minimum total level requirement and only considers worlds your account actually
  qualifies for, so you won't get hopped somewhere you'd just be bounced from.

*The plugin icon is a placeholder pending final art.*
