package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

        // Text input (anvil): block item moves; clicking the result slot saves the typed text.
        if (ph.getType() == PropertyHolder.Type.TITLE_INPUT || ph.getType() == PropertyHolder.Type.DESC_INPUT) {
            e.setCancelled(true);
            if (e.getRawSlot() == 2) saveTypedText(p, ph, e.getInventory());
            return;
        }

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
                    plugin.getMenus().openTextInput(p, prop, true);
            }
            case "setdesc" -> {
                if (prop != null && (p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId())))
                    plugin.getMenus().openTextInput(p, prop, false);
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

    private void saveTypedText(Player p, PropertyHolder ph, Inventory inv) {
        Property prop = plugin.getManager().get(ph.getProperty());
        if (prop == null) { p.closeInventory(); return; }
        String text = (inv instanceof AnvilInventory ai && ai.getRenameText() != null) ? ai.getRenameText() : "";
        boolean title = ph.getType() == PropertyHolder.Type.TITLE_INPUT;
        text = plugin.capText(text, title ? plugin.maxTitleLen() : plugin.maxDescLen());
        if (title) prop.setTitle(text.isEmpty() ? null : text);
        else prop.setDescription(text.isEmpty() ? null : text);
        plugin.getManager().save();
        p.closeInventory();
        p.sendMessage(ChatColor.GREEN + (title ? "Title" : "Description") + " saved"
                + (text.isEmpty() ? " (cleared)." : ": " + text));
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMenus().openPanel(p, prop));
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (!(e.getInventory().getHolder() instanceof PropertyHolder ph)) return;
        if (ph.getType() != PropertyHolder.Type.TITLE_INPUT && ph.getType() != PropertyHolder.Type.DESC_INPUT) return;
        String text = e.getInventory().getRenameText();
        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text("Save: " + (text == null || text.isEmpty() ? "(clear)" : text))
                .color(NamedTextColor.GREEN));
        result.setItemMeta(meta);
        e.setResult(result);
        e.getInventory().setRepairCost(0);
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
