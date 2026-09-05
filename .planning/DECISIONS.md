# Sol Renewed - locked decisions (from the user, 2026-09-05)

Keep from Bugatti's mod:
- Planet textures, Saturn ring sprite, Sol star texture + halo, warroom map icon, Luna boot-print image, galaxy background.
- "Breath of SOL" music (Taiwendo).
- Planet/station descriptions, but NO nicknames ("The Blue Marble" etc). Real names only.
- Magnetic field at Jupiter only (not Earth). Main asteroid belt.
- All cross-mod planet conditions (US / RAT / IndEvo / AOTD). Extra vanilla conditions added to spice up and nerf.
- ONE gate ("Sol Gate"), decorated Bugatti-style (debris, wrecks, caches, probe, survey ship, mothership, gate hauler).
- Jump points named the vanilla way ("Inner System Jump-point", "Fringe Jump-point").
- Comm relay, sensor array, nav buoy.
- Cryo-prison station (one prisoner officer, special skills).
- Only ONE abandoned station: Gungnir Dockyard over Mars, loot pruned of Superweapons.
- Seasonal IndEvo meteor showers.
- Alpha + Beta Centauri, radiation removed completely.
- The Dreams of the Past ark + a separate Domain cryosleeper.
- Moons: Luna, Io, Europa, Ganymede, Callisto, Titan, Charon. Nothing else (no Phobos/Deimos/Ceres/Enceladus/Oberon/Titania/Miranda/Triton).

Dropped: radiation (fields, storms, hullmod, "Irradiated" rig), Earth magnetic field, ISS / Titan's Eye / Telepylos stations,
research station, dredge platform, habitat, Voyager, Endurance, tech cache, second gate, hyperspace "Oort Cloud" labels,
second sensor array, nebula patch inside Sol.

Remnants: Solsector-style nests, four levels in LunaSettings. Default "Light" so Sol can be colonized from game start.
Later: a colony crisis that intensifies attacks (factions + Remnants) based on the player's interference with Sol. See BACKLOG.md.

AOTD: the second (random) Pluto station / Nidavellir elsewhere in the sector stay. Two of each is fine.

Licensing: skipped for now; the mod will be shown to the Bugatti and Solsector authors as a shared effort.

## Round 2 tweaks (user, 2026-09-05, after first look)
- Default Sol position (-10700, -28704); LunaSettings override stays.
- No AOTD superweapons in the Gungnir Dockyard (Gungnir railgun, shadowlance, shroud railgun all gone).
- Planets +40 radius each (Mercury 110 ... Jupiter 440, Pluto 95); moons unchanged; star 700 -> 840.
  Jupiter moons/rings, Uranus/Neptune rings, Charon and the hypershunt nudged outward to fit.
- No random derelict ships anywhere (wreck ring + the six at the gate). The three Radiants at Proxima stay.
- Earth loses lobster pens and Ancient Megacities (Engineered Utopia was never on it).
- Every ruins condition one level down except Earth's vast: Luna/Mars/Jupiter/Saturn extensive, Ganymede/Callisto
  widespread, Europa scattered, Neptune/Charon/Proxima b none.
- No gate hauler (vanilla has exactly one).
- No stellar mirrors/shades and no solar_array condition on Venus/Mars: the player builds them with TASC.
- No Venus minefield (IndEvo).
- No caches at the gate (the large cache would be empty outside the sector's own generation).
- RAT rolls its own relic conditions on first load (that is how Earth got Engineered Utopia). `Relics` snapshots the
  RAT conditions we placed and strips every other one from Sol / Alpha Centauri planets one frame into the first load.

## Round 3 (user, 2026-09-05)
- Author string is just "Oddisz". Bump at least the patch version with every change.
- Orbits spread out to match the bigger planets: Mercury 1800 ... Pluto 15300, Kuiper 16500. The Jupiter-Saturn
  gap is 3000 so Callisto (1360 from Jupiter) and Titan (1400 from Saturn) never cross; everything that sat between
  planets (gate, relay, sensor array, nav buoy, jump points, cryo prison, cryosleeper, Kuiper nest) moved with them.
- Colony crisis "Discovering the Past" built (v0.2.0). Design and numbers in CRISIS.md.

## Implementation notes (v0.1.0)
- Kotlin + gradle, same setup as Intel Renewed. `./gradlew jar` then `python package.py`.
- Sol id: system named "Sol" with memory flag `$sr_sol`; `SolSystem.find()` locates it.
- Megastructures = market conditions `aotd_nidavelir_complex` (Mars) and `aotd_pluto_station` (Pluto); hypershunt =
  vanilla `coronal_tap` salvage entity at 1000 from the star + `has_coronal_tap` tag.
- Every planet gets `$sr_body` memory so AOTD's condition guard passes.
- Remnant nests: `Remnants.nest()` = vanilla battlestation recipe + `RemnantStationFleetManager`; points x8 = FP.
- Cryo prison dialog hook and meteor listener are transient, re-registered in `onGameLoad`.
- Untested in-game as of 2026-09-05. First test checklist:
  1. New game, find Sol at about (-10700,-28704). Check planet count (16 bodies + star), sizes, click-ability.
  2. Mars: Nidavellir ring visible; Pluto: mining station orbiting. Hypershunt near the Sun with Tesseract guards.
  3. Gate + mothership/hauler. Gungnir Dockyard storage. Cryo prison dialog (wake one officer, station fades).
  4. Remnant nests at Neptune + Kuiper on Light; beacon at the jump point.
  5. Alpha Centauri: three stars, Proxima b, nest with three Radiant wrecks. Beta Centauri blue giant.
  6. Music plays in Sol. Descriptions show without nicknames.
  7. Crisis: colonize Mars -> "Discovering the Past" appears in intel with +60. Build an industry (+40), grow a
     size (+40 at size 4). At 200: pirate + Pather base intel appear near Sol, small pirate raid arrives. At 450
     a real wave. Check the reset to 450 after a Reckoning. LunaSettings knobs: pace 4.0 makes testing quick.
