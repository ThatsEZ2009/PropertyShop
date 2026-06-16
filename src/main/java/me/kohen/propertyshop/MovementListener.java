package me.kohen.propertyshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Rings and the for-sale action bar are driven by the border task; this just tidies up on quit. */
public class MovementListener implements Listener {
    private final PropertyShop plugin;

    public MovementListener(PropertyShop plugin) { this.plugin = plugin; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getBorders().forget(p.getUniqueId());
        plugin.getWandMap().clear(p);
        plugin.textInput.remove(p.getUniqueId());
    }
}
