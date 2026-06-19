package me.kohen.propertyshop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuListener implements Listener {
    private final PropertyShop plugin;

    public MenuListener(PropertyShop plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (!(h instanceof PropertyHolder ph)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Price editor: let the player freely place/take items (that IS the input).
        if (ph.getType() == PropertyHolder.Type.PRICE_EDITOR) return;

        // All other menus are display-only.
        e.setCancelled(true);
        String action = plugin.getMenus().actionOf(e.getCurrentItem());
        if (action == null || action.equals("none")) return;

        Property prop = ph.getProperty() == null ? null : plugin.getManager().get(ph.getProperty());

        if (action.startsWith("open:")) {
            Property target = plugin.getManager().get(action.substring(5));
            if (target != null) plugin.getMenus().openPanel(p, target);
            return;
        }
        if (action.startsWith("trust:")) {
            handleTrustToggle(p, prop, action.substring(6));
            return;
        }
        if (action.startsWith("pick:")) {
            if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) {
                String mat = action.substring(5);
                if (ph.getType() == PropertyHolder.Type.BORDER_PICK_A) prop.setBorderBlockA(mat);
                else prop.setBorderBlockB(mat);
                plugin.getManager().save();
                plugin.getMenus().openPanel(p, prop);
            }
            return;
        }

        switch (action) {
            case "close" -> p.closeInventory();
            case "main" -> plugin.getMenus().openMain(p);
            case "panel" -> { if (prop != null) plugin.getMenus().openPanel(p, prop); }
            case "create" -> {
                if (!p.hasPermission("propertyshop.admin")) return;
                p.closeInventory();
                Property created = plugin.createProperty(p, null);
                if (created != null) plugin.getMenus().openPanel(p, created);
            }
            case "preview" -> {
                if (prop != null) { p.closeInventory(); plugin.previewProperty(p, prop); }
            }
            case "buy" -> { if (prop != null) doBuy(p, prop); }
            case "setprice" -> {
                if (prop != null && p.hasPermission("propertyshop.admin"))
                    plugin.getMenus().openPriceEditor(p, prop);
            }
            case "expand" -> {
                if (prop != null && p.hasPermission("propertyshop.admin")) {
                    int added = plugin.getManager().expandWithSelection(p, prop);
                    if (added == -1) {
                        p.sendMessage(ChatColor.RED + "Your wand selection is in a different world than this plot.");
                    } else if (added == 0) {
                        p.sendMessage(ChatColor.RED + "No new chunks added. Select land with the wand first "
                                + "(those chunks may already belong to a plot).");
                    } else {
                        p.sendMessage(ChatColor.GREEN + "Added " + added + " chunk(s) to '" + prop.getName() + "'.");
                    }
                    plugin.getMenus().openPanel(p, prop);
                }
            }
            case "unclaim" -> {
                if (prop != null && p.hasPermission("propertyshop.admin")) {
                    prop.clearOwner();
                    plugin.getManager().save();
                    p.sendMessage(ChatColor.GREEN + "'" + prop.getName() + "' is for sale again.");
                    plugin.getMenus().openPanel(p, prop);
                }
            }
            case "delete" -> {
                if (prop != null && p.hasPermission("propertyshop.admin")) {
                    if (!e.isShiftClick()) {
                        p.sendMessage(ChatColor.YELLOW + "Shift-click Delete to confirm.");
                        return;
                    }
                    plugin.getManager().delete(prop);
                    p.sendMessage(ChatColor.GREEN + "Deleted '" + prop.getName() + "'.");
                    plugin.getMenus().openMain(p);
                }
            }
            case "trust" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    plugin.getMenus().openTrust(p, prop);
            }
            case "settitle" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    startTextInput(p, prop, true);
            }
            case "setdesc" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    startTextInput(p, prop, false);
            }
            case "toggleborder" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) {
                    prop.setBorderEnabled(!prop.isBorderEnabled());
                    plugin.getManager().save();
                    plugin.getMenus().openPanel(p, prop);
                }
            }
            case "toggletitle" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) {
                    prop.setTitleEnabled(!prop.isTitleEnabled());
                    plugin.getManager().save();
                    plugin.getMenus().openPanel(p, prop);
                }
            }
            case "togglepvp" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) {
                    prop.setPvp(!prop.isPvp());
                    plugin.getManager().save();
                    p.sendMessage(ChatColor.GREEN + "PvP on '" + prop.getName() + "' is now "
                            + (prop.isPvp() ? ChatColor.RED + "ON" : ChatColor.GREEN + "OFF") + ChatColor.GREEN + ".");
                    plugin.getMenus().openPanel(p, prop);
                }
            }
            case "bordera" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    plugin.getMenus().openBorderPicker(p, prop, true);
            }
            case "borderb" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    plugin.getMenus().openBorderPicker(p, prop, false);
            }
        }
    }

    private void handleTrustToggle(Player p, Property prop, String uuidStr) {
        if (prop == null) return;
        if (!(p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) return;
        UUID id;
        try { id = UUID.fromString(uuidStr); } catch (IllegalArgumentException ex) { return; }
        if (prop.isTrusted(id)) {
            prop.removeTrusted(id);
            p.sendMessage(ChatColor.YELLOW + "Removed trust.");
        } else {
            prop.addTrusted(id);
            p.sendMessage(ChatColor.GREEN + "Added trust.");
        }
        plugin.getManager().save();
        plugin.getMenus().openTrust(p, prop); // refresh
    }

    private void doBuy(Player p, Property prop) {
        if (prop.isOwned()) {
            p.sendMessage(prop.isOwnedBy(p.getUniqueId())
                    ? ChatColor.YELLOW + "You already own this."
                    : ChatColor.RED + "Already owned by " + prop.getOwnerName() + ".");
            return;
        }
        if (!prop.hasPrice()) { p.sendMessage(ChatColor.RED + "No price set yet."); return; }
        if (!plugin.getManager().canAfford(p, prop)) {
            p.sendMessage(ChatColor.RED + "You still need: " + plugin.getManager().missingItems(p, prop));
            return;
        }
        plugin.getManager().completePurchase(p, prop);
        p.sendMessage(ChatColor.GREEN + "You bought '" + prop.getName() + "'! Paid: " + prop.priceString());
        plugin.getMenus().openPanel(p, prop);
    }

    private void startTextInput(Player p, Property prop, boolean title) {
        plugin.textInput.put(p.getUniqueId(), new String[]{prop.getName(), title ? "T" : "D"});
        p.closeInventory();
        p.sendMessage("§eType the " + (title ? "title" : "description")
                + " in chat now. Type §fcancel§e to cancel. (One line.)");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (!(h instanceof PropertyHolder ph)) return;
        if (ph.getType() != PropertyHolder.Type.PRICE_EDITOR) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        Property prop = plugin.getManager().get(ph.getProperty());
        Inventory inv = e.getInventory();

        Map<Material, Integer> price = new HashMap<>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            price.merge(it.getType(), it.getAmount(), Integer::sum);
            // Give the items back - they only defined the price.
            for (ItemStack leftover : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
        inv.clear();

        if (prop == null) return;
        prop.clearPrice();
        for (Map.Entry<Material, Integer> en : price.entrySet()) prop.setPriceItem(en.getKey(), en.getValue());
        plugin.getManager().save();
        if (price.isEmpty()) p.sendMessage(ChatColor.YELLOW + "No items placed - price left unchanged/empty.");
        else p.sendMessage(ChatColor.GREEN + "Price for '" + prop.getName() + "' set to: " + prop.priceString());
    }
}
