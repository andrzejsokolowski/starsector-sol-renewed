package solrenewed.crisis

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.ComplicationRepImpact
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import solrenewed.SolSystem
import java.util.Random
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The retaliation side of the crisis: who attacks Sol, from where, how hard, and the pirate and
 * Luddic Path bases that move into the neighbourhood. Fleets are the game's own "fleet group"
 * raids, the same machinery its colony crises use, so they show up as intel with ETAs, can be
 * intercepted on the way, and go home when beaten.
 */
object Waves {

    private val log = Global.getLogger(Waves::class.java)

    /** How far from Sol the bases are planted, and how far away an existing base still counts as "near". */
    const val BASE_RANGE_LY = 12f
    /** A base further away than this is not used as a launch point; the faction's nearest market is instead. */
    const val SOURCE_RANGE_LY = 25f
    /** Never more than this many difficulty points per faction per wave (16 huge fleets). */
    const val MAX_BUDGET = 160

    private class Budget(val pirates: Int, val pathers: Int, val remnants: Int)

    private fun budget(stage: CrisisIntel.Stage): Budget = when (stage) {
        CrisisIntel.Stage.RAID -> Budget(40, 30, 40)
        CrisisIntel.Stage.ASSAULT -> Budget(60, 45, 55)
        else -> Budget(90, 60, 75)
    }

    /** Foothold: the bases move in and the pirates send a first, small raiding party to test the defences. */
    fun foothold(intel: CrisisIntel): List<FleetGroupIntel> {
        val sol = SolSystem.find() ?: return emptyList()
        ensureBases(intel)
        val targets = Misc.getMarketsInLocation(sol, Factions.PLAYER)
        if (targets.isEmpty()) return emptyList()
        val random = Random(intel.getRandom().nextLong())
        val raid = pirateRaid(sol, targets, listOf(5, 3), random) ?: return emptyList()
        intel.noteWave(CrisisIntel.Stage.FOOTHOLD, Factions.PIRATES)
        return listOf(raid)
    }

    /** Raid / Assault / Reckoning: one, two or all three factions at once. */
    fun launch(intel: CrisisIntel, stage: CrisisIntel.Stage): List<FleetGroupIntel> {
        val sol = SolSystem.find() ?: return emptyList()
        val targets = Misc.getMarketsInLocation(sol, Factions.PLAYER)
        if (targets.isEmpty()) return emptyList()
        val random = Random(intel.getRandom().nextLong())
        val budget = budget(stage)
        val mult = intel.strengthMult()
        val out = ArrayList<FleetGroupIntel>()
        for (faction in pickFactions(stage, sol, targets, random)) {
            val fgi = when (faction) {
                Factions.PIRATES -> pirateRaid(sol, targets, split(scaled(budget.pirates, mult), 3, 10, random), random)
                Factions.LUDDIC_PATH -> patherStrike(sol, targets, split(scaled(budget.pathers, mult), 3, 8, random), random)
                Factions.REMNANTS -> remnantStrike(sol, targets, remnantSizes(scaled(budget.remnants, mult), random), random)
                else -> null
            }
            if (fgi != null) {
                out.add(fgi)
                intel.noteWave(stage, faction)
            }
        }
        log.info("[SolRenewed] Crisis ${stage.name}: ${out.size} attack group(s), strength x$mult")
        return out
    }

    private fun scaled(base: Int, mult: Float): Int = min(MAX_BUDGET, (base * mult).roundToInt())

    private fun pickFactions(stage: CrisisIntel.Stage, sol: StarSystemAPI, targets: List<MarketAPI>, random: Random): List<String> {
        val count = when (stage) {
            CrisisIntel.Stage.RAID -> 1
            CrisisIntel.Stage.ASSAULT -> 2
            else -> 3
        }
        if (count >= 3) return listOf(Factions.PIRATES, Factions.LUDDIC_PATH, Factions.REMNANTS)
        val picker = WeightedRandomPicker<String>(random)
        picker.add(Factions.PIRATES, 3f + targets.size)
        val interest = targets.maxOf { LuddicPathBaseManager.getLuddicPathMarketInterest(it) }
        picker.add(Factions.LUDDIC_PATH, 1f + interest / 10f)
        picker.add(Factions.REMNANTS, 1f + 2f * nests(sol).size)
        val out = ArrayList<String>()
        repeat(count) {
            val pick = picker.pickAndRemove() ?: return out
            out.add(pick)
        }
        return out
    }

