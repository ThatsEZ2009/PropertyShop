package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class WandListener implements Listener {
    private final PropertyShop plugin;

    public WandListener(PropertyShop plugin) { this.plugin = plugin; }

    private boolean hasTag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Only an admin holding the tagged wand triggers selection. */
    private boolean usingWand(Player p, ItemStack item) {
        return hasTag(item, plugin.getWandKey()) && p.hasPermission("propertyshop.admin");
    }

    // Creative left-click (instant break) adds the chunk.
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!usingWand(p, p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        addChunk(p, e.getBlock());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (usingWand(p, item)) {
            Block b = e.getClickedBlock();
            if (b == null) return;
            e.setCancelled(true);
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) addChunk(p, b);
            else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) removeChunk(p, b);
            return;
        }

        // Right-click a placed control barrel -> open the panel for the property it sits in.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            BlockState st = e.getClickedBlock().getState();
            if (st instanceof TileState ts
                    && ts.getPersistentDataContainer().has(plugin.getPanelKey(), PersistentDataType.BYTE)) {
                e.setCancelled(true);
                Property prop = plugin.getManager().getAt(e.getClickedBlock().getChunk());
                if (prop != null) plugin.getMenus().openPanel(p, prop);
                else p.sendMessage(ChatColor.YELLOW + "This panel isn't inside a property yet.");
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!hasTag(e.getItemInHand(), plugin.getPanelKey())) return;
        BlockState st = e.getBlockPlaced().getState();
        if (st instanceof TileState ts) {
            ts.getPersistentDataContainer().set(plugin.getPanelKey(), PersistentDataType.BYTE, (byte) 1);
            ts.update();
            e.getPlayer().sendMessage(ChatColor.GREEN
                    + "Control barrel placed - right-click it to open the property menu.");
        }
    }

    private void addChunk(Player p, Block b) {
        int total = plugin.getSelection().add(p, b.getChunk());
        if (total < 0) {
            p.sendActionBar(Component.text("Selection is full (max chunks reached).", NamedTextColor.RED));
            return;
        }
        p.sendActionBar(Component.text("Added chunk " + b.getChunk().getX() + "," + b.getChunk().getZ()
                + "  (" + total + " selected)", NamedTextColor.GREEN));
    }

    private void removeChunk(Player p, Block b) {
        int total = plugin.getSelection().remove(p, b.getChunk());
        p.sendActionBar(Component.text("Removed chunk " + b.getChunk().getX() + "," + b.getChunk().getZ()
                + "  (" + total + " selected)", NamedTextColor.YELLOW));
    }
}
