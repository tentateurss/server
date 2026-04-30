package ru.eclipsia.skills.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.eclipsia.core.data.PlayerProfile;
import ru.eclipsia.skills.EclipsiaSkills;
import ru.eclipsia.skills.eclipse.EclipseItem;
import ru.eclipsia.skills.gui.SkillsGUI;
import ru.eclipsia.skills.manager.SkillManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель событий навыков
 */
public class SkillListener implements Listener {
    
    private final EclipsiaSkills plugin;
    private final Map<UUID, Map<Integer, Long>> cooldowns; // UUID -> (hotbarSlot -> lastUseTime)
    
    public SkillListener(EclipsiaSkills plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        // 1) ПКМ по эклипс-гему (изумруд/аметист) — автоэкип в первый свободный слот.
        EclipseItem gem = EclipseItem.fromItemStack(item);
        if (gem != null) {
            event.setCancelled(true);
            handleGemRightClick(player, gem, item);
            return;
        }

        // 2) ПКМ по иконке активного навыка в хотбаре — выполнить навык.
        Material type = item.getType();
        if (type != Material.IRON_SWORD && type != Material.BOW && type != Material.BLAZE_ROD) {
            return;
        }
        
        // Получаем активный навык
        int hotbarSlot = player.getInventory().getHeldItemSlot();
        SkillManager.ActiveSkill activeSkill = plugin.getSkillManager().getActiveSkill(player, hotbarSlot);
        
        if (activeSkill == null) return;
        
        event.setCancelled(true);
        
        EclipseItem skill = activeSkill.getSkill();
        
        // Проверяем кулдаун
        if (isOnCooldown(player, hotbarSlot, skill.getCooldownTicks())) {
            long remaining = getRemainingCooldown(player, hotbarSlot, skill.getCooldownTicks());
            player.sendMessage("§cКулдаун: §e" + (remaining / 20.0) + "§cс");
            return;
        }
        
        // Получаем профиль для расчета урона и проверки маны
        PlayerProfile profile = plugin.getAPI().getActiveProfile(player);
        if (profile == null) return;
        
        // Проверяем хватает ли маны
        int manaCost = skill.getManaCost();
        if (profile.getCurrentMana() < manaCost) {
            player.sendMessage("§cНедостаточно маны! Требуется: §9" + manaCost + " §c(есть: §9" + profile.getCurrentMana() + "§c)");
            return;
        }
        
        // Рассчитываем урон (с учётом бонусов с экипировки)
        double damage = calculateDamage(skill, profile, player);

        // ВАЖНО: списываем ману ДО executeSkill. Иначе мили-навык
        // мгновенно убивает моба → MobDeathListener.addExperience пишет
        // в кэш PlayerData новое значение experience, а наш блок «расходуем
        // ману» ниже берёт устаревший снапшот profile (exp=0) и через
        // updateProfile -> savePlayer затирает только что начисленный XP
        // (race кеша). У ARROW_SHOT/FIREBALL такого не было, потому что
        // моб умирал на ProjectileHitEvent в следующих тиках, после save
        // маны. Сейчас сохраняем ману до выполнения навыка — кэш будет
        // перезаписан addExperience уже поверх свежей маны.
        PlayerProfile updatedProfile = profile.toBuilder()
                .currentMana(profile.getCurrentMana() - manaCost)
                .build();

        plugin.getAPI().updateProfile(player, updatedProfile);

        // Выполняем навык (может породить EntityDeathEvent → addExperience)
        executeSkill(player, skill, activeSkill.getSupports(), damage);

        // Устанавливаем кулдаун
        setCooldown(player, hotbarSlot);
    }
    
    /**
     * Выполнить навык
     */
    private void executeSkill(Player player, EclipseItem skill, List<EclipseItem> supports, double damage) {
        switch (skill.getSkillClass()) {
            case MELEE_STRIKE -> executeMeleeStrike(player, supports, damage);
            case ARROW_SHOT -> executeArrowShot(player, supports, damage);
            case FIREBALL -> executeFireball(player, supports, damage);
        }
    }
    
