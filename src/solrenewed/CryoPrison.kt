package solrenewed

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignPlugin
import com.fs.starfarer.api.campaign.CampaignPlugin
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.PluginPick
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import java.util.Random

/**
 * "Unit 7: Cryo-Detention": a Domain-era prison platform in the outer system. Boarding it shows
 * eight frozen prisoner officers with their crime records; the player may wake exactly one, who
 * joins the fleet as a level 5 officer (max level 7) with a randomised skill set. The station then
 * fades away.
 */
object CryoPrison {

    const val ENTITY_TYPE = "sr_cryo_prison"
    const val TAG = "sr_cryo_prison"
    private const val KEY_INMATES = "\$sr_inmates"
    private const val KEY_RAPSHEET = "\$sr_inmate_rapsheet"
    private const val PLUGIN_ID = "sr_cryo_prison_plugin"

    fun spawn(sys: StarSystemAPI, focus: SectorEntityToken, orbitRadius: Float, angle: Float) {
        val e = sys.addCustomEntity("sr_cryo_prison", "The Inexorable Stasis", ENTITY_TYPE, "neutral")
        e.setCircularOrbitPointingDown(focus, angle, orbitRadius, 1200f)
        Uni.discoverable(e, 2500f)
        setupInmates(e)
    }

    /** Hooks the dialog. Registered as transient on every game load. */
    fun registerDialogHook() {
        val sector = Global.getSector()
        sector.unregisterPlugin(PLUGIN_ID)
        sector.registerPlugin(DialogPicker())
    }

    class DialogPicker : BaseCampaignPlugin() {
        override fun getId(): String = PLUGIN_ID
        override fun isTransient(): Boolean = true
        override fun pickInteractionDialogPlugin(interactionTarget: SectorEntityToken): PluginPick<InteractionDialogPlugin>? {
            if (!interactionTarget.hasTag(TAG)) return null
            return PluginPick(Dialog(), CampaignPlugin.PickPriority.MOD_SPECIFIC)
        }
    }

    // ------------------------------------------------------------------ prisoners

    private class Skill(val id: String, val weight: Float, val hasElite: Boolean = false)

    private val COMBAT_SKILLS = listOf(
        Skill("helmsmanship", 5f, true), Skill("combat_endurance", 5f, true), Skill("impact_mitigation", 4f, true),
        Skill("damage_control", 3f, true), Skill("field_modulation", 3f, true), Skill("point_defense", 3f, true),
        Skill("target_analysis", 3f, true), Skill("ballistic_mastery", 3f, true), Skill("systems_expertise", 3f, true),
        Skill("missile_specialization", 3f, true), Skill("gunnery_implants", 4f, true), Skill("energy_weapon_mastery", 3f, true),
        Skill("ordnance_expert", 4f, true), Skill("polarized_armor", 4f, true),
    )
    private val RAT_SKILLS = listOf(
        Skill("rat_perfect_planning", 5f), Skill("rat_maintaining_momentum", 4f), Skill("rat_maverick", 3f), Skill("rat_auto_engineer", 2f),
    )
    private val RAP_SHEET = listOf(
        "Mutiny under collapse conditions at Luna Shipyards.",
        "Destruction of a civilian freighter during a classified intercept.",
        "Desertion and theft of restricted FTL components.",
        "Negligent operation of autonomous strike drones leading to colony loss.",
        "Sabotage of a diplomatic convoy during the Jovian Gate Riots.",
        "Refusal to obey direct fire orders during civil unrest suppression.",
        "Trafficking of cryo-warden override codes to pirate organizations.",
        "Unauthorized military-grade cybernetic augmentation.",
        "Gross negligence leading to friendly fleet losses.",
        "Hijacking of relief shipments during early Collapse evacuations.",
        "Violent misconduct during transport of pre-Collapse data cores.",
        "Sedition and insubordination during Earth evacuation operations.",
        "Attempted unauthorized thaw and release of cryostorage inmates.",
        "Distribution of anti-Domain propaganda.",
        "Illegal AI manipulation and drone repurposing.",
        "Classified: marked 'Extremely Hazardous - Oversight Failure'.",
        "Classified: 'Omega-Level Offender - Do Not Revive'.",
        "All records lost; previous logs cite severe threat potential.",
        "Sabotage of a Jovian Secession drop shuttle (evidence inconclusive).",
        "Participation in Titan Naval Yard mutinies; politically sensitive misconduct.",
        "Unauthorized rescue prioritization during Evacuation Fleet collapse.",
        "Insubordination and intelligence compromise during Luna extremist sting operation.",
        "Reactor AI tampering causing shipyard detonation.",
        "Violent instability following prohibited cognition-enhancement trials.",
        "Loyalty-code violation during Mars Garrison crackdowns.",
        "Integration of an Alpha-level AI core into a combat simulator.",
        "Suspected involvement in Neptune Deep Relay catastrophic FTL event.",
        "Designation as strategic dissident during pre-Collapse security purges.",
    )

