import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import solrenewed.quest.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

/** Runs the compiled quest against campaign API fakes; does not start or modify a game. */
public class SolQuestRegressionTest {
    private static int checks;
    private static final String DEFEATED = "$defeatedDweller_test";
    private static final String SUBSTRATE = "$shroudedSubstrateAvailable";

    public static void main(String[] args) {
        Fixture f = new Fixture("SHROUD");
        check(!SolQuest.INSTANCE.hasShroud(), "No kill or substrate does not qualify");
        f.player.put(DEFEATED, false);
        check(!SolQuest.INSTANCE.hasShroud(), "A false vanilla kill flag does not qualify");
        f.player.put(DEFEATED, true);
        check(SolQuest.INSTANCE.hasShroud(), "An existing vanilla kill qualifies");
        f.player.clear();
        f.player.put(SUBSTRATE, 3);
        check(SolQuest.INSTANCE.hasShroud(), "Numeric substrate amount qualifies");
        f.player.put(SUBSTRATE, 0);
        check(!SolQuest.INSTANCE.hasShroud(), "Zero substrate does not qualify");
        f.player.clear();
        f.substrate = 2;
        check(SolQuest.INSTANCE.hasShroud(), "Carried substrate qualifies without opening its dialog");

        FleetMemberAPI shroud = member(true);
        FleetMemberAPI ordinary = member(false);
        for (boolean won : new boolean[] {true, false}) {
            f = new Fixture("SHROUD");
            f.battle(won, side(true, List.of(shroud), List.of(), List.of()),
                side(false, List.of(ordinary), List.of(), List.of(shroud)));
            check(!SolQuest.INSTANCE.hasShroud() && SolQuest.INSTANCE.getStage() == SolQuest.Stage.SHROUD,
                "Player losses and surviving or retreated enemies do not count, won=" + won);
            for (boolean disabled : new boolean[] {false, true}) {
                f = new Fixture("SHROUD");
                EngagementResultForFleetAPI enemy = side(false,
                    disabled ? List.of() : List.of(shroud),
                    disabled ? List.of(shroud) : List.of(), List.of());
                f.battle(won, side(true, List.of(), List.of(), List.of()), enemy);
                check(SolQuest.INSTANCE.getStage() == SolQuest.Stage.RETURN,
                    "Enemy casualty advances without salvage, won=" + won + ", disabled=" + disabled);
                check(SolQuest.INSTANCE.hasShroud() && f.player.isEmpty(),
                    "Kill persists without changing vanilla loot flags");
            }
        }

        f = new Fixture("NONE");
        f.battle(false, side(true, List.of(), List.of(), List.of()),
            side(false, List.of(shroud), List.of(), List.of()));
        check(SolQuest.INSTANCE.getStage() == SolQuest.Stage.NONE && SolQuest.INSTANCE.hasShroud(),
            "Kill before accepting the quest is retained without starting it");
        Map<String, Object> savedGlobal = new HashMap<>(f.global);
        f = new Fixture("NONE");
        f.global.putAll(savedGlobal);
        SolQuest.INSTANCE.needShroud(null);
        new SolQuestWatcher().advance(1f);
        check(SolQuest.INSTANCE.getStage() == SolQuest.Stage.RETURN,
            "Restored kill record advances through the campaign watcher");

        for (String proof : List.of("vanilla", "numeric", "cargo", "recorded")) {
            f = new Fixture("SHROUD");
            switch (proof) {
                case "vanilla" -> f.player.put(DEFEATED, true);
                case "numeric" -> f.player.put(SUBSTRATE, 1);
                case "cargo" -> f.substrate = 1;
                case "recorded" -> f.global.put(SolQuest.SHROUD_KILL_KEY, true);
            }
            SolQuest.INSTANCE.install();
            SolQuest.INSTANCE.install();
            check(SolQuest.INSTANCE.getStage() == SolQuest.Stage.RETURN,
                "Loading a stuck save advances using " + proof);
            check(f.listeners.size() == 1 && f.scripts.size() == 1,
                "Repeated installation keeps one listener and watcher");
            // Transient hooks disappear on reload while campaign memory remains.
            f.listeners.clear();
            f.scripts.clear();
            SolQuest.INSTANCE.install();
            check(f.listeners.size() == 1 && f.scripts.size() == 1,
                "Reload restores transient hooks");
        }

        f = new Fixture("SHROUD");
        SolQuest.INSTANCE.install();
        f.listeners.get(0).reportPlayerEngagement(result(true,
            side(true, List.of(), List.of(), List.of()),
            side(false, List.of(shroud), List.of(), List.of())));
        check(SolQuest.INSTANCE.getStage() == SolQuest.Stage.RETURN,
            "The installed listener handles a kill");

        for (String stage : List.of("TALK", "DECLINED", "TRAVEL", "RETURN", "DONE")) {
            f = new Fixture(stage);
            f.battle(true, side(true, List.of(), List.of(), List.of()),
                side(false, List.of(shroud), List.of(), List.of()));
            check(SolQuest.INSTANCE.getStage().name().equals(stage),
                "Combat preserves stage " + stage);
        }
        System.out.println("PASS: " + checks + " quest regression checks");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static FleetMemberAPI member(boolean shrouded) {
        ShipHullSpecAPI hull = fake(ShipHullSpecAPI.class, (name, args) -> {
            if (name.equals("hasTag")) return shrouded && Tags.DWELLER.equals(args[0]);
            throw unexpected(name);
        });
        return fake(FleetMemberAPI.class, (name, args) -> {
            if (name.equals("getHullSpec")) return hull;
            throw unexpected(name);
        });
    }

    private static EngagementResultForFleetAPI side(boolean player, List<FleetMemberAPI> destroyed,
            List<FleetMemberAPI> disabled, List<FleetMemberAPI> survivors) {
        return fake(EngagementResultForFleetAPI.class, (name, args) -> switch (name) {
            case "isPlayer" -> player;
            case "getDestroyed" -> destroyed;
            case "getDisabled" -> disabled;
            case "getDeployed", "getRetreated", "getReserves" -> survivors;
            default -> throw unexpected(name);
        });
    }

    private static EngagementResultAPI result(boolean won, EngagementResultForFleetAPI player,
            EngagementResultForFleetAPI enemy) {
        return fake(EngagementResultAPI.class, (name, args) -> switch (name) {
            case "didPlayerWin" -> won;
            case "getWinnerResult" -> won ? player : enemy;
            case "getLoserResult" -> won ? enemy : player;
            default -> throw unexpected(name);
        });
    }

    private static MemoryAPI memory(Map<String, Object> values) {
        return fake(MemoryAPI.class, (name, args) -> switch (name) {
            case "get", "getString" -> values.get(args[0]);
            case "getBoolean" -> Boolean.TRUE.equals(values.get(args[0]));
            case "getKeys" -> values.keySet();
            case "set" -> { values.put((String) args[0], args[1]); yield null; }
            case "unset" -> { values.remove(args[0]); yield null; }
            default -> throw unexpected(name);
        });
    }

    private static final class Fixture {
        final Map<String, Object> global = new HashMap<>();
        final Map<String, Object> player = new HashMap<>();
        final List<CampaignEventListener> listeners = new ArrayList<>();
        final List<EveryFrameScript> scripts = new ArrayList<>();
        float substrate;

        Fixture(String stage) {
            global.put(SolQuest.STAGE_KEY, stage);
            MemoryAPI globalMemory = memory(global);
            MemoryAPI playerMemory = memory(player);
            CargoAPI cargo = fake(CargoAPI.class, (name, args) -> {
                if (name.equals("getQuantity")) {
                    check(args[0] == CargoAPI.CargoItemType.SPECIAL &&
                        Items.SHROUDED_SUBSTRATE.equals(((SpecialItemData) args[1]).getId()),
                        "Cargo fallback requests the substrate item");
                    return substrate;
                }
                throw unexpected(name);
            });
            CampaignFleetAPI fleet = fake(CampaignFleetAPI.class, (name, args) -> {
                if (name.equals("getCargo")) return cargo;
                throw unexpected(name);
            });
            EconomyAPI economy = fake(EconomyAPI.class, (name, args) -> {
                if (name.equals("getMarket")) return null;
                throw unexpected(name);
            });
            CampaignClockAPI clock = fake(CampaignClockAPI.class, (name, args) -> {
                if (name.equals("convertToDays")) return args[0];
                throw unexpected(name);
            });
            Global.setSector(fake(SectorAPI.class, (name, args) -> switch (name) {
                case "getMemoryWithoutUpdate" -> globalMemory;
                case "getPlayerMemoryWithoutUpdate" -> playerMemory;
                case "getPlayerFleet" -> fleet;
                case "getStarSystems" -> List.of();
                case "getEconomy" -> economy;
                case "getClock" -> clock;
                case "getAllListeners" -> listeners;
                case "addTransientListener" -> { listeners.add((CampaignEventListener) args[0]); yield null; }
                case "hasTransientScript" -> scripts.stream().anyMatch(((Class<?>) args[0])::isInstance);
                case "addTransientScript" -> { scripts.add((EveryFrameScript) args[0]); yield null; }
                default -> throw unexpected(name);
            }));
        }

        void battle(boolean won, EngagementResultForFleetAPI own, EngagementResultForFleetAPI enemy) {
            new SolQuestBattleListener().reportPlayerEngagement(result(won, own, enemy));
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
                return handler.apply(method.getName(), args);
            }));
    }
}
