# Party Wayfinder

A RuneLite plugin that shows your party members' world and location, puts them on the world map, and gives you a one-click **Shortest-path** to them.

Built on RuneLite's own party service, so you join a party the normal way (Party panel, shared passphrase). Every member who wants to be seen needs this plugin enabled. It adds to the core Party plugin rather than replacing it.

## Features

- **Overlay** listing each party member: name, world (green = your world, yellow = another world, grey = offline) and a location label such as `Varrock`, `near Falador`, `Under Edgeville`, `Wilderness lvl 12` or `Instance (Barrows)`. Optional tile coordinates.
- **Right-click a row in the overlay:** `Shortest-path <name>` hands their tile to the Shortest Path plugin.
- **World map icons** in each member's party colour (the same colour the core Party plugin gives them). Bright with a white ring when they're on your world, faded when they're not. Icons snap to the map edge when off-screen. The core Party plugin also draws each member's avatar on the map; this icon sits on the same tile and adds the world status and menu option.
  - Right-click an icon on your world: `Shortest-path`.
  - Hover an icon on another world to see the world number.
  - Left-click: focus the map on them (RuneLite's standard behaviour).

## Joining someone on another world

The plugin doesn't hop for you. Hopping from in-game clicks can get accounts banned. The world number is always shown, so type RuneLite's built-in command in chat:

```
::hop 330
```

When you path to someone on another world, the chat message gives you the exact command.

## Requirements

- RuneLite's built-in **Party** plugin enabled, and everyone in the same party.
- [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) from the Plugin Hub for the `Shortest-path` action. Without it, the plugin tells you in chat.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Show overlay | On | Show the party list overlay |
| Show coordinates | Off | Show `x, y, plane` under each member |
| World map icons | On | Draw members on the world map |
| Update interval (ticks) | 2 | Minimum ticks between location updates while moving (1-10) |

## How it works

- Each client sends a small `PartyWayfinderLocationUpdate` message (name, world, x, y, plane, instanced) over the RuneLite party websocket when its tile or world changes, rate-limited by the update interval, plus a heartbeat every ~30 seconds. Logging out sends world 0 so others see you as offline.
- Locations are only shared with members of a party you joined. Nothing goes to a third-party server.
- Instanced locations are converted back to real-world template coordinates before sending.
- Members with no update for 90 seconds are shown as `(stale)` and dropped from the map.
- Shortest-path posts a `PluginMessage("shortestpath", "path", {target: WorldPoint})`, the Shortest Path plugin's public cross-plugin API.
- Member colours come from the core Party plugin (`PartyPluginService`), with a generated fallback until one is assigned.
- Location labels come from a nearest-landmark table (`LocationNames.java`). They're approximate hints, not exact area names. Add landmarks there to improve coverage.

## Development

Requires JDK 11.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-11.0.33.10-hotspot"
.\gradlew.bat build
.\gradlew.bat run
```

`run` launches a developer RuneLite client with the plugin loaded. To log in with a Jagex account, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

Testing the party features needs two clients in the same party, both running this plugin.

## License

BSD 2-Clause. See `LICENSE`.
