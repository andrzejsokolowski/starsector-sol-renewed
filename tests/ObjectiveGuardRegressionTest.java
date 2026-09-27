import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI.GenericPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Objectives;
import com.fs.starfarer.api.plugins.BuildObjectiveTypePicker;
import com.fs.starfarer.api.plugins.BuildObjectiveTypePicker.BuildObjectiveParams;
import com.fs.starfarer.api.plugins.impl.CoreBuildObjectiveTypePicker;
import solrenewed.ObjectivePickerGuard;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Replays the patrol "construct objective" callback from the game's BaseAssignmentAI against campaign
 * API fakes, using the game's own picker and Objectives.build; does not start or modify a game.
 */
public class ObjectiveGuardRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        Fixture f = new Fixture(Tags.COMM_RELAY, Tags.NAV_BUOY, Tags.SENSOR_ARRAY);
        boolean crashed = false;
        try {
            f.arrive();
        } catch (NullPointerException e) {
            crashed = true;
        }
        check(crashed && f.built.isEmpty(), "Without the guard, a build with nothing left to build crashes like the report");

        f = new Fixture(Tags.COMM_RELAY, Tags.NAV_BUOY, Tags.SENSOR_ARRAY);
        ObjectivePickerGuard.Companion.install();
        ObjectivePickerGuard.Companion.install();
        check(f.plugins.stream().filter(ObjectivePickerGuard.class::isInstance).count() == 1,
            "The guard is registered once however often the game loads");
        check(f.transientPlugins.stream().anyMatch(ObjectivePickerGuard.class::isInstance),
            "The guard is not written into the save");
        f.arrive();
        check(f.built.isEmpty(), "With the guard, nothing is built and the game does not crash");
        check(f.stableTags.contains(Tags.FADING_OUT_AND_EXPIRING) && f.scripts.size() == 1,
            "The stable location is held for one frame");
        f.scripts.get(0).advance(0.016f);
        check(!f.stableTags.contains(Tags.FADING_OUT_AND_EXPIRING) && f.scripts.get(0).isDone(),
            "The next frame the stable location is back to normal");
        check(f.pick() == null && f.pickers.get(0) instanceof ObjectivePickerGuard,
            "No build order is given when there is nothing to build");

        f = new Fixture(Tags.COMM_RELAY, Tags.NAV_BUOY);
        ObjectivePickerGuard.Companion.install();
        check(Entities.SENSOR_ARRAY_MAKESHIFT.equals(f.pick()) && f.scripts.isEmpty() && f.stableTags.isEmpty(),
            "The game's own choice passes through untouched when something is missing");

        f = new Fixture(Tags.COMM_RELAY, Tags.NAV_BUOY, Tags.SENSOR_ARRAY);
        f.stableTags.add(Tags.FADING_OUT_AND_EXPIRING);
        ObjectivePickerGuard.Companion.install();
        f.arrive();
        check(f.built.isEmpty() && f.scripts.isEmpty() && f.stableTags.contains(Tags.FADING_OUT_AND_EXPIRING),
            "A stable location that is really fading out is left alone");

        f = new Fixture(Tags.COMM_RELAY, Tags.NAV_BUOY, Tags.SENSOR_ARRAY);
        f.plugins.add(picker(GenericPluginManagerAPI.MOD_SPECIFIC, "other_mod_objective"));
        ObjectivePickerGuard.Companion.install();
        check("other_mod_objective".equals(f.pick()), "Another mod's picker keeps working under the guard");

        System.out.println("PASS: " + checks + " objective guard checks");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static GenericPlugin picker(int priority, String answer) {
        final class ModPicker implements BuildObjectiveTypePicker, GenericPlugin {
            public String pickObjectiveToBuild(BuildObjectiveParams params) { return answer; }
            public int getHandlingPriority(Object params) { return priority; }
        }
        return new ModPicker();
    }

    /** Builds objectives through the game's own code, which reads `entity` directly. */
    private static final class Builder extends Objectives {
        Builder(SectorEntityToken stableLoc) {
            entity = stableLoc;
        }
    }

    private static final class Fixture {
        final List<GenericPlugin> plugins = new ArrayList<>();
        final List<GenericPlugin> transientPlugins = new ArrayList<>();
        final List<EveryFrameScript> scripts = new ArrayList<>();
        final List<String> built = new ArrayList<>();
        final Set<String> stableTags = new HashSet<>();
        final FactionAPI faction;
        final SectorEntityToken stableLoc;
        List<BuildObjectiveTypePicker> pickers = new ArrayList<>();

        /** A system where this colony's faction already owns the objectives with the given tags. */
        Fixture(String... owned) {
            faction = fake(FactionAPI.class, (name, args) -> switch (name) {
                case "getId" -> "player";
                case "getCustomBoolean" -> { check(Factions.CUSTOM_PIRATE_BEHAVIOR.equals(args[0]), "Picker reads pirate behaviour"); yield false; }
                case "isHostileTo" -> false;
                default -> throw unexpected(name);
            });
            List<SectorEntityToken> objectives = new ArrayList<>();
            for (String tag : owned) {
                objectives.add(fake(SectorEntityToken.class, (name, args) -> switch (name) {
                    case "getFaction" -> faction;
                    case "hasTag" -> tag.equals(args[0]);
                    default -> throw unexpected(name);
                }));
            }
            LocationAPI system = fake(LocationAPI.class, (name, args) -> switch (name) {
                case "getEntitiesWithTag" -> {
                    check(Tags.OBJECTIVE.equals(args[0]), "Picker looks at the system's objectives");
                    yield objectives;
                }
                case "addCustomEntity" -> {
                    // The game looks up the entity spec by type; with no type the spec is null.
                    if (args[2] == null) throw new NullPointerException("this.spec is null");
                    built.add((String) args[2]);
                    throw new AssertionError("Only the crash path is replayed");
                }
                default -> throw unexpected(name);
            });
            stableLoc = fake(SectorEntityToken.class, (name, args) -> switch (name) {
                case "getContainingLocation" -> system;
                case "hasTag" -> stableTags.contains(args[0]);
                case "addTag" -> { stableTags.add((String) args[0]); yield null; }
                case "removeTag" -> { stableTags.remove(args[0]); yield null; }
                default -> throw unexpected(name);
            });
            plugins.add(new CoreBuildObjectiveTypePicker());
            GenericPluginManagerAPI manager = fake(GenericPluginManagerAPI.class, (name, args) -> switch (name) {
                case "hasPlugin" -> plugins.stream().anyMatch(((Class<?>) args[0])::isInstance);
                case "addPlugin" -> {
                    plugins.add((GenericPlugin) args[0]);
                    if (args.length > 1 && Boolean.TRUE.equals(args[1])) transientPlugins.add((GenericPlugin) args[0]);
                    yield null;
                }
                case "getPluginsOfClass" -> plugins.stream().filter(((Class<?>) args[0])::isInstance).toList();
                case "pickPlugin" -> plugins.stream()
                    .filter(((Class<?>) args[0])::isInstance)
                    .filter(p -> p.getHandlingPriority(args[1]) >= 0)
                    .max(Comparator.comparingInt(p -> p.getHandlingPriority(args[1])))
                    .orElse(null);
                default -> throw unexpected(name);
            });
            Global.setSector(fake(SectorAPI.class, (name, args) -> switch (name) {
                case "getGenericPlugins" -> manager;
                case "addScript" -> { scripts.add((EveryFrameScript) args[0]); yield null; }
                default -> throw unexpected(name);
            }));
        }

        BuildObjectiveParams params() {
            BuildObjectiveParams params = new BuildObjectiveParams();
            params.faction = faction;
            params.stableLoc = stableLoc;
            return params;
        }

        /** The picker lookup both the build order and the construction callback use. */
        String pick() {
            BuildObjectiveParams params = params();
            BuildObjectiveTypePicker pick = Global.getSector().getGenericPlugins().pickPlugin(BuildObjectiveTypePicker.class, params);
            pickers = pick == null ? List.of() : List.of(pick);
            return pick == null ? null : pick.pickObjectiveToBuild(params);
        }

        /** BaseAssignmentAI's construction callback: re-pick the type, then build whatever came back. */
        void arrive() {
            String type = pick();
            new Builder(stableLoc).build(type, faction.getId());
        }
    }

    private static AssertionError unexpected(String method) {
        return new AssertionError("Unexpected API call: " + method);
    }

    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> type.getSimpleName() + " fake";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw unexpected(method.getName());
                    };
                }
                return handler.apply(method.getName(), args == null ? new Object[0] : args);
            }));
    }
}
