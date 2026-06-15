package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {
    private final PropertyShop plugin;

    public ProtectionListener(PropertyShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // HIGH so other plugins (like DisplayShops) open their own GUI first; we only
    // stop the leftover vanilla container/door/lever action for outsiders.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (!plugin.isProtectedBlock(b.getType())) return; // not a private block -> allow (shops etc.)
        if (blocked(e.getPlayer(), b)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    /** True if this block sits in a property the player isn't allowed to touch. */
    private boolean blocked(Player p, Block b) {
        Property prop = plugin.getManager().getAt(b.getChunk());
        if (prop == null) return false;
        return !plugin.canBypass(p, prop);
    }

    private void deny(Player p) {
        p.sendActionBar(Component.text("You can't do that here - this property isn't yours.",
                NamedTextColor.RED));
    }
}
