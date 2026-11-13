package app.simplecloud.api.platform.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * The mod's entry class. The mod only ships the SimpleCloud API for other mods, so this
 * class does nothing.
 */
@Mod(value = NeoForgeApiProvider.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider {

    public static final String MOD_ID = "simplecloud_api";
}
