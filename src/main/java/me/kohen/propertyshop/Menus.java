package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the chest GUIs. Each clickable item carries an "action" string in its data. */
public class Menus {
    private final PropertyShop plugin;

    public Menus(PropertyShop plugin) { this.plugin = plugin; }

    // ---------- main list ----------
    public void openMain(Player player) {
        List<Property> all = plugin.getManager().all();
        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Properties"));
        holder.setInventory(inv);

        int slot = 0;
        for (Property p : all) {
            if (slot >= 45) break;
            List<String> lore = new ArrayList<>();
            lore.add("§7Chunks: §f" + p.getChunks().size());
            lore.add("§7Price: §f" + p.priceString());
            lore.add(p.isOwned() ? "§7Owner: §e" + p.getOwnerName() : "§aFOR SALE");
            lore.add("§8Click to open");
            inv.setItem(slot++, button(p.isOwned() ? Material.IRON_BLOCK : Material.GRASS_BLOCK,
                    "§f" + p.getName(), "open:" + p.getName(), lore));
        }

        if (player.hasPermission("propertyshop.admin")) {
            inv.setItem(49, button(Material.EMERALD_BLOCK, "§aNew property from selection", "create",
                    List.of("§7Uses your wand selection", "§7(or the chunk you're standing in)")));
        }
        inv.setItem(53, button(Material.BARRIER, "§cClose", "close", List.of()));
        player.openInventory(inv);
    }

    // ---------- one property ----------
    public void openPanel(Player player, Property p) {
        // Owned plots are private: only the owner, their trusted players, or an admin may open them.
        // For-sale plots stay open so anyone can buy.
        if (p.isOwned()
                && !player.hasPermission("propertyshop.admin")
                && !p.isOwnedBy(player.getUniqueId())
                && !p.isTrusted(player.getUniqueId())) {
            player.sendMessage("§cThat property is owned by " + p.getOwnerName() + " - you can't open it.");
            return;
        }
        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.PANEL, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Property: " + p.getName()));
        holder.setInventory(inv);
        boolean admin = player.hasPermission("propertyshop.admin");
        boolean ownerOrAdmin = admin || p.isOwnedBy(player.getUniqueId());

        List<String> head = new ArrayList<>();
        head.add("§7Chunks: §f" + p.getChunks().size());
        head.add("§7Price: §f" + p.priceString());
        head.add(p.isOwned() ? "§7Owner: §e" + p.getOwnerName() : "§aFOR SALE");
        inv.setItem(4, button(Material.PAPER, "§f" + p.getName(), "none", head));

        inv.setItem(10, button(Material.ENDER_EYE, "§bPreview borders", "preview",
                List.of("§7Light up this property's chunks")));

        if (!p.isOwned()) {
            inv.setItem(12, button(Material.EMERALD, "§aBuy this property", "buy",
                    List.of("§7Cost: §f" + p.priceString(), "§7Pays with items in your inventory")));
        }
        if (ownerOrAdmin) {
            inv.setItem(14, button(Material.NAME_TAG, "§eTrusted players", "trust",
                    List.of("§7Let friends build & use chests here")));
        }
        if (admin) {
            inv.setItem(11, button(Material.GOLD_INGOT, "§6Set price", "setprice",
                    List.of("§7Drop the items you want to charge")));
            inv.setItem(15, button(Material.LEVER, "§eMake available again", "unclaim",
                    List.of("§7Clears the owner; back up for sale")));
            inv.setItem(16, button(Material.RED_WOOL, "§cDelete property", "delete",
                    List.of("§7§lShift-click§7 to confirm")));
        }
        inv.setItem(22, button(Material.ARROW, "§7Back to list", "main", List.of()));
        player.openInventory(inv);
    }

    // ---------- price editor (drop items in) ----------
    public void openPriceEditor(Player player, Property p) {
        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.PRICE_EDITOR, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Price for " + p.getName()));
        holder.setInventory(inv);
        player.openInventory(inv);
        player.sendMessage("§6Put the items you want to charge into this window, then close it.");
        player.sendMessage("§7Example: 10 diamonds + 1 elytra. Your items are given back — they just set the price.");
    }

    // ---------- trust online players ----------
    public void openTrust(Player player, Property p) {
        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.TRUST, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Trust: " + p.getName()));
        holder.setInventory(inv);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            if (p.isOwnedBy(online.getUniqueId())) continue; // owner already has access
            boolean trusted = p.isTrusted(online.getUniqueId());
            inv.setItem(slot++, button(Material.NAME_TAG, "§f" + online.getName(),
                    "trust:" + online.getUniqueId(),
                    List.of(trusted ? "§aTrusted — click to remove" : "§7Not trusted — click to add")));
        }
        if (slot == 0) {
            inv.setItem(22, button(Material.BARRIER, "§7No other players online", "none", List.of()));
        }
        inv.setItem(53, button(Material.ARROW, "§7Back", "panel", List.of()));
        player.openInventory(inv);
    }

    // ---------- helpers ----------
    public ItemStack button(Material m, String name, String action, List<String> lore) {
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(legacy(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(legacy(s));
            meta.lore(l);
        }
        meta.getPersistentDataContainer().set(plugin.getActionKey(), PersistentDataType.STRING, action);
        is.setItemMeta(meta);
        return is;
    }

    public String actionOf(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(plugin.getActionKey(), PersistentDataType.STRING);
    }

    private static Component legacy(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
