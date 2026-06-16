package me.kohen.propertyshop;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MovementListener implements Listener {
    private final PropertyShop plugin;

    public MovementListener(PropertyShop plugin) { this.plugin = plugin; }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getWorld() == to.getWorld()
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return; // same chunk - nothing to do
        }
        update(e.getPlayer(), to);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        update(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getBorders().clear(e.getPlayer());
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) update(e.getPlayer(), e.getPlayer().getLocation());
        }, 40L);
    }

    private void update(Player p, Location to) {
        Property prop = plugin.getManager().getAt(to.getChunk());
        boolean canSee = prop != null
                && (prop.isOwnedBy(p.getUniqueId()) || prop.isTrusted(p.getUniqueId()));

        // Border: show for the owner/trusted inside their plot; clear otherwise.
        String shownNow = plugin.getBorders().shownProperty(p);
        if (canSee) {
            if (!prop.getName().equals(shownNow)) {
                plugin.getBorders().clear(p);
                plugin.getBorders().show(p, prop);
            }
        } else if (shownNow != null) {
            plugin.getBorders().clear(p);
        }

        // Action bar: only for people who DON'T get the border (so it doesn't fight KohenMMO).
        if (plugin.getConfig().getBoolean("action-bar.enabled", true) && prop != null && !canSee) {
            String raw;
            if (prop.isOwned()) {
                raw = plugin.getConfig().getString("action-bar.owned", "&aProperty: &f{name} &7- Owner: &e{owner}")
                        .replace("{name}", prop.getName())
                        .replace("{owner}", prop.getOwnerName() == null ? "Unknown" : prop.getOwnerName());
            } else {
                raw = plugin.getConfig().getString("action-bar.for-sale", "&6Property: &f{name} &7- FOR SALE! /property buy - {price}")
                        .replace("{name}", prop.getName())
                        .replace("{price}", prop.priceString());
            }
            p.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        }
    }
}
