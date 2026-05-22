# OraWorldRegen

**Automatic World Regeneration Plugin for Paper/Spigot + Multiverse-Core**

Automatically regenerates worlds on a schedule and provides everything you need: countdown timers, player teleportation, backups, world borders, post-regen commands, GUI management, and more.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [GUI Overview](#gui-overview)
- [Schedule Configuration](#schedule-configuration)
- [Gate Auto-Generation](#gate-auto-generation)
- [Regeneration Flow](#regeneration-flow)
- [Data Files](#data-files)
- [License](#license)

---

## Requirements

| Item | Version |
|------|---------|
| Minecraft | 1.20.4 |
| Server | Paper / Spigot |
| Java | 17 or higher |
| Required Plugins | OraPluginAPI, Multiverse-Core |
| Optional Plugins | Multiverse-Portals (for gate auto-generation) |

---

## Installation

1. Place `OraWorldRegen-1.0.0.jar` in your `plugins/` folder
2. Place `OraPluginAPI.jar` and `Multiverse-Core.jar` in the same folder
3. Start the server — `plugins/OraWorldRegen/config.yml` will be generated automatically
4. Edit `config.yml` to register your worlds, then run `/owr reload` to apply changes

---

## Configuration

`plugins/OraWorldRegen/config.yml`

```yaml
worlds:
  resources:                           # Arbitrary key (used as the world identifier)
    multiverse-world-name: "resources" # World name as registered in Multiverse-Core
    environment: NORMAL                # NORMAL / NETHER / THE_END
    world-type: NORMAL                 # NORMAL / FLAT / AMPLIFIED / LARGE_BIOMES
    seed: ""                           # Leave blank for a random seed
    generator: ""                      # Leave blank for vanilla generation

    enabled: true
    countdown-seconds: 300             # Countdown before regeneration (0 = immediate)
    fallback-world: "world"            # World to teleport players to during regen

    schedules:
      - cron: "0 4 * * 0"             # Every Sunday at 4:00 AM

    backup:
      enabled: false
      directory: "backups"            # Relative to the plugin folder (absolute paths work too)
      max-count: 5                    # Maximum number of backups to keep

    world-border:
      enabled: false
      size: 2000.0                    # Border diameter (e.g. 2000 = 2000×2000)
      center-x: 0.0
      center-z: 0.0
      damage-amount: 0.2
      damage-buffer: 5.0
      warning-distance: 5
      warning-time: 15

    post-regen-commands:
      - "mv modify set gamemode survival {world}"  # {world} is replaced with the world name

    return-players:
      enabled: true                   # Return players to their original locations after regen
      delay-seconds: 60               # Seconds to wait after completion before returning players

settings:
  notify-at-seconds: [300, 120, 60, 30, 10, 5, 4, 3, 2, 1]
  timezone: "Asia/Tokyo"
  debug: false
```

### Gate Configuration

To automatically create Multiverse-Portals gates after regeneration, add a `gates` section to `config.yml`:

```yaml
gates:
  myGate:
    world: "resources"           # World where the gate will be placed
    location:
      x1: 10
      y1: 64
      z1: 10
      x2: 12
      y2: 67
      z2: 10
    frame-block: "OBSIDIAN"      # Block used for the gate frame
    portal-block: "WATER"        # Block filling the gate interior (WATER / LAVA / NETHER_PORTAL / AIR, etc.)
    destination: "w:world"       # Multiverse destination string
    enabled: true
    owner: "OraWorldRegen"
```

---

## Commands

All commands start with `/owr`.

| Command | Description |
|---------|-------------|
| `/owr` | Open the main menu GUI |
| `/owr gui` | Open the main menu GUI |
| `/owr start <world>` | Manually trigger regeneration (players see a confirmation GUI) |
| `/owr cancel <world>` | Cancel a countdown or queued regeneration |
| `/owr queue` | Show the current regeneration queue |
| `/owr status` | Show the status of all registered worlds |
| `/owr list` | List all registered worlds |
| `/owr history [world] [page]` | View regeneration history |
| `/owr reload` | Reload `config.yml` |
| `/owr help` | Show help |

---

## Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `oraworldregen.admin` | Access to all `/owr` commands | OP only |

---

## GUI Overview

### Main Menu (`/owr`)

Each registered world is displayed as a colored wool block indicating its current status.

| Wool Color | Status |
|-----------|--------|
| Green | Idle (ready to regenerate) |
| Yellow | Countdown in progress |
| Orange | Regenerating |
| Cyan | Queued |
| Red | Disabled |

- **Left-click** → Open the world detail GUI
- **Right-click** → Start immediate regeneration (with confirmation dialog) or cancel

### World Detail GUI

Displays full configuration details including schedules, backup settings, world border, recent history, and more. You can also toggle enabled/disabled and trigger regeneration from here.

---

## Schedule Configuration

Two formats are supported and can be mixed within the same world's schedule list.

### Human-readable format (recommended)

`time` is required; `dayofweek` and `day` are optional.

```yaml
schedules:
  # Every Monday at 18:30
  - time: "18:30"
    dayofweek: "MONDAY"

  # Every month on the 15th at 12:00
  - time: "12:00"
    day: 15

  # Every day at 03:00 (omit dayofweek and day)
  - time: "03:00"

  # Every Wednesday AND the 15th of the month at 09:00 (AND condition)
  - time: "09:00"
    dayofweek: "WEDNESDAY"
    day: 15
```

| Key | Value | Description |
|-----|-------|-------------|
| `time` | `"HH:mm"` | Trigger time (required) |
| `dayofweek` | `MONDAY` – `SUNDAY` | Day-of-week filter (omit for every day) |
| `day` | `1` – `31` | Day-of-month filter (omit for every day) |

When both `dayofweek` and `day` are specified, both conditions must be met (**AND**).

### Cron format (backward compatible)

The original cron syntax remains fully supported.

```yaml
schedules:
  - cron: "0 4 * * 0"   # Every Sunday at 4:00 AM
```

```
minute  hour  day  month  weekday(0=Sunday)
0       4     *    *      0    → Every Sunday at 4:00 AM
0       0     *    *      *    → Every day at midnight
```

> **Note:** CronParser supports `*` (wildcard) and fixed numeric values only. Step values (`*/n`) and ranges (`1-5`) are not supported.

---

## Gate Auto-Generation

After regeneration completes, the plugin automatically:

1. Places gate frame and portal blocks in the world
2. Writes the gate entry to `plugins/Multiverse-Portals/portals.yml`
3. Runs `/mvp reload` to apply the changes (2-second delay to ensure world loading is complete)

**Destination format examples:**

| Format | Meaning |
|--------|---------|
| `w:world` | Spawn point of `world` |
| `e:world:100,64,200` | Specific coordinates |
| `e:world:100,64,200:0:90` | Coordinates with yaw/pitch |
| `p:otherPortal` | Another Multiverse portal |

---

## Regeneration Flow

```
startRegen()
  │
  ├─ [countdown-seconds > 0]
  │     Countdown with broadcast messages and title displays
  │
  ├─ Teleport all players to fallback-world
  ├─ [backup.enabled] Create zip backup (async)
  ├─ Unload world from Multiverse
  ├─ Delete world folder (async)
  ├─ Re-import world via Multiverse
  ├─ [world-border.enabled] Apply world border settings
  ├─ [gates] Place gate blocks → write portals.yml → /mvp reload
  ├─ [post-regen-commands] Execute post-regen commands
  └─ [return-players.enabled] Teleport players back to their original locations
```

If multiple worlds are queued for regeneration at the same time, they execute one at a time — each world waits in the queue until the previous one completes.

---

## Data Files

| File | Contents |
|------|----------|
| `plugins/OraWorldRegen/config.yml` | Main configuration |
| `plugins/OraWorldRegen/history.yml` | Regeneration history (up to 200 entries) |
| `plugins/OraWorldRegen/regen.log` | Plain-text log of all regeneration events |
| `plugins/OraWorldRegen/backups/` | Backup zip files (when backup is enabled) |

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.