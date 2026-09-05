# Sol Renewed - analysis of the two source mods (2026-09-05)

Working name "Sol Renewed" is provisional. Folder can be renamed once the user picks a name.

## The two mods

| | Bugatti's "Sol Solar System" 9.9.91.DEV | "Solsector" 0.81.2 (Ugly American) |
|---|---|---|
| Folder | `D:\Games\StarSector\mods\Sol Solar System-9.9.91.DEV` | `D:\Games\StarSector\mods\Solsector` |
| Mod id | `bugatti.Sol` | `Solsector` |
| Code | compiled jar only (decompiled copy in `reference/bugatti_decompiled`) | full Java source ships in `jars/soljars` |
| Bodies | 25 planets/moons + ~20 stations/derelicts, Pluto orbit 23000 | ~90 planets/moons + hundreds of asteroids/comets, real Kepler orbits, log-scaled |
| Config | LunaSettings (spawn toggles, position, megastructure toggles) | `data/config/sol_settings.json` (remnant_difficulty 0-3, detail knobs) |
| License | none stated; music by Taiwendo | CC-BY 4.0 (attribution required) |

Both are enabled in `enabled_mods.json` right now, along with aotd_vok, aotd_sop, Cryo_but_better (Dreams of the Past),
IndEvo, assortment_of_things (RAT), US, nexerelin, lunalib, superweapons, customizablestarsystems.

## Bugatti's mod: how it works

Entry: `bugatti.plugins.SolPlug`
- `onNewGame`: registers `CryoPlug` (interaction dialog for the cryo-prison station).
- `onNewGameAfterProcGen`: `Sol.genSys()` builds the system; optionally Alpha/Beta Centauri; Wide Horizons registration.
- `onNewGameAfterEconomyLoad`: IndEvo meteor showers listener; AOTD `MegaSuppressor` + `MegaBootstrap` (one-frame script that calls `MegaSpawner`).

System build (`bugatti.systems.Sol`):
- `sec.createStarSystem("Sol")`, star `sol_star` type `star_yellow` r=1250 corona 500, position (-54000,-36000) via LunaSettings, pushed clear of neighbours (`circleSearch`, 6000 clearance).
- Tags: theme_derelict, theme_hidden, theme_unsafe, theme_special, theme_remnant_resurgent. Memory `$US_skipSystem=true`. Music key `music_sol_system`.
- Own constellation "Oort Cloud"; hyperspace labels "Oort" / "Cloud" (custom entity type `Sol_Labels`, name-only, no icon).
- Planets via `sys.addPlanet(id, focus, name, type, angle, radius, orbitRadius, orbitDays)`, every planet gets a hand-made market
  (`craftMarket`: faction neutral/independent, size 0, `setPlanetConditionMarketOnly(true)`, survey level NONE).
- Cross-mod conditions added only when the mod is enabled (`ModCheck.cond(market, modId, conds...)`).
- Extras: magnetic fields (Earth, Jupiter), ring bands (Jupiter x2, Saturn x4, Uranus, Neptune x2, Kuiper "ring"), asteroid belts
  (Main Belt 7000, three Kuiper belts 24000-25000), orbital junk, debris/asteroid fields at jump points, nebula patch, Remnant beacon
  (RESURGENT), hyperspace cleared around system, 12 random wrecks in a ring.
- Two inactive gates ("Newton Gate", "Charon Gate"), 2 stable points, comm relay, 2 sensor arrays, nav buoy, orbital habitat,
  research station, remnant mining station, derelict probe/survey ship, cryosleeper "Foundation", tech cache, cryo-prison
  "The Inexorable Stasis" (custom entity `Sol_Solitary`), Prometheus Initiative (Dreams of the Past `ark` if present).
