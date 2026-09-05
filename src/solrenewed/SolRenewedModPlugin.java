package solrenewed;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.apache.log4j.Logger;

/**
 * Entry point. Sol and the Centaurus constellation are generated once, when a new game is created
 * (after the sector's own procgen so we can find a free spot on the map). Everything that must
 * survive a save/load (the cryo-prison dialog hook, the meteor-shower listener) is re-registered on
 * every game load instead of being written into the save.
 */
public class SolRenewedModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "sol_renewed";

    private static final Logger log = Global.getLogger(SolRenewedModPlugin.class);

    @Override
    public void onNewGameAfterProcGen() {
        SrSettings.reload();
        if (!SrSettings.INSTANCE.getSpawnSol()) {
            log.info("[SolRenewed] Sol generation disabled in settings.");
            return;
        }
        try {
            SolSystem.INSTANCE.generate(Global.getSector());
        } catch (Throwable t) {
            log.error("[SolRenewed] Sol generation failed", t);
        }
        if (SrSettings.INSTANCE.getSpawnCentauri()) {
            try {
                Centauri.INSTANCE.generate(Global.getSector());
            } catch (Throwable t) {
                log.error("[SolRenewed] Centaurus generation failed", t);
            }
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        StarSystemAPI sol = SolSystem.INSTANCE.find();
        if (sol == null) return;
        SrSettings.reload();
        CryoPrison.INSTANCE.registerDialogHook();
        if (ModCheck.INSTANCE.getHasIndEvo() && SrSettings.INSTANCE.getMeteors()) {
            MeteorShowers.INSTANCE.install(sol);
        }
    }
}
