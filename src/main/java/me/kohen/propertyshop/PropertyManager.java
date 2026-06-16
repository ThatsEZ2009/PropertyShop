package me.kohen.propertyshop;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PropertyManager {
    private final PropertyShop plugin;
    private final File file;
    private final Map<String, Property> properties = new LinkedHashMap<>();   // lowercase name -> property
    private final Map<String, String> chunkIndex = new HashMap<>();           // "world:x,z" -> lowercase name

    public PropertyManager(PropertyShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "properties.yml");
        load();
    }

    public static String chunkKey(Chunk c) { return c.getX() + "," + c.getZ(); }
    private static String indexKey(String world, String chunkKey) { return world + ":" + chunkKey; }

    public Property get(String name) { return properties.get(name.toLowerCase()); }
    public boolean exists(String name) { return properties.containsKey(name.toLowerCase()); }
    public List<Property> all() { return new ArrayList<>(properties.values()); }

    public Property getAt(Chunk c) {
        String name = chunkIndex.get(indexKey(c.getWorld().getName(), chunkKey(c)));
        return name == null ? null : properties.get(name);
    }

    public Property ownerOfChunk(String world, String chunkKey) {
        String name = chunkIndex.get(indexKey(world, chunkKey));
        return name == null ? null : properties.get(name);
    }

    /** First unused plotN name. */
    public String nextAutoName() {
        int i = 1;
        while (exists("plot" + i)) i++;
        return "plot" + i;
    }

    public Property create(String name, String world) {
        Property p = new Property(name, world);
        properties.put(name.toLowerCase(), p);
        save();
        return p;
    }

    public void delete(Property p) {
        for (String ck : p.getChunks()) chunkIndex.remove(indexKey(p.getWorld(), ck));
        properties.remove(p.getName().toLowerCase());
        save();
    }

    public void addChunk(Property p, String chunkKey) {
        p.addChunk(chunkKey);
        chunkIndex.put(indexKey(p.getWorld(), chunkKey), p.getName().toLowerCase());
        save();
    }

    public void removeChunk(Property p, String chunkKey) {
        p.removeChunk(chunkKey);
        chunkIndex.remove(indexKey(p.getWorld(), chunkKey));
        save();
    }

    /** Add many chunks at once, skipping any already taken. Returns how many were added. */
    public int addChunks(Property p, List<String> chunkKeys) {
        int added = 0;
        for (String ck : chunkKeys) {
            if (ownerOfChunk(p.getWorld(), ck) != null) continue; // already in some property
            p.addChunk(ck);
            chunkIndex.put(indexKey(p.getWorld(), ck), p.getName().toLowerCase());
            added++;
        }
        save();
        return added;
    }

    public boolean canAfford(Player p, Property prop) {
        for (Map.Entry<Material, Integer> e : prop.getPrice().entrySet()) {
            if (countItem(p.getInventory(), e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public String missingItems(Player p, Property prop) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> e : prop.getPrice().entrySet()) {
            int have = countItem(p.getInventory(), e.getKey());
            if (have < e.getValue()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getValue() - have).append(" more ").append(Property.nice(e.getKey()));
            }
        }
        return sb.toString();
    }

    public void completePurchase(Player p, Property prop) {
        for (Map.Entry<Material, Integer> e : prop.getPrice().entrySet()) {
            removeItem(p.getInventory(), e.getKey(), e.getValue());
        }
        prop.setOwner(p.getUniqueId(), p.getName());
        save();
    }

    private int countItem(PlayerInventory inv, Material m) {
        int total = 0;
        for (ItemStack it : inv.getContents()) if (it != null && it.getType() == m) total += it.getAmount();
        return total;
    }

    private void removeItem(PlayerInventory inv, Material m, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != m) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) contents[i] = null;
        }
        inv.setContents(contents);
    }

    // ---------------- persistence ----------------
    public void load() {
        properties.clear();
        chunkIndex.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("properties");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            String world = s.getString("world", "world");
            Property p = new Property(name, world);
            for (String ck : s.getStringList("chunks")) {
                p.addChunk(ck);
                chunkIndex.put(indexKey(world, ck), name.toLowerCase());
            }
            String ownerStr = s.getString("owner", "");
            if (ownerStr != null && !ownerStr.isEmpty()) {
                try { p.setOwner(UUID.fromString(ownerStr), s.getString("owner-name", "Unknown")); }
                catch (IllegalArgumentException ignored) {}
            }
            for (String t : s.getStringList("trusted")) {
                try { p.addTrusted(UUID.fromString(t)); } catch (IllegalArgumentException ignored) {}
            }
            String titleStr = s.getString("title", "");
            if (titleStr != null && !titleStr.isEmpty()) p.setTitle(titleStr);
            String descStr = s.getString("description", "");
            if (descStr != null && !descStr.isEmpty()) p.setDescription(descStr);
            ConfigurationSection priceSec = s.getConfigurationSection("price");
            if (priceSec != null) {
                for (String mat : priceSec.getKeys(false)) {
                    Material m = Material.matchMaterial(mat);
                    if (m != null) p.setPriceItem(m, priceSec.getInt(mat));
                }
            }
            properties.put(name.toLowerCase(), p);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Property p : properties.values()) {
            String base = "properties." + p.getName() + ".";
            yml.set(base + "world", p.getWorld());
            yml.set(base + "chunks", new ArrayList<>(p.getChunks()));
            yml.set(base + "owner", p.getOwner() == null ? "" : p.getOwner().toString());
            yml.set(base + "owner-name", p.getOwnerName() == null ? "" : p.getOwnerName());
            yml.set(base + "title", p.getTitle() == null ? "" : p.getTitle());
            yml.set(base + "description", p.getDescription() == null ? "" : p.getDescription());
            List<String> trust = new ArrayList<>();
            for (UUID id : p.getTrusted()) trust.add(id.toString());
            yml.set(base + "trusted", trust);
            for (Map.Entry<Material, Integer> e : p.getPrice().entrySet()) {
                yml.set(base + "price." + e.getKey().name(), e.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save properties.yml: " + ex.getMessage());
        }
    }
}