- Four abandoned stations with storage loot (ISS, Titan's Eye, Gungnir Dockyard, Telepylos) filled by `Looter` tables.
- Remnant station "Irradiated Rig" at fixed (6000,-18000) with 3 Radiant derelicts + weapons cache + fleet manager; every
  Remnant fleet in-system gets renamed "Irradiated ..." and herded back to the station (`IrradiatedFleetManager`).
- RADIATION: `radiation_field` terrain (`RadiationPlug`, crew/marine/CR loss, storms) around the Remnant rig and Telepylos;
  `radiation_shielding` hullmod in `hull_mods.csv`. -> USER WANTS ALL OF THIS GONE.
- `Decolicter`: each gate/cryosleeper gets a debris field with defenders, 6 random wrecks, caches, probe, survey ship,
  and a chance of a Domain mothership / gate hauler.
- Jump points: "Einstein-Rosen Bridge" (2345) and "Fringe Quantum Tunnel" (11111), fixed orbits, no autogen.
- Alpha Centauri (trinary, Proxima B planet, another irradiated Remnant rig) + Beta Centauri (blue giant, empty). Optional.
- Zodiac hyperspace labels code exists but is never called.

Planet table (angle, radius, orbitRadius, orbitDays) - Bugatti values:
```
Mercury  270  60   2500   120      Luna      0  40   350 (Earth)   56
Venus    180 140   3200   450      Phobos   90  19   180 (Mars)     2
Earth     90 180   4400   730      Deimos  210  15   320 (Mars)     5
Mars     360  80   6000  1374      Io      120  50   725 (Jup)   3.5
Ceres    270  20   7700  3364      Europa    0  40  1125 (Jup)     7
Jupiter  150 500   9800  8666      Ganymede 240 70  1400 (Jup)    14
Saturn   300 425  14000 21512      Callisto 160 55  1800 (Jup)    34
Uranus     0 350  18000 61374      Enceladus 180 25 1360 (Sat)   2.7
Neptune   60 340  21000 120380     Titan     0  55  2000 (Sat)    32
Pluto    240  32  23000 181120     Oberon   30  60  1800 (Ura)    26
                                   Titania 120  90  1200 (Ura)    39
                                   Miranda 210  30   650 (Ura)    52
                                   Triton    0  50   900 (Nep)    12
                                   Charon    0  16   120 (Plu)    13
```
Planet types: Mercury barren-bombarded, Venus toxic, Earth US_continent (terran fallback), Mars barren-desert,
Ceres rocky_ice, Jupiter US_gas_giant, Saturn US_gas_giantB, Uranus US_iceA, Neptune US_iceB (gas_giant fallbacks),
Pluto frozen, Luna barren, Phobos barren_castiron, Deimos barren-bombarded, Io lava, Europa/Ganymede/Callisto rocky_ice,
Enceladus cryovolcanic, Titan toxic_cold, Oberon/Titania rocky_ice, Miranda rocky_unstable, Triton cryovolcanic, Charon rocky_ice.

Conditions per planet (vanilla + modded) - see `reference/bugatti_decompiled/bugatti/systems/Sol.java` lines 144-473.
Notables: Mars `aotd_nidavelir_complex`; Pluto `aotd_pluto_station`; Venus/Callisto/Enceladus/Titania/Triton `pre_collapse_facility`;
Earth `US_elevator US_religious US_magnetic US_base rat_ancient_megacities IndEvo_RuinsCondition` (+ `$IndEvo_ruinsIndustryId=IndEvo_Memorial`);
Mars `rat_ancient_military_hub rat_kinetic_launchsystem US_elevator US_shrooms` (+ ruins -> `IndEvo_HullDecon`);
Deimos ruins -> `IndEvo_HullForge`; Charon ruins -> `IndEvo_ResLab`; Jupiter `IndEvo_ArtilleryStationCondition rat_ancient_fuel_hub`;
Saturn `rat_ancient_fuel_hub`; Titan `rat_warscape`; Ganymede `rat_rampant_military_core`; Europa `rat_bionic_plantlife`;
Luna `rat_ancient_industries US_tunnels US_crystals` (note: Bugatti's Luna call passes "US_base" as the MOD id - a bug, those never apply).
RAT relic conditions also need the entity tag `rat_relic_condition` + the condition id as a tag.

Mod ids checked by Bugatti: lunalib, II_BG (Interstellar Imperium battlegroup, not installed), assortment_of_things, IndEvo,
Cryo_but_better (Dreams of the Past), aotd_vok, US, alcoholism, WideHorizons(+Basic), superweapons, NuclearWeapons.

### Megastructures - IMPORTANT finding
Bugatti's `MegaSpawner` imports `data.kaysaar.aotd.vok.campaign.econ.globalproduction.*` (`GPManager`, `GPBaseMegastructure`,
`BifrostMega`). Those classes DO NOT EXIST in the installed AoTD VoK 5.0.5 (it uses
`data.kaysaar.aotd.vok.campaign.econ.megastructures.*`). `MegaBootstrap` wraps the call in `catch(Throwable)`, so it fails
silently and logs an error. The megastructures still appear because of the MARKET CONDITIONS added during system gen:

- `aotd_pluto_station` condition (`PlutoStation.apply`) -> `PlutoMegastructure.trueInit` -> adds custom entity
  `aotd_pluto_station` ("Pluto Mining Station") orbiting the planet at radius+340, music `aotd_mega`, discoverable.
- `aotd_nidavelir_complex` condition (`NidavelirComplex.apply`) -> `NidavelirMegastructure.trueInit` -> adds the damaged
  shipyard ring visual (`nid_shipyards_damaged`) attached to the planet.
- Both need the planet's memory to be non-empty when the condition applies (it is, in practice) and a market with a faction.
- Vanilla hypershunt: custom entity `coronal_tap` orbiting the star + system tag `has_coronal_tap`. AOTD hooks the vanilla
  entity itself (`coronal_hypershunt` spec) so nothing else is needed.
- AOTD's own random placement (`AoTDDataInserter.spawnMegas`): picks systems within 10 LY of a hypershunt system, skips systems
  tagged theme_hidden/core, skips planets tagged `not_random_mission_target` or with `$aotd_mega_already`, prefers a lava planet
  for Pluto (or creates one), any planet r>40 for Nidavellir. So the sector ALWAYS gets a second Pluto station + Nidavellir
  elsewhere ("dual spawning"). Bugatti's `MegaSuppressor` (off by default, "buggy with Mars") strips those.
- AOTD Bifrost gate (Transfrost) also broken in Bugatti's build for the same API reason.

### Other Bugatti assets worth keeping
- Planet textures (25 jpg/png), cloud layers, Saturn ring sprite, Sol star texture + halo, warroom icon, Luna boot-print
  interaction image, galaxy background. 42 MB.
- Music "Breath of SOL" by Taiwendo (`sounds.json` -> `music_sol_system`). 25 MB folder incl. 4 unused tracks.
- 34 hand-written descriptions in `data/strings/descriptions.csv` (planets, stations, cryo unit).
- Custom entity `Sol_Solitary` + `CryoInteraction` dialog: 8 prisoner officers with rap sheets, pick ONE (level 5, max 7,
  RAT skill if RAT present).
- `Looter` tables: cross-mod loot for abandoned stations (needs pruning; references mods not installed).
- `ShowerSpawner`: IndEvo seasonal meteor showers (only when player is in/near Sol).

## Solsector: how it works
- `data.scripts.SolModPlugin.onNewGame` -> `SolTotal.generate()` (4000 lines) + `GiantMoonsTotal` + `CometsCentaursTNOs`.
- `AstroCalc.spawnSPSObject(...)` takes real diameter (km), semi-major axis (AU), eccentricity, node/perihelion angles, epoch;
  distance = `extComp*log(intComp*AU+1)`, size = `sizeExt*log(sizeInt*km+1) + sqrt(km)/(1+sizeDenom*km) + sizeConst`
  (Earth ~ r119, Luna ~ r70, Pluto ~ r60 - so sizes are actually gamified already; distances are the problem: 10000*ln(0.5*AU+1)
  puts Neptune at ~27000 and Sedna/Farfarout much further).
- Custom conditions: ~50 (space elevators, orbital ring, megaforges, insurgent network, distance tiers "Distant/Abyssal/Hadal/
  Erebal/Tartarean/Oortal", goblin/degenerate subpops, world war, etc.), one industry (subsurface aquaponics).
  Space elevator/ring sprites (`graphics/elevators`), 138 MB of graphics total.
- Optional settled markets (`SolEconomies`) spawn on entry.
- Remnants (the part the user likes) - `remnant_difficulty` 0..3, `remnantSizeModifier` -10/0/+10 FP:
  - `RemnantNexusFactory.spawnNexus(system, focus, variant, angle, radius, period, maxFleets, minFP, maxFP, coreType)`:
    empty remnant battlestation fleet, station mode, aggressive, no jump, transponder on, alpha (or given) core commander,
    `RemnantStationFleetManager(nexus, 1f, 1, maxFleets, 150f, minFP, maxFP)` keeps spawning patrol fleets from it.
  - `RemnantPatrolFactory.spawnPatrol(system, source, FP)`: one-shot Ordo-style fleet, PATROL_SYSTEM forever.
  - Difficulty 1: 3 nexi at Mercury (2 standard + 1 damaged/beta), 1 at Mars, 4 standard nexi around the Sun, 1 patrol 200 FP.
  - Difficulty 2: + 4 asteroid nexi, Ceres damaged nexus, 2 `station1_Standard` weapon platforms, patrols 120/220/400 FP.
  - Difficulty 3: + 2 `remnant_weapon_platform1_Standard`, nexi at Titan/Amalthea/Triton, patrols 300/200/200/800 FP.
  - Total at 3: ~18 nexi each up to 5 fleets of 30-70 FP => the "overpopulated" feel.
  - Also `RemnantThemeGenerator.addBeacon(RESURGENT)`, tags theme_remnant, theme_remnant_main, theme_remnant_resurgent.

## Customizable Star Systems (user's own config, reference scale)
`D:\Games\StarSector\mods\Customizable Star Systems-3.0.2\data\config\customStarSystems.json`: three systems.
Scale used: star r150-500, planets r30-275 (gas giants 275-545), orbit radii 800-7200, orbit days 45-900, jump points at
2500-3200 and 7840-8840, belts ~9750. Max system radius ~10000. THIS is the target feel for Sol Renewed.
Bugatti's mod still ships its old CSS json (`isEnabled:false`) - same content as the Java version, can be mined for values.

## Verified IDs (all exist in installed versions)
Conditions: US_base US_bedrock US_crash US_cryosanctum US_crystals US_elevator US_floating US_fluorescent US_magnetic
US_religious US_shrooms US_tunnels; rat_ancient_fuel_hub rat_ancient_industries rat_ancient_megacities rat_ancient_military_hub
rat_bionic_plantlife rat_kinetic_launchsystem rat_rampant_military_core rat_warscape; IndEvo_ArtilleryStationCondition
IndEvo_RuinsCondition IndEvo_mineFieldCondition; aotd_nidavelir_complex aotd_pluto_station pre_collapse_facility.
Planet types: US_continent US_gas_giant US_gas_giantB US_iceA US_iceB. IndEvo industries: IndEvo_HullDecon IndEvo_HullForge
IndEvo_Memorial IndEvo_ResLab. Entities: IndEvo_GachaStation IndEvo_abandonedPetCenter IndEvo_arsenalStation
rat_orbital_construction_station; DoP `ark`; vanilla coronal_tap inactive_gate derelict_cryosleeper station_mining_remnant
derelict_vambrace weapons_cache_remnant orbital_dockyard station_sporeship_derelict.

## Proposed gamified layout (draft, to tune in-game)
Star: yellow, r 600, corona 300. All radii >= 50 (user's floor).
```
body       r   orbit   days   parent        notes
Mercury    70   1400     60   star          hypershunt orbits the star at ~900
Venus     130   2200    100   star
Earth     160   3100    150   star          Luna r55 @ 380, 30 d; gate near Earth L-point
Mars      100   4100    200   star          Nidavellir ring
(belt)         5100                          Main Belt asteroid belt
Jupiter   400   6300    400   star          Io 55@620/6d  Europa 55@800/9d  Ganymede 75@1020/14d  Callisto 65@1280/22d
Saturn    350   8000    550   star          4 ring bands, Titan r70 @ 950 / 25 d (if kept)
Uranus    260   9600    700   star          1 ring band
Neptune   250  11100    850   star
Pluto      55  12600   1000   star          Pluto Mining Station, Charon r50 @ 220 / 12 d (if kept)
(kuiper)       13500                         thin outer belt
jump points ~2600 (inner) and ~9000 (fringe); stable points x2; comm relay / nav buoy / sensor array.
```
