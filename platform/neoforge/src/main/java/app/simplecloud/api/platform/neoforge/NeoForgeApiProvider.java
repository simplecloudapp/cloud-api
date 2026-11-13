package app.simplecloud.api.platform.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * Loads the SimpleCloud API classes for other mods; it does no work of its own.
 */
@Mod(value = NeoForgeApiProvider.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeApiProvider {

    public static final String MOD_ID = "simplecloud_api";
}
