package pl.runes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

public class RuneListener implements Listener {

    private final RuneManager manager;

    public RuneListener(RuneManager manager) {
        this.manager = manager;
    }

    // Right click with a rune = use ability
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        RuneType t = manager.getRune(e.getItem());
        if (t == null) return;
        e.setCancelled(true);
        manager.use(e.getPlayer(), t);
    }

    // Rune type limit: block picking up more types than allowed
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!manager.canPickup(p, e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- crafting

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        // runes cannot be used as ingredients (e.g. decorated pots)
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (manager.getRune(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
        // a rune that was already crafted cannot be crafted again
        RuneType out = manager.getRune(e.getInventory().getResult());
        if (out != null && manager.craftingLimited() && manager.isCrafted(out)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        RuneType t = manager.getRune(e.getRecipe().getResult());
        if (t == null || !manager.craftingLimited()) return;
        if (manager.isCrafted(t)) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(ChatColor.RED + t.displayName + " zostala juz stworzona na tym serwerze!");
            return;
        }
        if (e.isShiftClick()) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(ChatColor.RED + "Kliknij runę zwykłym kliknięciem (bez Shift).");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftDone(CraftItemEvent e) {
        RuneType t = manager.getRune(e.getRecipe().getResult());
        if (t == null || !manager.craftingLimited()) return;
        if (!manager.isCrafted(t)) {
            manager.markCrafted(t);
            Bukkit.broadcastMessage(ChatColor.GOLD + e.getWhoClicked().getName() + ChatColor.YELLOW
                    + " stworzyl " + ChatColor.GOLD + t.displayName + ChatColor.YELLOW + "! Nikt inny juz jej nie stworzy.");
        }
    }

    // Crafter blocks cannot produce runes
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (manager.getRune(e.getResult()) != null) e.setCancelled(true);
    }

    // ---------------------------------------------- runes cannot go into containers

    private boolean isRune(ItemStack it) {
        return manager.getRune(it) != null;
    }

    private boolean isBundle(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta() instanceof BundleMeta;
    }

    /** Anything that is not the player's own inventory / 2x2 crafting grid counts as a container. */
    private boolean isExternal(Inventory inv) {
        InventoryType t = inv.getType();
        return t != InventoryType.CRAFTING && t != InventoryType.PLAYER && t != InventoryType.CREATIVE;
    }

    private void denyContainer(InventoryClickEvent e) {
        e.setCancelled(true);
        e.getWhoClicked().sendMessage(ChatColor.RED + "Run nie mozna wkladac do pojemnikow!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        // bundles (either direction)
        if ((isBundle(e.getCurrentItem()) && isRune(e.getCursor()))
                || (isBundle(e.getCursor()) && isRune(e.getCurrentItem()))) {
            denyContainer(e);
            return;
        }

        Inventory top = e.getView().getTopInventory();
        if (!isExternal(top)) return;

        boolean clickedTop = e.getRawSlot() >= 0 && e.getRawSlot() < top.getSize();
        Player p = e.getWhoClicked() instanceof Player pl ? pl : null;

        if (clickedTop) {
            // rune on the cursor dropped into the container
            if (isRune(e.getCursor())) {
                denyContainer(e);
                return;
            }
            // number key swap from hotbar
            if (e.getClick() == ClickType.NUMBER_KEY && p != null && e.getHotbarButton() >= 0
                    && isRune(p.getInventory().getItem(e.getHotbarButton()))) {
                denyContainer(e);
                return;
            }
            // offhand swap key
            if (e.getClick() == ClickType.SWAP_OFFHAND && p != null
                    && isRune(p.getInventory().getItemInOffHand())) {
                denyContainer(e);
            }
        } else if (e.getClickedInventory() != null && e.isShiftClick() && isRune(e.getCurrentItem())) {
            // shift-click from own inventory into the container
            denyContainer(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isExternal(top) || !isRune(e.getOldCursor())) return;
        int size = top.getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < size) {
                e.setCancelled(true);
                e.getWhoClicked().sendMessage(ChatColor.RED + "Run nie mozna wkladac do pojemnikow!");
                return;
            }
        }
    }

    // hoppers, droppers and hopper minecarts never move or pick up runes
    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent e) {
        if (isRune(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (isRune(e.getItem().getItemStack())) e.setCancelled(true);
    }

    // item frames, armor stands, allays
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (!(ent instanceof ItemFrame || ent instanceof ArmorStand || ent instanceof Allay)) return;
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isRune(hand)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isRune(e.getPlayerItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------- abilities

    // No fall damage: Wind slam, and passively with Wind / Breeze rune
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p && manager.hasNoFall(p)) {
            e.setCancelled(true);
        }
    }

    // HeartBreak rune tracks the last entity hit
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (damager instanceof Player p && e.getEntity() instanceof LivingEntity victim && !p.equals(victim)) {
            manager.recordHit(p, victim);
        }
    }

    // Frost rune: frozen players cannot move (they can still look around)
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!manager.isFrozen(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    // Explosion rune: custom explosion handling
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Fireball fb && manager.isRuneFireball(fb)) {
            manager.explodeFireball(fb);
            fb.remove();
        }
    }

    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        if (manager.isRuneFireball(e.getEntity())) e.setCancelled(true);
    }

    // Ancient rune: cage blocks are unbreakable
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (manager.isProtected(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(manager::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(manager::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(manager::isProtected)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(manager::isProtected)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (manager.isProtected(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.onQuit(e.getPlayer());
    }
}