    private fun setupInmates(entity: SectorEntityToken) {
        val mem = entity.memoryWithoutUpdate
        if (mem.contains(KEY_INMATES)) return
        val rng = Random()
        val used = HashSet<String>()
        val inmates = ArrayList<PersonAPI>()
        repeat(8) { inmates.add(genInmate(rng, used)) }
        mem.set(KEY_INMATES, inmates)
    }

    private fun genInmate(rng: Random, usedCharges: MutableSet<String>): PersonAPI {
        val p = Global.getFactory().createPerson()
        p.setFaction("neutral")
        val gender = if (rng.nextBoolean()) FullName.Gender.MALE else FullName.Gender.FEMALE
        p.gender = gender
        p.portraitSprite = pickPortrait(gender)
        p.name = inmateName(rng, gender)
        val personalities = listOf("timid", "cautious", "steady", "aggressive", "reckless")
        p.setPersonality(personalities[rng.nextInt(personalities.size)])

        var combat = 5
        if (ModCheck.hasRAT && pickSkill(p, RAT_SKILLS, rng)?.let { p.stats.setSkillLevel(it.id, 1f); true } == true) combat = 4
        repeat(combat) {
            val s = pickSkill(p, COMBAT_SKILLS, rng) ?: return@repeat
            p.stats.setSkillLevel(s.id, skillLevel(p, s, rng).toFloat())
        }
        p.stats.level = 5
        p.memoryWithoutUpdate.set("\$officerMaxLevel", 7)

        val free = RAP_SHEET.filter { it !in usedCharges }
        val story = if (free.isEmpty()) "[NO RECORDS REMAINING]" else free[rng.nextInt(free.size)]
        usedCharges.add(story)
        p.memoryWithoutUpdate.set(KEY_RAPSHEET, story)
        return p
    }

    private fun inmateName(rng: Random, gender: FullName.Gender): FullName {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        repeat(7) { sb.append(chars[rng.nextInt(chars.length)]) }
        return FullName("Inmate", sb.toString(), gender)
    }

    private fun pickPortrait(gender: FullName.Gender): String {
        val picker = Global.getSector().getFaction("independent").getPortraits(gender)
        if (picker == null || picker.isEmpty) return Global.getSettings().getSpriteName("characters", "generic")
        return picker.pick()
    }

    private fun personalityMult(p: PersonAPI, skill: Skill): Float {
        val gunnery = skill.id in setOf("gunnery_implants", "ballistic_mastery", "energy_weapon_mastery", "target_analysis")
        val defensive = skill.id in setOf("impact_mitigation", "polarized_armor", "damage_control", "point_defense")
        return when (p.personalityAPI.id) {
            "aggressive", "reckless" -> if (gunnery) 1.6f else 1.1f
            "cautious" -> if (defensive) 1.5f else 0.9f
            "timid" -> if (defensive) 1.7f else 0.7f
            else -> 1f
        }
    }

