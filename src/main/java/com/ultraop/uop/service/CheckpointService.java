package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.util.Vector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

public final class CheckpointService {
    private final UopStore store;

    public CheckpointService(UopStore store) {
        this.store = store;
    }

    private String key(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("Checkpoint name must be 1-48 letters, numbers, '.', '_' or '-'.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    public int save(String name, List<Player> players) {
        String checkpoint = key(name);
        if (players.isEmpty()) throw new IllegalArgumentException("No online players were selected.");
        String root = "checkpoints." + checkpoint;
        // A repeated save replaces the whole roster; old players must not leak into the new snapshot.
        store.data().set(root + ".players", null);
        store.data().set(root + ".return", null);
        store.data().set(root + ".display-name", name);
        int saved = 0;
        for (Player player : players) {
            String base = root + ".players." + player.getName().toLowerCase();
            Location location = player.getLocation();
            store.data().set(base + ".world", location.getWorld().getUID().toString());
            store.data().set(base + ".world-name", location.getWorld().getName());
            store.data().set(base + ".x", location.getX());
            store.data().set(base + ".y", location.getY());
            store.data().set(base + ".z", location.getZ());
            store.data().set(base + ".yaw", location.getYaw());
            store.data().set(base + ".pitch", location.getPitch());
            Vector velocity = player.getVelocity();
            store.data().set(base + ".velocity-x", velocity.getX());
            store.data().set(base + ".velocity-y", velocity.getY());
            store.data().set(base + ".velocity-z", velocity.getZ());
            store.data().set(base + ".storage", cloneItems(player.getInventory().getStorageContents()));
            store.data().set(base + ".armor", cloneItems(player.getInventory().getArmorContents()));
            ItemStack offhand = player.getInventory().getItemInOffHand();
            store.data().set(base + ".offhand", offhand == null ? null : offhand.clone());
            store.data().set(base + ".held-slot", player.getInventory().getHeldItemSlot());
            store.data().set(base + ".health", player.getHealth());
            store.data().set(base + ".food", player.getFoodLevel());
            store.data().set(base + ".saturation", player.getSaturation());
            store.data().set(base + ".level", player.getLevel());
            store.data().set(base + ".exp", player.getExp());
            store.data().set(base + ".total-experience", player.getTotalExperience());
            store.data().set(base + ".fire-ticks", player.getFireTicks());
            store.data().set(base + ".fall-distance", player.getFallDistance());
            store.data().set(base + ".game-mode", player.getGameMode().name());
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

    private List<ItemStack> cloneItems(ItemStack[] source) {
        List<ItemStack> result = new ArrayList<>(source.length);
        for (ItemStack item : source) result.add(item == null ? null : item.clone());
        return result;
    }

    public List<String> list() {
        ConfigurationSection section = store.data().getConfigurationSection("checkpoints");
        if (section == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String child : section.getKeys(false)) names.add(section.getString(child + ".display-name", child));
        return names;
    }

    public boolean delete(String name) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) return false;
        store.createBackup("checkpoint-delete");
        store.data().set("checkpoints." + checkpoint, null);
        store.save();
        return true;
    }

    public void clear() {
        store.createBackup("checkpoint-clear");
        store.data().set("checkpoints", null);
        store.save();
    }

    public boolean hasSnapshot(String name, String playerName) {
        String checkpoint = key(name);
        return store.data().contains("checkpoints." + checkpoint + ".players." + playerName.toLowerCase() + ".world");
    }

    public int restore(String name, List<Player> players) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) throw new IllegalArgumentException("Checkpoint not found: " + name);
        int restored = 0;
        for (Player player : players) {
            String base = "checkpoints." + checkpoint + ".players." + player.getName().toLowerCase();
            if (!store.data().contains(base + ".world")) continue;
            World world;
            try {
                world = Bukkit.getWorld(UUID.fromString(store.data().getString(base + ".world")));
            } catch (Exception ex) {
                world = null;
            }
            if (world == null) {
                String worldName = store.data().getString(base + ".world-name");
                if (worldName != null) world = Bukkit.getWorld(worldName);
            }
            if (world == null) continue;
            Location target = new Location(world,
                    store.data().getDouble(base + ".x"),
                    store.data().getDouble(base + ".y"),
                    store.data().getDouble(base + ".z"),
                    (float) store.data().getDouble(base + ".yaw"),
                    (float) store.data().getDouble(base + ".pitch"));

            // Keep a one-step return location so /checkpoint back has distinct semantics.
            Location previous = player.getLocation();

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

            if (!player.teleport(target)) continue;
            String returnBase = "checkpoints." + checkpoint + ".return." + player.getName().toLowerCase(Locale.ROOT);
            store.data().set(returnBase + ".world", previous.getWorld().getUID().toString());
            store.data().set(returnBase + ".world-name", previous.getWorld().getName());
            store.data().set(returnBase + ".x", previous.getX());
            store.data().set(returnBase + ".y", previous.getY());
            store.data().set(returnBase + ".z", previous.getZ());
            store.data().set(returnBase + ".yaw", previous.getYaw());
            store.data().set(returnBase + ".pitch", previous.getPitch());
            player.getInventory().setStorageContents(contents);
            player.getInventory().setArmorContents(armorContents);
            Object offhand = store.data().get(base + ".offhand");
            player.getInventory().setItemInOffHand(offhand instanceof ItemStack item ? item.clone() : null);
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, store.data().getInt(base + ".held-slot", 0))));
            player.setFoodLevel(Math.max(0, Math.min(20, store.data().getInt(base + ".food", 20))));
            Object saturationValue = store.data().get(base + ".saturation");
            float saturation = saturationValue instanceof Number number ? number.floatValue() : 5.0f;
            player.setSaturation(Math.max(0.0f, saturation));
            player.setLevel(Math.max(0, store.data().getInt(base + ".level", 0)));
            float experience = (float) store.data().getDouble(base + ".exp", 0.0);
            player.setExp(Math.max(0.0f, Math.min(1.0f, experience)));
            player.setTotalExperience(Math.max(0, store.data().getInt(base + ".total-experience", 0)));
            player.setFireTicks(Math.max(0, store.data().getInt(base + ".fire-ticks", 0)));
            // Avoid carrying fall accumulation or stored momentum into a restored position.
            player.setFallDistance(0.0f);
            String gameMode = store.data().getString(base + ".game-mode");
            if (gameMode != null) {
                try { player.setGameMode(GameMode.valueOf(gameMode)); }
                catch (IllegalArgumentException ignored) { /* Keep the current mode for invalid old data. */ }
            }
            double health = store.data().getDouble(base + ".health", 20.0);
            player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), health)));
            for (PotionEffect existing : player.getActivePotionEffects()) player.removePotionEffect(existing.getType());
            for (Map<?, ?> entry : store.data().getMapList(base + ".effects")) {
                String typeKey = String.valueOf(entry.get("type"));
                NamespacedKey key = NamespacedKey.fromString(typeKey);
                PotionEffectType type = key == null ? null : Registry.POTION_EFFECT_TYPE.get(key);
                if (type == null) continue;
                Object durationValue = entry.get("duration");
                Object amplifierValue = entry.get("amplifier");
                int duration = durationValue instanceof Number n ? n.intValue() : 0;
                int amplifier = amplifierValue instanceof Number n ? n.intValue() : 0;
                boolean ambient = Boolean.TRUE.equals(entry.get("ambient"));
                boolean particles = !Boolean.FALSE.equals(entry.get("particles"));
                boolean icon = !Boolean.FALSE.equals(entry.get("icon"));
                if (duration > 0) player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
            player.setVelocity(new Vector(
                    store.data().getDouble(base + ".velocity-x", 0.0),
                    store.data().getDouble(base + ".velocity-y", 0.0),
                    store.data().getDouble(base + ".velocity-z", 0.0)));
            player.updateInventory();
            restored++;
        }
        if (restored > 0) store.save();
        return restored;
    }

    /** Returns each player to the position saved immediately before the last checkpoint teleport. */
    public int back(String name, List<Player> players) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) {
            throw new IllegalArgumentException("Checkpoint not found: " + name);
        }
        int returned = 0;
        for (Player player : players) {
            String base = "checkpoints." + checkpoint + ".return." + player.getName().toLowerCase(Locale.ROOT);
            if (!store.data().contains(base + ".world")) continue;
            World world = null;
            try { world = Bukkit.getWorld(UUID.fromString(store.data().getString(base + ".world"))); }
            catch (Exception ignored) { /* Try the saved world name below. */ }
            if (world == null) {
                String worldName = store.data().getString(base + ".world-name");
                if (worldName != null) world = Bukkit.getWorld(worldName);
            }
            if (world == null) continue;
            Location target = new Location(world, store.data().getDouble(base + ".x"),
                    store.data().getDouble(base + ".y"), store.data().getDouble(base + ".z"),
                    (float) store.data().getDouble(base + ".yaw"),
                    (float) store.data().getDouble(base + ".pitch"));
            if (player.teleport(target)) {
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0.0f);
                store.data().set(base, null);
                returned++;
            }
        }
        if (returned > 0) store.save();
        return returned;
    }
}