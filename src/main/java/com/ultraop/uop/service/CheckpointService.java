package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CheckpointService {
    public static final String BACK_CHECKPOINT = "_uop_back";
    private final UopStore store;

    public CheckpointService(UopStore store) {
        this.store = store;
    }

    private String key(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("Checkpoint name must be 1-48 letters, numbers, '.', '_' or '-'.");
        }
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    public int save(String name, List<Player> players) {
        return save(name, players, true);
    }

    /** Save the pre-teleport state without removing other players' back snapshots. */
    public void saveBack(Player player) {
        save(BACK_CHECKPOINT, List.of(player), false);
    }

    public int restoreBack(List<Player> players) {
        if (!store.data().contains("checkpoints." + key(BACK_CHECKPOINT))) return 0;
        return restore(BACK_CHECKPOINT, players);
    }

    public boolean hasBackSnapshot(String playerName) {
        return hasSnapshot(BACK_CHECKPOINT, playerName);
    }

    private int save(String name, List<Player> players, boolean replaceExisting) {
        String checkpoint = key(name);
        if (players.isEmpty()) throw new IllegalArgumentException("No online players were selected.");
        String root = "checkpoints." + checkpoint;
        if (replaceExisting) store.data().set(root + ".players", null);
        store.data().set(root + ".display-name", name);
        int saved = 0;
        for (Player player : players) {
            String base = root + ".players." + player.getName().toLowerCase(java.util.Locale.ROOT);
            Location location = player.getLocation();
            store.data().set(base + ".world", location.getWorld().getUID().toString());
            store.data().set(base + ".world-name", location.getWorld().getName());
            store.data().set(base + ".x", location.getX());
            store.data().set(base + ".y", location.getY());
            store.data().set(base + ".z", location.getZ());
            store.data().set(base + ".yaw", location.getYaw());
            store.data().set(base + ".pitch", location.getPitch());
            store.data().set(base + ".storage", cloneItems(player.getInventory().getStorageContents()));
            store.data().set(base + ".armor", cloneItems(player.getInventory().getArmorContents()));
            ItemStack offhand = player.getInventory().getItemInOffHand();
            store.data().set(base + ".offhand", offhand == null ? null : offhand.clone());
            store.data().set(base + ".held-slot", player.getInventory().getHeldItemSlot());
            store.data().set(base + ".health", player.getHealth());
            store.data().set(base + ".level", player.getLevel());
            store.data().set(base + ".exp", player.getExp());
            store.data().set(base + ".total-experience", player.getTotalExperience());
            store.data().set(base + ".fire-ticks", player.getFireTicks());
            store.data().set(base + ".fall-distance", player.getFallDistance());
            store.data().set(base + ".velocity", player.getVelocity());
            store.data().set(base + ".game-mode", player.getGameMode().name());
            store.data().set(base + ".food", player.getFoodLevel());
            store.data().set(base + ".saturation", player.getSaturation());
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
        for (String child : section.getKeys(false)) {
            if (child.equalsIgnoreCase(BACK_CHECKPOINT)) continue;
            names.add(section.getString(child + ".display-name", child));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean delete(String name) {
        String checkpoint = key(name);
        if (checkpoint.equalsIgnoreCase(BACK_CHECKPOINT)) {
            throw new IllegalArgumentException("The internal back snapshot cannot be deleted directly.");
        }
        if (!store.data().contains("checkpoints." + checkpoint)) return false;
        store.data().set("checkpoints." + checkpoint, null);
        store.save();
        return true;
    }

    public void clear() {
        store.data().set("checkpoints", null);
        store.save();
    }

    public boolean hasSnapshot(String name, String playerName) {
        String checkpoint = key(name);
        return store.data().contains("checkpoints." + checkpoint + ".players." + playerName.toLowerCase(java.util.Locale.ROOT) + ".world");
    }

    public int restore(String name, List<Player> players) {
        String checkpoint = key(name);
        if (!store.data().contains("checkpoints." + checkpoint)) throw new IllegalArgumentException("Checkpoint not found: " + name);
        int restored = 0;
        for (Player player : players) {
            String base = "checkpoints." + checkpoint + ".players." + player.getName().toLowerCase();
            if (!store.data().contains(base + ".world")) continue;
            World world = null;
            String worldId = store.data().getString(base + ".world", "");
            try {
                if (!worldId.isBlank()) world = Bukkit.getWorld(UUID.fromString(worldId));
            } catch (IllegalArgumentException ignored) {
                // Older snapshots may contain an invalid or obsolete UUID; try its stored world name.
            }
            if (world == null) {
                String worldName = store.data().getString(base + ".world-name", "");
                if (!worldName.isBlank()) world = Bukkit.getWorld(worldName);
            }
            if (world == null) continue;
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

            if (!player.teleport(target)) continue;
            player.getInventory().setStorageContents(contents);
            player.getInventory().setArmorContents(armorContents);
            Object offhand = store.data().get(base + ".offhand");
            player.getInventory().setItemInOffHand(offhand instanceof ItemStack item ? item.clone() : null);
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, store.data().getInt(base + ".held-slot", 0))));
            player.setFoodLevel(Math.max(0, Math.min(20, store.data().getInt(base + ".food", 20))));
            Object saturationValue = store.data().get(base + ".saturation");
            float saturation = saturationValue instanceof Number number ? number.floatValue() : 5.0f;
            player.setSaturation(Math.max(0.0f, saturation));
            double health = store.data().getDouble(base + ".health", 20.0);
            player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), health)));
            if (store.data().contains(base + ".level")) player.setLevel(Math.max(0, store.data().getInt(base + ".level", 0)));
            if (store.data().contains(base + ".exp")) player.setExp(Math.max(0.0f, Math.min(1.0f, (float) store.data().getDouble(base + ".exp", 0.0))));
            if (store.data().contains(base + ".total-experience")) player.setTotalExperience(Math.max(0, store.data().getInt(base + ".total-experience", 0)));
            if (store.data().contains(base + ".fire-ticks")) player.setFireTicks(Math.max(0, store.data().getInt(base + ".fire-ticks", 0)));
            if (store.data().contains(base + ".fall-distance")) player.setFallDistance(Math.max(0.0f, (float) store.data().getDouble(base + ".fall-distance", 0.0)));
            Object velocity = store.data().get(base + ".velocity");
            if (velocity instanceof Vector vector) player.setVelocity(vector.clone());
            String gameMode = store.data().getString(base + ".game-mode", "");
            try {
                if (!gameMode.isBlank()) player.setGameMode(GameMode.valueOf(gameMode));
            } catch (IllegalArgumentException ignored) {
                // Preserve the current mode when a legacy snapshot contains an unknown value.
            }
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
            player.updateInventory();
            restored++;
        }
        return restored;
    }
}