    private fun pickSkill(p: PersonAPI, pool: List<Skill>, rng: Random): Skill? {
        val have = p.stats.skillsCopy.map { it.skill.id }.toSet()
        val picker = WeightedRandomPicker<Skill>(rng)
        for (s in pool) {
            if (s.id in have) continue
            if (runCatching { Global.getSettings().getSkillSpec(s.id) }.getOrNull() == null) continue
            picker.add(s, s.weight * personalityMult(p, s))
        }
        return if (picker.isEmpty) null else picker.pick()
    }

    private fun skillLevel(p: PersonAPI, skill: Skill, rng: Random): Int {
        if (!skill.hasElite) return 1
        val base = when (p.personalityAPI.id) {
            "reckless" -> 0.6f
            "aggressive" -> 0.45f
            "steady" -> 0.3f
            "cautious" -> 0.2f
            "timid" -> 0.1f
            else -> 0.25f
        }
        val chance = base * Math.min(personalityMult(p, skill) * 0.8f, 1f)
        return if (rng.nextFloat() < chance) 2 else 1
    }

    // ------------------------------------------------------------------ dialog

    private enum class Option { APPROACH, CONTINUE, LEAVE }

    class Dialog : InteractionDialogPlugin {
        private lateinit var dialog: InteractionDialogAPI
        private lateinit var text: TextPanelAPI
        private lateinit var options: OptionPanelAPI
        private lateinit var entity: SectorEntityToken
        private var inmates: List<PersonAPI> = emptyList()

        override fun init(dialog: InteractionDialogAPI) {
            this.dialog = dialog
            text = dialog.textPanel
            options = dialog.optionPanel
            entity = dialog.interactionTarget
            @Suppress("UNCHECKED_CAST")
            inmates = (entity.memoryWithoutUpdate.get(KEY_INMATES) as? List<PersonAPI>) ?: emptyList()
            text.addPara("Your fleet moves into the shadow of the cryo-detention platform, a derelict Domain-era solitary confinement vault drifting in silent orbit. Its armored hull is cracked and frost-scored, but faint power signatures still pulse through internal conduits like a lingering heartbeat.")
            text.addPara("Cryogenic containment nodes remain active - degraded, recursive, and partially corrupted. Accessing them will risk triggering long-dormant security directives written during the Collapse. Whatever remains inside has been waiting for centuries, suspended between duty and oblivion.")
            if (inmates.isEmpty()) {
                text.addPara("Every pod reads as failed. There is nothing left to wake.", Misc.getGrayColor())
            } else {
                options.addOption("Attempt to breach the station", Option.APPROACH)
            }
            options.addOption("Leave", Option.LEAVE)
            options.setShortcut(Option.LEAVE, 1, false, false, false, true)
        }

        override fun optionSelected(optionText: String?, optionData: Any?) {
            when (optionData) {
                Option.APPROACH -> approach()
                Option.CONTINUE -> selection()
                Option.LEAVE -> dialog.dismiss()
                is PersonAPI -> release(optionData)
            }
        }

        private fun approach() {
            options.clearOptions()
            text.addPara("Closing with the cryostation requires slow, deliberate maneuvering. Automated targeting arrays track your fleet with sluggish, half-dead sensors - not hostile, but not entirely inert either. Your techs broadcast layers of spoofed Domain command-tags, hoping the old systems still recognize them.")
            text.addPara("Your boarding team breaches the outer shell through a hull segment warped by centuries of micrometeor impacts. Inside, the air is stale and motionless. Frozen bulkheads open reluctantly, and long-dead officer logs flicker across cracked holoscreens as if trying to remember their last orders.")
            text.addPara("In the central vault, cryopods stand arranged around a dormant containment warhead - part deterrent, part executioner. After a tense override procedure, your specialists manage to disarm the fail-safe. Only then do the pod diagnostics sync, revealing survivors who were never meant to wake again.")
            options.addOption("Continue", Option.CONTINUE)
        }

