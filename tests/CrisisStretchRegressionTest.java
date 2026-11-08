import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import solrenewed.crisis.CrisisIntel;
import solrenewed.crisis.CrisisIntel.Stage;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Runs the crisis meter's stretch after a Reckoning on the game's own event meter (BaseEventIntel) with the
 * crisis' stages, against campaign API fakes: how long the meter gets, where the marks go, and that the drop
 * after a Reckoning never starts an attack. Does not start or modify a game.
 */
public class CrisisStretchRegressionTest {
    private static int checks;

    /** The crisis' meter without the waves: records every stage the game fires. */
    static class Meter extends BaseEventIntel {
        final List<Object> fired = new ArrayList<>();
        /** Stretch on reaching the Reckoning, the way a Reckoning that finds no attackers ends at once. */
        float stretchOnReckoning = 0f;

        Meter() {
            setMaxProgress(CrisisIntel.MAX_PROGRESS);
            addStage(Stage.START, 0);
            addStage(Stage.FOOTHOLD, CrisisIntel.P_FOOTHOLD, true, StageIconSize.MEDIUM);
            addStage(Stage.RAID, CrisisIntel.P_RAID, true, StageIconSize.MEDIUM);
            addStage(Stage.ASSAULT, CrisisIntel.P_ASSAULT, true, StageIconSize.LARGE);
            addStage(Stage.RECKONING, CrisisIntel.P_RECKONING, true, StageIconSize.LARGE);
        }

        @Override
        protected void notifyStageReached(EventStageData stage) {
            fired.add(stage.id);
            if (stage.id == Stage.RECKONING && stretchOnReckoning > 0f) {
                CrisisIntel.stretchAndDrop(this, stretchOnReckoning);
            }
        }

        int mark(Stage stage) {
            return getDataFor(stage).progress;
        }
    }

    public static void main(String[] args) {
        ListenerManagerAPI listeners = fake(ListenerManagerAPI.class, (name, a) -> {
            if (name.equals("addListener")) return null;
            throw unexpected(name);
        });
        Global.setSector(fake(SectorAPI.class, (name, a) -> switch (name) {
            case "addScript" -> null;
            case "getListenerManager" -> listeners;
            default -> throw unexpected(name);
        }));

        check(CrisisIntel.lengthFor(0, 2f) == 1f, "Before any Reckoning the meter is 1000 long");
        check(CrisisIntel.lengthFor(1, 2f) == 2f && CrisisIntel.lengthFor(3, 2f) == 8f,
            "Every Reckoning doubles the meter at the default setting");
        check(CrisisIntel.lengthFor(2, 1.5f) == 2.25f, "A setting of 1.5 makes it 1.5 times longer each time");
        check(CrisisIntel.lengthFor(5, 1f) == 1f, "A setting of 1 keeps the old fixed marks");
        check(CrisisIntel.lengthFor(1, 10f) == 4f && CrisisIntel.lengthFor(1, 0.5f) == 1f,
            "The setting is held between 1 and 4");
        check(CrisisIntel.lengthFor(40, 2f) == CrisisIntel.MAX_LENGTH, "The meter stops growing at 64 times its base");

        Meter m = new Meter();
        m.setProgress(1000);
        check(m.fired.equals(List.of(Stage.FOOTHOLD, Stage.RAID, Stage.ASSAULT, Stage.RECKONING)),
            "The first climb fires every stage once");

        CrisisIntel.stretchAndDrop(m, 2f);
        check(m.getMaxProgress() == 2000 && m.getProgress() == 900, "After the first Reckoning: 900 of 2000");
        check(m.mark(Stage.FOOTHOLD) == 400 && m.mark(Stage.RAID) == 900 && m.mark(Stage.ASSAULT) == 1400
            && m.mark(Stage.RECKONING) == 2000, "Every mark moved to twice its points");
        check(m.fired.size() == 4, "The drop after a Reckoning starts no attack");

        m.setProgress(1399);
        check(m.fired.size() == 4, "No attack before the new Assault mark");
        m.setProgress(1400);
        check(m.fired.size() == 5 && m.fired.get(4) == Stage.ASSAULT, "The Assault comes at its new mark, 1400");
        m.setProgress(2000);
        check(m.fired.size() == 6 && m.fired.get(5) == Stage.RECKONING, "The Reckoning comes at its new mark, 2000");

        CrisisIntel.stretchAndDrop(m, 8f);
        check(m.getMaxProgress() == 8000 && m.getProgress() == 3600 && m.fired.size() == 6,
            "A big jump (an old save catching up) drops to 3600 of 8000 and starts no attack");
        m.setProgress(5599);
        check(m.fired.size() == 6, "No Raid or Foothold again on the way up");
        m.setProgress(8000);
        check(m.fired.subList(6, m.fired.size()).equals(List.of(Stage.ASSAULT, Stage.RECKONING)),
            "Only the Assault and the Reckoning come again, at 5600 and 8000");

        CrisisIntel.stretchAndDrop(m, 1f);
        check(m.getMaxProgress() == 1000 && m.getProgress() == 450 && m.fired.size() == 8,
            "Turning the growth down to 1 puts the old marks back without an attack");

        Meter quadruple = new Meter();
        quadruple.setProgress(1000);
        CrisisIntel.stretchAndDrop(quadruple, 4f);
        check(quadruple.getProgress() == 1800 && quadruple.getMaxProgress() == 4000 && quadruple.fired.size() == 4,
            "At four times the length the Raid mark is far past the old top, and still no Raid fires");

        Meter instant = new Meter();
        instant.stretchOnReckoning = 2f;
        instant.setProgress(1000);
        check(instant.getProgress() == 900 && instant.getMaxProgress() == 2000 && instant.fired.size() == 4,
            "A Reckoning that ends at once (no attackers found) stretches the meter the same way");
        instant.stretchOnReckoning = 4f;
        instant.setProgress(2000);
        check(instant.fired.subList(4, instant.fired.size()).equals(List.of(Stage.ASSAULT, Stage.RECKONING))
            && instant.getProgress() == 1800 && instant.getMaxProgress() == 4000,
            "The next cycle fires Assault and Reckoning once each and stretches again");

        System.out.println("PASS: " + checks + " crisis stretch checks");
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
