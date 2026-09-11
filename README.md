# StationsAnywhere

![Banner](banner.png)

A mod for [Sector Space](https://store.steampowered.com/app/3978250/Sector_Space/)
that adds a window listing every sector the game marks as a special zone
(station-restricted) and lets you grant a station-deploy permit to each one via
a checkbox. Undiscovered zones stay masked until you find them.

It is an [SSFML](https://github.com/) / Fabric-style mixin mod that hooks into
the game at runtime, so no game files are modified permanently.

Source: [github.com/jpreed00/StationsAnywhere](https://github.com/jpreed00/StationsAnywhere)

## What it does

- Press **O** (default) to open or close the Stations Anywhere window.
- The window lists restricted sectors in a table:
  - **Deploy** — checkbox to allow station deploy (and claim/repair) there
  - **Name**, **Sector** (coordinates), **Region**, **Type**
- Discovered zones show their real name and coordinates; undiscovered zones
  show as `??????` / `(?? / ??)`, drawn greyed-out, and cannot be clicked until
  found.
- Permits persist per world. Unchecking a sector only stops *future* deploys —
  it never removes stations you have already placed.

## How it works (and why it's light on performance)

The mod is almost entirely event-driven:

- **Deploy gate** redirects the special-zone / protected / deep-void checks
  inside `game.platform.Deployables.checkStationOrDrone` when that sector has a
  permit, so you can place a station there. Drones/platforms are unchanged.
- **Claim/repair** redirects the matching `isSpecialZone()` check in
  `menu.StationWindow.updateReclaimPanel`, so a permitted special zone can also
  be claimed and repaired.
- **Sector changes** hook `game.world.Galaxy.setCurrentSector` to rebuild the
  list (only when the window is open).
- The **only per-frame code** is a single key check in
  `game.HeadsUpDisplay.update` to detect the toggle key.

Permits are stored per world in `stationsanywhere_data/` next to the game, so
no hook into the game's own save system is required.

## Requirements

- **Sector Space** `>= 0.6.0.2` (may work with older versions; only tested on this one).
- **SSFML** (the Sector Space Fabric Mod Loader) with **Fabric Loader** `>= 0.18.4`.
- **JDK 25+** (only needed to build from source, not to play).

## Installation (players)

1. Download `stationsanywhere.jar` from
   [GitHub Releases](https://github.com/jpreed00/StationsAnywhere/releases) or
   [build from source](#building-from-source).
2. Copy it into the `mods\` folder inside your Sector Space install directory, e.g.:
   ```
   ...\Steam\steamapps\common\Sector Space\mods\stationsanywhere.jar
   ```
3. Launch Sector Space (with SSFML installed). The mod loads automatically.

## Building from source

The mod compiles against the game's own jars, so the project folder is expected
to live **inside your Sector Space install directory** (alongside
`Sector Space.jar`, `SSFML.jar`, and `libs\`):

```
Sector Space\
├─ Sector Space.jar
├─ SSFML.jar
├─ libs\*.jar
├─ mods\
└─ StationsAnywhere\        <- this repo
   ├─ stationsanywhere\mixin\*.java
   ├─ code\window\*.java
   ├─ code\store\*.java
   ├─ fabric.mod.json
   └─ stationsanywhere.mixins.json
```

```
git clone https://github.com/jpreed00/StationsAnywhere.git
```

### 1. Point to your JDK

Make sure a JDK 25+ `javac`/`jar` is on your `PATH`, or note the path to its
`bin` folder (the bundled `Makefile` defaults to `D:\Java\jdk-25.0.4.1`).

### 2. Build

From the `StationsAnywhere` folder:

```
make
```

That compiles the sources against the game jars one directory up and packages
`..\mods\stationsanywhere.jar`, ready to load. `make run` will also launch the
game.

From the Sector Space directory, `make` builds every mod that has a Makefile.

To build by hand instead:

```
javac -encoding UTF-8 -cp "../Sector Space.jar;../SSFML.jar;../libs/*" -d build/classes stationsanywhere/mixin/*.java code/window/*.java code/store/*.java
jar --create --file ../mods/stationsanywhere.jar -C build/classes . -C . fabric.mod.json -C . stationsanywhere.mixins.json
```

> On Linux/macOS, use `:` instead of `;` as the classpath separator.

## Project layout

| Path                         | Purpose                                                    |
| ---------------------------- | ---------------------------------------------------------- |
| `stationsanywhere/mixin/`    | Mixins: deploy gate, claim/repair, sector change, hotkey   |
| `code/window/`               | The Stations Anywhere window and its row model            |
| `code/store/`                | Persistent per-world permit set                            |
| `fabric.mod.json`            | Mod metadata, dependencies, and mixin registration          |
| `stationsanywhere.mixins.json` | Mixin configuration                                      |

## License

Released under the [MIT License](LICENSE).
