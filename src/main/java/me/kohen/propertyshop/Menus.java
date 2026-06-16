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
        boolean admin = player.hasPermission("propertyshop.admin");
        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Properties"));
        holder.setInventory(inv);

        int slot = 0;
        for (Property p : plugin.getManager().all()) {
            if (slot >= 45) break;
            boolean forSale = p.isActive() && !p.isOwned();
            if (!admin && !forSale) continue; // players only see buyable plots
            List<String> lore = new ArrayList<>();
            lore.add("§7Chunks: §f" + p.getChunks().size());
            Material icon;
            if (!p.isActive()) { icon = Material.GRAY_CONCRETE; lore.add("§8Draft - no price set"); }
            else if (p.isOwned()) { icon = Material.IRON_BLOCK; lore.add("§7Owner: §e" + p.getOwnerName()); }
            else { icon = Material.LIME_CONCRETE; lore.add("§aFOR SALE §7- " + p.priceString()); }
            lore.add("§8Click to open");
            inv.setItem(slot++, button(icon, "§f" + p.getName(), "open:" + p.getName(), lore));
        }

        if (admin) {
            inv.setItem(49, button(Material.EMERALD_BLOCK, "§aNew property from selection", "create",
                    List.of("§7Uses your wand selection", "§7(or the chunk you're standing in)")));
        }
        inv.setItem(53, button(Material.BARRIER, "§cClose", "close", List.of()));
        player.openInventory(inv);
    }

    // ---------- one property ----------
    public void openPanel(Player player, Property p) {
        boolean admin = player.hasPermission("propertyshop.admin");
        boolean owner = p.isOwnedBy(player.getUniqueId());
        boolean trusted = p.isTrusted(player.getUniqueId());

        // Drafts (no price) don't exist for players; owned plots are private.
        if (!p.isActive() && !admin) { player.sendMessage("§cThat property isn't available."); return; }
        if (p.isOwned() && !admin && !owner && !trusted) {
            player.sendMessage("§cThat property is owned by " + p.getOwnerName() + " - you can't open it.");
            return;
        }

        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.PANEL, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Property: " + p.getName()));
        holder.setInventory(inv);

        List<String> head = new ArrayList<>();
        head.add("§7Chunks: §f" + p.getChunks().size());
        if (!p.isActive()) head.add("§8Draft - set a price to list it");
        else if (p.isOwned()) head.add("§7Owner: §e" + p.getOwnerName());
        else head.add("§aFOR SALE §7- " + p.priceString());
        inv.setItem(4, button(Material.PAPER, "§f" + p.getName(), "none", head));

        // Build only the buttons that apply, then center them so there's never a gap.
        List<ItemStack> btns = new ArrayList<>();
        btns.add(button(Material.ENDER_EYE, "§bPreview borders", "preview", List.of("§7Light up this property")));
        if (p.isActive() && !p.isOwned())
            btns.add(button(Material.EMERALD, "§aBuy this property", "buy",
                    List.of("§7Cost: §f" + p.priceString(), "§7Pays with items in your inventory")));
        if (admin)
            btns.add(button(Material.GOLD_INGOT, "§6Set price", "setprice", List.of("§7Drop the items to charge")));
        if (p.isOwned() && (admin || owner))
            btns.add(button(Material.NAME_TAG, "§eTrusted players", "trust", List.of("§7Let friends build & use chests")));
        if (admin || owner) {
            btns.add(button(Material.OAK_SIGN, "§bSet plot title", "settitle", List.of("§7The big text shown on entry")));
            btns.add(button(Material.WRITABLE_BOOK, "§bSet description", "setdesc", List.of("§7One line under the title")));
        }
        if (admin && p.isOwned())
            btns.add(button(Material.LEVER, "§eMake available again", "unclaim", List.of("§7Clears the owner")));
        if (admin)
            btns.add(button(Material.RED_WOOL, "§cDelete property", "delete", List.of("§7§lShift-click§7 to confirm")));

        int n = btns.size();
        int start = 9 + Math.max(0, (9 - n) / 2);
        for (int i = 0; i < n && start + i <= 17; i++) inv.setItem(start + i, btns.get(i));

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

    // ---------- text input (anvil) ----------
    public void openTextInput(Player player, Property p, boolean title) {
        PropertyHolder holder = new PropertyHolder(
                title ? PropertyHolder.Type.TITLE_INPUT : PropertyHolder.Type.DESC_INPUT, p.getName());
        Inventory inv = Bukkit.createInventory(holder, org.bukkit.event.inventory.InventoryType.ANVIL,
                Component.text(title ? "Set plot title" : "Set description"));
        holder.setInventory(inv);
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        String cur = title ? p.getTitleText() : (p.getDescription() == null ? "" : p.getDescription());
        meta.displayName(legacy(cur.isEmpty() ? "type here" : cur));
        paper.setItemMeta(meta);
        inv.setItem(0, paper);
        player.openInventory(inv);
        player.sendMessage("§7Type the " + (title ? "title" : "description")
                + " in the box, then click the result on the right to save. One line only.");
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
