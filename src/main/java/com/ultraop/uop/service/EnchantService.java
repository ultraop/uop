package com.ultraop.uop.service;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EnchantService {
    public Enchantment resolve(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        NamespacedKey key = value.contains(":") ? NamespacedKey.fromString(value) : NamespacedKey.minecraft(value);
        if (key == null) throw new IllegalArgumentException("Invalid enchantment key: " + name);
        Enchantment enchantment = Registry.ENCHANTMENT.get(key);
        if (enchantment == null) throw new IllegalArgumentException("Unknown enchantment: " + name);
        return enchantment;
    }

    public void validateApply(String scope, String enchantmentName, int level) {
        String normalized = scope == null ? "" : scope.toLowerCase(Locale.ROOT);
        if (!List.of("all", "armor", "equipment", "mainhand", "offhand", "inventory").contains(normalized)) {
            throw new IllegalArgumentException("Scope must be all, armor, equipment, mainhand, offhand or inventory.");
        }
        Enchantment enchantment = resolve(enchantmentName);
        if (level < 1 || level > enchantment.getMaxLevel()) {
            throw new IllegalArgumentException("Level must be 1-" + enchantment.getMaxLevel() + " for " + enchantmentName + ".");
        }
    }

    public void validateRemove(String scope, String slot, String enchantmentName) {
        String normalized = scope == null ? "" : scope.toLowerCase(Locale.ROOT);
        if (!List.of("all", "armor", "equipment", "hand", "inventory").contains(normalized)) {
            throw new IllegalArgumentException("Scope must be all, armor, equipment, hand or inventory.");
        }
        if (slot != null && !slot.isBlank()
                && !List.of("mainhand", "offhand", "head", "helmet", "chest", "chestplate",
                "legs", "leggings", "feet", "boots").contains(slot.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown equipment slot: " + slot);
        }
        resolve(enchantmentName);
    }

    public int apply(List<Player> players, String scope, String enchantmentName, int level) {
        validateApply(scope, enchantmentName, level);
        Enchantment enchantment = resolve(enchantmentName);
        if (level < 1 || level > enchantment.getMaxLevel()) {
            throw new IllegalArgumentException("Level must be 1-" + enchantment.getMaxLevel() + " for " + enchantmentName + ".");
        }
        int changed = 0;
        for (Player player : players) {
            for (ItemStack item : select(player.getInventory(), scope)) {
                if (item == null || item.getType().isAir() || !enchantment.canEnchantItem(item)) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;
                meta.addEnchant(enchantment, level, false);
                item.setItemMeta(meta);
                changed++;
            }
            player.updateInventory();
        }
        return changed;
    }

    public int remove(List<Player> players, String scope, String slot, String enchantmentName) {
        validateRemove(scope, slot, enchantmentName);
        Enchantment enchantment = resolve(enchantmentName);
        int changed = 0;
        for (Player player : players) {
            List<ItemStack> items = select(player.getInventory(), scope);
            if (slot != null && !slot.isBlank()) {
                items = switch (slot.toLowerCase(Locale.ROOT)) {
                    case "mainhand" -> Collections.singletonList(player.getInventory().getItemInMainHand());
                    case "offhand" -> Collections.singletonList(player.getInventory().getItemInOffHand());
                    case "head", "helmet" -> Collections.singletonList(player.getInventory().getHelmet());
                    case "chest", "chestplate" -> Collections.singletonList(player.getInventory().getChestplate());
                    case "legs", "leggings" -> Collections.singletonList(player.getInventory().getLeggings());
                    case "feet", "boots" -> Collections.singletonList(player.getInventory().getBoots());
                    default -> throw new IllegalArgumentException("Unknown equipment slot: " + slot);
                };
            }
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir() || !item.containsEnchantment(enchantment)) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.removeEnchant(enchantment);
                    item.setItemMeta(meta);
                    changed++;
                }
            }
            player.updateInventory();
        }
        return changed;
    }

    private List<ItemStack> select(PlayerInventory inventory, String scope) {
        List<ItemStack> result = new ArrayList<>();
        switch (scope.toLowerCase(Locale.ROOT)) {
            case "all" -> {
                result.addAll(Arrays.asList(inventory.getStorageContents()));
                result.addAll(Arrays.asList(inventory.getArmorContents()));
                result.add(inventory.getItemInOffHand());
            }
            case "armor" -> result.addAll(Arrays.asList(inventory.getArmorContents()));
            case "equipment" -> {
                result.addAll(Arrays.asList(inventory.getArmorContents()));
                result.add(inventory.getItemInMainHand());
                result.add(inventory.getItemInOffHand());
            }
            case "hand" -> {
                result.add(inventory.getItemInMainHand());
                result.add(inventory.getItemInOffHand());
            }
            case "mainhand" -> result.add(inventory.getItemInMainHand());
            case "offhand" -> result.add(inventory.getItemInOffHand());
            case "inventory" -> result.addAll(Arrays.asList(inventory.getStorageContents()));
            default -> throw new IllegalArgumentException("Scope must be all, armor, equipment, hand, mainhand, offhand or inventory.");
        }
        return result;
    }
}