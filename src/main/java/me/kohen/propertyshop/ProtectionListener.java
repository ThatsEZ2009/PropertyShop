package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {
    private final PropertyShop plugin;

    public ProtectionListener(PropertyShop plugin) { this.plugin = plugin; }

    private boolean isDisplay(EntityType t) {
        return t == EntityType.ITEM_FRAME || t == EntityType.GLOW_ITEM_FRAME
                || t == EntityType.PAINTING || t == EntityType.ARMOR_STAND;
    }

    /** True if loc is in an active plot the player isn't allowed to mess with. */
    private boolean blocked(Player p, Location loc) {
        Property prop = plugin.activeAt(loc.getChunk());
        return prop != null && !plugin.canBypass(p, prop);
    }

    private boolean inPlot(Location loc) {
        return plugin.activeAt(loc.getChunk()) != null;
    }

    private void deny(Player p) {
        p.sendActionBar(Component.text("You can't do that here - this property isn't yours.", NamedTextColor.RED));
    }

    // ---------------- blocks ----------------
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !plugin.isProtectedBlock(b.getType())) return;
        if (blocked(e.getPlayer(), b.getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    // ---------------- item frames / paintings / armor stands ----------------
    @EventHandler(ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && blocked(p, e.getEntity().getLocation())) {
            e.setCancelled(true); deny(p);
        } else if (!(e.getRemover() instanceof Player) && inPlot(e.getEntity().getLocation())) {
            e.setCancelled(true); // mobs/other can't break frames in a plot
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                || e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) {
            if (inPlot(e.getEntity().getLocation())) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (!isDisplay(ent.getType())) return;
        if (blocked(e.getPlayer(), ent.getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (blocked(e.getPlayer(), e.getRightClicked().getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!isDisplay(e.getEntity().getType())) return;
        if (e.getDamager() instanceof Player p && blocked(p, e.getEntity().getLocation())) {
            e.setCancelled(true); deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!isDisplay(e.getEntity().getType())) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if ((c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                && inPlot(e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ---------------- explosions (protect plot blocks from outside blasts) ----------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.activeAt(b.getChunk()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.activeAt(b.getChunk()) != null);
    }
}
