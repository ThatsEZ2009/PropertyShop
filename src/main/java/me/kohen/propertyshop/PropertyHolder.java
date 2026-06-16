package me.kohen.propertyshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as one of our menus and remembers which property it's about. */
public class PropertyHolder implements InventoryHolder {
    public enum Type { MAIN, PANEL, PRICE_EDITOR, TRUST, TITLE_INPUT, DESC_INPUT, BORDER_PICK_A, BORDER_PICK_B }

    private final Type type;
    private final String property; // nullable
    private Inventory inventory;

    public PropertyHolder(Type type, String property) {
        this.type = type;
        this.property = property;
    }

    public Type getType() { return type; }
    public String getProperty() { return property; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