    /**
     * Удар в ближнем бою.
     * <p>Прицеливание ведётся от <b>глаз</b> игрока в направлении его взгляда:
     * центр AOE-сферы помещается на расстоянии {@code radius} перед лицом,
     * а не возле ног. Высота AOE равна радиусу (а не 0.5), чтобы ловить
     * мобов как ниже, так и выше уровня глаз. Каждой жертве выставляется
     * metadata {@code eclipse_killer} — fallback для credit убийства,
     * если Bukkit потерял killer в EntityDeathEvent.
     */
    private void executeMeleeStrike(Player player, List<EclipseItem> supports, double damage) {
        double radius = 3.0;
        boolean hasExplosion = false;
        boolean hasMulti = false;
        for (EclipseItem support : supports) {
            switch (support.getSupportClass()) {
                case AOE_RADIUS -> radius += 3.0;          // увеличиваем радиус взмаха
                case EXPLOSION  -> hasExplosion = true;     // взрыв всех в зоне взмаха
                case MULTI_SHOT -> hasMulti = true;         // +2 удара подряд
            }
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location targetLoc = eye.clone().add(direction.clone().multiply(radius));

        meleeStrikeAt(player, targetLoc, radius, damage);

        if (hasExplosion) {
            // EXPLOSION в мили — взрывает всех, кто попал под взмах: повтор
            // damageInRadius с полным damage и крупный визуальный взрыв.
            targetLoc.getWorld().createExplosion(targetLoc, 0f, false, false);
            targetLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, targetLoc, 3, 0.6, 0.6, 0.6);
            targetLoc.getWorld().spawnParticle(Particle.LAVA, targetLoc, 18, radius / 2, radius / 2, radius / 2);
            damageInRadius(targetLoc, radius, player, damage * 0.7);
        }

        if (hasMulti) {
            // MULTI_SHOT в мили — два дополнительных удара (всего 3) с интервалом
            // 3 тика, эмулирует комбо. До фикса был +1 (всего 2).
            final double finalRadius = radius;
            final double finalDamage = damage;
            for (int i = 1; i <= 2; i++) {
                final int idx = i;
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    Location follow = player.getEyeLocation()
                            .add(player.getEyeLocation().getDirection().normalize().multiply(finalRadius));
                    meleeStrikeAt(player, follow, finalRadius, finalDamage * 0.7);
                }, 3L * idx);
            }
        }

        // Яркая красная вспышка в точке удара — индикатор активной поддержки.
        if (!supports.isEmpty()) {
            spawnSkillFx(targetLoc, EclipseItem.SkillClass.MELEE_STRIKE, 28);
        }
    }

    /** Один "сэндвич" удара — урон + sweep-эффект в указанной точке. */
    private void meleeStrikeAt(Player player, Location targetLoc, double radius, double damage) {
        List<Entity> entities = player.getWorld()
                .getNearbyEntities(targetLoc, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> !e.equals(player))
                .filter(e -> !(e instanceof Player)) // TODO: PvP-флаг
                .toList();

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                markKiller(living, player);
                tagSkillDmg(living);
                living.damage(damage, player);
                ru.eclipsia.core.combat.DamageDisplay.show(
                        living, damage, ru.eclipsia.core.combat.DamageType.PHYSICAL);
            }
        }

        // Яркие красные частицы вместо тусклого sweep — DUST RED + FLAME +
        // DAMAGE_INDICATOR; ванильный sweep оставляем, но толще.
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, targetLoc, 12,
                radius / 2, radius / 2, radius / 2);
        player.getWorld().spawnParticle(Particle.REDSTONE, targetLoc, 30,
                radius / 2, radius / 2, radius / 2,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 40, 40), 1.8f));
        player.getWorld().spawnParticle(Particle.FLAME, targetLoc, 14,
                radius / 2, radius / 2, radius / 2, 0.02);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, targetLoc, 8,
                radius / 2, radius / 2, radius / 2);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
    }

    /**
     * Проставить eclipse_killer + eclipse_last_damager на жертве, чтобы
     * MobDeathListener гарантированно выдал XP. Дублируем оба ключа: на
     * мили-удары (без проджектайла) Bukkit не всегда успевает обновить
     * {@code getKiller()} к моменту EntityDeathEvent — особенно если урон
     * пошёл через {@link LivingEntity#damage(double, org.bukkit.entity.Entity)}
     * и в финальном тике игрок уже не считается активным damager-ом.
     */
    private void markKiller(LivingEntity victim, Player killer) {
        org.bukkit.metadata.FixedMetadataValue value =
                new org.bukkit.metadata.FixedMetadataValue(plugin, killer.getUniqueId().toString());
        victim.setMetadata("eclipse_killer", value);
        victim.setMetadata("eclipse_last_damager", value);
    }

    /**
     * Выстрел из лука. Поддержки:
     * <ul>
     *   <li>{@code MULTI_SHOT} — +2 стрелы веером (всего 3)</li>
     *   <li>{@code EXPLOSION} — стрела детонирует при попадании
     *       (без разрушения блоков)</li>
     *   <li>{@code AOE_RADIUS} — наносит урон по площади вокруг точки
     *       попадания (радиус 3 блока)</li>
     * </ul>
     * При наличии любой поддержки стрелы получают зелёный partikle-trail.
     */
    /**
     * Подавляет ванильный EntityShootBowEvent сразу после нашего выстрела —
     * иначе если игрок ПКМ-кастует ARROW_SHOT, удерживая лук, через
     * release-trigger Bukkit спавнит ВТОРУЮ стрелу (она часто летит назад,
     * т.к. velocity берётся из release-force, а наш кастомный shootArrow
     * уже двинул игрока). До фикса юзер видел "стрела возвращается".
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBowShootSuppressIfSkillFiring(
            org.bukkit.event.entity.EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (p.hasMetadata("eclipse_skill_firing")) {
            event.setCancelled(true);
        }
    }

    private void executeArrowShot(Player player, List<EclipseItem> supports, double damage) {
        // Метка для подавления ванильного EntityShootBowEvent на 6 тиков —
        // см. onBowShootSuppressIfSkillFiring.
        player.setMetadata("eclipse_skill_firing",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { player.removeMetadata("eclipse_skill_firing", plugin); }
            catch (Throwable ignored) {}
        }, 6L);


        boolean multiShot = supports.stream()
                .anyMatch(s -> s.getSupportClass() == EclipseItem.SupportClass.MULTI_SHOT);
        boolean explosion = supports.stream()
                .anyMatch(s -> s.getSupportClass() == EclipseItem.SupportClass.EXPLOSION);
        String supportCsv = csvSupports(supports);

        // EXPLOSION на стреле — взрывается на месте попадания + ДОПОЛНИТЕЛЬНО
        // даёт +2 стрелы (по ТЗ user'а: «стрелы взрываются... +2 стрелы»).
        // MULTI_SHOT — отдельно тоже даёт +2. Если оба активны — суммируется
        // в 5 стрел. AOE_RADIUS — превращает стрелу в "луч" (см. handleArrowHit
        // и трейл, который наносит урон по всем мобам по пути).
        int total = 1;
        if (multiShot) total += 2;
        if (explosion) total += 2;

        // Углы веером: 0, ±15, ±30 для 5 стрел (с дублями фильтруем).
        double[][] angles = {
            {0, 0, 0, 0, 0},
            {0, 15, -15, 0, 0},
            {0, 15, -15, 30, -30}
        };
        double[] use;
        if (total <= 1) use = new double[]{0};
        else if (total <= 3) use = new double[]{0, 15, -15};
        else use = new double[]{0, 15, -15, 30, -30};
        for (double a : use) {
            shootArrow(player, a, damage, supportCsv, supports);
        }
    }

    /** Выпустить стрелу + повесить на неё метаданные эклипса и трейл частиц. */
    private void shootArrow(Player player, double angleOffset, double damage,
                            String supportCsv, List<EclipseItem> supports) {
        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setDamage(0);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(true);
        // Чтобы стрела не "отскакивала" от моба и не падала под ноги:
        // setDamage(0) превращает её в небоевую, и мы вручную удаляем
        // в handleArrowHit/startArrowBeam через arrow.remove().
        try { arrow.setBounce(false); } catch (Throwable ignored) {}

        arrow.setMetadata("eclipse_shooter",
                new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
        arrow.setMetadata("eclipse_damage",
                new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        arrow.setMetadata("eclipse_supports",
                new org.bukkit.metadata.FixedMetadataValue(plugin, supportCsv));

        if (angleOffset != 0) {
            Vector velocity = arrow.getVelocity();
            velocity.rotateAroundY(Math.toRadians(angleOffset));
            arrow.setVelocity(velocity);
        }

        // Яркие зелёные частицы на спавне.
        player.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 6);
        player.getWorld().spawnParticle(Particle.REDSTONE, arrow.getLocation(), 8,
                0.2, 0.2, 0.2,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 255, 60), 1.6f));

        // Трейл — всегда (а не только при поддержке): красивее, и AOE
        // ("луч") нуждается в каждом тике для damage-along-path.
        boolean aoeBeam = supports.stream()
                .anyMatch(s -> s.getSupportClass() == EclipseItem.SupportClass.AOE_RADIUS);
        if (aoeBeam) {
            startArrowBeam(arrow, player, damage);
        } else {
            startProjectileTrail(arrow, EclipseItem.SkillClass.ARROW_SHOT);
        }
    }
    
    /**
     * Огненный шар. Поддержки:
     * <ul>
     *   <li>{@code MULTI_SHOT} — выпускает 3 шара веером</li>
     *   <li>{@code EXPLOSION} — взрывает по площади (радиус 4)</li>
     *   <li>{@code AOE_RADIUS} — взрыв расширяется до 7 блоков</li>
     * </ul>
     */
    private void executeFireball(Player player, List<EclipseItem> supports, double damage) {
        boolean multiShot = supports.stream()
                .anyMatch(s -> s.getSupportClass() == EclipseItem.SupportClass.MULTI_SHOT);
        String supportCsv = csvSupports(supports);

        if (multiShot) {
            // Веер 3 фаерболов. Угол ±25° + поперечное смещение 2.0 блока,
            // чтобы хитбоксы (1×1) не пересекались. Плюс в handleFireballHit
            // фаерболы одного игрока ИГНОРИРУЮТ друг друга (cancel event,
            // не удаляются, не взрываются) — пролетают сквозь и взрываются
            // только на мобах/блоках.
            shootFireball(player, 0, 0, damage, supportCsv, supports);
            shootFireball(player, 25, 2.0, damage, supportCsv, supports);
            shootFireball(player, -25, -2.0, damage, supportCsv, supports);
        } else {
            shootFireball(player, 0, 0, damage, supportCsv, supports);
        }

        // Урон не дублируем в чат — теперь видно цифрой над целью.
    }

    /**
     * Спавн одного фаербола с заданным горизонтальным углом и боковым
     * (перпендикулярным взгляду) смещением.
     *
     * @param angleOffset   горизонтальное отклонение направления полёта (градусы)
     * @param lateralOffset смещение точки спавна вбок относительно forward
     *                      (положительное — вправо, в блоках)
     */
    private void shootFireball(Player player, double angleOffset, double lateralOffset,
                               double damage, String supportCsv, List<EclipseItem> supports) {
        Vector forward = player.getLocation().getDirection().clone().normalize();
        Vector dir = forward.clone();
        if (angleOffset != 0) dir = dir.rotateAroundY(Math.toRadians(angleOffset));
        Vector dirNorm = dir.clone().normalize();

        // База: 2 блока перед глазами игрока в направлении ПОЛЁТА фаербола.
        Location spawn = player.getEyeLocation().add(dirNorm.clone().multiply(2.0));

        // Дополнительное боковое смещение перпендикулярно forward — чтобы
        // 3 шара стартовали в 1.5 блока друг от друга (хитбокс 1×1).
        if (lateralOffset != 0) {
            Vector perp = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
            spawn.add(perp.multiply(lateralOffset));
        }

        LargeFireball fireball = player.getWorld().spawn(spawn, LargeFireball.class);
        fireball.setShooter(player);
        fireball.setDirection(dirNorm);
        fireball.setVelocity(dirNorm.clone().multiply(1.5));
        fireball.setYield(0); // Не ломаем блоки — урон считаем сами в onProjectileHit.

        fireball.setMetadata("eclipse_damage",
                new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        fireball.setMetadata("eclipse_shooter",
                new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId()));
        fireball.setMetadata("eclipse_supports",
                new org.bukkit.metadata.FixedMetadataValue(plugin, supportCsv));

        if (!supports.isEmpty()) {
            startProjectileTrail(fireball, EclipseItem.SkillClass.FIREBALL);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Эклипс-стрелы (ARROW_SHOT) — обработка попадания + поддержек.
        if (event.getEntity() instanceof Arrow arrow
                && arrow.hasMetadata("eclipse_shooter")) {
            handleArrowHit(event, arrow);
            return;
        }

        if (!(event.getEntity() instanceof LargeFireball fireball)) return;
        if (!fireball.hasMetadata("eclipse_damage")) return;

        handleFireballHit(event, fireball);
    }

    /**
     * Попадание эклипс-стрелы. Прямой урон — стандартно через
     * Arrow.setDamage(). Поддержки добавляют:
     * <ul>
     *   <li>EXPLOSION — невзрывной "толчок" + взрыв-партиклы вокруг точки.</li>
     *   <li>AOE_RADIUS — повторный урон по всем мобам в радиусе 3 блока.</li>
     * </ul>
     */
    private void handleArrowHit(ProjectileHitEvent event, Arrow arrow) {
        UUID shooterId;
        try {
            shooterId = UUID.fromString(
                    arrow.getMetadata("eclipse_shooter").get(0).asString());
        } catch (IllegalArgumentException ignored) {
            return;
        }
        Player shooter = plugin.getServer().getPlayer(shooterId);

        double damage = arrow.hasMetadata("eclipse_damage")
                ? arrow.getMetadata("eclipse_damage").get(0).asDouble() : 5.0;

        // Прямое попадание: применяем УРОН САМИ через damage(double, Player),
        // т.к. ванильный setDamage обнулён (см. shootArrow). Так лук бьёт
        // ровно baseDamage * statMultiplier — без скрытого ×velocity-крита.
        if (event.getHitEntity() instanceof LivingEntity directHit
                && !(directHit instanceof Player)) {
            directHit.setMetadata("eclipse_killer",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, shooterId.toString()));
            directHit.setMetadata("eclipse_last_damager",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, shooterId.toString()));
            tagSkillDmg(directHit);
            if (shooter != null) {
                directHit.damage(damage, shooter);
            } else {
                directHit.damage(damage);
            }
            ru.eclipsia.core.combat.DamageDisplay.show(
                    directHit, damage, ru.eclipsia.core.combat.DamageType.PHYSICAL);
        }

        java.util.Set<EclipseItem.SupportClass> supports = readSupports(arrow);
        if (supports.isEmpty() || shooter == null) {
            // Удаляем стрелу даже без поддержек — иначе торчит из моба.
            arrow.remove();
            return;
        }

        Location loc = arrow.getLocation();

        // EXPLOSION — взрыв в точке попадания (AoE урон в радиусе 4 блока).
        if (supports.contains(EclipseItem.SupportClass.EXPLOSION)) {
            loc.getWorld().createExplosion(loc, 0f, false, false);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, 0.4, 0.4, 0.4);
            loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 40, 1.5, 1.5, 1.5,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 255, 60), 1.6f));
            damageInRadius(loc, 4.0, shooter, damage * 0.6);
        }

        // AOE_RADIUS уже отработал "лучом" по пути полёта (startArrowBeam),
        // здесь только финальный flash в точке попадания.

        // Зелёный "флэш" в точке попадания — индикатор поддержки.
        spawnSkillFx(loc, EclipseItem.SkillClass.ARROW_SHOT, 24);

        // Удаляем стрелу — иначе она физически торчит/падает от моба
        // и выглядит как "отражение". Удалять надо в любом случае,
        // даже если попали в блок (без HitEntity).
        arrow.remove();
    }

    /**
     * Попадание эклипс-фаербола. По умолчанию — взрыв по площади (4 блока).
     * AOE_RADIUS расширяет радиус до 7 блоков. EXPLOSION уже подразумевается
     * (фаербол всегда "взрывной" эклипс), но мы показываем дополнительный
     * визуальный взрыв.
     */
    private void handleFireballHit(ProjectileHitEvent event, LargeFireball fireball) {
        double damage = fireball.getMetadata("eclipse_damage").get(0).asDouble();
        UUID shooterId = UUID.fromString(
                fireball.getMetadata("eclipse_shooter").get(0).asString());
        Player shooter = plugin.getServer().getPlayer(shooterId);
        if (shooter == null) return;

        // Если фаербол врезался в свой же эклипс-фаербол того же игрока
        // (multi-shot веер) — ПОЛНОСТЬЮ ИГНОРИРУЕМ столкновение: cancel
        // event, оба шара продолжают лететь как ни в чём не бывало.
        // Так multi-shot гарантированно даёт 3 попадания, а не 1 (который
        // первым во что-то врезался) или 0 (если они выпилили друг друга
        // на спавне). На мобов и блоки реакция остаётся нормальная.
        if (event.getHitEntity() instanceof LargeFireball other
                && other != fireball
                && other.hasMetadata("eclipse_shooter")) {
            try {
                UUID otherShooter = UUID.fromString(
                        other.getMetadata("eclipse_shooter").get(0).asString());
                if (otherShooter.equals(shooterId)) {
                    event.setCancelled(true);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // metadata повреждена — обычная обработка ниже.
            }
        }

        Location loc = fireball.getLocation();
        java.util.Set<EclipseItem.SupportClass> supports = readSupports(fireball);

        // Связки складываются: AOE_RADIUS расширяет радиус, EXPLOSION даёт
        // дополнительный мощный взрыв с увеличенным damage-фактором,
        // MULTI_SHOT уже отработал на старте (3 шара). Сложные связки типа
        // aoe+explosion+multi работают: 3 шара × увеличенный радиус ×
        // удвоенная зона. Каждое попадание здесь — независимое событие.
        double radius = 4.0;
        double aoeMult = 1.0;
        if (supports.contains(EclipseItem.SupportClass.AOE_RADIUS))  { radius = 7.0; aoeMult += 0.3; }
        if (supports.contains(EclipseItem.SupportClass.EXPLOSION))   { radius += 1.5; aoeMult += 0.4; }

        // Прямое попадание (с метаданными чтобы StatsCombatListener не дублировал).
        if (event.getHitEntity() instanceof LivingEntity directHit
                && !(directHit instanceof Player)) {
            directHit.setMetadata("eclipse_killer",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, shooterId.toString()));
            tagSkillDmg(directHit);
            directHit.damage(damage, shooter);
            ru.eclipsia.core.combat.DamageDisplay.show(
                    directHit, damage, ru.eclipsia.core.combat.DamageType.FIRE);
        }
        damageInRadius(loc, radius, shooter, damage * aoeMult,
                ru.eclipsia.core.combat.DamageType.FIRE);

        // Визуальный взрыв (без блоков). EXPLOSION в связке — двойной flash.
        loc.getWorld().createExplosion(loc, 0f, false, false);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 3, 0.5, 0.5, 0.5);
        if (supports.contains(EclipseItem.SupportClass.EXPLOSION)) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
            loc.getWorld().spawnParticle(Particle.LAVA, loc, 14, radius / 2, radius / 2, radius / 2);
        }
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 50, radius / 2, radius / 2, radius / 2, 0.05);
        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc, 12, 0.8, 0.8, 0.8, 0.02);

        if (!supports.isEmpty()) {
            spawnSkillFx(loc, EclipseItem.SkillClass.FIREBALL, 30);
        }
    }
    
    /**
     * Рассчитать урон навыка
     */
    private double calculateDamage(EclipseItem skill, PlayerProfile profile) {
        return calculateDamage(skill, profile, null);
    }

    /**
     * Расчёт урона навыка с учётом базовых статов профиля И бонусов
     * с экипировки игрока (Сила/Ловкость/Интеллект из лора предметов).
     *
     * <p>Бонус с экипировки запрашивается через рефлексию у EclipsiaItems
     * — это soft-dep, поэтому если плагин не загружен, статы берутся
     * только из профиля.
     */
    private double calculateDamage(EclipseItem skill, PlayerProfile profile, Player player) {
        double baseDamage = skill.getBaseDamage();
        String statName = switch (skill.getSkillClass()) {
            case MELEE_STRIKE -> "strength";
            case ARROW_SHOT -> "dexterity";
            case FIREBALL -> "intelligence";
        };

        // Берём суммарный стат: профиль + экипировка + изученные перки.
        // До фикса перки не влияли на урон навыков — игрок прокачивал
        // dex/intl/str в дереве, а fireball/arrow/melee били как прежде.
        int totalStat = (player != null)
                ? ru.eclipsia.core.stats.StatResolver.total(player, profile, statName)
                : profile.getStat(statName);

        return baseDamage * (1 + totalStat / 50.0);
    }

    /**
     * Получить бонус стата с экипировки через рефлексию (soft-dep на EclipsiaItems).
     * Кешируем Method-ссылку чтобы не делать reflection на каждый удар.
     */
    private static volatile java.lang.reflect.Method equipBonusMethod;
    private static volatile boolean equipBonusResolved;

    private int getEquipmentStatBonus(Player player, String statName) {
        try {
            if (!equipBonusResolved) {
                synchronized (SkillListener.class) {
                    if (!equipBonusResolved) {
                        try {
                            Class<?> clz = Class.forName(
                                    "ru.eclipsia.items.equipment.EquipmentBonusApplier");
                            equipBonusMethod = clz.getMethod(
                                    "getStatBonus", Player.class, String.class);
                        } catch (Exception ignored) {
                            // EclipsiaItems не установлен — просто пропускаем.
                        }
                        equipBonusResolved = true;
                    }
                }
            }
            if (equipBonusMethod == null) return 0;
            Object result = equipBonusMethod.invoke(null, player, statName);
            return (result instanceof Integer i) ? i : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Проверить кулдаун
     */
    private boolean isOnCooldown(Player player, int hotbarSlot, int cooldownTicks) {
        Map<Integer, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        
        Long lastUse = playerCooldowns.get(hotbarSlot);
        if (lastUse == null) return false;
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long cooldownMs = cooldownTicks * 50L; // тики в миллисекунды
        
        return elapsed < cooldownMs;
    }
    
    /**
     * Получить оставшееся время кулдауна в тиках
     */
    private long getRemainingCooldown(Player player, int hotbarSlot, int cooldownTicks) {
        Map<Integer, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        
        Long lastUse = playerCooldowns.get(hotbarSlot);
        if (lastUse == null) return 0;
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long cooldownMs = cooldownTicks * 50L;
        
        return Math.max(0, (cooldownMs - elapsed) / 50);
    }
    
    /**
     * Установить кулдаун
     */
    private void setCooldown(Player player, int hotbarSlot) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(hotbarSlot, System.currentTimeMillis());
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // GUI навыков
        if (title.equals("§6Навыки")) {
            handleSkillsGuiClick(event, player);
            return;
        }

        // ПОЛНАЯ ЗАЩИТА иконок навыков в хотбаре: навык нельзя:
        //  — забрать (клик по слоту навыка),
        //  — положить на слот навыка (overwrite курсором),
        //  — свопнуть number-key (hotbar-button ведёт в/из слота навыка),
        //  — shift-click выдернуть в инвентарь,
        //  — перетащить курсором, если в нём оказалась иконка навыка.
        SkillManager.PlayerSkills skills = plugin.getSkillManager().getPlayerSkills(player);
        if (skills == null) return;

        int slot = event.getSlot();
        boolean clickedPlayerInv = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == org.bukkit.event.inventory.InventoryType.PLAYER;

        // 1. Клик по слоту хотбара, в котором зарегистрирован навык.
        if (clickedPlayerInv && skills.hotbarMapping.containsKey(slot)) {
            event.setCancelled(true);
            return;
        }

        // 2. Number-key swap: hotbarButton ведёт в слот с навыком.
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && event.getHotbarButton() >= 0
                && skills.hotbarMapping.containsKey(event.getHotbarButton())) {
            event.setCancelled(true);
            return;
        }

        // 3. Курсор держит иконку навыка (например, из прошлого таба) — блок.
        ItemStack cursor = event.getCursor();
        if (cursor != null && isSkillIcon(cursor)) {
            event.setCancelled(true);
            return;
        }

        // 4. Забрали навык иначе (shift-click, drop key) — currentItem = иконка.
        ItemStack current = event.getCurrentItem();
        if (current != null && isSkillIcon(current)) {
            event.setCancelled(true);
        }
    }

    /**
     * Проверить, что предмет — иконка навыка по material + наличию display name.
     * Используется для перехватов, когда маппинг хотбара недоступен.
     */
    private boolean isSkillIcon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type != Material.IRON_SWORD && type != Material.BOW && type != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Любая drag-операция с иконкой навыка на курсоре — отмена.
        if (isSkillIcon(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }

        String title = event.getView().getTitle();
        int topSize = event.getView().getTopInventory().getSize();

        // В GUI §6Навыки drag-распределение в верхний инвентарь — мультислот;
        // мы не поддерживаем такую вставку (только клик), поэтому отменяем.
        // Drag целиком по нижнему инвентарю не трогаем — игрок может
        // свободно перекладывать гемы у себя в инвентаре.
        if ("§6Навыки".equals(title)) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        SkillManager.PlayerSkills skills = plugin.getSkillManager().getPlayerSkills(player);
        if (skills == null) return;
        for (int rawSlot : event.getRawSlots()) {
            int invSlot = rawSlot - topSize;
            if (invSlot < 0) continue;
            // Хотбар имеет rawSlot = topSize + 27 + n.
            int hotbarSlot = rawSlot - (topSize + 27);
            if (hotbarSlot >= 0 && hotbarSlot <= 8 && skills.hotbarMapping.containsKey(hotbarSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isSkillIcon(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cНельзя выбросить навык!");
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isSkillIcon(event.getMainHandItem()) || isSkillIcon(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * При выходе игрока сохраняем актуальное состояние навыков (включая
     * hotbarMapping — баг 3) и очищаем in-memory кэш, чтобы при следующем
     * заходе данные перечитались с диска.
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try {
            plugin.getSkillManager().saveSkillsToProfile(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить навыки игрока "
                    + player.getName() + " при выходе: " + e.getMessage());
        }
        plugin.getSkillManager().clearCache(player.getUniqueId());
    }

    /**
     * Если при закрытии любого инвентаря в курсоре игрока оказалась иконка
     * навыка — удаляем её. Bukkit по умолчанию дропает курсор в инвентарь
     * игрока, что создаст дубль иконки.
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        ItemStack onCursor = player.getItemOnCursor();
        if (onCursor != null && isSkillIcon(onCursor)) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Защита от поднятия иконки навыка с земли. Иконки не должны существовать
     * как dropped-items, но если такое случилось (например, после неаккуратного
     * /clear) — иконка просто уничтожается, а игрок не получит дубль навыка.
     */
    @EventHandler
    public void onPlayerPickupItem(org.bukkit.event.player.PlayerAttemptPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (isSkillIcon(item)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    /**
     * Иконки навыков не должны терять прочность от ударов / стрельбы.
     * IRON_SWORD/BOW — обычные предметы Minecraft, и без cancel они сломаются
     * через ~250 ударов, навсегда удалив навык игрока из хотбара.
     */
    @EventHandler
    public void onItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (isSkillIcon(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ============================================================
    //  Авто-экип ПКМ по гему в руке
    // ============================================================
    /**
     * Игрок ПКМ-кнул эклипс-гем (изумруд для навыка / аметист для поддержки).
     * Кладём его в первый свободный слот соответствующего типа:
     * <ul>
     *   <li>SKILL_GEM → первый пустой {@code skillSlots[i]}; insertSkill
     *       автоматически создаст иконку в хотбаре.</li>
     *   <li>SUPPORT_GEM → первый пустой {@code supportSlots[skill][j]} среди
     *       занятых навыков (без навыка поддержку прикреплять некуда).</li>
     * </ul>
     * Гем уменьшается на 1 в руке игрока. Если экип не удался — гем
     * сохраняется (амортизация ошибки).
     */
    private void handleGemRightClick(Player player, EclipseItem gem, ItemStack gemStack) {
        SkillManager mgr = plugin.getSkillManager();
        SkillManager.PlayerSkills skills = mgr.getPlayerSkills(player);

        if (gem.getType() == EclipseItem.EclipseType.SKILL_GEM) {
            int free = -1;
            for (int i = 0; i < 5; i++) {
                if (skills.skillSlots[i] == null) { free = i; break; }
            }
            if (free < 0) {
                player.sendMessage("§cВсе 5 слотов навыков заняты. Снимите один в §6/skills§c.");
                return;
            }
            // ВАЖНО: уменьшаем стек ДО insertSkill, иначе insertSkill положит
            // иконку в тот же слот, где сейчас гем, и потеряем гем безвозвратно.
            decrementHand(player, gemStack);
            if (!mgr.insertSkill(player, free, gem)) {
                // Возвращаем гем игроку.
                player.getInventory().addItem(gem.toItemStack());
            }
            return;
        }

        if (gem.getType() == EclipseItem.EclipseType.SUPPORT_GEM) {
            // Ищем первый занятый skillSlot со свободным supportSlot.
            int targetSkill = -1, targetSupport = -1;
            for (int i = 0; i < 5 && targetSkill < 0; i++) {
                if (skills.skillSlots[i] == null) continue;
                for (int j = 0; j < 2; j++) {
                    if (skills.supportSlots[i][j] == null) {
                        targetSkill = i; targetSupport = j; break;
                    }
                }
            }
            if (targetSkill < 0) {
                player.sendMessage("§cНет навыка со свободным слотом поддержки. "
                        + "Сначала вставьте навык или освободите support-слот.");
                return;
            }
            decrementHand(player, gemStack);
            if (!mgr.insertSupport(player, targetSkill, targetSupport, gem)) {
                player.getInventory().addItem(gem.toItemStack());
            }
        }
    }

    private void decrementHand(Player player, ItemStack inHand) {
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(amount - 1);
        }
    }

    // ============================================================
    //  GUI §6Навыки — реальная вставка/изъятие гемов
    // ============================================================
    /**
     * Полная замена прежнего обработчика. Поддерживает сценарии:
     * <ol>
     *   <li>Курсор пустой + клик по занятому слоту → снять гем в курсор
     *       (или в инвентарь, если shift-click).</li>
     *   <li>Курсор содержит подходящий гем + клик по пустому слоту →
     *       вставить гем (курсор очищается).</li>
     *   <li>Тип гема не совпадает со слотом → отказ + сообщение.</li>
     *   <li>Любой shift-click по гему в нижнем инвентаре → попытка
     *       автовставки в первый свободный подходящий слот GUI.</li>
     * </ol>
     * Вся логика идёт через cancel + ручную манипуляцию курсором/инвентарём
     * — иначе Bukkit может частично применить операцию (особенно с stacks).
     */
    private void handleSkillsGuiClick(InventoryClickEvent event, Player player) {
        SkillManager mgr = plugin.getSkillManager();
        SkillManager.PlayerSkills skills = mgr.getPlayerSkills(player);
        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        boolean isTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(top);

        // Никогда не позволяем тащить иконку навыка через GUI — её нельзя
        // даже временно держать на курсоре, иначе игрок может задублировать
        // навык, кинув курсор в инвентарь.
        if (isSkillIcon(event.getCursor()) || isSkillIcon(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && event.getHotbarButton() >= 0
                && skills != null
                && skills.hotbarMapping.containsKey(event.getHotbarButton())) {
            event.setCancelled(true);
            return;
        }

        if (!isTop) {
            // Клик по нижнему (своему) инвентарю.
            // Запрещаем трогать слоты хотбара, в которых лежит иконка навыка.
            if (skills != null
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().getType()
                            == org.bukkit.event.inventory.InventoryType.PLAYER
                    && skills.hotbarMapping.containsKey(event.getSlot())) {
                event.setCancelled(true);
                return;
            }

            // Shift-click по гему — авто-экип в первый подходящий слот GUI.
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                EclipseItem gem = EclipseItem.fromItemStack(current);
                if (gem != null) {
                    event.setCancelled(true);
                    if (current.getAmount() <= 1) {
                        event.setCurrentItem(null);
                    } else {
                        current.setAmount(current.getAmount() - 1);
                    }
                    handleGemRightClickFromGui(player, gem);
                    reopenSkillsGui(player);
                    return;
                }
                // Не-гем: запрещаем shift-перенос в верхний инвентарь
                // (Bukkit бы попытался положить предмет в наши слоты GUI).
                event.setCancelled(true);
                return;
            }
            // Обычный клик по своему инвентарю — Bukkit обрабатывает сам:
            // игрок может свободно поднять/положить гем, чтобы потом
            // вручную перетащить его в слот GUI левым кликом.
            return;
        }

        // Клик по верхнему инвентарю (GUI) — всегда вручную.
        event.setCancelled(true);

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (SkillsGUI.isSkillSlot(rawSlot)) {
            int skillIndex = SkillsGUI.getSkillIndex(rawSlot);
            handleSkillSlotClick(player, skillIndex, cursor, current, event);
        } else if (SkillsGUI.isSupportSlot(rawSlot)) {
            int[] idx = SkillsGUI.getSupportIndex(rawSlot);
            if (idx == null) return;
            handleSupportSlotClick(player, idx[0], idx[1], cursor, current, event);
        }
    }

    /** Сходно с {@link #handleGemRightClick}, но без манипуляции рукой. */
    private void handleGemRightClickFromGui(Player player, EclipseItem gem) {
        SkillManager mgr = plugin.getSkillManager();
        SkillManager.PlayerSkills skills = mgr.getPlayerSkills(player);
        if (gem.getType() == EclipseItem.EclipseType.SKILL_GEM) {
            for (int i = 0; i < 5; i++) {
                if (skills.skillSlots[i] == null) {
                    if (!mgr.insertSkill(player, i, gem)) {
                        player.getInventory().addItem(gem.toItemStack());
                    }
                    return;
                }
            }
            player.sendMessage("§cВсе 5 слотов навыков заняты.");
            player.getInventory().addItem(gem.toItemStack());
        } else {
            for (int i = 0; i < 5; i++) {
                if (skills.skillSlots[i] == null) continue;
                for (int j = 0; j < 2; j++) {
                    if (skills.supportSlots[i][j] == null) {
                        if (!mgr.insertSupport(player, i, j, gem)) {
                            player.getInventory().addItem(gem.toItemStack());
                        }
                        return;
                    }
                }
            }
            player.sendMessage("§cНет свободного слота поддержки.");
            player.getInventory().addItem(gem.toItemStack());
        }
    }

    private void handleSkillSlotClick(Player player, int skillIndex, ItemStack cursor,
                                       ItemStack current, InventoryClickEvent event) {
        SkillManager mgr = plugin.getSkillManager();
        SkillManager.PlayerSkills skills = mgr.getPlayerSkills(player);
        EclipseItem inSlot = skills.skillSlots[skillIndex];

        EclipseItem cursorGem = EclipseItem.fromItemStack(cursor);

        // Курсор пуст: снимаем гем (если есть) → отдаём в инвентарь/курсор.
        if (cursorGem == null) {
            if (inSlot == null) return;
            // Сначала сохраняем поддержки, чтобы вернуть их в инвентарь
            EclipseItem[] supports = new EclipseItem[2];
            supports[0] = skills.supportSlots[skillIndex][0];
            supports[1] = skills.supportSlots[skillIndex][1];

            // Возвращаем гем игроку. Сначала пробуем addItem (стэк),
            // если не влезло — кладём в курсор.
            mgr.removeSkill(player, skillIndex);
            ItemStack returned = inSlot.toItemStack();
            java.util.Map<Integer, ItemStack> overflow =
                    player.getInventory().addItem(returned);

            // Возвращаем поддержки в инвентарь (если были)
            for (EclipseItem support : supports) {
                if (support != null) {
                    java.util.Map<Integer, ItemStack> supOverflow =
                            player.getInventory().addItem(support.toItemStack());
                    if (!supOverflow.isEmpty()) {
                        // Если не влезло — дропаем рядом
                        player.getWorld().dropItemNaturally(player.getLocation(),
                                supOverflow.values().iterator().next());
                    }
                }
            }

            if (!overflow.isEmpty()) {
                event.setCursor(overflow.values().iterator().next());
            }
            player.sendMessage("§7Навык снят: §6" + inSlot.getName());
            reopenSkillsGui(player);
            return;
        }

        // В курсоре чужой тип (поддержка) — отказ.
        if (cursorGem.getType() != EclipseItem.EclipseType.SKILL_GEM) {
            player.sendMessage("§cВ этот слот можно ставить только §aэклипс-навык §c(изумруд).");
            return;
        }

        // В курсоре навык; в слоте уже есть навык — отказ (поддержим swap позже).
        if (inSlot != null) {
            player.sendMessage("§cСлот занят. Сначала снимите навык кликом без курсора.");
            return;
        }

        // Вставляем навык. Уменьшаем курсор на 1, но обнуляем полностью —
        // эклипс-гемы у нас не стэкаются по дизайну.
        if (mgr.insertSkill(player, skillIndex, cursorGem)) {
            event.setCursor(null);
            reopenSkillsGui(player);
        } else {
            player.sendMessage("§cНе удалось вставить навык.");
        }
    }

    private void handleSupportSlotClick(Player player, int skillIndex, int supportIndex,
                                         ItemStack cursor, ItemStack current,
                                         InventoryClickEvent event) {
        SkillManager mgr = plugin.getSkillManager();
        SkillManager.PlayerSkills skills = mgr.getPlayerSkills(player);
        EclipseItem skillInSlot = skills.skillSlots[skillIndex];
        EclipseItem inSlot = skills.supportSlots[skillIndex][supportIndex];

        EclipseItem cursorGem = EclipseItem.fromItemStack(cursor);

        if (cursorGem == null) {
            if (inSlot == null) return;
            EclipseItem removed = mgr.removeSupport(player, skillIndex, supportIndex);
            if (removed == null) return;
            ItemStack returned = removed.toItemStack();
            java.util.Map<Integer, ItemStack> overflow =
                    player.getInventory().addItem(returned);
            if (!overflow.isEmpty()) {
                event.setCursor(overflow.values().iterator().next());
            }
            player.sendMessage("§7Поддержка снята: §d" + removed.getName());
            reopenSkillsGui(player);
            return;
        }

        if (cursorGem.getType() != EclipseItem.EclipseType.SUPPORT_GEM) {
            player.sendMessage("§cВ этот слот можно ставить только §dэклипс-поддержку §c(аметист).");
            return;
        }

        if (skillInSlot == null) {
            player.sendMessage("§cСначала вставьте навык в слот #" + (skillIndex + 1) + ".");
            return;
        }

        if (inSlot != null) {
            player.sendMessage("§cСлот поддержки занят. Сначала снимите её кликом без курсора.");
            return;
        }

        if (mgr.insertSupport(player, skillIndex, supportIndex, cursorGem)) {
            event.setCursor(null);
            reopenSkillsGui(player);
        }
    }

    private void reopenSkillsGui(Player player) {
        // Переоткрываем на след. тике, чтобы текущий клик-фрейм не зависал.
        org.bukkit.Bukkit.getScheduler().runTask(plugin,
                () -> new SkillsGUI(plugin.getSkillManager()).open(player));
    }

    // ============================================================
    //  Поддержки навыков — общие хелперы
    // ============================================================

    /** Сериализовать список поддержек в csv enum-имён для metadata снаряда. */
    private String csvSupports(List<EclipseItem> supports) {
        if (supports.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (EclipseItem s : supports) {
            if (s.getSupportClass() == null) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(s.getSupportClass().name());
        }
        return sb.toString();
    }

    /** Прочитать поддержки из metadata снаряда обратно. */
    private java.util.Set<EclipseItem.SupportClass> readSupports(org.bukkit.entity.Entity entity) {
        java.util.EnumSet<EclipseItem.SupportClass> out = java.util.EnumSet.noneOf(EclipseItem.SupportClass.class);
        if (!entity.hasMetadata("eclipse_supports")) return out;
        String csv = entity.getMetadata("eclipse_supports").get(0).asString();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",")) {
            try {
                out.add(EclipseItem.SupportClass.valueOf(part.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    /** Нанести урон всем мобам в сфере вокруг точки (исключая стрелка). */
    private void damageInRadius(Location loc, double radius, Player shooter, double damage) {
        damageInRadius(loc, radius, shooter, damage,
                ru.eclipsia.core.combat.DamageType.PHYSICAL);
    }

    /**
     * Вариант с явным типом урона — нужен фаерболу, чтобы цифры всплывали
     * оранжевым (FIRE), а не белым (PHYSICAL).
     */
    private void damageInRadius(Location loc, double radius, Player shooter,
                                double damage,
                                ru.eclipsia.core.combat.DamageType type) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.equals(shooter)) continue;
            if (e instanceof Player) continue;
            markKiller(living, shooter);
            tagSkillDmg(living);
            living.damage(damage, shooter);
            ru.eclipsia.core.combat.DamageDisplay.show(living, damage, type);
        }
    }

    /**
     * Помечаем жертву меткой эклипс-урона на 1 тик. StatsCombatListener
     * увидит метку и не будет ни считать крит, ни рисовать второй
     * DamageDisplay поверх нашего. До фикса каждое попадание по мобу
     * приводило к двум всплывающим цифрам.
     */
    private void tagSkillDmg(LivingEntity victim) {
        if (victim == null) return;
        victim.setMetadata("eclipse_skill_dmg",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { victim.removeMetadata("eclipse_skill_dmg", plugin); }
            catch (Throwable ignored) {}
        }, 2L);
    }

    /**
     * AOE-стрела — "луч": каждый тик пока летит, наносит урон по всем
     * живым в радиусе 1.5 блока вокруг своей текущей позиции. Каждый моб
     * может получить урон от одной стрелы только один раз (через сет).
     */
    private void startArrowBeam(Arrow arrow, Player shooter, double damage) {
        java.util.Set<UUID> alreadyHit = new java.util.HashSet<>();
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (arrow == null || arrow.isDead() || arrow.isOnGround() || ticks++ > 200) {
                    cancel();
                    return;
                }
                Location l = arrow.getLocation();
                // Зелёный лучевой trail.
                l.getWorld().spawnParticle(Particle.REDSTONE, l, 6, 0.4, 0.4, 0.4,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 255, 60), 1.6f));
                l.getWorld().spawnParticle(Particle.CRIT, l, 2);
                for (Entity e : l.getWorld().getNearbyEntities(l, 1.5, 1.5, 1.5)) {
                    if (!(e instanceof LivingEntity living)) continue;
                    if (e instanceof Player) continue;
                    if (e.equals(shooter)) continue;
                    if (!alreadyHit.add(e.getUniqueId())) continue;
                    markKiller(living, shooter);
                    tagSkillDmg(living);
                    living.damage(damage * 0.6, shooter);
                    ru.eclipsia.core.combat.DamageDisplay.show(
                            living, damage * 0.6, ru.eclipsia.core.combat.DamageType.PHYSICAL);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Партикл-эффект "поддержка активна" — индикатор для игрока, что
     * скилл срабатывает не как обычный, а с гем-усилением.
     * Цвет/тип зависит от класса навыка:
     * <ul>
     *   <li>ARROW_SHOT — зелёные точки (DUST, color GREEN)</li>
     *   <li>FIREBALL   — искры фейерверка (FIREWORK)</li>
     *   <li>MELEE_STRIKE — красные точки (DUST, color RED)</li>
     * </ul>
     */
    private void spawnSkillFx(Location loc, EclipseItem.SkillClass cls, int count) {
        if (loc == null || loc.getWorld() == null) return;
        switch (cls) {
            case ARROW_SHOT -> {
                loc.getWorld().spawnParticle(Particle.REDSTONE, loc, count, 0.5, 0.5, 0.5,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 255, 60), 1.8f));
                loc.getWorld().spawnParticle(Particle.CRIT, loc, count / 2, 0.3, 0.3, 0.3);
                loc.getWorld().spawnParticle(Particle.COMPOSTER, loc, count / 3,
                        0.4, 0.4, 0.4, 0.0);
            }
            case FIREBALL -> {
                loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, count,
                        0.5, 0.5, 0.5, 0.05);
                loc.getWorld().spawnParticle(Particle.FLAME, loc, count, 0.5, 0.5, 0.5, 0.04);
            }
            case MELEE_STRIKE -> {
                loc.getWorld().spawnParticle(Particle.REDSTONE, loc, count, 0.6, 0.6, 0.6,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 40, 40), 1.8f));
                loc.getWorld().spawnParticle(Particle.FLAME, loc, count / 2,
                        0.5, 0.5, 0.5, 0.02);
                loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, count / 3,
                        0.4, 0.4, 0.4);
            }
        }
    }

    /**
     * Партикл-трейл за снарядом, пока он не воткнулся / не помер.
     * Запускается раз в тик; tick-counter ограничен 200 (10 сек) от
     * "вечных" фаерболов / стрел в чанке.
     */
    private void startProjectileTrail(org.bukkit.entity.Projectile projectile,
                                      EclipseItem.SkillClass cls) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (projectile == null || projectile.isDead()
                        || (projectile instanceof Arrow a && a.isOnGround())
                        || ticks++ > 200) {
                    cancel();
                    return;
                }
                spawnSkillFx(projectile.getLocation(), cls, 3);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