    /** Splits a difficulty budget into fleets of `lo`..`hi` points (10 = one huge fleet). */
    private fun split(total: Int, lo: Int, hi: Int, random: Random): List<Int> {
        val out = ArrayList<Int>()
        var left = total
        while (left > 0) {
            var size = lo + random.nextInt(hi - lo + 1)
            if (size > left) size = maxOf(lo, left)
            out.add(size)
            left -= size
        }
        return out
    }

    /** Remnants come as one big fleet with escorts, like the game's own Remnant attack. */
    private fun remnantSizes(total: Int, random: Random): List<Int> {
        if (total <= 10) return listOf(maxOf(4, total))
        val out = arrayListOf(10)
        out.addAll(split(total - 10, 6, 10, random))
        return out
    }

    // ---------------------------------------------------------------- pirates

    private fun pirateRaid(sol: StarSystemAPI, targets: List<MarketAPI>, sizes: List<Int>, random: Random): FleetGroupIntel? {
        val source = pirateSource(sol)
        if (source == null) {
            log.warn("[SolRenewed] Crisis: no pirate market anywhere to launch a raid from")
            return null
        }
        val params = GenericRaidParams(Random(random.nextLong()), true)
        params.factionId = source.factionId
        params.source = source
        params.prepDays = 7f + random.nextFloat() * 7f
        params.payloadDays = 27f + 7f * random.nextFloat()
        params.raidParams.where = sol
        params.raidParams.allowedTargets.addAll(targets)
        params.raidParams.allowNonHostileTargets = true
        params.style = if (random.nextFloat() < 0.33f) FleetStyle.QUANTITY else FleetStyle.STANDARD
        params.fleetSizes.addAll(sizes)
        reveal(source)
        return post(params)
    }

    private fun pirateSource(sol: StarSystemAPI): MarketAPI? {
        val base = nearestPirateBase(sol, SOURCE_RANGE_LY)
        if (base != null) return base.market
        return nearestMarketOfFaction(sol, Factions.PIRATES)
    }

    // ------------------------------------------------------------ Luddic Path

    private fun patherStrike(sol: StarSystemAPI, targets: List<MarketAPI>, sizes: List<Int>, random: Random): FleetGroupIntel? {
        val source = patherSource(sol)
        if (source == null) {
            log.warn("[SolRenewed] Crisis: no Luddic Path market anywhere to launch a strike from")
            return null
        }
        // The colony the Pathers hate most: AI cores and heavy industry first, size as the tie-breaker.
        val target = targets.maxByOrNull { LuddicPathBaseManager.getLuddicPathMarketInterest(it) * 100f + it.size } ?: return null
        val params = GenericRaidParams(Random(random.nextLong()), true)
        params.factionId = Factions.LUDDIC_PATH
        params.source = source
        params.prepDays = 10f + random.nextFloat() * 10f
        params.payloadDays = 27f + 7f * random.nextFloat()
        params.raidParams.where = sol
        params.raidParams.allowedTargets.add(target)
        params.raidParams.allowNonHostileTargets = true
        params.raidParams.setBombardment(BombardType.SATURATION)
        params.style = if (random.nextFloat() < 0.33f) FleetStyle.QUANTITY else FleetStyle.STANDARD
        params.fleetSizes.addAll(sizes)
        reveal(source)
        return post(params)
    }

    private fun patherSource(sol: StarSystemAPI): MarketAPI? {
        val base = nearestPatherBase(sol, SOURCE_RANGE_LY)
        if (base != null) return base.market
        return nearestMarketOfFaction(sol, Factions.LUDDIC_PATH)
    }

    // --------------------------------------------------------------- Remnants

