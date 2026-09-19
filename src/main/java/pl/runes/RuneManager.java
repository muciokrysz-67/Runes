package pl.runes;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RuneManager {

    private final RunePlugin plugin;
    private final NamespacedKey runeKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey breakKey;
    private final NamespacedKey fireballKey;

    private final Map<RuneType, Material> materials = new EnumMap<>(RuneType.class);
    private final Map<UUID, Map<RuneType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Set<RuneType>> activeRunes = new HashMap<>();
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Set<UUID> noFall = new HashSet<>();
    private final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Map<UUID, BukkitTask> heartbreakTasks = new HashMap<>();
    private final List<Map<Block, BlockData>> cages = new ArrayList<>();
    private final Set<Block> protectedBlocks = new HashSet<>();
    private final Set<RuneType> crafted = EnumSet.noneOf(RuneType.class);
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private File craftedFile;

    public RuneManager(RunePlugin plugin) {
        this.plugin = plugin;
        this.runeKey = new NamespacedKey(plugin, "rune");
        this.healthKey = new NamespacedKey(plugin, "rune_health");
        this.breakKey = new NamespacedKey(plugin, "rune_heartbreak");
        this.fireballKey = new NamespacedKey(plugin, "rune_fireball");
        loadMaterials();
        loadCrafted();
    }

    // ------------------------------------------------------------------ setup

    public void reload() {
        plugin.reloadConfig();
        loadMaterials();
        registerRecipes();
    }

    private void loadMaterials() {
        for (RuneType t : RuneType.values()) {
            String name = plugin.getConfig().getString("runes." + t.name() + ".material", t.defaultMaterial.name());
            Material m = Material.matchMaterial(name);
            materials.put(t, m != null ? m : t.defaultMaterial);
        }
    }

    public void startTasks() {
        registerRecipes();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassives, 20L, 20L);
    }

    public void shutdown() {
        unregisterRecipes();
        saveCrafted();
        for (Map<Block, BlockData> cage : new ArrayList<>(cages)) restoreCage(cage);
        for (UUID id : new ArrayList<>(heartbreakTasks.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) clearHeartbreak(le);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            setHealthBonus(p, false);
        }
    }

    public void resetCooldowns(Player p) {
        cooldowns.remove(p.getUniqueId());
        for (RuneType t : RuneType.values()) {
            p.setCooldown(materials.get(t), 0);
        }
    }

    public int cooldownSeconds(RuneType t) {
        return plugin.getConfig().getInt("runes." + t.name() + ".cooldown", t.defaultCooldown);
    }

    private void bar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }


    // ---------------------------------------------------------------- crafting

    /** When true, every rune type can be crafted only once on the whole server. */
    public boolean craftingLimited() {
        return plugin.getConfig().getBoolean("crafting.only-one-per-rune", true);
    }

    public boolean isCrafted(RuneType t) {
        return crafted.contains(t);
    }

    public Set<RuneType> craftedRunes() {
        return new LinkedHashSet<>(crafted);
    }

    /** Called when a rune item is really destroyed - allows crafting it again. */
    public void runeDestroyed(RuneType t) {
        if (!craftingLimited() || !isCrafted(t)) return;
        resetCrafted(t);
        Bukkit.broadcastMessage(ChatColor.GOLD + t.displayName + ChatColor.YELLOW
                + " zostala zniszczona! Mozna ja stworzyc na nowo.");
    }

    public void markCrafted(RuneType t) {
        crafted.add(t);
        saveCrafted();
    }

    public void resetCrafted(RuneType t) {
        crafted.remove(t);
        saveCrafted();
    }

    public void resetAllCrafted() {
        crafted.clear();
        saveCrafted();
    }

    private void loadCrafted() {
        craftedFile = new File(plugin.getDataFolder(), "crafted.yml");
        crafted.clear();
        if (!craftedFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(craftedFile);
        for (String s : y.getStringList("crafted")) {
            RuneType t = RuneType.fromString(s);
            if (t != null) crafted.add(t);
        }
    }

    private void saveCrafted() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (RuneType t : crafted) list.add(t.name());
        y.set("crafted", list);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(craftedFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac crafted.yml: " + e.getMessage());
        }
    }

    private void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys) Bukkit.removeRecipe(key);
        recipeKeys.clear();
    }

    private void registerRecipes() {
        unregisterRecipes();
        if (!plugin.getConfig().getBoolean("crafting.enabled", true)) return;

        for (RuneType t : RuneType.values()) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crafting.recipes." + t.name());
            if (sec == null) continue;
            List<String> shape = sec.getStringList("shape");
            ConfigurationSection ing = sec.getConfigurationSection("ingredients");
            if (shape.size() != 3 || ing == null) {
                plugin.getLogger().warning("Receptura " + t.name() + ": potrzebne 3 wiersze 'shape' i sekcja 'ingredients'.");
                continue;
            }
            boolean ok = true;
            Set<Character> letters = new LinkedHashSet<>();
            for (String row : shape) {
                if (row.length() != 3) ok = false;
                for (char c : row.toCharArray()) if (c != ' ') letters.add(c);
            }
            if (!ok) {
                plugin.getLogger().warning("Receptura " + t.name() + ": kazdy wiersz 'shape' musi miec 3 znaki.");
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, "rune_" + t.name().toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, createRune(t, 1));
            recipe.shape(shape.toArray(new String[0]));
            for (char c : letters) {
                String matName = ing.getString(String.valueOf(c));
                Material m = matName == null ? null : Material.matchMaterial(matName);
                if (m == null) {
                    plugin.getLogger().warning("Receptura " + t.name() + ": nieznany skladnik '" + c + "' = " + matName
                            + " - receptura pominieta. Popraw config.yml.");
                    ok = false;
                    break;
                }
                recipe.setIngredient(c, m);
            }
            if (ok) {
                Bukkit.addRecipe(recipe);
                recipeKeys.add(key);
            }
        }
    }

    // ------------------------------------------------------------------ items

    public ItemStack createRune(RuneType type, int amount) {
        ItemStack item = new ItemStack(materials.get(type), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + type.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "(" + type.subtitle + ")");
        for (String line : type.description) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add(ChatColor.YELLOW + "Cooldown: " + cooldownSeconds(type) + "s");
        lore.add(ChatColor.DARK_GRAY + "PPM aby uzyc");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(runeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public RuneType getRune(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer().get(runeKey, PersistentDataType.STRING);
        return RuneType.fromString(v);
    }

    public Set<RuneType> currentRunes(Player p) {
        return enforceLimit(p);
    }

    /** Keeps at most max-rune-types different rune types in the inventory, drops the rest. */
    private Set<RuneType> enforceLimit(Player p) {
        UUID id = p.getUniqueId();
        int max = Math.max(1, plugin.getConfig().getInt("max-rune-types", 3));
        ItemStack[] contents = p.getInventory().getContents();

        Set<RuneType> previous = activeRunes.getOrDefault(id, new LinkedHashSet<>());
        Set<RuneType> present = new LinkedHashSet<>();
        for (ItemStack it : contents) {
            RuneType t = getRune(it);
            if (t != null) present.add(t);
        }

        Set<RuneType> chosen = new LinkedHashSet<>();
        for (RuneType t : previous) {
            if (present.contains(t) && chosen.size() < max) chosen.add(t);
        }
        for (RuneType t : present) {
            if (chosen.size() < max) chosen.add(t);
        }

        if (chosen.isEmpty()) {
            activeRunes.remove(id);
            return chosen;
        }
        activeRunes.put(id, chosen);

        boolean dropped = false;
        for (int i = 0; i < contents.length; i++) {
            RuneType t = getRune(contents[i]);
            if (t != null && !chosen.contains(t)) {
                ItemStack stack = contents[i];
                p.getInventory().setItem(i, null);
                Item item = p.getWorld().dropItem(p.getLocation(), stack);
                item.setPickupDelay(100);
                dropped = true;
            }
        }
        if (dropped) {
            bar(p, ChatColor.RED + "Mozesz miec maksymalnie " + max + " rodzaje run naraz!");
        }
        return chosen;
    }

    public boolean canPickup(Player p, ItemStack stack) {
        RuneType incoming = getRune(stack);
        if (incoming == null) return true;
        Set<RuneType> cur = enforceLimit(p);
        int max = Math.max(1, plugin.getConfig().getInt("max-rune-types", 3));
        return cur.contains(incoming) || cur.size() < max;
    }

    // --------------------------------------------------------------- passives

    private void tickPassives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<RuneType> owned = enforceLimit(p);
            if (owned.contains(RuneType.FLAME)) {
                passive(p, PotionEffectType.FIRE_RESISTANCE, 0);
                if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                    passive(p, PotionEffectType.RESISTANCE, 0);
                }
            }
            if (owned.contains(RuneType.MINER)) {
                passive(p, PotionEffectType.HASTE, 0);
            }
            setHealthBonus(p, owned.contains(RuneType.HEART) || owned.contains(RuneType.HEARTBREAK));
        }
    }

    private void passive(Player p, PotionEffectType type, int amp) {
        PotionEffect ex = p.getPotionEffect(type);
        if (ex != null && (ex.getAmplifier() > amp || (ex.getAmplifier() == amp && ex.getDuration() > 60))) return;
        p.addPotionEffect(new PotionEffect(type, 60, amp, true, false, true));
    }

    private void setHealthBonus(Player p, boolean want) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        boolean present = a.getModifiers().stream().anyMatch(m -> healthKey.equals(m.getKey()));
        if (want && !present) {
            a.addModifier(new AttributeModifier(healthKey, 4.0, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY)); // +2 hearts
        } else if (!want && present) {
            removeModifier(a, healthKey);
        }
    }

    private void removeModifier(AttributeInstance a, NamespacedKey key) {
        for (AttributeModifier m : new ArrayList<>(a.getModifiers())) {
            if (key.equals(m.getKey())) a.removeModifier(m);
        }
    }

    public void onJoin(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) removeModifier(a, breakKey);
    }

    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        lastHit.remove(id);
        activeRunes.remove(id);
        frozen.remove(id);
    }

    // -------------------------------------------------------------- listeners

    public void recordHit(Player damager, LivingEntity victim) {
        lastHit.put(damager.getUniqueId(), victim.getUniqueId());
    }

    public boolean isFrozen(Player p) {
        Long until = frozen.get(p.getUniqueId());
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            frozen.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    /** No fall damage during Wind slam, and passively while holding Wind or Breeze rune. */
    public boolean hasNoFall(Player p) {
        if (noFall.contains(p.getUniqueId())) return true;
        Set<RuneType> owned = activeRunes.get(p.getUniqueId());
        return owned != null && (owned.contains(RuneType.WIND) || owned.contains(RuneType.BREEZE));
    }

    public boolean isProtected(Block b) {
        return protectedBlocks.contains(b);
    }

    public boolean isRuneFireball(Entity e) {
        return e.getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE);
    }

    // True damage: ignores armor and enchantments
    private void trueDamage(LivingEntity target, double amount, Player source) {
        if (target.isDead()) return;
        if (target instanceof Player pl
                && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) return;
        if (source != null) target.damage(0.01, source);
        else target.damage(0.01);
        if (target.isDead()) return;
        target.setHealth(Math.max(0.0, target.getHealth() - amount));
    }

    public void explodeFireball(Fireball fb) {
        double radius = plugin.getConfig().getDouble("explosion.radius", 4.0);
        double damage = plugin.getConfig().getDouble("explosion.damage", 6.0);
        Location l = fb.getLocation();
        World w = l.getWorld();
        Player shooter = fb.getShooter() instanceof Player p ? p : null;
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 4f, 1f);
        for (Entity e : w.getNearbyEntities(l, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.equals(shooter)) {
                trueDamage(le, damage, shooter);
            }
        }
    }

    // ---------------------------------------------------------------- abilities

    public void use(Player p, RuneType t) {
        UUID id = p.getUniqueId();
        if (!enforceLimit(p).contains(t)) return;

        long now = System.currentTimeMillis();
        Map<RuneType, Long> cd = cooldowns.computeIfAbsent(id, k -> new EnumMap<>(RuneType.class));
        Long until = cd.get(t);
        if (until != null && until > now) {
            long left = (until - now + 999) / 1000;
            bar(p, ChatColor.RED + t.displayName + " - cooldown: " + left + "s");
            return;
        }

        boolean ok = switch (t) {
            case BREEZE -> breeze(p);
            case FLAME -> flame(p);
            case EXPLOSION -> explosion(p);
            case WIND -> wind(p);
            case FROST -> frost(p);
            case BLADE -> blade(p);
            case MINER -> miner(p);
            case HEART -> heart(p);
            case HEARTBREAK -> heartbreak(p);
            case ANCIENT -> ancient(p);
        };

        if (ok) {
            cd.put(t, now + cooldownSeconds(t) * 1000L);
            p.setCooldown(materials.get(t), cooldownSeconds(t) * 20);
            bar(p, ChatColor.GOLD + t.displayName + " aktywowana!");
        }
    }

    // Breeze: dash forward, damage grows with distance flown when the enemy is hit
    private boolean breeze(Player p) {
        double total = plugin.getConfig().getDouble("breeze.dash-distance", 20);
        double base = plugin.getConfig().getDouble("breeze.base-damage", 2.0);
        double perBlock = plugin.getConfig().getDouble("breeze.damage-per-block", 0.4);
        double speed = 2.0;
        int steps = (int) Math.ceil(total / speed);
        Location start = p.getLocation().clone();
        Vector dir = p.getLocation().getDirection().normalize();
        Set<UUID> hit = new HashSet<>();

        p.getWorld().playSound(start, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || i >= steps) {
                    cancel();
                    return;
                }
                p.setVelocity(dir.clone().multiply(speed));
                p.setFallDistance(0);
                double traveled = p.getLocation().distance(start);
                for (Entity e : p.getNearbyEntities(1.6, 1.6, 1.6)) {
                    if (e instanceof LivingEntity le && !hit.contains(e.getUniqueId())) {
                        hit.add(e.getUniqueId());
                        le.damage(base + traveled * perBlock, p);
                        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, le.getLocation().add(0, 1, 0), 8);
                    }
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 6, 0.3, 0.3, 0.3, 0.02);
                i++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // Flame: water dries up around the player for N seconds
    private boolean flame(Player p) {
        int seconds = plugin.getConfig().getInt("flame.duration-seconds", 10);
        int r = plugin.getConfig().getInt("flame.radius", 10);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || ticks >= seconds * 20) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                World w = c.getWorld();
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (x * x + y * y + z * z > r * r) continue;
                            int by = c.getBlockY() + y;
                            if (by < w.getMinHeight() || by >= w.getMaxHeight()) continue;
                            Block b = w.getBlockAt(c.getBlockX() + x, by, c.getBlockZ() + z);
                            Material m = b.getType();
                            if (m == Material.WATER || m == Material.KELP || m == Material.KELP_PLANT
                                    || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                                    || m == Material.BUBBLE_COLUMN) {
                                b.setType(Material.AIR, false);
                            } else if (b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) {
                                wl.setWaterlogged(false);
                                b.setBlockData(wl, false);
                            }
                        }
                    }
                }
                w.spawnParticle(Particle.FLAME, c.clone().add(0, 1, 0), 30, 2, 1, 2, 0.02);
                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    // Explosion: buffed fireball
    private boolean explosion(Player p) {
        double speed = plugin.getConfig().getDouble("explosion.speed", 1.2);
        LargeFireball fb = p.launchProjectile(LargeFireball.class, p.getEyeLocation().getDirection().multiply(speed));
        fb.setShooter(p);
        fb.setYield(0f);
        fb.setIsIncendiary(false);
        fb.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1f);
        return true;
    }

    // Wind: launch up ~40 blocks, slam down, true damage to everyone around
    private boolean wind(Player p) {
        UUID id = p.getUniqueId();
        if (noFall.contains(id)) return false;
        double height = plugin.getConfig().getDouble("wind.height", 40);
        double radius = plugin.getConfig().getDouble("wind.landing-radius", 5.0);
        double startY = p.getLocation().getY();
        noFall.add(id);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);

        new BukkitRunnable() {
            int tick = 0;
            int downTicks = 0;
            int stuck = 0;
            boolean down = false;
            double peak = startY;
            double last = startY;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    noFall.remove(id);
                    cancel();
                    return;
                }
                tick++;
                double y = p.getLocation().getY();
                if (!down) {
                    if (y > peak) peak = y;
                    if (y - last < 0.05) stuck++;
                    else stuck = 0;
                    last = y;
                    if (peak - startY >= height || (stuck >= 3 && tick > 3) || tick > 80) {
                        down = true;
                    } else {
                        p.setVelocity(new Vector(0, 2.5, 0));
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 5, 0.2, 0.2, 0.2, 0.02);
                        return;
                    }
                }
                downTicks++;
                if ((downTicks > 3 && (p.isOnGround() || p.isInWater())) || downTicks > 200) {
                    land();
                    return;
                }
                p.setVelocity(new Vector(0, -2.5, 0));
            }

            private void land() {
                double dmg = plugin.getConfig().getDouble("wind.damage", 6.0);
                Location l = p.getLocation();
                for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof LivingEntity le) trueDamage(le, dmg, p);
                }
                l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
                l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);
                cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> noFall.remove(id), 20L);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // Frost: freeze a random player in range
    private boolean frost(Player p) {
        int r = plugin.getConfig().getInt("frost.radius", 15);
        int seconds = plugin.getConfig().getInt("frost.duration-seconds", 3);
        List<Player> candidates = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof Player t && !t.isDead() && t.getGameMode() != GameMode.SPECTATOR
                    && t.getLocation().distanceSquared(p.getLocation()) <= (double) r * r) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            bar(p, ChatColor.RED + "Brak graczy w zasiegu.");
            return false;
        }
        Player target = candidates.get(new Random().nextInt(candidates.size()));
        UUID tid = target.getUniqueId();
        frozen.put(tid, System.currentTimeMillis() + seconds * 1000L);
        target.setFreezeTicks(target.getMaxFreezeTicks());
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        bar(target, ChatColor.AQUA + "Zostales zamrozony!");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            frozen.remove(tid);
            Player t = Bukkit.getPlayer(tid);
            if (t != null) t.setFreezeTicks(0);
        }, seconds * 20L);
        return true;
    }

    // Blade: Strength 3 for 15s, then all effects are cleared
    private boolean blade(Player p) {
        int level = plugin.getConfig().getInt("blade.strength-level", 3);
        int seconds = plugin.getConfig().getInt("blade.duration-seconds", 15);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, level - 1));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(e.getType());
            }
        }, seconds * 20L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
        return true;
    }

    private boolean miner(Player p) {
        int level = plugin.getConfig().getInt("miner.ability-haste-level", 3);
        int seconds = plugin.getConfig().getInt("miner.duration-seconds", 30);
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, level - 1));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.5f);
        return true;
    }

    private boolean heart(Player p) {
        int level = plugin.getConfig().getInt("heart.resistance-level", 3);
        int seconds = plugin.getConfig().getInt("heart.duration-seconds", 10);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, level - 1));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 8, 0.4, 0.2, 0.4);
        return true;
    }

    // HeartBreak: last hit entity loses hearts for 30s
    private boolean heartbreak(Player p) {
        UUID vid = lastHit.get(p.getUniqueId());
        Entity ent = vid == null ? null : Bukkit.getEntity(vid);
        if (!(ent instanceof LivingEntity victim) || victim.isDead()) {
            bar(p, ChatColor.RED + "Brak ostatnio trafionego celu.");
            return false;
        }
        AttributeInstance a = victim.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return false;
        double hearts = plugin.getConfig().getDouble("heartbreak.hearts-removed", 2);
        int seconds = plugin.getConfig().getInt("heartbreak.duration-seconds", 30);

        removeModifier(a, breakKey);
        a.addModifier(new AttributeModifier(breakKey, -hearts * 2, AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.ANY));
        BukkitTask old = heartbreakTasks.remove(vid);
        if (old != null) old.cancel();
        heartbreakTasks.put(vid, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            heartbreakTasks.remove(vid);
            Entity e = Bukkit.getEntity(vid);
            if (e instanceof LivingEntity le) clearHeartbreak(le);
        }, seconds * 20L));

        victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1.5, 0), 15, 0.4, 0.4, 0.4);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 1.2f);
        if (victim instanceof Player vp) {
            bar(vp, ChatColor.DARK_RED + "Twoje serca zostaly zlamane!");
        }
        return true;
    }

    private void clearHeartbreak(LivingEntity le) {
        AttributeInstance a = le.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) removeModifier(a, breakKey);
        BukkitTask t = heartbreakTasks.remove(le.getUniqueId());
        if (t != null) t.cancel();
    }

    // Ancient: unbreakable sculk sphere (hollow shell), restored after the duration
    private boolean ancient(Player p) {
        int r = plugin.getConfig().getInt("ancient.radius", 16);
        int seconds = plugin.getConfig().getInt("ancient.duration-seconds", 30);
        Location c = p.getLocation().getBlock().getLocation();
        World w = c.getWorld();
        Map<Block, BlockData> saved = new HashMap<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > r || d <= r - 1.5) continue;
                    int by = c.getBlockY() + y;
                    if (by < w.getMinHeight() || by >= w.getMaxHeight()) continue;
                    Block b = w.getBlockAt(c.getBlockX() + x, by, c.getBlockZ() + z);
                    if (protectedBlocks.contains(b)) continue;
                    if (b.getType() == Material.BEDROCK) continue;
                    if (b.getState() instanceof TileState) continue; // never destroy chests etc.
                    saved.put(b, b.getBlockData());
                    b.setType(Material.SCULK, false);
                }
            }
        }
        if (saved.isEmpty()) return false;

        protectedBlocks.addAll(saved.keySet());
        cages.add(saved);
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.7f);
        w.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 60, 2, 1, 2, 0.05);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreCage(saved), seconds * 20L);
        return true;
    }

    private void restoreCage(Map<Block, BlockData> saved) {
        if (!cages.remove(saved)) return;
        protectedBlocks.removeAll(saved.keySet());
        for (Map.Entry<Block, BlockData> e : saved.entrySet()) {
            e.getKey().setBlockData(e.getValue(), false);
        }
    }
}
