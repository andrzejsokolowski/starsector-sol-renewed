import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import solrenewed.crisis.Deterrent;
import solrenewed.crisis.Deterrent.Boss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Checks the Deterrent mission rules (who the bosses are, how strong, which flagship, when it starts),
 * the planetkiller item row, and that the flagship variants and pictures it names exist on this machine.
 * Does not start or modify a game.
 */
public class DeterrentRegressionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Deterrent d = Deterrent.INSTANCE;
        check(Boss.values().length == 3, "Three bosses");
        check(Boss.PIRATES.getFactionId().equals(Factions.PIRATES) && Boss.PATH.getFactionId().equals(Factions.LUDDIC_PATH)
            && Boss.REMNANTS.getFactionId().equals(Factions.REMNANTS), "Pirates, the Luddic Path and the Remnants");
        check(Boss.PIRATES.getFleetName().equals("Sol Base Loot Armada") && Boss.PATH.getFleetName().equals("Unholy Relic Purge Fleet")
            && Boss.REMNANTS.getFleetName().equals("Origin Found"), "The fleets carry the agreed names");

        check("Endless_mk2_XIV_Doomship".equals(d.flagshipFor(Boss.PIRATES, id -> true)), "Moar XIV installed: the Endless leads the pirates");
        check(d.flagshipFor(Boss.PIRATES, id -> false) == null, "Without it, the fleet's own biggest ship leads");
        check("swp_cathedral_gra".equals(d.flagshipFor(Boss.PATH, id -> !id.equals("swp_cathedral_for"))),
            "The first Cathedral variant the game has is used");
        check(d.flagshipFor(Boss.REMNANTS, id -> id.startsWith("kyris_blinding")).startsWith("kyris_blinding"),
            "The Blinding leads the Remnants");

        check(d.points(Boss.PIRATES, 1f) == 260f && d.points(Boss.REMNANTS, 1f) == 200f, "260 fleet points (200 for Remnants) at strength 1");
        check(d.points(Boss.PATH, 2f) == 520f, "The strength setting scales them");
        check(d.points(Boss.PATH, 0.25f) == 130f && d.points(Boss.PATH, 9f) == 780f, "Held between half and three times");

        check(!d.shouldStart(0, true, false, false), "Not before the first Reckoning");
        check(d.shouldStart(1, true, false, false), "After the first Reckoning");
        check(d.shouldStart(4, true, false, false), "Also in saves long past it");
        check(!d.shouldStart(2, true, true, false), "Only once");
        check(!d.shouldStart(2, true, false, true), "Never after Sol holds all three");
        check(!d.shouldStart(2, false, false, false), "Never with the crisis switched off");

        Path root = Path.of("").toAbsolutePath();
        String item = Files.readAllLines(root.resolve("data/campaign/special_items.csv")).stream()
            .filter(l -> l.contains(",sr_planetkiller,")).findFirst().orElse("");
        check(!item.isEmpty(), "The planetkiller item exists");
        check(item.contains("no_sell") && item.contains("no_drop"), "It cannot be sold and never drops at random");
        check(item.contains(",3,1,"), "Three fit in one stack");

        // The game reads "order" as a number for every special item and stops at start on an empty one.
        List<List<String>> rows = csv(Files.readString(root.resolve("data/campaign/special_items.csv")));
        int order = rows.get(0).indexOf("order");
        check(order >= 0, "special_items.csv has an order column");
        for (List<String> row : rows.subList(1, rows.size())) {
            String id = row.size() > 1 ? row.get(1).trim() : "";
            if (id.isEmpty() || row.get(0).trim().startsWith("#")) continue;
            String value = row.size() > order ? row.get(order).trim() : "";
            boolean number;
            try {
                Double.parseDouble(value);
                number = true;
            } catch (NumberFormatException e) {
                number = false;
            }
            check(number, "Special item " + id + " has a number in its order column (has \"" + value + "\")");
        }

        Path core = Path.of(Global.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        check(Files.exists(core.resolve("graphics/icons/cargo/planetkiller.png")), "Its picture is the game's own planetkiller icon");
        for (String n : List.of("start", "foothold", "raid", "assault", "reckoning")) {
            check(Files.exists(root.resolve("graphics/icons/crisis/stage_" + n + ".png")), "Stage picture " + n + " is packaged");
        }

        Path mods = core.getParent().resolve("mods");
        int found = 0;
        for (Boss boss : Boss.values()) {
            for (String variant : boss.getFlagships()) {
                if (variantExists(mods, variant)) found++;
            }
        }
        check(found == 7, "All seven flagship variants exist in the local mods folder (found " + found + ")");

        System.out.println("PASS: " + checks + " deterrent checks");
    }

    private static boolean variantExists(Path mods, String id) throws IOException {
        if (!Files.isDirectory(mods)) return false;
        try (Stream<Path> files = Files.walk(mods, 6)) {
            return files.anyMatch(p -> p.getFileName().toString().equals(id + ".variant"));
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    /** Splits CSV text into rows of fields; quoted fields may hold commas, quotes and line breaks. */
    private static List<List<String>> csv(String text) {
        List<List<String>> rows = new java.util.ArrayList<>();
        List<String> row = new java.util.ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new java.util.ArrayList<>();
            } else field.append(c);
        }
        if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }
}
