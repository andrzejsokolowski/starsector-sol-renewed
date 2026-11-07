import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import solrenewed.SolMusic;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.zip.ZipFile;

/**
 * Checks which Sol and Alpha Centauri entities get the game's "keep the location music" flag, against
 * campaign API fakes, and that the local game still reads that flag; does not start or modify a game.
 */
public class SolMusicRegressionTest {
    private static int checks;
    private static boolean lunaLib;

    public static void main(String[] args) throws Exception {
        check(SolMusic.KEEP_KEY.equals(MusicPlayerPluginImpl.KEEP_PLAYING_LOCATION_MUSIC_DURING_ENCOUNTER_MEM_KEY),
            "The flag is the game's own keep-location-music key");
        Path core = Path.of(Global.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        try (ZipFile engine = new ZipFile(core.resolve("starfarer_obf.jar").toFile())) {
            byte[] campaignState = engine.getInputStream(engine.getEntry("com/fs/starfarer/campaign/CampaignState.class")).readAllBytes();
            check(new String(campaignState, "ISO-8859-1").contains("KEEP_PLAYING_LOCATION_MUSIC_DURING_ENCOUNTER_MEM_KEY"),
                "The game's campaign screen reads that key when a dialog opens");
        }

        Map<String, Object> planet = new HashMap<>();
        Map<String, Object> star = new HashMap<>();
        Map<String, Object> station = new HashMap<>();
        Map<String, Object> fleet = new HashMap<>();
        Map<String, Object> asteroid = new HashMap<>();
        Map<String, Object> jumpPoint = new HashMap<>();
        Map<String, Object> terrain = new HashMap<>();
        Map<String, Object> scored = new HashMap<>();
        scored.put(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "music_encounter_mysterious");
        Map<String, Object> flaggedByGame = new HashMap<>();
        flaggedByGame.put(SolMusic.KEEP_KEY, true);

        List<SectorEntityToken> entities = new ArrayList<>(List.of(
            entity(PlanetAPI.class, planet, false),
            entity(PlanetAPI.class, star, true),
            entity(CustomCampaignEntityAPI.class, station, false),
            entity(CampaignFleetAPI.class, fleet, false),
            entity(AsteroidAPI.class, asteroid, false),
            entity(JumpPointAPI.class, jumpPoint, false),
            entity(CampaignTerrainAPI.class, terrain, false),
            entity(CustomCampaignEntityAPI.class, scored, false),
            entity(PlanetAPI.class, flaggedByGame, false)));
        Map<String, Object> solMemory = new HashMap<>();
        solMemory.put(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "sr_music_sol");
        StarSystemAPI sol = system("Sol", solMemory, entities);
        StarSystemAPI other = system("Corvus", new HashMap<>(), new ArrayList<>());

        check(SolMusic.playsSolMusic(sol), "Sol plays the Sol track");
        check(!SolMusic.playsSolMusic(other), "A system with other music is left alone");
        check(!SolMusic.playsSolMusic(null), "No location is left alone");

        check(SolMusic.apply(sol, true) == 2, "Turning it on flags exactly the planet and the station");
        check(flagged(planet) && flagged(station), "Docking at a planet or station keeps the Sol track");
        check(!flagged(star) && !flagged(fleet) && !flagged(asteroid) && !flagged(jumpPoint) && !flagged(terrain),
            "Stars, fleets, asteroids, jump points and terrain are not touched");
        check(fleet.isEmpty(), "Fleet encounters keep their own music");
        check(!flagged(scored), "An entity with its own music keeps it");
        check(!flaggedByGame.containsKey(SolMusic.OURS_KEY), "A flag set by the game or another mod is not claimed");
        check(SolMusic.apply(sol, true) == 0, "A second pass changes nothing");

        entities.add(entity(CustomCampaignEntityAPI.class, new HashMap<>(), false));
        check(SolMusic.apply(sol, true) == 1, "Something new in Sol (a relay, a colony station) is flagged on the next pass");

        planet.put(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY_MISSION, "music_mission");
        check(SolMusic.apply(sol, true) == 1 && !planet.containsKey(SolMusic.KEEP_KEY),
            "A mission that gives a planet its own music gets its music back");
        planet.remove(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY_MISSION);
        SolMusic.apply(sol, true);

        check(SolMusic.apply(sol, false) == 3, "Turning it off removes the flags this mod set");
        check(!planet.containsKey(SolMusic.KEEP_KEY) && !planet.containsKey(SolMusic.OURS_KEY)
            && !station.containsKey(SolMusic.KEEP_KEY) && !station.containsKey(SolMusic.OURS_KEY),
            "No trace of this mod is left on those entities");
        check(Boolean.TRUE.equals(flaggedByGame.get(SolMusic.KEEP_KEY)), "Turning it off leaves the game's own flag alone");

        // The script: runs at once on load, then every two seconds, only in a system with the Sol track.
        final StarSystemAPI[] where = {other};
        CampaignFleetAPI player = fake(CampaignFleetAPI.class, (name, a) -> {
            if (name.equals("getContainingLocation")) return where[0];
            throw unexpected(name);
        });
        Global.setSector(fake(SectorAPI.class, (name, a) -> {
            if (name.equals("getPlayerFleet")) return player;
            throw unexpected(name);
        }));
        ModManagerAPI mods = fake(ModManagerAPI.class, (name, a) -> {
            if (name.equals("isModEnabled")) return lunaLib && "lunalib".equals(a[0]);
            throw unexpected(name);
        });
        Global.setSettings(fake(SettingsAPI.class, (name, a) -> {
            if (name.equals("getModManager")) return mods;
            throw unexpected(name);
        }));

        SolMusic.Keeper keeper = new SolMusic.Keeper();
        keeper.advance(0.016f);
        check(!flagged(planet), "Outside Sol the script changes nothing");
        where[0] = sol;
        keeper.advance(0.5f);
        check(!flagged(planet), "The script waits for its next two second pass");
        keeper.advance(1.6f);
        check(flagged(planet) && flagged(station), "Inside Sol the script flags the planets and stations");
        check(!keeper.runWhilePaused() && !keeper.isDone(), "The script runs for the whole session, never while paused");

        SolMusic.Keeper fresh = new SolMusic.Keeper();
        planet.clear();
        fresh.advance(0.016f);
        check(flagged(planet), "After a game load the first frame already flags Sol, before the player can dock");

        System.out.println("PASS: " + checks + " Sol music checks");
    }

    private static boolean flagged(Map<String, Object> memory) {
        return Boolean.TRUE.equals(memory.get(SolMusic.KEEP_KEY)) && Boolean.TRUE.equals(memory.get(SolMusic.OURS_KEY));
    }

    private static <T extends SectorEntityToken> SectorEntityToken entity(Class<T> type, Map<String, Object> values, boolean isStar) {
        MemoryAPI memory = memory(values);
        return fake(type, (name, a) -> switch (name) {
            case "getMemoryWithoutUpdate" -> memory;
            case "isStar" -> isStar;
            default -> throw unexpected(type.getSimpleName() + "." + name);
        });
    }

    private static StarSystemAPI system(String name, Map<String, Object> values, List<SectorEntityToken> entities) {
        MemoryAPI memory = memory(values);
        return fake(StarSystemAPI.class, (method, a) -> switch (method) {
            case "getMemoryWithoutUpdate" -> memory;
            case "getAllEntities" -> entities;
            case "getName" -> name;
            default -> throw unexpected("StarSystemAPI." + method);
        });
    }

    private static MemoryAPI memory(Map<String, Object> values) {
        return fake(MemoryAPI.class, (name, a) -> {
            switch (name) {
                case "getString": {
                    Object v = values.get((String) a[0]);
                    return v == null ? null : v.toString();
                }
                case "getBoolean": return Boolean.TRUE.equals(values.get((String) a[0]));
                case "contains": return values.containsKey((String) a[0]);
                case "set":
                    if (a.length != 2) throw unexpected("MemoryAPI.set with expiry");
                    values.put((String) a[0], a[1]);
                    return null;
                case "unset": values.remove((String) a[0]); return null;
                default: throw unexpected("MemoryAPI." + name);
            }
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