    private fun remnantStrike(sol: StarSystemAPI, targets: List<MarketAPI>, sizes: List<Int>, random: Random): FleetGroupIntel? {
        val source = remnantSource(sol, random) ?: return null
        val picker = WeightedRandomPicker<MarketAPI>(random)
        for (t in targets) picker.add(t, (t.size * t.size * t.size).toFloat())
        val target = picker.pick() ?: return null
        val params = GenericRaidParams(Random(random.nextLong()), true)
        params.makeFleetsHostile = true
        params.remnant = true
        params.factionId = Factions.REMNANTS
        params.source = source.first
        params.prepDays = if (source.second) 0f else 5f
        params.payloadDays = 27f + 7f * random.nextFloat()
        params.raidParams.where = sol
        params.raidParams.type = FGRaidType.SEQUENTIAL
        params.raidParams.tryToCaptureObjectives = false
        params.raidParams.allowedTargets.add(target)
        params.raidParams.allowNonHostileTargets = true
        params.raidParams.setBombardment(BombardType.SATURATION)
        params.forcesNoun = "remnant forces"
        params.style = FleetStyle.STANDARD
        params.repImpact = ComplicationRepImpact.FULL
        params.fleetSizes.addAll(sizes)
        return post(params)
    }

    /**
     * Where the Remnants come from: a live nest in Sol, else the Alpha Centauri nest, else "somewhere
     * beyond the fringe jump-point". The market is a stand-in that is never added to the economy, the
     * same trick the game's own Remnant crisis uses. The boolean says whether the source is inside Sol.
     */
    private fun remnantSource(sol: StarSystemAPI, random: Random): Pair<MarketAPI, Boolean>? {
        val local = nests(sol)
        val nest: CampaignFleetAPI? = if (local.isNotEmpty()) {
            local[random.nextInt(local.size)]
        } else {
            Global.getSector().getStarSystem("Alpha Centauri")?.let { nests(it).firstOrNull() }
        }
        if (nest != null) {
            val fake = Global.getFactory().createMarket(nest.id, nest.name, 3)
            fake.primaryEntity = nest
            fake.factionId = Factions.REMNANTS
            fake.stats.dynamic.getMod(Stats.FLEET_QUALITY_MOD).modifyFlat("nexus_" + nest.id, 1f)
            return Pair(fake, nest.containingLocation === sol)
        }
        val fringe: SectorEntityToken = sol.getEntityById("sr_jump_fringe") ?: sol.jumpPoints.lastOrNull() ?: return null
        val fake = Global.getFactory().createMarket("sr_crisis_deep_signal", "the deep", 3)
        fake.primaryEntity = fringe
        fake.factionId = Factions.REMNANTS
        return Pair(fake, false)
    }

    /** Every Remnant station-mode fleet in the system, i.e. our nests (and any nexus the game put there). */
    fun nests(sys: StarSystemAPI): List<CampaignFleetAPI> =
        sys.fleets.filter { it.isStationMode && it.faction.id == Factions.REMNANTS && it.isAlive }

    // ------------------------------------------------------------------ bases

    /** Plants a pirate base and a Luddic Path base near Sol unless one of each is already in range. */
    fun ensureBases(intel: CrisisIntel) {
        val sol = SolSystem.find() ?: return
        val used = HashSet<StarSystemAPI>()
        nearestPirateBase(sol, BASE_RANGE_LY)?.let { used.add(it.system) }
        nearestPatherBase(sol, BASE_RANGE_LY)?.let { used.add(it.system) }
        if (nearestPirateBase(sol, BASE_RANGE_LY) == null) {
            val tier = when {
                intel.cycles >= 3 -> PirateBaseIntel.PirateBaseTier.TIER_5_3MODULE
                intel.cycles == 2 -> PirateBaseIntel.PirateBaseTier.TIER_4_3MODULE
                intel.cycles == 1 -> PirateBaseIntel.PirateBaseTier.TIER_3_2MODULE
                else -> PirateBaseIntel.PirateBaseTier.TIER_2_1MODULE
            }
            for (sys in candidateSystems(sol, used)) {
                val base = PirateBaseIntel(sys, Factions.PIRATES, tier)
                if (base.isDone) continue
                used.add(sys)
                log.info("[SolRenewed] Crisis: pirate base planted in ${sys.name}")
                break
            }
        }
        if (nearestPatherBase(sol, BASE_RANGE_LY) == null) {
            for (sys in candidateSystems(sol, used)) {
                val base = LuddicPathBaseIntel(sys, Factions.LUDDIC_PATH)
                if (base.isDone) continue
                used.add(sys)
                log.info("[SolRenewed] Crisis: Luddic Path base planted in ${sys.name}")
                break
            }
        }
    }

