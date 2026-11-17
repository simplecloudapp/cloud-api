package app.simplecloud.api.provider.paper;

import app.simplecloud.api.internal.integration.adventure.AdventureIntegration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final AdventureIntegration adventureIntegration;

    public PlayerConnectionListener(AdventureIntegration adventureIntegration) {
        this.adventureIntegration = adventureIntegration;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        adventureIntegration.forgetPlayer(event.getPlayer().getUniqueId());
    }
}
