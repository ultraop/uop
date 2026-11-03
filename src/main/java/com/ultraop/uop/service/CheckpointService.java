package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CheckpointService {
    private final UopStore store;

    public CheckpointService(UopStore store) {
        this.store = store;
    }

    private String key(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("Checkpoint name must be 1-48 letters, numbers, '.', '_' or '-'.");
        }
        return name.toLowerCase();
    }

    public int save(String name, List<Player> players) {
        String checkpoint = key(name);
        if (players.isEmpty()) throw new IllegalArgumentException("No online players were selected.");
        String root = "checkpoints." + checkpoint;
        store.data().set(root + ".display-name", name);
        int saved = 0;
        for (Player player : players) {
            String base = root + ".players." + player.getName().toLowerCase();
            Location location = player.getLocation();
            store.data().set(base + ".world", location.getWorld().getUID().toString());
            store.data().set(base + ".x", location.getX());
            store.data().set(base + ".y", location.getY());
            store.data().set(base + ".z", location.getZ());
            store.data().set(base + ".yaw", location.getYaw());
            store.data().set(base + ".pitch", location.getPitch());
            store.data().set(base + ".storage", new ArrayList<>(List.of(player.getInventory().getStorageContents())));
            store.data().set(base + ".armor", new ArrayList<>(List.of(player.getInventory().getArmorContents())));
            store.data().set(base + ".offhand", player.getInventory().getItemInOffHand());
            store.data().set(base + ".held-slot", player.getInventory().getHeldItemSlot());
            store.data().set(base + ".health", player.getHealth());
            store.data().set(base + ".food", player.getFoodLevel());
            List<Map<String, Object>> effects = new ArrayList<>();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("type", effect.getType().getKey().toString());
                entry.put("duration", effect.getDuration());
                entry.put("amplifier", effect.getAmplifier());
                entry.put("ambient", effect.isAmbient());
                entry.put("particles", effect.hasParticles());
                entry.put("icon", effect.hasIcon());
                effects.add(entry);
            }
            store.data().set(base + ".effects", effects);
            store.data().set(base + ".last-name", player.getName());
            store.data().set(base + ".last-uuid", player.getUniqueId().toString());
            saved++;
        }
        store.save();
        return saved;
    }

    public List<String> list() {
        ConfigurationSection section = store.data().getConfigurationSection("checkpoints");
        if (section == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String child : section.getKeys(false)) {
            names.add(section.getString(child + ".display-name", child));
        }
        return names;
    }

    public boolean delete(String name) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) return false;
        store.data().set("checkpoints." + checkpoint, null);
        store.save();
        return true;
    }

    public void clear() {
        store.data().set("checkpoints", null);
        store.save();
    }

    public int restore(String name, List<Player> players) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) throw new IllegalArgumentException("Checkpoint not found: " + name);
        int restored = 0;
        List<String> missing = new ArrayList<>();
        for (Player player : players) {
            String base = "checkpoints." + checkpoint + ".players." + player.getName().toLowerCase();
            if (!store.data().contains(base + ".world")) {
                missing.add(player.getName());
                continue;
            }
            World world;
            try {
                world = Bukkit.getWorld(UUID.fromString(store.data().getString(base + ".world")));
            } catch (Exception ex) {
                world = null;
            }
            if (world == null) {
                missing.add(player.getName() + " (world unavailable)");
                continue;
            }
            Location target = new Location(world,
                    store.data().getDouble(base + ".x"),
                    store.data().getDouble(base + ".y"),
                    store.data().getDouble(base + ".z"),
                    (float) store.data().getDouble(base + ".yaw"),
                    (float) store.data().getDouble(base + ".pitch"));

            List<?> storage = store.data().getList(base + ".storage", List.of());
            ItemStack[] contents = new ItemStack[36];
            for (int i = 0; i < Math.min(36, storage.size()); i++) {
                contents[i] = storage.get(i) instanceof ItemStack item ? item.clone() : null;
            }
            List<?> armor = store.data().getList(base + ".armor", List.of());
            ItemStack[] armorContents = new ItemStack[4];
            for (int i = 0; i < Math.min(4, armor.size()); i++) {
                armorContents[i] = armor.get(i) instanceof ItemStack item ? item.clone() : null;
            }

            player.getInventory().setStorageContents(contents);
            player.getInventory().setArmorContents(armorContents);
            Object offhand = store.data().get(base + ".offhand");
            player.getInventory().setItemInOffHand(offhand instanceof ItemStack item ? item.clone() : null);
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, store.data().getInt(base + ".held-slot", 0))));
            player.setFoodLevel(Math.max(0, Math.min(20, store.data().getInt(base + ".food", 20))));
            double health = store.data().getDouble(base + ".health", 20.0);
            player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), health)));
            for (PotionEffect existing : player.getActivePotionEffects()) player.removePotionEffect(existing.getType());
            for (Map<?, ?> entry : store.data().getMapList(base + ".effects")) {
                String typeKey = String.valueOf(entry.get("type"));
                PotionEffectType type = PotionEffectType.getByKey(org.bukkit.NamespacedKey.fromString(typeKey));
                if (type == null) continue;
                int duration = ((Number) entry.getOrDefault("duration", 0)).intValue();
                int amplifier = ((Number) entry.getOrDefault("amplifier", 0)).intValue();
                boolean ambient = Boolean.TRUE.equals(entry.get("ambient"));
                boolean particles = !Boolean.FALSE.equals(entry.get("particles"));
                boolean icon = !Boolean.FALSE.equals(entry.get("icon"));
                player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
            player.teleport(target);
            player.updateInventory();
            restored++;
        }
        return restored;
    }
}