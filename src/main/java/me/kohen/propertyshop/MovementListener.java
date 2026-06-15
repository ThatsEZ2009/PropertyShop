package me.kohen.propertyshop;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {
    private final PropertyShop plugin;

    public MovementListener(PropertyShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        // Only react when the player crosses into a different chunk.
        if (from.getWorld() == to.getWorld()
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }

        Property prop = plugin.getManager().getAt(to.getChunk());
        if (prop == null) return;

        Player p = e.getPlayer();
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
