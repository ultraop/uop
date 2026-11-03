package com.ultraop.uop.service;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
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

    public int apply(List<Player> players, String scope, String enchantmentName, int level) {
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
        Enchantment enchantment = resolve(enchantmentName);
        int changed = 0;
        for (Player player : players) {
            List<ItemStack> items = select(player.getInventory(), scope);
            if (slot != null && !slot.isBlank()) {
                items = switch (slot.toLowerCase(Locale.ROOT)) {
                    case "mainhand" -> new ArrayList<>(List.of(safe(player.getInventory().getItemInMainHand())));
                    case "offhand" -> new ArrayList<>(List.of(safe(player.getInventory().getItemInOffHand())));
                    case "head", "helmet" -> new ArrayList<>(List.of(safe(player.getInventory().getHelmet())));
                    case "chest", "chestplate" -> new ArrayList<>(List.of(safe(player.getInventory().getChestplate())));
                    case "legs", "leggings" -> new ArrayList<>(List.of(safe(player.getInventory().getLeggings())));
                    case "feet", "boots" -> new ArrayList<>(List.of(safe(player.getInventory().getBoots())));
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

    private ItemStack safe(ItemStack item) {
        return item == null ? null : item;
    }

    private List<ItemStack> select(PlayerInventory inventory, String scope) {
        List<ItemStack> result = new ArrayList<>();
        switch (scope.toLowerCase(Locale.ROOT)) {
            case "all" -> {
                result.addAll(List.of(inventory.getStorageContents()));
                result.addAll(List.of(inventory.getArmorContents()));
                result.add(inventory.getItemInMainHand());
                result.add(inventory.getItemInOffHand());
            }
            case "armor" -> result.addAll(List.of(inventory.getArmorContents()));
            case "equipment" -> {
                result.addAll(List.of(inventory.getArmorContents()));
                result.add(inventory.getItemInMainHand());
                result.add(inventory.getItemInOffHand());
            }
            case "mainhand" -> result.add(inventory.getItemInMainHand());
            case "offhand" -> result.add(inventory.getItemInOffHand());
            case "inventory" -> result.addAll(List.of(inventory.getStorageContents()));
            default -> throw new IllegalArgumentException("Scope must be all, armor, equipment, mainhand, offhand or inventory.");
        }
        return result;
    }
}