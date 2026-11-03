package com.ultraop.uop.listener;

import com.ultraop.uop.profile.PlayerProfileService;
import com.ultraop.uop.service.DamageService;
import com.ultraop.uop.service.FreezeImmortalService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class GameplayListener implements Listener {
    private final PlayerProfileService profiles;
    private final FreezeImmortalService freeze;
    private final DamageService damage;

    public GameplayListener(PlayerProfileService profiles, FreezeImmortalService freeze, DamageService damage) {
        this.profiles = profiles;
        this.freeze = freeze;
        this.damage = damage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.observe(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!freeze.frozen(player) || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean moved = from.getWorld() != to.getWorld()
                || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean rotated = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (moved || (freeze.full(player) && rotated)) {
            Location allowed = from.clone();
            if (!freeze.full(player)) {
                allowed.setYaw(to.getYaw());
                allowed.setPitch(to.getPitch());
            }
            event.setTo(allowed);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player player) attacker = player;
            else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) attacker = player;
        }
        event.setDamage(event.getDamage() * damage.multiplier(attacker, victim));
        if (freeze.immortal(victim) && event.getFinalDamage() >= victim.getHealth() + victim.getAbsorptionAmount()) {
            event.setCancelled(true);
            victim.setAbsorptionAmount(0.0);
            victim.setHealth(1.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (freeze.frozen(player) || profiles.locked(player, "drop") || profiles.locked(player, "inventory")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && freeze.frozen(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (freeze.full(player) || profiles.locked(player, "inventory")) {
            event.setCancelled(true);
            return;
        }
        if (profiles.locked(player, "armor") && isArmorSlotOrTransfer(event)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (freeze.full(player) || profiles.locked(player, "inventory")) {
            event.setCancelled(true);
            return;
        }
        if (profiles.locked(player, "armor") && event.getRawSlots().stream().anyMatch(slot -> slot >= 5 && slot <= 8)) {
            event.setCancelled(true);
        }
    }

    private boolean isArmorSlotOrTransfer(InventoryClickEvent event) {
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) return true;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                || event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            return isArmor(event.getCurrentItem());
        }
        return event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR;
    }

    private boolean isArmor(org.bukkit.inventory.ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET");
    }
}