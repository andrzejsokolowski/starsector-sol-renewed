import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import solrenewed.crisis.CrisisIntel;
import solrenewed.crisis.CrisisIntel.Share;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

/** Runs the crisis' monthly point rules against campaign API fakes; does not start or modify a game. */
public class CrisisMonthlyRegressionTest {
    private static int checks;
    private static final Map<String, IndustrySpecAPI> specs = new HashMap<>();

    public static void main(String[] args) {
        spec("farming", 75000f, null);
        spec("heavyindustry", 500000f, null);
        spec("orbitalworks", 300000f, "heavyindustry");
        spec("loop_a", 100000f, "loop_b");
        spec("loop_b", 100000f, "loop_a");
        spec("orphan", 50000f, "not_installed");
        Global.setSettings(fake(SettingsAPI.class, (name, a) -> {
            if (name.equals("getIndustrySpec")) {
                IndustrySpecAPI s = specs.get((String) a[0]);
                if (s == null) throw new RuntimeException("no industry " + a[0]);
                return s;
            }
            throw unexpected(name);
        }));

        check(CrisisIntel.colonyPoints(3) == 6f && CrisisIntel.colonyPoints(6) == 12f,
            "Every colony size adds 2 a month");
        check(CrisisIntel.industryPoints(specs.get("farming")) == 1.5f, "Farming at 75k adds 1.5 a month");
        check(CrisisIntel.industryPoints(specs.get("heavyindustry")) == 10f, "Heavy Industry at 500k adds 10 a month");
        check(CrisisIntel.industryPoints(specs.get("orbitalworks")) == 16f,
            "An upgrade also counts what it was built on");
        check(CrisisIntel.industryPoints(specs.get("loop_a")) == 4f, "A looping upgrade chain counts each step once");
        check(CrisisIntel.industryPoints(specs.get("orphan")) == 1f,
            "An upgrade of an industry that is not installed counts only itself");

        MarketAPI mars = market("Mars", 6,
            industry("farming", true, false, false),
            industry("orbitalworks", true, true, true),
            industry("heavyindustry", true, true, false),
            industry("orphan", false, false, false));
        MarketAPI luna = market("Luna", 3);
        List<MarketAPI> markets = List.of(mars, luna);

        List<Share> colonies = CrisisIntel.colonyShares(markets);
        check(colonies.size() == 2 && total(colonies) == 18f, "Colonies of size 6 and 3 add 18 a month");
        check(colonies.get(0).getLabel().equals("Mars, size 6"), "Colony lines name the colony and its size");

        List<Share> industries = CrisisIntel.industryShares(markets);
        check(industries.size() == 2, "Structures and industries still under construction do not count");
        check(total(industries) == 17.5f, "A finished farm and an industry mid upgrade add 17.5 a month");
        check(industries.get(1).getLabel().equals("orbitalworks on Mars"),
            "Industry lines name the industry and the colony");
        check(CrisisIntel.industryShares(List.of(luna)).isEmpty(), "A colony without industries adds no industry line");

        System.out.println("PASS: " + checks + " crisis monthly checks");
    }

    private static float total(List<Share> shares) {
        float sum = 0f;
        for (Share s : shares) sum += s.getPoints();
        return sum;
    }

    private static void spec(String id, float cost, String downgrade) {
        specs.put(id, fake(IndustrySpecAPI.class, (name, args) -> switch (name) {
            case "getId" -> id;
            case "getCost" -> cost;
            case "getDowngrade" -> downgrade;
            default -> throw unexpected(name);
        }));
    }

    /** An industry; `building` and `upgrading` follow Industry.isBuilding (building or upgrading) and isUpgrading. */
    private static Industry industry(String id, boolean isIndustry, boolean building, boolean upgrading) {
        return fake(Industry.class, (name, args) -> switch (name) {
            case "isIndustry" -> isIndustry;
            case "isBuilding" -> building;
            case "isUpgrading" -> upgrading;
            case "getSpec" -> specs.get(id);
            case "getCurrentName" -> id;
            default -> throw unexpected(name);
        });
    }

    private static MarketAPI market(String marketName, int size, Industry... industries) {
        return fake(MarketAPI.class, (name, args) -> switch (name) {
            case "getName" -> marketName;
            case "getSize" -> size;
            case "getIndustries" -> new ArrayList<>(List.of(industries));
            default -> throw unexpected(name);
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
