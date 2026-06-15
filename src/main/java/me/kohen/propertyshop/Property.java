package me.kohen.propertyshop;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One property: name, chunks it covers, item price, owner, and trusted players. */
public class Property {
    private final String name;
    private String world;
    private final Set<String> chunks = new LinkedHashSet<>(); // "x,z"
    private final Map<Material, Integer> price = new LinkedHashMap<>();
    private final Set<UUID> trusted = new LinkedHashSet<>();
    private UUID owner;          // null = for sale
    private String ownerName;

    public Property(String name, String world) {
        this.name = name;
        this.world = world;
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public Set<String> getChunks() { return chunks; }
    public boolean hasChunk(String key) { return chunks.contains(key); }
    public void addChunk(String key) { chunks.add(key); }
    public void removeChunk(String key) { chunks.remove(key); }

    public Map<Material, Integer> getPrice() { return price; }
    public boolean hasPrice() { return !price.isEmpty(); }
    public void clearPrice() { price.clear(); }
    public void setPriceItem(Material m, int amount) { price.put(m, amount); }

    public Set<UUID> getTrusted() { return trusted; }
    public boolean isTrusted(UUID id) { return trusted.contains(id); }
    public void addTrusted(UUID id) { trusted.add(id); }
    public void removeTrusted(UUID id) { trusted.remove(id); }

    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public boolean isOwned() { return owner != null; }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public void clearOwner() {
        this.owner = null;
        this.ownerName = null;
        this.trusted.clear();
    }

    public boolean isOwnedBy(UUID id) {
        return owner != null && owner.equals(id);
    }

    public String priceString() {
        if (price.isEmpty()) return "not set";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> e : price.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(nice(e.getKey()));
        }
        return sb.toString();
    }

    public static String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
