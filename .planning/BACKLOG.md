# Sol Renewed - backlog (ideas parked for later)

## Colony crisis: "Discovering the past"  (requested 2026-09-05)

A new colony crisis for the Sol system. The more people live in Sol, the harder
and more brutal the attacks on the player become.

- Population in Starsector is 10^size:  size 3 = 1,000 people, size 5 = 100,000,
  size 6 = 1,000,000. Scale the crisis off the *total* population of player
  colonies inside Sol (sum of 10^size across Sol markets), not off colony count.
- Escalation should be a ladder: each stage sends a bigger / nastier fleet
  (Remnant-flavoured fits the theme: the system is Remnant-infested).
- Hook: vanilla colony-crisis framework (HostileActivityEventIntel + a custom
  HostileActivityFactor). Check how Nexerelin / AOTD add their own factors so
  ours coexists.
- Open questions to settle when we get to it:
  - Who attacks: Remnants, Omega, "the past" (Domain-era derelicts waking up)?
  - Does the crisis end permanently once the top stage is beaten, like vanilla?
  - Should it also fire for NPC-faction colonies in Sol, or only player ones?

Status: BUILT 2026-09-05 as v0.2.0, see CRISIS.md for the design that was actually implemented.
