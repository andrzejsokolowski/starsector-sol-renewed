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
- v0.2.1: crisis points reworked (colony +10, growth by head count 5/15/40/100/250/600, industry = build cost / 10k,
  no hypershunt points), in-game text cut to one or two lines per stage. Mercury 73 / Venus 113 (a third smaller),
  Jupiter 540 / Saturn 480 (+100); Jupiter's rings and moons and Saturn's rings and Titan pushed out to fit, the
  outer planets shifted again so nothing crosses: Saturn 10600, Uranus 12700, Neptune 14000, Pluto 15400, Kuiper 16600.
- Parked idea: a small "find Sol" quest, with Sol only generated when it completes. Feasible: the game itself creates
  whole systems mid-campaign (abyssal "Deep Space" encounters), and our generator is self-contained, so the quest's
  last step can call SolSystem.generate + Centauri.generate. Not started.

## Round 4: the quest (user, 2026-09-05, v0.3.0)
- "Transferring Sol", the user's script step by step: bar event (level 10+, Transverse Jump known, shows in every bar
  until accepted) -> Glasya-Labolas (AI, user's own portrait) in the Eochu Bres comm directory -> the conversation as
  written, with "Yes" / "W-What?" (no quest yet) / "No, I don't care" (ends it; talking again offers a restart) /
  "Skip the quest" (confirmation, then Sol spawns) -> Shroud kill check (updated in v0.3.4 below);
  if none, stage SHROUD until a kill -> Macro-scale Translocator (price 0, no_sell, restored to the
  hold if it goes missing) -> signal beacon in hyperspace 4000 units north of the map edge above Hybrasil (the game's
  abyss is the lower-left corner plus everything outside the map; north inside the map is not abyssal) -> dialog opens
  on arrival -> Sol + Centauri generated mid-game, quest done.
- Default spawn mode is Quest; LunaSettings "When Sol appears" can restore "At game start". Saves that already have
  Sol are untouched. Everything Sol needs at runtime is installed by SolRenewedModPlugin.installSolRuntime after a
  mid-game spawn.
- Code: solrenewed.quest (SolQuest state machine + helpers, SolQuestIntel, SolQuestBarEvent, SrQuestCMD rule command,
  SignalDialog, SolQuestWatcher, TranslocatorItemPlugin); data/campaign/rules.csv and special_items.csv;
  settings.json registers the rule command package and the portrait.

## Round 5: first playtest fixes (user, 2026-09-05, v0.3.1)
- The "Continue" that hands over the translocator never appeared on the RETURN greeting. Cause: FireBest applies a rule's
  own options first, then runs its script, and `FireAll PopulateOptions` clears the option panel before adding the
  PopulateOptions results. Rule of thumb for this mod: a rule whose script fires PopulateOptions must not carry options
  itself; put them in a PopulateOptions rule instead (srGL_optTake).
- Translocator uses the vanilla Janus Device icon (graphics/icons/cargo/janus_device.png) until the user draws one.
- Another installed mod injects a "Meet $himOrHer in person" option into every person conversation; not ours, harmless.

## Round 6: playtest 2 (user, 2026-09-05, v0.3.2)
- Quest confirmed working end to end: bar lead -> conversation -> device -> signal -> Sol spawns mid-game and is enterable.
- The translocator could still be sold. `no_sell` only stops markets stocking an item; the tag that actually locks a
  special item inside the player fleet (no sell, no drop) is `mission_item`, same as the vanilla Janus Device,
  Planetkiller and Wormhole Scanner. Added it; the cargo tooltip now reads "Can not remove item" by itself.
- Crisis still untested. User suspects the 12 ly base-spawn radius is too wide but asked for NO change until they test.

## Round 7: crisis panel legend (user, 2026-09-05, v0.3.3)
- The four stage description rows in the middle of the crisis panel duplicated the marker tooltips; removed by having
  addStageDescriptionText write nothing except for START (the game skips a row whose text has zero height).
- afterStageDescriptions now draws a "What raises attention" table: colony founded, each size step, and industry
  expressed as credits-per-point, all scaled by the LunaSettings pace knob, plus live examples read from the industry
  specs so they stay right with other mods installed.

## Round 8: Shroud kill progress (v0.3.4)
- The vanilla defeated-Dweller flags are set during victory salvage. A kill followed by retreat can miss those flags.
- The substrate amount in player memory is a temporary number set by the item dialog. The old check read a boolean.
- A transient battle listener now records destroyed or disabled enemy hulls with Tags.DWELLER, regardless of victory.
  The permanent quest flag also remembers kills before the quest starts. Player losses and surviving enemies do not count.
- Existing true vanilla kill flags, positive substrate amounts, and substrate in cargo remain valid evidence for older saves.
  Loading a save advances SHROUD to RETURN when that evidence exists. A kill without surviving evidence requires another fight.
- The listener and watcher are installed once per load. The crisis ranges remain unchanged.
- Validation: jar build and forbidden-API scan passed. The campaign API regression harness covers combat results,
  older-save evidence, restored memory, repeated installation, and stage guards. An in-game playtest is still needed.

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
  7. Quest (default mode): new game -> no Sol on the map. Level 10 + Transverse Jump -> researcher in every bar.
     Accept -> intel "Transferring Sol" points at Eochu Bres. Talk to Glasya-Labolas (comm directory) -> full
     conversation; without a Shroud kill the intel says to go kill one; with one, the translocator appears in cargo
     and a purple beacon sits above the map edge north of Hybrasil. Fly onto it -> dialog -> Sol appears, intel
     points at it. Also try "Skip the quest" and "No, I don't care" then talking again. Console: no need.
  8. Crisis: colonize Mars -> "Discovering the Past" appears in intel with +10. Build Mining (+10) or Heavy
     Industry (+50), grow to size 4 (+5). At 200: pirate + Pather base intel appear near Sol, small pirate raid arrives. At 450
     a real wave. Check the reset to 450 after a Reckoning. LunaSettings knobs: pace 4.0 makes testing quick.
