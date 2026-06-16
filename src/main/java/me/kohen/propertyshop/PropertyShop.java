package me.kohen.propertyshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PropertyShop extends JavaPlugin {

    private PropertyManager manager;
    private SelectionManager selection;
    private Menus menus;
    private BorderManager borders;
    private HologramManager holograms;
    private WandMapManager wandMap;
    private final Set<Material> protectedBlocks = new HashSet<>();
    private final Set<Material> borderSurfaces = new HashSet<>();
    private final java.util.Map<java.util.UUID, String> titleLast = new java.util.HashMap<>();
    public final java.util.Map<java.util.UUID, String[]> textInput = new java.util.HashMap<>(); // uuid -> {propName, "T"/"D"}

    private NamespacedKey actionKey;
    private NamespacedKey wandKey;
    private NamespacedKey panelKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "action");
        wandKey = new NamespacedKey(this, "wand");
        panelKey = new NamespacedKey(this, "panel");

        manager = new PropertyManager(this);
        selection = new SelectionManager(this);
        menus = new Menus(this);
        borders = new BorderManager(this);
        holograms = new HologramManager(this);
        wandMap = new WandMapManager(this);
        buildProtectedSet();
        buildBorderSurfaces();

        PropertyCommand cmd = new PropertyCommand(this);
        getCommand("property").setExecutor(cmd);
        getCommand("property").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);

        startWandHighlight();
        holograms.cleanupStray();
        startBorderTask();
        startHologramTask();
        getLogger().info("PropertyShop v1.10.0 enabled.");
    }

    @Override
    public void onDisable() {
        if (borders != null) borders.clearAll();
        if (wandMap != null) wandMap.clearAll();
        if (holograms != null) holograms.removeAll();
        if (manager != null) manager.save();
    }

    public HologramManager getHolograms() { return holograms; }
    public WandMapManager getWandMap() { return wandMap; }

    public PropertyManager getManager() { return manager; }
    public SelectionManager getSelection() { return selection; }
    public Menus getMenus() { return menus; }
    public BorderManager getBorders() { return borders; }
    public NamespacedKey getActionKey() { return actionKey; }
    public NamespacedKey getWandKey() { return wandKey; }
    public NamespacedKey getPanelKey() { return panelKey; }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void reloadAll() {
        reloadConfig();
        buildProtectedSet();
        buildBorderSurfaces();
        manager.load();
    }

    private void buildProtectedSet() {
        protectedBlocks.clear();
        for (String s : getConfig().getStringList("protected-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) protectedBlocks.add(m);
        }
    }

    private void buildBorderSurfaces() {
        borderSurfaces.clear();
        for (String s : getConfig().getStringList("border.surface-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) borderSurfaces.add(m);
        }
        if (borderSurfaces.isEmpty()) { // stale/old config with no list - use sensible defaults
            String[] defs = {"GRASS_BLOCK","DIRT","COARSE_DIRT","PODZOL","ROOTED_DIRT","MYCELIUM",
                    "DIRT_PATH","FARMLAND","SAND","RED_SAND","GRAVEL","STONE","GRANITE","DIORITE",
                    "ANDESITE","DEEPSLATE","TUFF","SANDSTONE","RED_SANDSTONE","SNOW_BLOCK","MUD",
                    "CLAY","MOSS_BLOCK","NETHERRACK","SOUL_SAND","SOUL_SOIL","END_STONE","BASALT","BLACKSTONE"};
            for (String s : defs) { Material m = Material.matchMaterial(s); if (m != null) borderSurfaces.add(m); }
        }
    }

    /** The ring may only paint on these natural blocks (never water/lava/builds). */
    public boolean isBorderSurface(Material m) {
        return borderSurfaces.contains(m);
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

    public boolean canBypass(Player p, Property prop) {
        if (p.hasPermission("propertyshop.admin")) return true;
        if (prop.isOwnedBy(p.getUniqueId())) return true;
        return prop.isTrusted(p.getUniqueId());
    }

    /** The property at this chunk only if it's "active" (owned or priced). Drafts return null. */
    public Property activeAt(Chunk c) {
        Property p = manager.getAt(c);
        return (p != null && p.isActive()) ? p : null;
    }

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

    // ---------------- particle outlines ----------------
    private void startBorderTask() {
        new BukkitRunnable() {
            int cycle = 0;
            @Override public void run() {
                cycle++;
                int radius = getConfig().getInt("for-sale-ring.view-chunks", 8);
                int ownRange = getConfig().getInt("border.show-range", 5);
                boolean titlesOn = getConfig().getBoolean("titles.enabled", true);
                for (Player p : getServer().getOnlinePlayers()) {
                    String world = p.getWorld().getName();
                    Chunk pc = p.getLocation().getChunk();
                    int cx = pc.getX(), cz = pc.getZ();
                    int px = p.getLocation().getBlockX(), pz = p.getLocation().getBlockZ();
                    java.util.Map<String, Boolean> desired = new java.util.HashMap<>();
                    for (Property prop : manager.all()) {
                        if (!prop.getWorld().equals(world)) continue;
                        if (prop.isOwned()) {
                            if (prop.isBorderEnabled() && nearPlot(prop, px, pz, ownRange)) {
                                desired.put(prop.getName(), false); // owned border - visible to everyone near it
                            }
                        } else if (prop.hasPrice()) {
                            if (withinRadius(prop, cx, cz, radius)) desired.put(prop.getName(), true); // green (inside)
                        }
                    }
                    borders.reconcile(p, desired);
                    if ((cycle & 1) == 0) borders.resend(p); // re-push so Bedrock keeps the rings

                    // Title only when ENTERING a plot (not every chunk inside it).
                    Property in = activeAt(pc);
                    String inName = (in == null) ? null : in.getName();
                    String last = titleLast.get(p.getUniqueId());
                    if (inName == null) {
                        titleLast.remove(p.getUniqueId());
                    } else if (!inName.equals(last)) {
                        titleLast.put(p.getUniqueId(), inName);
                        if (titlesOn) {
                            if (in.isOwned()) {
                                if (in.isTitleEnabled()) showTitle(p, in.getTitleText(), in.getDescription());
                            } else {
                                showTitle(p, "&a&lFOR SALE", "&fPrice: " + in.priceString());
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 15L);
    }

    /** True if the player is within `range` blocks of the plot's bounding box. */
    private boolean nearPlot(Property prop, int px, int pz, int range) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : prop.getChunks()) {
            String[] pa = key.split(",");
            try {
                int bx = Integer.parseInt(pa[0]) << 4, bz = Integer.parseInt(pa[1]) << 4;
                minX = Math.min(minX, bx); maxX = Math.max(maxX, bx + 15);
                minZ = Math.min(minZ, bz); maxZ = Math.max(maxZ, bz + 15);
            } catch (NumberFormatException ignored) {}
        }
        if (minX == Integer.MAX_VALUE) return false;
        return px >= minX - range && px <= maxX + range && pz >= minZ - range && pz <= maxZ + range;
    }

    private boolean withinRadius(Property prop, int cx, int cz, int radius) {
        for (String key : prop.getChunks()) {
            String[] pa = key.split(",");
            try {
                int x = Integer.parseInt(pa[0]), z = Integer.parseInt(pa[1]);
                if (Math.abs(x - cx) <= radius && Math.abs(z - cz) <= radius) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private void startHologramTask() {
        new BukkitRunnable() {
            @Override public void run() { holograms.refreshAll(); }
        }.runTaskTimer(this, 60L, 100L);
    }

    private void showTitle(Player p, String titleStr, String subStr) {
        var ser = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        net.kyori.adventure.text.Component t = ser.deserialize(titleStr == null ? "" : titleStr);
        net.kyori.adventure.text.Component s = (subStr == null || subStr.isEmpty())
                ? net.kyori.adventure.text.Component.empty() : ser.deserialize(subStr);
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(getConfig().getInt("titles.fade-in-ms", 300)),
                java.time.Duration.ofMillis(getConfig().getInt("titles.stay-ms", 2000)),
                java.time.Duration.ofMillis(getConfig().getInt("titles.fade-out-ms", 1000)));
        p.showTitle(net.kyori.adventure.title.Title.title(t, s, times));
    }

    /** Force one line and cap length so a title/description never wraps. */
    public String capText(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public int maxTitleLen() { return getConfig().getInt("titles.max-title-length", 24); }
    public int maxDescLen() { return getConfig().getInt("titles.max-description-length", 40); }

    /** Called (on the main thread) after a player types a title/description in chat. */
    public void applyTextInput(Player p, String[] pend, String msg) {
        Property prop = manager.get(pend[0]);
        if (prop == null) { p.sendMessage("§cThat property no longer exists."); return; }
        if (msg.equalsIgnoreCase("cancel")) { p.sendMessage("§7Cancelled."); return; }
        boolean title = pend[1].equals("T");
        String text = capText(msg, title ? maxTitleLen() : maxDescLen());
        if (title) prop.setTitle(text.isEmpty() ? null : text);
        else prop.setDescription(text.isEmpty() ? null : text);
        manager.save();
        p.sendMessage("§a" + (title ? "Title" : "Description") + " saved: §f" + text);
        menus.openPanel(p, prop);
    }

    private void startWandHighlight() {
        new BukkitRunnable() {
            int cyc = 0;
            @Override public void run() {
                cyc++;
                for (Player p : getServer().getOnlinePlayers()) {
                    wandMap.update(p);
                    if (cyc % 4 == 0) wandMap.resend(p); // keep it on Bedrock too
                }
            }
        }.runTaskTimer(this, 20L, 8L);
    }

    public void previewChunk(Player p, Chunk c) {
        scheduleOutline(p, c.getWorld(), List.of(new int[]{c.getX(), c.getZ()}));
    }

    public void previewProperty(Player p, Property prop) {
        World w = getServer().getWorld(prop.getWorld());
        if (w == null) return;
        java.util.List<int[]> list = new java.util.ArrayList<>();
        int count = 0;
        for (String key : prop.getChunks()) {
            if (count++ >= 25) break;
            String[] pa = key.split(",");
            try { list.add(new int[]{Integer.parseInt(pa[0]), Integer.parseInt(pa[1])}); }
            catch (NumberFormatException ignored) {}
        }
        scheduleOutline(p, w, list);
    }

    private void scheduleOutline(Player p, World w, List<int[]> chunks) {
        int seconds = getConfig().getInt("preview-seconds", 6);
        Color yellow = Color.fromRGB(255, 221, 0);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= seconds * 2 || !p.isOnline()) { cancel(); return; }
                for (int[] c : chunks) chunkParticles(p, w, c[0], c[1], yellow, false);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    /** Draw a chunk outline (and a sparse interior fill if fill=true) in the given color. */
    private void chunkParticles(Player p, World w, int cx, int cz, Color color, boolean fill) {
        int minX = cx << 4, minZ = cz << 4, maxX = minX + 16, maxZ = minZ + 16;
        double y = p.getLocation().getY() + 1.0;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.3f);
        for (int x = minX; x <= maxX; x += 2) {
            p.spawnParticle(Particle.DUST, new Location(w, x, y, minZ), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(w, x, y, maxZ), 1, dust);
        }
        for (int z = minZ; z <= maxZ; z += 2) {
            p.spawnParticle(Particle.DUST, new Location(w, minX, y, z), 1, dust);
            p.spawnParticle(Particle.DUST, new Location(w, maxX, y, z), 1, dust);
        }
        if (fill) {
            for (int x = minX + 4; x < maxX; x += 4)
                for (int z = minZ + 4; z < maxZ; z += 4)
                    p.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, dust);
        }
    }
}
