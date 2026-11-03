package com.ultraop.uop.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ArmorKitService {
    private static final List<String> DIRECT_MATERIALS = List.of("iron", "gold", "diamond", "netherite", "mixed");
    private static final List<String> CLASSIC_MATERIALS = List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite");
    private static final List<String> WEAPONS = List.of("sword", "mace", "axe", "bow", "crossbow", "trident", "pickaxe", "shovel", "hoe");
    private static final Set<String> CURSES = Set.of("binding_curse", "vanishing_curse");

    public List<String> materials() {
        return DIRECT_MATERIALS;
    }

    public List<String> classicMaterials() {
        return CLASSIC_MATERIALS;
    }

    public List<String> weaponNames() {
        return WEAPONS;
    }

    public List<String> modes() {
        return List.of("plain", "enchanted", "max");
    }

    public void validateSetArgs(String material, String mode, boolean includeWeapons, List<String> weaponNames) {
        normalizeClassicMaterial(material);
        parseTier(mode);
        if (includeWeapons) createWeapons(weaponNames, mode, parseTier(mode));
        else if (weaponNames != null && !weaponNames.isEmpty()) {
            throw new IllegalArgumentException("Weapon names require weapon_on to be true.");
        }
    }

    public void validateWeaponArgs(String mode, List<String> weaponNames) {
        createWeapons(weaponNames, mode, parseTier(mode));
    }

    /**
     * Legacy-compatible armor set operation with a no-drop replacement policy.
     * Old armor is moved into the player's storage inventory. If that cannot be
     * done without dropping/losing anything, the operation is cancelled.
     */
    public boolean setArmor(Player player, String requestedMaterial, String mode,
                            boolean includeWeapons, List<String> weaponNames) {
        String material = normalizeClassicMaterial(requestedMaterial);
        int tier = parseTier(mode);
        List<ItemStack> newWeapons = includeWeapons ? createWeapons(weaponNames, mode, tier) : List.of();

        PlayerInventory current = player.getInventory();
        Inventory staging = stagedStorage(current);
        for (ItemStack oldArmor : current.getArmorContents()) {
            if (oldArmor == null || oldArmor.getType().isAir()) continue;
            if (!staging.addItem(oldArmor.clone()).isEmpty()) return false;
        }
        for (ItemStack weapon : newWeapons) {
            if (!staging.addItem(weapon.clone()).isEmpty()) return false;
        }

        ItemStack[] armor = new ItemStack[]{
                classicArmor(material, "HELMET"),
                classicArmor(material, "CHESTPLATE"),
                classicArmor(material, "LEGGINGS"),
                classicArmor(material, "BOOTS")
        };
        if (tier > 0) {
            for (int index = 0; index < armor.length; index++) {
                String slot = switch (index) {
                    case 0 -> "helmet";
                    case 1 -> "chest";
                    case 2 -> "legs";
                    default -> "boots";
                };
                applyTierEnchantments(armor[index], tier, slot);
            }
        }

        // Mutate the actual inventory only after every item has fitted in staging.
        current.setStorageContents(cloneItems(staging.getContents()));
        current.setArmorContents(armor);
        player.updateInventory();
        return true;
    }

    /** Adds one or more selected weapons/tools without partial insertion or item drops. */
    public boolean giveWeapons(Player player, String mode, List<String> weaponNames) {
        int tier = parseTier(mode);
        List<ItemStack> additions = createWeapons(weaponNames, mode, tier);
        Inventory staging = stagedStorage(player.getInventory());
        for (ItemStack item : additions) {
            if (!staging.addItem(item.clone()).isEmpty()) return false;
        }
        player.getInventory().setStorageContents(cloneItems(staging.getContents()));
        player.updateInventory();
        return true;
    }

    /** Removes equipped armor by returning it to storage; never drops overflow. */
    public boolean removeArmor(Player player) {
        PlayerInventory current = player.getInventory();
        Inventory staging = stagedStorage(current);

        // The main-hand item already occupies the selected hotbar slot. Move it
        // through the staging inventory so it is returned exactly once.
        int heldSlot = current.getHeldItemSlot();
        ItemStack mainHand = staging.getItem(heldSlot);
        staging.setItem(heldSlot, null);
        if (mainHand != null && !mainHand.getType().isAir()
                && !staging.addItem(mainHand.clone()).isEmpty()) return false;

        ItemStack offhand = current.getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()
                && !staging.addItem(offhand.clone()).isEmpty()) return false;

        for (ItemStack oldArmor : current.getArmorContents()) {
            if (oldArmor == null || oldArmor.getType().isAir()) continue;
            if (!staging.addItem(oldArmor.clone()).isEmpty()) return false;
        }

        current.setStorageContents(cloneItems(staging.getContents()));
        current.setArmorContents(new ItemStack[4]);
        current.setItemInOffHand(null);
        player.updateInventory();
        return true;
    }

    public boolean giveDirect(Player player, String requestedMaterial, int level) {
        String material = requestedMaterial.toLowerCase(Locale.ROOT);
        if (!DIRECT_MATERIALS.contains(material)) {
            throw new IllegalArgumentException("Material must be iron, gold, diamond, netherite or mixed.");
        }
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Enchantment preset level must be 1-5.");
        }

        PlayerInventory current = player.getInventory();
        Inventory staging = stagedStorage(current);
        for (ItemStack oldArmor : current.getArmorContents()) {
            if (oldArmor != null && !oldArmor.getType().isAir()
                    && !staging.addItem(oldArmor.clone()).isEmpty()) return false;
        }

        String helmetMaterial = pickMaterial(material);
        String chestMaterial = pickMaterial(material);
        String legsMaterial = pickMaterial(material);
        String bootsMaterial = pickMaterial(material);
        String weaponMaterial = pickMaterial(material);

        ItemStack helmet = enchanted(directArmor(helmetMaterial, "HELMET"), "armor", "helmet", level);
        ItemStack chest = enchanted(directArmor(chestMaterial, "CHESTPLATE"), "armor", "chest", level);
        ItemStack legs = enchanted(directArmor(legsMaterial, "LEGGINGS"), "armor", "legs", level);
        ItemStack boots = enchanted(directArmor(bootsMaterial, "BOOTS"), "armor", "boots", level);
        ItemStack sword = enchanted(directWeapon(weaponMaterial, "SWORD"), "sword", "sword", level);
        ItemStack pickaxe = enchanted(directWeapon(weaponMaterial, "PICKAXE"), "pickaxe", "pickaxe", level);

        List<ItemStack> kitItems = new ArrayList<>();
        kitItems.add(sword);
        kitItems.add(pickaxe);
        kitItems.add(new ItemStack(Material.BREAD, 16));
        kitItems.add(new ItemStack(Material.COOKED_BEEF, level * 4));
        for (ItemStack item : kitItems) {
            if (!staging.addItem(item).isEmpty()) return false;
        }

        current.setStorageContents(cloneItems(staging.getContents()));
        current.setHelmet(helmet);
        current.setChestplate(chest);
        current.setLeggings(legs);
        current.setBoots(boots);
        player.updateInventory();
        return true;
    }

    private Inventory stagedStorage(PlayerInventory inventory) {
        Inventory staging = Bukkit.createInventory(null, 36);
        ItemStack[] contents = cloneItems(inventory.getStorageContents());
        staging.setContents(contents);
        return staging;
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    private String normalizeClassicMaterial(String input) {
        String material = input == null ? "" : input.toLowerCase(Locale.ROOT);
        if (!CLASSIC_MATERIALS.contains(material)) {
            throw new IllegalArgumentException("Armor material must be leather, chainmail, iron, gold, diamond or netherite.");
        }
        return material;
    }

    private ItemStack classicArmor(String material, String piece) {
        String prefix = switch (material) {
            case "leather" -> "LEATHER";
            case "chainmail" -> "CHAINMAIL";
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> throw new IllegalArgumentException("Unsupported armor material: " + material);
        };
        return new ItemStack(Material.valueOf(prefix + "_" + piece));
    }

    private int parseTier(String mode) {
        if (mode == null) throw new IllegalArgumentException("Armor mode is required.");
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("plain")) return 0;
        if (normalized.equals("max")) return 10;
        if (normalized.startsWith("enchanted ")) {
            try {
                int level = Integer.parseInt(normalized.substring("enchanted ".length()).trim());
                if (level >= 1 && level <= 10) return level;
            } catch (NumberFormatException ignored) {
                // Report a consistent user-facing mode error below.
            }
        }
        throw new IllegalArgumentException("Armor mode must be plain, enchanted 1-10, or max.");
    }

    private List<ItemStack> createWeapons(List<String> requested, String mode, int tier) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Select at least one weapon/tool, or set weapon_on to false.");
        }
        List<ItemStack> result = new ArrayList<>();
        for (String raw : requested) {
            String name = raw.toLowerCase(Locale.ROOT);
            Material type = switch (name) {
                case "sword" -> Material.NETHERITE_SWORD;
                case "mace" -> Material.MACE;
                case "axe" -> Material.NETHERITE_AXE;
                case "bow" -> Material.BOW;
                case "crossbow" -> Material.CROSSBOW;
                case "trident" -> Material.TRIDENT;
                case "pickaxe" -> Material.NETHERITE_PICKAXE;
                case "shovel" -> Material.NETHERITE_SHOVEL;
                case "hoe" -> Material.NETHERITE_HOE;
                default -> throw new IllegalArgumentException("Unknown weapon/tool: " + raw);
            };
            ItemStack item = new ItemStack(type);
            if (tier > 0) applyTierEnchantments(item, tier, name);
            result.add(item);
        }
        return result;
    }

    private void applyTierEnchantments(ItemStack item, int tier, String slot) {
        if (item == null || item.getType().isAir() || tier <= 0) return;
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            String id = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
            if (CURSES.contains(id) || id.endsWith("_curse") || !enchantment.canEnchantItem(item)) continue;
            if (!allowedAtTier(id, tier)) continue;
            int level = tier >= 9 ? enchantment.getMaxLevel() : Math.min(tier, enchantment.getMaxLevel());
            if (level < 1) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
    }

    private boolean allowedAtTier(String id, int tier) {
        if (tier == 1) return id.equals("mending");
        if (tier == 2) return id.equals("mending") || id.equals("unbreaking");
        if (tier <= 4) return id.equals("mending") || id.equals("unbreaking")
                || id.endsWith("protection") || id.equals("feather_falling") || id.equals("respiration");
        if (tier <= 6) return allowedAtTier(id, 4) || id.equals("aqua_affinity")
                || id.equals("depth_strider") || id.equals("soul_speed") || id.equals("swift_sneak");
        if (tier <= 8) return allowedAtTier(id, 6) || id.equals("thorns") || id.equals("frost_walker")
                || id.equals("silk_touch") || id.equals("fortune");
        return true;
    }

    private String pickMaterial(String material) {
        if (!material.equals("mixed")) return material;
        return List.of("iron", "gold", "diamond", "netherite")
                .get(ThreadLocalRandom.current().nextInt(4));
    }

    private ItemStack directArmor(String material, String piece) {
        String prefix = switch (material) {
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> throw new IllegalArgumentException("Unsupported direct armor material: " + material);
        };
        return new ItemStack(Material.valueOf(prefix + "_" + piece));
    }

    private ItemStack directWeapon(String material, String type) {
        String prefix = switch (material) {
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> throw new IllegalArgumentException("Unsupported direct weapon material: " + material);
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