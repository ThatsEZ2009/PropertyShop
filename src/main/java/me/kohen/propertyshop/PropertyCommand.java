package me.kohen.propertyshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PropertyCommand implements CommandExecutor, TabCompleter {
    private final PropertyShop plugin;
    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "create", "addchunk", "removechunk", "setprice", "clearprice",
            "delete", "unclaim", "reload", "wand", "barrel", "clearselection");

    public PropertyCommand(PropertyShop plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase();
        boolean admin = p.hasPermission("propertyshop.admin");

        if (ADMIN_SUBS.contains(sub) && !admin) {
            p.sendMessage(ChatColor.RED + "You don't have permission for that.");
            return true;
        }

        switch (sub) {
            case "menu" -> plugin.getMenus().openMain(p);
            case "preview" -> doPreview(p);
            case "info" -> doInfo(p);
            case "buy" -> doBuy(p);
            case "list" -> doList(p);
            case "wand" -> { p.getInventory().addItem(makeWand());
                p.sendMessage(ChatColor.GREEN + "Got the Property Selector. Left-click a chunk to add it, right-click to remove it, then /property create."); }
            case "clearselection" -> { plugin.getSelection().clear(p);
                p.sendMessage(ChatColor.YELLOW + "Cleared your chunk selection."); }
            case "barrel" -> { p.getInventory().addItem(makePanelBarrel());
                p.sendMessage(ChatColor.GREEN + "Got a Property Panel barrel. Place it and right-click it to open the menu."); }
            case "trust" -> doTrust(p, args, true);
            case "untrust" -> doTrust(p, args, false);
            case "trustlist" -> doTrustList(p);
            case "create" -> {
                String name = args.length >= 2 ? args[1] : null;
                Property created = plugin.createProperty(p, name);
                if (created != null) plugin.getMenus().openPanel(p, created);
            }
            case "addchunk", "removechunk", "setprice", "clearprice", "delete", "unclaim", "reload" -> handleAdmin(p, sub, args);
            default -> help(p);
        }
        return true;
    }

    // ---------------- player commands ----------------
    private void doPreview(Player p) {
        Property prop = plugin.getManager().getAt(p.getLocation().getChunk());
        if (prop == null) {
            p.sendMessage(ChatColor.YELLOW + "No property here - outlining just this chunk.");
            plugin.previewChunk(p, p.getLocation().getChunk());
            return;
        }
        p.sendMessage(prop.isOwned()
                ? ChatColor.AQUA + "'" + prop.getName() + "' - owned by " + prop.getOwnerName() + "."
                : ChatColor.GREEN + "'" + prop.getName() + "' - FOR SALE for " + prop.priceString() + ".");
        plugin.previewProperty(p, prop);
    }

    private void doInfo(Player p) {
        Property prop = plugin.getManager().getAt(p.getLocation().getChunk());
        if (prop == null) { p.sendMessage(ChatColor.YELLOW + "No property here."); return; }
        p.sendMessage(ChatColor.GOLD + "=== " + prop.getName() + " ===");
        p.sendMessage(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + prop.getChunks().size());
        p.sendMessage(ChatColor.GRAY + "Price: " + ChatColor.WHITE + prop.priceString());
        if (prop.isOwned()) p.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + prop.getOwnerName());
        else p.sendMessage(ChatColor.GREEN + "FOR SALE - use /property buy here");
    }

    private void doBuy(Player p) {
        Property prop = plugin.getManager().getAt(p.getLocation().getChunk());
        if (prop == null) { p.sendMessage(ChatColor.RED + "No property here to buy."); return; }
        if (prop.isOwned()) {
            p.sendMessage(prop.isOwnedBy(p.getUniqueId())
                    ? ChatColor.YELLOW + "You already own this property."
                    : ChatColor.RED + "Already owned by " + prop.getOwnerName() + ".");
            return;
        }
        if (!prop.hasPrice()) { p.sendMessage(ChatColor.RED + "No price set yet."); return; }
        if (!plugin.getManager().canAfford(p, prop)) {
            p.sendMessage(ChatColor.RED + "You still need: " + plugin.getManager().missingItems(p, prop));
            p.sendMessage(ChatColor.GRAY + "Price: " + prop.priceString());
            return;
        }
        plugin.getManager().completePurchase(p, prop);
        p.sendMessage(ChatColor.GREEN + "You bought '" + prop.getName() + "'! Paid: " + prop.priceString());
    }

    private void doList(Player p) {
        List<Property> all = plugin.getManager().all();
        if (all.isEmpty()) { p.sendMessage(ChatColor.YELLOW + "No properties exist yet."); return; }
        p.sendMessage(ChatColor.GOLD + "=== Properties ===");
        for (Property prop : all) {
            String status = prop.isOwned()
                    ? ChatColor.YELLOW + "owned by " + prop.getOwnerName()
                    : ChatColor.GREEN + "FOR SALE (" + prop.priceString() + ")";
            p.sendMessage(ChatColor.WHITE + prop.getName() + ChatColor.GRAY + " - "
                    + prop.getChunks().size() + " chunk(s) - " + status);
        }
    }

    private void doTrust(Player p, String[] args, boolean add) {
        Property prop = plugin.getManager().getAt(p.getLocation().getChunk());
        if (prop == null) { p.sendMessage(ChatColor.RED + "Stand inside your property first."); return; }
        if (!(p.hasPermission("propertyshop.admin") || prop.isOwnedBy(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "Only the owner can manage trust here."); return;
        }
        if (args.length < 2) { p.sendMessage(ChatColor.RED + "/property " + (add ? "trust" : "untrust") + " <player>"); return; }
        UUID id = resolve(args[1]);
        if (id == null) { p.sendMessage(ChatColor.RED + "Couldn't find a player named '" + args[1] + "'."); return; }
        if (add) { prop.addTrusted(id); p.sendMessage(ChatColor.GREEN + "Trusted " + args[1] + " on '" + prop.getName() + "'."); }
        else { prop.removeTrusted(id); p.sendMessage(ChatColor.YELLOW + "Removed " + args[1] + " from '" + prop.getName() + "'."); }
        plugin.getManager().save();
    }

    private void doTrustList(Player p) {
        Property prop = plugin.getManager().getAt(p.getLocation().getChunk());
        if (prop == null) { p.sendMessage(ChatColor.RED + "Stand inside a property first."); return; }
        if (prop.getTrusted().isEmpty()) { p.sendMessage(ChatColor.GRAY + "No trusted players on '" + prop.getName() + "'."); return; }
        StringBuilder sb = new StringBuilder();
        for (UUID id : prop.getTrusted()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            if (sb.length() > 0) sb.append(", ");
            sb.append(op.getName() == null ? id.toString() : op.getName());
        }
        p.sendMessage(ChatColor.GOLD + "Trusted on '" + prop.getName() + "': " + ChatColor.WHITE + sb);
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return (op != null && (op.hasPlayedBefore() || op.isOnline())) ? op.getUniqueId() : null;
    }

    // ---------------- admin commands ----------------
    private void handleAdmin(Player p, String sub, String[] args) {
        if (sub.equals("reload")) { plugin.reloadAll(); p.sendMessage(ChatColor.GREEN + "PropertyShop reloaded."); return; }

        if (args.length < 2) { p.sendMessage(ChatColor.RED + "/property " + sub + " <name>"); return; }
        Property prop = plugin.getManager().get(args[1]);
        if (prop == null) { p.sendMessage(ChatColor.RED + "No property named '" + args[1] + "'."); return; }

        switch (sub) {
            case "delete" -> { plugin.getManager().delete(prop); p.sendMessage(ChatColor.GREEN + "Deleted '" + prop.getName() + "'."); }
            case "unclaim" -> { prop.clearOwner(); plugin.getManager().save(); p.sendMessage(ChatColor.GREEN + "'" + prop.getName() + "' is for sale again."); }
            case "clearprice" -> { prop.clearPrice(); plugin.getManager().save(); p.sendMessage(ChatColor.GREEN + "Cleared price for '" + prop.getName() + "'."); }
            case "addchunk" -> {
                Chunk c = p.getLocation().getChunk();
                if (!c.getWorld().getName().equals(prop.getWorld())) { p.sendMessage(ChatColor.RED + "That property is in world '" + prop.getWorld() + "'."); return; }
                String key = PropertyManager.chunkKey(c);
                Property other = plugin.getManager().ownerOfChunk(prop.getWorld(), key);
                if (other != null) { p.sendMessage(ChatColor.RED + "This chunk is already part of '" + other.getName() + "'."); return; }
                plugin.getManager().addChunk(prop, key);
                p.sendMessage(ChatColor.GREEN + "Added this chunk to '" + prop.getName() + "' (now " + prop.getChunks().size() + ").");
                plugin.previewChunk(p, c);
            }
            case "removechunk" -> {
                Chunk c = p.getLocation().getChunk();
                String key = PropertyManager.chunkKey(c);
                if (!prop.hasChunk(key)) { p.sendMessage(ChatColor.RED + "This chunk isn't part of '" + prop.getName() + "'."); return; }
                plugin.getManager().removeChunk(prop, key);
                p.sendMessage(ChatColor.GREEN + "Removed this chunk (now " + prop.getChunks().size() + ").");
            }
            case "setprice" -> {
                if (args.length < 4 || (args.length - 2) % 2 != 0) {
                    p.sendMessage(ChatColor.RED + "/property setprice <name> <ITEM> <amount> [<ITEM> <amount> ...]");
                    p.sendMessage(ChatColor.GRAY + "Or just open the property in /property menu and use Set Price.");
                    return;
                }
                prop.clearPrice();
                for (int i = 2; i + 1 < args.length; i += 2) {
                    Material m = Material.matchMaterial(args[i]);
                    if (m == null || !m.isItem()) { p.sendMessage(ChatColor.RED + "Unknown item: " + args[i]); return; }
                    int amt;
                    try { amt = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ex) { p.sendMessage(ChatColor.RED + "'" + args[i + 1] + "' isn't a number."); return; }
                    if (amt <= 0) { p.sendMessage(ChatColor.RED + "Amount must be at least 1."); return; }
                    prop.setPriceItem(m, amt);
                }
                plugin.getManager().save();
                p.sendMessage(ChatColor.GREEN + "Price set: " + prop.priceString());
            }
        }
    }

    // ---------------- item factories ----------------
    private ItemStack makeWand() {
        ItemStack is = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text("Property Selector").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Left-click a chunk to add it").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click a chunk to remove it").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Then /property create").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(plugin.getWandKey(), PersistentDataType.BYTE, (byte) 1);
        is.setItemMeta(meta);
        return is;
    }

    private ItemStack makePanelBarrel() {
        ItemStack is = new ItemStack(Material.BARREL);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text("Property Panel").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place me, then right-click").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("to open the property menu").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(plugin.getPanelKey(), PersistentDataType.BYTE, (byte) 1);
        is.setItemMeta(meta);
        return is;
    }

    private void help(Player p) {
        p.sendMessage(ChatColor.GOLD + "=== PropertyShop ===");
        p.sendMessage(ChatColor.YELLOW + "/property menu " + ChatColor.GRAY + "- open the clickable menu");
        p.sendMessage(ChatColor.YELLOW + "/property preview " + ChatColor.GRAY + "- outline the property you're in");
        p.sendMessage(ChatColor.YELLOW + "/property info | buy | list");
        p.sendMessage(ChatColor.YELLOW + "/property trust <player> " + ChatColor.GRAY + "- let a friend use your plot");
        if (p.hasPermission("propertyshop.admin")) {
            p.sendMessage(ChatColor.GRAY + "Admin: wand (left-click adds chunks, right-click removes),");
            p.sendMessage(ChatColor.GRAY + "       create [name], clearselection, barrel, setprice,");
            p.sendMessage(ChatColor.GRAY + "       addchunk, removechunk, clearprice, unclaim, delete, reload");
        }
    }

    // ---------------- tab complete ----------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("propertyshop.admin");
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("menu", "preview", "info", "buy", "list", "trust", "untrust", "trustlist"));
            if (admin) opts.addAll(ADMIN_SUBS);
            return filter(opts, args[0]);
        }
        if (args.length == 2 && admin && Arrays.asList("addchunk", "removechunk", "setprice", "clearprice", "delete", "unclaim").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Property prop : plugin.getManager().all()) names.add(prop.getName());
            return filter(names, args[1]);
        }
        if (args.length == 2 && Arrays.asList("trust", "untrust").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return filter(names, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> opts, String start) {
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.toLowerCase().startsWith(start.toLowerCase())) out.add(o);
        return out;
    }
}
