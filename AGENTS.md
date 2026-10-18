# Sol Renewed

A gamified Sol system for Starsector 0.98a, derived from Bugatti's "Sol Solar System" (art, music,
descriptions) and Solsector (Remnant nests). These instructions apply to this repository, including
when it is opened without the parent mod workspace. The workspace `AGENTS.md` one folder up governs
releases, packaging, Git identity and communication.

## Notion

Tasks, history, test lists and Andrzej's decisions: the **Sol Renewed board** in Andrzej's Notion,
under **Starsector mods** (`collection://8e9df42e-fb25-49c5-9abe-5cfa9bb1726e`). Working rules and
game API notes: **Claude Information** (`collection://7bf9b1d6-b9e5-4b62-87f1-757bbaa51919`),
starting with "Start here: working on Starsector mods through Notion". Read the board before
working, give every task a ticket (ticket ids start with SOL) and keep it current. The Notion tools
are deferred: load them with ToolSearch first.

Update the board during the work, not only at release time. Before changing files, put the
message on its ticket: a new request gets a new ticket (In progress), and a problem Andrzej
reports on Needs Human Test work moves that ticket to Changes Requested with the notes quoted.
Before ending the turn, update the ticket's body (What was done, How, Commits, Test list) and
its Status, Version and End date. Read the board with `notion-query-data-sources` mode `view`
on `view://3fd20ca7-5d20-81e2-be86-000ca6c13a56` or `notion-fetch` a ticket. SQL and rows mode share a small
quota that runs out. The workspace `AGENTS.md` has the full routine.

## Essentials

- id `sol_renewed`, package `solrenewed`, Kotlin and Gradle. Author string in `mod_info.json` is
  plain "Oddisz".
- Build: `./gradlew jar` (runs `checkNoForbiddenApis`). Package: `python package.py` stages
  `SolRenewed/` and writes `Sol-Renewed.zip`.
- Planning notes in `.planning/` (DECISIONS.md holds Andrzej's keep and drop list). Decompiled
  third party code in `.planning/reference/` stays out of Git and out of the ZIP.
- Real planet names only, one gate, no radiation anywhere. The crisis base radius stays at 12 ly
  until Andrzej has played the crisis.
- Every change bumps at least the patch version.

## Update metadata

Keep `mod_info.json`, `sol_renewed.version` and `data/config/version/version_files.csv`
consistent. Forum topic 35983 (a string in `mod_info.json`, a number in the `.version` file).

- updateCheckURL and masterVersionFile:
  `https://raw.githubusercontent.com/andrzejsokolowski/starsector-sol-renewed/main/sol_renewed.version`
- directDownloadURL, asset `Sol-Renewed.zip`:
  `https://github.com/andrzejsokolowski/starsector-sol-renewed/releases/latest/download/Sol-Renewed.zip`