        private fun selection() {
            options.clearOptions()
            val n = inmates.size.toString()
            text.addPara("A full sweep of the vault returns fragmented life-signs: $n pods still contain viable occupants. The rest show catastrophic failures, corrupted kill-orders, or sealed redacted classifications. The station's command systems destabilize as your intrusion deepens - you will only have time to release one before the cryostation reasserts its automated protocols.",
                Misc.getTextColor(), Misc.getHighlightColor(), n, "one")

            val tooltip = text.beginTooltip()
            tooltip.addSectionHeading("CRYO-VAULT LOG: SOLITARY STORAGE UNIT 7-SOL", Alignment.MID, 5f)
            for (person in inmates) {
                tooltip.addSpacer(10f)
                val img = tooltip.beginImageWithText(person.portraitSprite, 48f)
                img.addPara(person.nameString, 0f, Misc.getTextColor(), Misc.getHighlightColor(), person.nameString)
                val personality = personalityName(person)
                img.addPara("Personality: $personality", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), personality)
                val rap = person.memoryWithoutUpdate.getString(KEY_RAPSHEET)
                if (rap != null) img.addPara("Offense: $rap", 3f, Misc.getStoryBrightColor(), Misc.getHighlightColor())
                tooltip.addImageWithText(0f)
            }
            text.addTooltip()

            val maxOfficers = Global.getSector().characterData.person.stats.officerNumber.modifiedValue.toInt()
            val current = Global.getSector().playerFleet.fleetData.officersCopy.size
            val canRecruit = current < maxOfficers
            for (person in inmates) {
                options.addOption("Release: " + person.nameString, person)
                options.addOptionTooltipAppender(person) { t, _ ->
                    t.addSectionHeading(person.nameString, Alignment.MID, 3f)
                    t.addImage(person.portraitSprite, 128f, 3f)
                    val personality = personalityName(person)
                    t.addPara("Psyche: $personality", 5f, Misc.getGrayColor(), Misc.getHighlightColor(), personality)
                    val rap = person.memoryWithoutUpdate.getString(KEY_RAPSHEET)
                    if (rap != null) t.addPara(rap, 5f, Misc.getStoryDarkBrighterColor(), Misc.getHighlightColor())
                    t.addPara("Their skills and capabilities will be revealed upon recruitment.", 5f, Misc.getGrayColor())
                }
                if (!canRecruit) {
                    options.setEnabled(person, false)
                    options.setTooltip(person, "Fleet is at maximum number of officers.")
                }
            }
            options.addOption("Leave", Option.LEAVE)
            options.setShortcut(Option.LEAVE, 1, false, false, false, true)
        }

        private fun release(person: PersonAPI) {
            options.clearOptions()
            text.addPara("As you authorize the release sequence, the selected cryopod cycles through a cascade of decompression, warming fluid, and neural reactivation pulses. Across the chamber, the remaining pods abruptly hard-lock, their vitals flatlining as the system initiates purge protocols.")
            text.addPara("The revived sleeper is extracted by your medics - disoriented, frostbitten, and blinking against light they haven't seen in centuries. Their identity stabilizes on your datapad as their memories begin to surface. Whatever sentence they once served is now irrelevant; their future lies with your fleet.")
            text.addPara("Officer " + person.nameString + " has joined your fleet.", Misc.getPositiveHighlightColor())
            Global.getSector().playerFleet.fleetData.addOfficer(person)
            entity.memoryWithoutUpdate.set(KEY_INMATES, ArrayList<PersonAPI>())
            options.addOption("Leave", Option.LEAVE)
            options.setShortcut(Option.LEAVE, 1, false, false, false, true)
            Misc.fadeAndExpire(entity)
        }

        private fun personalityName(p: PersonAPI): String {
            val id = p.personalityAPI.id
            return id.substring(0, 1).uppercase() + id.substring(1).lowercase()
        }

        override fun optionMousedOver(optionText: String?, optionData: Any?) {}
        override fun advance(amount: Float) {}
        override fun backFromEngagement(battleResult: EngagementResultAPI?) {}
        override fun getContext(): Any? = null
        override fun getMemoryMap(): Map<String, MemoryAPI>? = null
    }
}