    /**
     * Systems near Sol a base could hide in, nearest first: no colonies, not hidden or special, and
     * either a system the game itself would consider (ruins, misc, dead Remnant) or our own Beta Centauri.
     */
    private fun candidateSystems(sol: StarSystemAPI, exclude: Set<StarSystemAPI>): List<StarSystemAPI> {
        val out = ArrayList<Pair<StarSystemAPI, Float>>()
        for (sys in Global.getSector().starSystems) {
            if (sys === sol || sys in exclude) continue
            val dist = Misc.getDistanceLY(sys.location, sol.location)
            if (dist > BASE_RANGE_LY) continue
            if (sys.hasTag(Tags.THEME_SPECIAL) || sys.hasTag(Tags.THEME_HIDDEN)) continue
            if (sys.hasPulsar()) continue
            if (Misc.getMarketsInLocation(sys).isNotEmpty()) continue
            val ok = sys.name == "Beta Centauri" ||
                sys.hasTag(Tags.THEME_MISC_SKIP) || sys.hasTag(Tags.THEME_MISC) ||
                sys.hasTag(Tags.THEME_REMNANT_NO_FLEETS) || sys.hasTag(Tags.THEME_REMNANT_DESTROYED) ||
                sys.hasTag(Tags.THEME_RUINS) || sys.hasTag(Tags.THEME_CORE_UNPOPULATED)
            if (!ok) continue
            out.add(Pair(sys, dist))
        }
        return out.sortedBy { it.second }.map { it.first }
    }

    private fun nearestPirateBase(sol: StarSystemAPI, rangeLY: Float): PirateBaseIntel? {
        var best: PirateBaseIntel? = null
        var bestDist = rangeLY
        for (i in Global.getSector().intelManager.getIntel(PirateBaseIntel::class.java)) {
            val base = i as PirateBaseIntel
            if (base.isEnding || base.isEnded || base.market == null || base.system == null) continue
            val d = Misc.getDistanceLY(base.system.location, sol.location)
            if (d <= bestDist) {
                best = base
                bestDist = d
            }
        }
        return best
    }

    private fun nearestPatherBase(sol: StarSystemAPI, rangeLY: Float): LuddicPathBaseIntel? {
        var best: LuddicPathBaseIntel? = null
        var bestDist = rangeLY
        for (i in Global.getSector().intelManager.getIntel(LuddicPathBaseIntel::class.java)) {
            val base = i as LuddicPathBaseIntel
            if (base.isEnding || base.isEnded || base.market == null || base.system == null) continue
            val d = Misc.getDistanceLY(base.system.location, sol.location)
            if (d <= bestDist) {
                best = base
                bestDist = d
            }
        }
        return best
    }

    private fun nearestMarketOfFaction(sol: StarSystemAPI, factionId: String): MarketAPI? {
        var best: MarketAPI? = null
        var bestDist = Float.MAX_VALUE
        for (m in Global.getSector().economy.marketsCopy) {
            if (m.factionId != factionId || m.primaryEntity == null || m.isPlanetConditionMarketOnly) continue
            val d = Misc.getDistanceLY(m.locationInHyperspace, sol.location)
            if (d < bestDist) {
                best = m
                bestDist = d
            }
        }
        return best
    }

    /** A hidden base that launches an attack gives itself away, like in the game's own crises. */
    private fun reveal(source: MarketAPI) {
        if (!Misc.isHiddenBase(source)) return
        PirateBaseIntel.getIntelFor(source)?.let {
            if (!it.isPlayerVisible) {
                it.makeKnown()
                it.sendUpdateIfPlayerHasIntel(PirateBaseIntel.DISCOVERED_PARAM, false)
            }
        }
        LuddicPathBaseIntel.getIntelFor(source)?.let {
            if (!it.isPlayerVisible) {
                it.makeKnown()
                it.sendUpdateIfPlayerHasIntel(LuddicPathBaseIntel.DISCOVERED_PARAM, false)
            }
        }
    }

    private fun post(params: GenericRaidParams): FleetGroupIntel {
        val raid = GenericRaidFGI(params)
        Global.getSector().intelManager.addIntel(raid)
        return raid
    }
}
