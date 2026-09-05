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

## Implementation notes (v0.1.0)
- Kotlin + gradle, same setup as Intel Renewed. `./gradlew jar` then `python package.py`.
- Sol id: system named "Sol" with memory flag `$sr_sol`; `SolSystem.find()` locates it.
- Megastructures = market conditions `aotd_nidavelir_complex` (Mars) and `aotd_pluto_station` (Pluto); hypershunt =
  vanilla `coronal_tap` salvage entity at 1000 from the star + `has_coronal_tap` tag.
- Every planet gets `$sr_body` memory so AOTD's condition guard passes.
- Remnant nests: `Remnants.nest()` = vanilla battlestation recipe + `RemnantStationFleetManager`; points x8 = FP.
- Cryo prison dialog hook and meteor listener are transient, re-registered in `onGameLoad`.
- Untested in-game as of 2026-09-05. First test checklist:
  1. New game, find Sol at about (-54000,-36000). Check planet count (16 bodies + star), sizes, click-ability.
  2. Mars: Nidavellir ring visible; Pluto: mining station orbiting. Hypershunt near the Sun with Tesseract guards.
  3. Gate + mothership/hauler. Gungnir Dockyard storage. Cryo prison dialog (wake one officer, station fades).
  4. Remnant nests at Neptune + Kuiper on Light; beacon at the jump point.
  5. Alpha Centauri: three stars, Proxima b, nest with three Radiant wrecks. Beta Centauri blue giant.
  6. Music plays in Sol. Descriptions show without nicknames.
