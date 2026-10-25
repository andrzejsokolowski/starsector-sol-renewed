import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import solrenewed.CryoPrison;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

/**
 * Picks cryo prison inmate portraits against campaign API fakes and checks the fallback portrait against
 * the local game files; does not start or modify a game.
 */
public class CryoPrisonPortraitRegressionTest {
    private static final String FALLBACK = "graphics/portraits/portrait_generic_grayscale.png";
    private static int checks;
    private static WeightedRandomPicker<String> portraits;

    public static void main(String[] args) throws Exception {
        // Any settings lookup fails, like a sprite key missing from settings.json.
        Global.setSettings(fake(SettingsAPI.class, (name, a) -> {
            throw new RuntimeException("Texture [generic] from category [characters] not found");
        }));
        FactionAPI independent = fake(FactionAPI.class, (name, a) -> {
            if (name.equals("getPortraits")) return portraits;
            throw unexpected(name);
        });
        Global.setSector(fake(SectorAPI.class, (name, a) -> {
            if (name.equals("getFaction") && "independent".equals(a[0])) return independent;
            throw unexpected(name);
        }));

        portraits = new WeightedRandomPicker<>();
        portraits.add("graphics/portraits/portrait_mercenary01.png");
        check(pick(Gender.MALE).equals("graphics/portraits/portrait_mercenary01.png"),
            "An inmate gets an independent portrait when the faction has one");

        portraits = new WeightedRandomPicker<>();
        check(pick(Gender.FEMALE).equals(FALLBACK),
            "An inmate gets the default portrait when the independents have none, without a settings lookup");

        portraits = null;
        check(pick(Gender.MALE).equals(FALLBACK), "An inmate gets the default portrait when the faction returns no list");

        Path core = Path.of(Global.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        check(Files.isRegularFile(core.resolve(FALLBACK)), "The default portrait file exists in the base game");
        check(Files.readString(core.resolve("data/config/settings.json")).contains("\"default_portrait\":\"" + FALLBACK + "\""),
            "The base game registers that file as its default portrait, so it is always loaded");

        System.out.println("PASS: " + checks + " cryo prison portrait checks");
    }

    private static String pick(Gender gender) throws Exception {
        Method m = CryoPrison.class.getDeclaredMethod("pickPortrait", Gender.class);
        m.setAccessible(true);
        return (String) m.invoke(CryoPrison.INSTANCE, gender);
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
