package me.kohen.propertyshop;

import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PropertyShop extends JavaPlugin {

    private PropertyManager manager;
    private SelectionManager selection;
    private Menus menus;
    private final Set<Material> protectedBlocks = new HashSet<>();

    private NamespacedKey actionKey;  // menu button action
    private NamespacedKey wandKey;     // selection wand item
    private NamespacedKey panelKey;    // control barrel (item + placed block)

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "action");
        wandKey = new NamespacedKey(this, "wand");
        panelKey = new NamespacedKey(this, "panel");

        manager = new PropertyManager(this);
        selection = new SelectionManager(this);
        menus = new Menus(this);
        buildProtectedSet();

        PropertyCommand cmd = new PropertyCommand(this);
        getCommand("property").setExecutor(cmd);
        getCommand("property").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        getLogger().info("PropertyShop v1.2.1 enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    public PropertyManager getManager() { return manager; }
    public SelectionManager getSelection() { return selection; }
    public Menus getMenus() { return menus; }
    public NamespacedKey getActionKey() { return actionKey; }
    public NamespacedKey getWandKey() { return wandKey; }
    public NamespacedKey getPanelKey() { return panelKey; }

    public void reloadAll() {
        reloadConfig();
        buildProtectedSet();
        manager.load();
    }

    private void buildProtectedSet() {
        protectedBlocks.clear();
        for (String s : getConfig().getStringList("protected-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) protectedBlocks.add(m);
        }
    }

    public boolean isProtectedBlock(Material m) {
        if (protectedBlocks.contains(m)) return true;
        if (getConfig().getBoolean("protect-tags.doors", true) && Tag.DOORS.isTagged(m)) return true;
        if (getConfig().getBoolean("protect-tags.trapdoors", true) && Tag.TRAPDOORS.isTagged(m)) return true;
        if (getConfig().getBoolean("protect-tags.fence-gates", true) && Tag.FENCE_GATES.isTagged(m)) return true;
        if (getConfig().getBoolean("protect-tags.buttons", true) && Tag.BUTTONS.isTagged(m)) return true;
        if (getConfig().getBoolean("protect-tags.beds", true) && Tag.BEDS.isTagged(m)) return true;
        if (getConfig().getBoolean("protect-tags.shulker-boxes", true) && Tag.SHULKER_BOXES.isTagged(m)) return true;
        return false;
    }

    /** Admins, the owner, and trusted players ignore protection. */
    public boolean canBypass(Player p, Property prop) {
        if (p.hasPermission("propertyshop.admin")) return true;
        if (prop.isOwnedBy(p.getUniqueId())) return true;
        return prop.isTrusted(p.getUniqueId());
    }

    /**
     * Create a property from the player's wand selection, or the chunk they stand in if none.
     * Messages the player and returns the new Property (or null on failure).
     */
    public Property createProperty(Player player, String optionalName) {
        String world;
        List<String> chunks;
        if (selection.has(player)) {
            world = selection.world(player);
            chunks = selection.chunks(player);
        } else {
            Chunk c = player.getLocation().getChunk();
            world = c.getWorld().getName();
            chunks = List.of(PropertyManager.chunkKey(c));
            player.sendMessage(ChatColor.GRAY + "No wand selection - using the chunk you're standing in. "
                    + "(Use /property wand to select a bigger area.)");
        }

        String name = (optionalName != null) ? optionalName : manager.nextAutoName();
        if (manager.exists(name)) {
            player.sendMessage(ChatColor.RED + "A property named '" + name + "' already exists.");
            return null;
        }

        Property prop = manager.create(name, world);
        int added = manager.addChunks(prop, chunks);
        if (added == 0) {
            manager.delete(prop);
            player.sendMessage(ChatColor.RED + "All of those chunks are already part of other properties.");
            return null;
        }
        int skipped = chunks.size() - added;
        player.sendMessage(ChatColor.GREEN + "Created '" + name + "' with " + added + " chunk(s)"
                + (skipped > 0 ? ChatColor.GRAY + " (" + skipped + " skipped - already taken)" : "") + ".");
        player.sendMessage(ChatColor.GRAY + "Now set a price: open it and use Set Price, or /property setprice " + name + " ...");
        selection.clear(player);
        return prop;
    }

    public void previewChunk(Player p, Chunk c) {
        outline(p, c.getWorld(), c.getX() << 4, c.getZ() << 4);
    }

    public void previewProperty(Player p, Property prop) {
        World w = getServer().getWorld(prop.getWorld());
        if (w == null) return;
        int count = 0;
        for (String key : prop.getChunks()) {
            if (count++ >= 25) break; // don't spam particles for huge plots
            String[] parts = key.split(",");
            try {
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                outline(p, w, cx << 4, cz << 4);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void outline(Player p, World w, int minX, int minZ) {
        int maxX = minX + 16, maxZ = minZ + 16;
        double y = p.getLocation().getY() + 1;
        int seconds = getConfig().getInt("preview-seconds", 6);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= seconds * 2 || !p.isOnline()) { cancel(); return; }
                for (int x = minX; x <= maxX; x++) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, x, y, minZ), 1);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, x, y, maxZ), 1);
                }
                for (int z = minZ; z <= maxZ; z++) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, minX, y, z), 1);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, new Location(w, maxX, y, z), 1);
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 10L);
    }
}
