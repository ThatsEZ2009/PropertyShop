package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.AbstractVillager;
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
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {
    private final PropertyShop plugin;

    public ProtectionListener(PropertyShop plugin) { this.plugin = plugin; }

    private boolean isDisplay(EntityType t) {
        return t == EntityType.ITEM_FRAME || t == EntityType.GLOW_ITEM_FRAME
                || t == EntityType.PAINTING || t == EntityType.ARMOR_STAND;
    }

    /** A non-monster mob (animal, villager, tameable pet) that outsiders shouldn't be able to hurt. */
    private boolean isProtectedCreature(Entity e) {
        if (e instanceof Monster) return false;          // zombies/creepers/etc -> still fightable
        if (e instanceof Player) return false;           // players handled by PvP rule
        return e instanceof Animals                      // cows, sheep, horses, wolves, cats...
                || e instanceof Tameable
                || e instanceof AbstractVillager             // villagers + wandering traders
                || e instanceof WanderingTrader;
    }

    /** Resolve the actual player behind damage, including arrows/snowballs they shot. */
    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
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

    // ---------------- buckets (water/lava/fill) ----------------
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Block b = e.getBlockClicked().getRelative(e.getBlockFace());
        if (blocked(e.getPlayer(), b.getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (blocked(e.getPlayer(), e.getBlockClicked().getLocation())) { e.setCancelled(true); deny(e.getPlayer()); }
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

    // ---------------- damage: displays, animals/villagers, and PvP ----------------
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Location loc = victim.getLocation();
        Property prop = plugin.activeAt(loc.getChunk());

        // (a) displays: frames / paintings / armor stands
        if (isDisplay(victim.getType())) {
            Player p = damagingPlayer(e.getDamager());
            if (p != null && prop != null && !plugin.canBypass(p, prop)) { e.setCancelled(true); deny(p); }
            return;
        }

        // (b) PvP toggle: player vs player inside a plot
        if (victim instanceof Player) {
            Player attacker = damagingPlayer(e.getDamager());
            if (attacker == null || attacker.equals(victim)) return;
            if (prop != null && !prop.isPvp() && !attacker.hasPermission("propertyshop.admin")) {
                e.setCancelled(true);
                attacker.sendActionBar(Component.text("PvP is off on this plot.", NamedTextColor.RED));
            }
            return;
        }

        // (c) animals / villagers / pets: outsiders can't hurt them on a plot
        if (prop != null && isProtectedCreature(victim)) {
            Player p = damagingPlayer(e.getDamager());
            if (p != null && !plugin.canBypass(p, prop)) { e.setCancelled(true); deny(p); }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        EntityDamageEvent.DamageCause c = e.getCause();
        boolean explosion = (c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION);

        // Protect displays from explosions on a plot (existing behaviour)
        if (isDisplay(victim.getType()) && explosion && inPlot(victim.getLocation())) {
            e.setCancelled(true);
            return;
        }
        // Protect animals/villagers from explosion damage on a plot too
        if (explosion && isProtectedCreature(victim) && inPlot(victim.getLocation())) {
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
