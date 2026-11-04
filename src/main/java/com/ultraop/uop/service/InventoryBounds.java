package com.ultraop.uop.service;

/** Bukkit-independent bounds validation so these rules can be unit tested without a server runtime. */
public final class InventoryBounds {
    private InventoryBounds() {}

    public static int validateSlot(int slot) {
        if (slot < 0 || slot > 40) {
            throw new IllegalArgumentException("Inventory slot must be between 0 and 40.");
        }
        return slot;
    }

    public static int validateAmount(int amount) {
        if (amount < 1 || amount > 99) {
            throw new IllegalArgumentException("Amount must be between 1 and 99.");
        }
        return amount;
    }
}
