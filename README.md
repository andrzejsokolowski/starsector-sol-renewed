# Sol Renewed

A gamified Sol system for Starsector 0.98a. The eight planets, Pluto, and the moons that matter
(Luna, Io, Europa, Ganymede, Callisto, Titan, Charon) at distances you can actually fly across,
with nothing smaller than radius 50 so every body is easy to click.

Built on the shoulders of two other Sol mods: **Sol Solar System** by Bugatti Echelon (textures,
star art, descriptions, the cryo-prison, the Centauri constellation, music "Breath of SOL" by
Taiwendo) and **Solsector** by Ugly American (the Remnant nest approach). No radiation mechanic.

## What is in the system

- A hypershunt orbiting Sol, one gate with a Domain-era mothership and gate hauler nearby, two
  jump points, a comm relay, sensor array and nav buoy, two stable points on Earth's orbit.
- Ashes of the Domain: the Nidavellir shipyard ring on Mars and the Pluto Mining Station at Pluto
  (both still spawn elsewhere in the sector too). Pre-collapse facilities on Venus and Callisto.
- Planet conditions from Unknown Skies, Random Assortment of Things and Industrial Evolution
  when those mods are on (IndEvo ruins with fixed industries on Earth, Mars and Charon; an artillery
  station over Jupiter; a minefield over Venus).
- A Domain cryosleeper past Pluto and, with Dreams of the Past, a Domain-era Ark near Neptune.
- Gungnir Dockyard, an abandoned station over Mars with its storage still stocked.
- "The Inexorable Stasis", a cryo-detention platform: wake one of eight prisoner officers.
- Remnant presence in four steps (LunaSettings): none, light (outer system only, default), heavy,
  overrun. Light leaves the inner planets free to colonize from day one.
- Alpha Centauri (three stars, Proxima b, a guarded Remnant nest with three Radiant wrecks) and
  Beta Centauri about 4 light-years away.
- Seasonal Industrial Evolution meteor showers while you are in or near Sol.

## Building

`./gradlew jar` (needs `starsectorPath` in `gradle.properties`), then `python package.py` to stage
the release folder and zip it for a mod manager.
