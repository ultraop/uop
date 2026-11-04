package com.ultraop.uop.listener;

import com.ultraop.uop.profile.PlayerProfileService;
import com.ultraop.uop.service.DamageService;
import com.ultraop.uop.service.FreezeImmortalService;
import com.ultraop.uop.service.PendingActionService;
import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameplayListener implements Listener {
    private final PlayerProfileService profiles;
    private final FreezeImmortalService freeze;
    private final DamageService damage;
    private final PendingActionService pending;
    private final JavaPlugin plugin;

    public GameplayListener(PlayerProfileService profiles, FreezeImmortalService freeze, DamageService damage,
                            PendingActionService pending, JavaPlugin plugin) {
        this.profiles = profiles;
        this.freeze = freeze;
        this.damage = damage;
        this.pending = pending;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        profiles.observe(player);
        // Defer inventory/teleport mutations until the join event has completed.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            int applied = pending.applyOnJoin(player);
            if (applied > 0) player.sendMessage("§7[§6UOP§7] §aRestored " + applied + " pending Team action(s).");
        });
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
    public void onTeleport(PlayerTeleportEvent event) {
        if (freeze.frozen(event.getPlayer())
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
            event.setCancelled(true);
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
        if (freeze.full(victim) || (attacker != null && freeze.frozen(attacker))
                || damage.friendlyFireDenied(attacker, victim)) {
            event.setCancelled(true);
            return;
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
        if (freeze.frozen(player) || profiles.locked(player, "drop")
                || profiles.locked(player, "inventory")
                || (profiles.locked(player, "armor") && isArmor(event.getItemDrop().getItemStack()))) {
            event.setCancelled(true);
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (freeze.full(player) || profiles.locked(player, "inventory")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (freeze.full(player) || profiles.locked(player, "inventory")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (freeze.full(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && (freeze.frozen(player) || profiles.locked(player, "inventory"))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        boolean rightClick = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        if (profiles.locked(player, "armor") && rightClick && isArmor(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (!freeze.frozen(player)) return;
        boolean armorEquip = !freeze.full(player) && isArmor(event.getItem()) && rightClick;
        if (!armorEquip) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (freeze.frozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
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
        if (profiles.locked(player, "armor") && event.getRawSlots().stream().anyMatch(rawSlot ->
                event.getView().getInventory(rawSlot).equals(player.getInventory())
                        && event.getView().getSlotType(rawSlot) == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR)) {
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