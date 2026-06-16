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
    private static final int[] PANEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private Material matOr(String name, Material def) {
        if (name == null || name.isEmpty()) return def;
        Material m = Material.matchMaterial(name);
        return (m == null || !m.isBlock()) ? def : m;
    }

    public void openPanel(Player player, Property p) {
        boolean admin = player.hasPermission("propertyshop.admin");
        boolean owner = p.isOwnedBy(player.getUniqueId());
        boolean trusted = p.isTrusted(player.getUniqueId());

        if (!p.isActive() && !admin) { player.sendMessage("§cThat property isn't available."); return; }
        if (p.isOwned() && !admin && !owner && !trusted) {
            player.sendMessage("§cThat property is owned by " + p.getOwnerName() + " - you can't open it.");
            return;
        }

        PropertyHolder holder = new PropertyHolder(PropertyHolder.Type.PANEL, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Property: " + p.getName()));
        holder.setInventory(inv);

        List<String> head = new ArrayList<>();
        head.add("§7Chunks: §f" + p.getChunks().size());
        if (!p.isActive()) head.add("§8Draft - set a price to list it");
        else if (p.isOwned()) head.add("§7Owner: §e" + p.getOwnerName());
        else head.add("§aFOR SALE §7- " + p.priceString());
        inv.setItem(4, button(Material.PAPER, "§f" + p.getName(), "none", head));

        boolean manage = p.isOwned() && (admin || owner);
        List<ItemStack> btns = new ArrayList<>();
        btns.add(button(Material.ENDER_EYE, "§bPreview borders", "preview", List.of("§7Light up this property")));
        if (p.isActive() && !p.isOwned())
            btns.add(button(Material.EMERALD, "§aBuy this property", "buy",
                    List.of("§7Cost: §f" + p.priceString(), "§7Pays with items in your inventory")));
        if (admin)
            btns.add(button(Material.GOLD_INGOT, "§6Set price", "setprice", List.of("§7Drop the items to charge")));
        if (manage)
            btns.add(button(Material.NAME_TAG, "§eTrusted players", "trust", List.of("§7Let friends build & use chests")));
        if (admin || owner) {
            btns.add(button(Material.OAK_SIGN, "§bSet plot title", "settitle", List.of("§7The big text shown on entry")));
            btns.add(button(Material.WRITABLE_BOOK, "§bSet description", "setdesc", List.of("§7One line under the title")));
        }
        if (manage) {
            btns.add(button(p.isBorderEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§bGround Border: " + (p.isBorderEnabled() ? "§aON" : "§cOFF"), "toggleborder",
                    List.of("§7Click to toggle the border", "§7around your plot")));
            btns.add(button(p.isTitleEnabled() ? Material.GLOWSTONE_DUST : Material.GRAY_DYE,
                    "§bEntry Title: " + (p.isTitleEnabled() ? "§aON" : "§cOFF"), "toggletitle",
                    List.of("§7Click to toggle the big text", "§7shown when entering your plot")));
            btns.add(button(matOr(p.getBorderBlockA(), Material.GREEN_CONCRETE), "§bBorder Color 1", "bordera",
                    List.of("§7Click to pick the first", "§7staggered border block")));
            btns.add(button(matOr(p.getBorderBlockB(), Material.LIME_CONCRETE), "§bBorder Color 2", "borderb",
                    List.of("§7Click to pick the second", "§7staggered border block")));
        }
        if (admin && p.isOwned())
            btns.add(button(Material.LEVER, "§eMake available again", "unclaim", List.of("§7Clears the owner")));
        if (admin)
            btns.add(button(Material.RED_WOOL, "§cDelete property", "delete", List.of("§7§lShift-click§7 to confirm")));

        for (int i = 0; i < btns.size() && i < PANEL_SLOTS.length; i++) inv.setItem(PANEL_SLOTS[i], btns.get(i));
        inv.setItem(49, button(Material.ARROW, "§7Back to list", "main", List.of()));
        player.openInventory(inv);
    }

    // ---------- border color picker ----------
    private static final String[] PALETTE = {
            "WHITE_CONCRETE","ORANGE_CONCRETE","MAGENTA_CONCRETE","LIGHT_BLUE_CONCRETE","YELLOW_CONCRETE",
            "LIME_CONCRETE","PINK_CONCRETE","GRAY_CONCRETE","LIGHT_GRAY_CONCRETE","CYAN_CONCRETE",
            "PURPLE_CONCRETE","BLUE_CONCRETE","BROWN_CONCRETE","GREEN_CONCRETE","RED_CONCRETE","BLACK_CONCRETE",
            "WHITE_WOOL","ORANGE_WOOL","MAGENTA_WOOL","LIGHT_BLUE_WOOL","YELLOW_WOOL","LIME_WOOL","PINK_WOOL",
            "GRAY_WOOL","CYAN_WOOL","PURPLE_WOOL","BLUE_WOOL","GREEN_WOOL","RED_WOOL","BLACK_WOOL",
            "GLOWSTONE","SEA_LANTERN","GOLD_BLOCK","EMERALD_BLOCK","REDSTONE_BLOCK","LAPIS_BLOCK","AMETHYST_BLOCK","QUARTZ_BLOCK"
    };

    public void openBorderPicker(Player player, Property p, boolean isA) {
        PropertyHolder holder = new PropertyHolder(
                isA ? PropertyHolder.Type.BORDER_PICK_A : PropertyHolder.Type.BORDER_PICK_B, p.getName());
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Border Color " + (isA ? "1" : "2")));
        holder.setInventory(inv);
        int slot = 0;
        for (String name : PALETTE) {
            if (slot >= 44) break;
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) continue;
            inv.setItem(slot++, button(m, "§f" + pretty(name), "pick:" + name, List.of("§7Click to use this block")));
        }
        inv.setItem(44, button(Material.ARROW, "§7Back", "main", List.of()));
        player.openInventory(inv);
    }

    private String pretty(String matName) {
        String[] parts = matName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : parts) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
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
