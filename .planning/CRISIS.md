# "Discovering the Past" - the Sol colony crisis (designed 2026-09-05)

The more the player builds in Sol, the more the Sector notices. One event with a meter, like the
game's Hostile Activity / Hyperspace Topography events. The meter only moves when the player develops
Sol; nothing ticks by itself.

## What fills the meter (flat, one-time)
| what                                   | points                              |
|----------------------------------------|-------------------------------------|
| colony founded in Sol                  | +60                                 |
| colony grows a size                    | +10 x new size (4 -> +40, 6 -> +60) |
| industry finished (not a structure)    | +40 (upgrades count as new)         |
| hypershunt in Sol switched on          | +100                                |
All multiplied by the LunaSettings "pace" knob. Only player-owned colonies count.
Checked every few days by comparing against a snapshot kept in the event; a save that already has
developed Sol colonies when the mod is added only gets +60 per existing colony.

## Stages (meter 0-1000)
| meter | stage      | what happens                                                                |
|-------|------------|-----------------------------------------------------------------------------|
| 0     | Start      | description only                                                            |
| 200   | Foothold   | a pirate base and a Luddic Path base set up within ~12 ly of Sol (only if none is there already), plus a small pirate raid as a first taste |
| 450   | Raid       | one faction attacks, rolled from pirates / Pathers / Remnants               |
| 700   | Assault    | two factions attack at once                                                 |
| 1000  | Reckoning  | all three at once; when it is over the meter drops to 450 and the escalation counter goes up (every later wave is 25% bigger, max +150%); the bases are re-founded if the player cleared them |

Stages fire when the meter crosses them. If a wave is still in flight when the next stage is
reached, the next wave waits until the current one is over (queued, not skipped).

## Who attacks and how
- Pirates: from the nearest pirate base (or the nearest pirate market). Raid: they capture the
  objectives, then raid the colonies (stability loss, loot). Budget 40 / 60 / 90.
- Luddic Path: from the nearest Pather base (or Pather market). Saturation-bombardment attempt on the
  Sol colony the Pathers hate most (AI cores, industries). Budget 30 / 45 / 60.
- Remnants: from a live nest in Sol; if the player has destroyed them all, from the Alpha Centauri nest;
  if that is gone too, they come in through the fringe jump point. Saturation bombardment of the biggest
  colony. Budget 40 / 55 / 75.
Budgets are "difficulty" points in the game's own fleet-creator scale (10 = one huge fleet), split into
fleets of 3-10, then multiplied by escalation and the LunaSettings "strength" knob. For comparison the
game's own crises use pirates 25-100, Pathers 10-40, Remnants 50.
Roll weights for Raid/Assault: pirates 3 + colonies, Pathers 1 + Pather interest, Remnants 1 + live nests x2.

## Vanilla interplay
- The game's own Hostile Activity also treats every Remnant nest in Sol as a "Remnant Nexus" and can
  send its own Remnant bombardment. That is stock behaviour for any nest system and is left alone.
- Pather cells on Sol colonies come from the game's own base manager once a Pather base is near.
- Pirate bases never raid player systems on their own (the game leaves that to crises), so our waves
  are the only pirate raids that target Sol.

## Ending
The event is created when the first player colony appears in Sol and ends if the player has no
colony left in Sol (meter and escalation are lost).

## LunaSettings
`sr_crisis` on/off, `sr_crisis_pace` (meter speed, default 1.0), `sr_crisis_strength` (wave size,
default 1.0).

## Code
`solrenewed.crisis.CrisisIntel` (the event), `CrisisFactor` (one-time meter entries), `Waves` (fleet
groups + bases), `CrisisWatcher` (transient script that creates the event). Hooked from
`SolRenewedModPlugin.onGameLoad`.
