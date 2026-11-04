package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ArmorLoadoutService {
    private final UopStore store;
    private final InventoryService inventory;

    public ArmorLoadoutService(UopStore store, InventoryService inventory) {
        this.store = store;
        this.inventory = inventory;
    }

    private String key(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("Loadout name must be 1-48 letters, numbers, '.', '_' or '-'.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    public void save(String name, Player player) {
        String base = "armor-loadouts." + key(name);
        store.data().set(base + ".display-name", name);
        store.data().set(base + ".storage", cloneItems(player.getInventory().getStorageContents()));
        store.data().set(base + ".armor", cloneItems(player.getInventory().getArmorContents()));
        ItemStack offhand = player.getInventory().getItemInOffHand();
        store.data().set(base + ".offhand", offhand == null ? null : offhand.clone());
        store.data().set(base + ".held-slot", player.getInventory().getHeldItemSlot());
        store.save();
    }

    private List<ItemStack> cloneItems(ItemStack[] source) {
        List<ItemStack> result = new ArrayList<>(source.length);
        for (ItemStack item : source) result.add(item == null ? null : item.clone());
        return result;
    }

    public List<String> list() {
        var section = store.data().getConfigurationSection("armor-loadouts");
        if (section == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String child : section.getKeys(false)) result.add(section.getString(child + ".display-name", child));
        return result;
    }

    public void delete(String name) {
        String base = "armor-loadouts." + key(name);
        if (!store.data().contains(base)) throw new IllegalArgumentException("Loadout not found: " + name);
        store.data().set(base, null);
        store.save();
    }

    public boolean give(String name, Player player) {
        String base = "armor-loadouts." + key(name);
        if (!store.data().contains(base)) return false;
        List<?> storage = store.data().getList(base + ".storage", List.of());
        List<?> armor = store.data().getList(base + ".armor", List.of());
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armorContents = new ItemStack[4];
        for (int i = 0; i < Math.min(36, storage.size()); i++) contents[i] = storage.get(i) instanceof ItemStack item ? item.clone() : null;
        for (int i = 0; i < Math.min(4, armor.size()); i++) armorContents[i] = armor.get(i) instanceof ItemStack item ? item.clone() : null;
        // The loadout replaces the entire inventory, so snapshot the current
        // state before any live player data is changed.
        inventory.backup(player, "auto-loadout-" + System.currentTimeMillis());
        player.getInventory().setStorageContents(contents);
        player.getInventory().setArmorContents(armorContents);
        Object offhand = store.data().get(base + ".offhand");
        player.getInventory().setItemInOffHand(offhand instanceof ItemStack item ? item.clone() : null);
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, store.data().getInt(base + ".held-slot", 0))));
        player.updateInventory();
        return true;
    }

    public String chooseRandomName(List<String> allowedNames) {
        List<String> candidates = allowedNames.isEmpty() ? list() : allowedNames;
        List<String> existing = candidates.stream().filter(this::exists).toList();
        if (existing.isEmpty()) return null;
        return existing.get(ThreadLocalRandom.current().nextInt(existing.size()));
    }

    public boolean giveRandom(List<String> allowedNames, Player player) {
        String selected = chooseRandomName(allowedNames);
        return selected != null && give(selected, player);
    }

    public boolean exists(String name) {
        try {
            return store.data().contains("armor-loadouts." + key(name));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}