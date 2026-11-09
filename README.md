# Sol Renewed

[Forum thread](https://fractalsoftworks.com/forum/index.php?topic=35983)

A gamified Sol system for Starsector 0.98a. The eight planets, Pluto, and the moons that matter
(Luna, Io, Europa, Ganymede, Callisto, Titan, Charon) at distances you can actually fly across,
with nothing smaller than radius 50 so every body is easy to click.

Built on the shoulders of two other Sol mods: **Sol Solar System** by Bugatti Echelon (textures,
star art, descriptions, the cryo-prison, the Centauri constellation, music "Breath of SOL" by
Taiwendo) and **Solsector** by Ugly American (the Remnant nest approach). No radiation mechanic.

## Getting there: the "Transferring Sol" quest

By default Sol does not exist until you bring it here. From level 10, once your fleet can transverse
jump, a concerned researcher waits in every bar until you accept: a rogue Tri-Tachyon AI on Eochu Bres
(Hybrasil), Glasya-Labolas, has picked up Sol transmitting from a region of the Abyss too dense to jump
into. If you have already destroyed a shrouded ship it hands you the Macro-scale Translocator at once;
otherwise go into the Abyss, kill one, and come back. Fly the device to the signal far beyond the
northern edge of the map, activate it on top of the signal, and Sol arrives in the Persean Sector.
Glasya-Labolas also has a "skip the quest" option that places Sol immediately, and LunaSettings can
switch the mod back to generating Sol with the sector at game start.

## What is in the system

- A hypershunt orbiting Sol, one gate with a Domain-era mothership nearby, two jump points, a
  comm relay, sensor array and nav buoy, two stable points on Earth's orbit.
- Ashes of the Domain: the Nidavellir shipyard ring on Mars and the Pluto Mining Station at Pluto
  (both still spawn elsewhere in the sector too). Pre-collapse facilities on Venus and Callisto.
- Planet conditions from Unknown Skies, Random Assortment of Things and Industrial Evolution
  when those mods are on (IndEvo ruins with fixed industries on Earth, Mars and Charon; an artillery
  station over Jupiter).
- A Domain cryosleeper past Pluto and, with Dreams of the Past, a Domain-era Ark near Neptune.
- Gungnir Dockyard, an abandoned station over Mars with its storage still stocked.
- "The Inexorable Stasis", a cryo-detention platform: wake one of eight prisoner officers.
- Remnant presence in four steps (LunaSettings): none, light (outer system only, default), heavy,
  overrun. Light leaves the inner planets free to colonize from day one.
- Alpha Centauri (three stars, Proxima b, a guarded Remnant nest with three Radiant wrecks) and
  Beta Centauri about 4 light-years away.
- Seasonal Industrial Evolution meteor showers while you are in or near Sol.
- The Sol music ("Breath of SOL") keeps playing while you are docked at a planet or station in Sol
  or Alpha Centauri. Fleet encounters and battles keep their own music. LunaSettings can switch this off.
- Wide Horizons and Wide Horizons Basic: Sol and the Centauri systems are placed to match the
  larger map.

## The colony crisis: "Discovering the Past"

Colonize Sol and the Sector starts to remember it. Every month your Sol colonies fill a meter in
the intel screen. Big colonies count far more than small ones: nothing up to size 3, then 5, 15,
25, 40, 60 and 80 for sizes 4 to 9, and 100 from size 10 on. Every industry adds 1 per 200,000
credits of its own build cost (what it was upgraded from does not count). The meter keeps rising
while you hold Sol, and nothing lowers it.

Every mark on the meter brings one attack, each stronger than the last: at 200 a pirate base and
a Luddic Path base move in nearby and a pirate raid comes (50%), at 450 a strike force (75%), at
700 a war fleet (100%), at 1000 an armada (150%). The attacker is the pirates, the Luddic Path,
the Remnants from the nests in Sol, or one of the Sector's factions: any faction with colonies,
modded ones too, unless you stand at 30 or better with it, it holds your commission, or it is the
independents. Pirates and Pathers send fewer, better ships, and Remnant attacks are smaller.

After the Reckoning the meter and every mark on it get 1.5 times as long (1500, then 2250, 3375
and so on, up to 8000), the meter drops back to the Raid mark and every later attack is stronger.
LunaSettings: on/off, meter speed, attack strength, how much longer the meter gets per Reckoning.

## Building

`./gradlew jar` (needs `starsectorPath` in `gradle.properties`), then `python package.py` to stage
the release folder and zip it for a mod manager.

After building, run `python tests/run_tests.py` to run the regression tests against campaign API fakes.
This test requires a JDK and uses the game path from `gradle.properties`. It does not start Starsector.
