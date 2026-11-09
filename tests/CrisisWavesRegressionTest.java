import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import solrenewed.crisis.CrisisIntel.Stage;
import solrenewed.crisis.Waves;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Runs the crisis wave rules against campaign API fakes: how strong each stage's one attack is, and which
 * of the Sector's factions may invade Sol. Does not start or modify a game.
 */
public class CrisisWavesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        Waves w = Waves.INSTANCE;
        check(w.stageMult(Stage.FOOTHOLD) == 0.5f && w.stageMult(Stage.RAID) == 0.75f
            && w.stageMult(Stage.ASSAULT) == 1f && w.stageMult(Stage.RECKONING) == 1.5f,
            "One attack per stage at 50%, 75%, 100% and 150%");
        check(w.budget(Stage.ASSAULT, Factions.PIRATES, 1f) == 50, "The Assault is 50 points at 100%");
        check(w.budget(Stage.FOOTHOLD, Factions.PIRATES, 1f) == 25 && w.budget(Stage.RAID, Factions.PIRATES, 1f) == 38
            && w.budget(Stage.RECKONING, Factions.PIRATES, 1f) == 75,
            "Foothold 25, Raid 38, Reckoning 75 points");
        check(w.budget(Stage.RECKONING, Factions.REMNANTS, 1f) == 53, "Remnants send 70% of that (53 at the Reckoning)");
        check(w.budget(Stage.ASSAULT, Factions.LUDDIC_PATH, 1f) == 45, "The Path sends 90% (45 at the Assault)");
        check(w.budget(Stage.ASSAULT, Factions.HEGEMONY, 1f) == 50, "A faction invasion gets the full budget");
        check(w.budget(Stage.RECKONING, Factions.PIRATES, 2.5f) == 160, "Strength never takes one attack past 160 points");
        check(w.budget(Stage.FOOTHOLD, Factions.PIRATES, 0.1f) == 3, "And never below 3");

        Set<String> withMarkets = Set.of(Factions.HEGEMONY, Factions.TRITACHYON, "HSI", Factions.INDEPENDENT,
            Factions.PIRATES, Factions.LUDDIC_PATH, Factions.PERSEAN, Factions.DIKTAT, Factions.PLAYER);
        check(w.isInvader(faction(Factions.HEGEMONY, true, true, -0.2f), withMarkets, null), "The Hegemony may invade");
        check(w.isInvader(faction("HSI", true, true, 0f), withMarkets, null), "A modded faction (Halo Dynamics) may invade");
        check(w.isInvader(faction(Factions.TRITACHYON, true, true, 0.29f), withMarkets, null), "A faction at 29 still may");
        check(!w.isInvader(faction(Factions.TRITACHYON, true, true, 0.30f), withMarkets, null), "A faction at 30 never does");
        check(!w.isInvader(faction(Factions.PERSEAN, true, true, -0.5f), withMarkets, Factions.PERSEAN),
            "The player's commission faction never does");
        check(!w.isInvader(faction("hidden_helper", false, true, -1f), Set.of("hidden_helper"), null),
            "A faction hidden from the intel screen never does");
        check(!w.isInvader(faction(Factions.OMEGA, true, true, -1f), withMarkets, null), "A faction without colonies never does");
        check(!w.isInvader(faction(Factions.DIKTAT, true, false, -1f), withMarkets, null), "A faction without ships never does");
        check(!w.isInvader(faction(Factions.INDEPENDENT, true, true, 0f), withMarkets, null), "The independents never do");
        check(!w.isInvader(faction(Factions.PIRATES, true, true, -1f), withMarkets, null)
            && !w.isInvader(faction(Factions.LUDDIC_PATH, true, true, -1f), withMarkets, null)
            && !w.isInvader(faction(Factions.REMNANTS, true, true, -1f), withMarkets, null),
            "Pirates, Path and Remnants have their own attacks");
        check(!w.isInvader(faction(Factions.PLAYER, true, true, 0f), withMarkets, null), "The player never invades Sol");

        System.out.println("PASS: " + checks + " crisis wave checks");
    }

    private static FactionAPI faction(String id, boolean inIntel, boolean ships, float rel) {
        RelationshipAPI relationship = fake(RelationshipAPI.class, (name, a) -> switch (name) {
            case "getRel" -> rel;
            case "isHostile" -> rel <= RepLevel.HOSTILE.getMin();
            default -> throw unexpected("RelationshipAPI." + name);
        });
        return fake(FactionAPI.class, (name, a) -> switch (name) {
            case "getId" -> id;
            case "isPlayerFaction" -> Factions.PLAYER.equals(id);
            case "isShowInIntelTab" -> inIntel;
            case "getKnownShips" -> ships ? Set.of("onslaught") : Set.of();
            case "getRelToPlayer" -> relationship;
            default -> throw unexpected("FactionAPI." + name);
        });
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
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
