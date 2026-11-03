package com.ultraop.uop.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ArmorKitService {
    private static final List<String> MATERIALS = List.of("iron", "gold", "diamond", "netherite", "mixed");

    public List<String> materials() {
        return MATERIALS;
    }

    public boolean giveDirect(Player player, String requestedMaterial, int level) {
        String material = requestedMaterial.toLowerCase();
        if (!MATERIALS.contains(material)) throw new IllegalArgumentException("Material must be iron, gold, diamond, netherite or mixed.");
        if (level < 1 || level > 5) throw new IllegalArgumentException("Enchantment preset level must be 1-5.");

        PlayerInventory current = player.getInventory();
        Inventory staging = Bukkit.createInventory(null, 36);
        staging.setContents(current.getStorageContents());

        ItemStack[] oldArmor = current.getArmorContents();
        for (ItemStack item : oldArmor) {
            if (item != null && !item.getType().isAir()) {
                if (!staging.addItem(item.clone()).isEmpty()) return false;
            }
        }

        String helmetMaterial = pickMaterial(material);
        String chestMaterial = pickMaterial(material);
        String legsMaterial = pickMaterial(material);
        String bootsMaterial = pickMaterial(material);
        String weaponMaterial = pickMaterial(material);

        ItemStack helmet = enchanted(armor(helmetMaterial, "HELMET"), "armor", "helmet", level);
        ItemStack chest = enchanted(armor(chestMaterial, "CHESTPLATE"), "armor", "chest", level);
        ItemStack legs = enchanted(armor(legsMaterial, "LEGGINGS"), "armor", "legs", level);
        ItemStack boots = enchanted(armor(bootsMaterial, "BOOTS"), "armor", "boots", level);
        ItemStack sword = enchanted(weapon(weaponMaterial, "SWORD"), "sword", "sword", level);
        ItemStack pickaxe = enchanted(weapon(weaponMaterial, "PICKAXE"), "pickaxe", "pickaxe", level);

        List<ItemStack> kitItems = new ArrayList<>();
        kitItems.add(sword);
        kitItems.add(pickaxe);
        kitItems.add(new ItemStack(Material.BREAD, 16));
        kitItems.add(new ItemStack(Material.COOKED_BEEF, 16));
        for (ItemStack item : kitItems) {
            if (!staging.addItem(item).isEmpty()) return false;
        }

        // Nothing is changed until capacity and every item have been validated.
        current.setStorageContents(staging.getContents());
        current.setHelmet(helmet);
        current.setChestplate(chest);
        current.setLeggings(legs);
        current.setBoots(boots);
        player.updateInventory();
        return true;
    }

    private String pickMaterial(String material) {
        if (!material.equals("mixed")) return material;
        return List.of("iron", "gold", "diamond", "netherite")
                .get(ThreadLocalRandom.current().nextInt(4));
    }

    private ItemStack armor(String material, String piece) {
        String prefix = switch (material) {
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> throw new IllegalArgumentException("Unsupported armor material: " + material);
        };
        return new ItemStack(Material.valueOf(prefix + "_" + piece));
    }

    private ItemStack weapon(String material, String type) {
        String prefix = switch (material) {
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> throw new IllegalArgumentException("Unsupported weapon material: " + material);
        };
        return new ItemStack(Material.valueOf(prefix + "_" + type));
    }

    private ItemStack enchanted(ItemStack item, String kind, String slot, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.MENDING, 1, true);
        if (level >= 2) meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        if (level >= 3) meta.addEnchant(Enchantment.UNBREAKING, 3, true);

        if (kind.equals("armor")) {
            if (level >= 3) meta.addEnchant(Enchantment.PROTECTION, level >= 4 ? 4 : 3, true);
            if (level >= 4 && slot.equals("boots")) meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            if (level >= 4 && slot.equals("helmet")) {
                meta.addEnchant(Enchantment.RESPIRATION, 3, true);
                meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
            }
            if (level >= 5) {
                meta.addEnchant(Enchantment.THORNS, 3, true);
                if (slot.equals("boots")) {
                    meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
                    meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
                }
                if (slot.equals("legs")) meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
            }
        } else if (kind.equals("sword")) {
            if (level >= 2) meta.addEnchant(Enchantment.SHARPNESS, level >= 4 ? 5 : (level == 3 ? 3 : 2), true);
            if (level >= 3) meta.addEnchant(Enchantment.LOOTING, level >= 5 ? 3 : 2, true);
            if (level >= 4) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            }
            if (level >= 5) meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
        } else if (kind.equals("pickaxe")) {
            if (level >= 2) meta.addEnchant(Enchantment.EFFICIENCY, level >= 4 ? 5 : (level == 3 ? 3 : 2), true);
            if (level >= 3) meta.addEnchant(Enchantment.FORTUNE, level >= 5 ? 3 : 2, true);
        }
        item.setItemMeta(meta);
        return item;
    }
}