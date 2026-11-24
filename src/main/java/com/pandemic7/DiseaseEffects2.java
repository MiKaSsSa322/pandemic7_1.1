package com.pandemic7;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Серверные эффекты болезней 51–100. */
final class DiseaseEffects2 {
    private DiseaseEffects2() {}

    static void refresh(ServerPlayer p, EnumSet<Disease> s) {
        DiseaseEffects.mod(p, Attributes.MAX_HEALTH, Disease.RADIATION, "hp", -4.0,
                AttributeModifier.Operation.ADDITION, s.contains(Disease.RADIATION));
        DiseaseEffects.mod(p, Attributes.ATTACK_SPEED, Disease.STIFF_JOINTS, "aspd", -0.4,
                AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.STIFF_JOINTS));
        DiseaseEffects.mod(p, Attributes.MOVEMENT_SPEED, Disease.STIFF_JOINTS, "spd", -0.05,
                AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.STIFF_JOINTS));
        DiseaseEffects.mod(p, Attributes.MOVEMENT_SPEED, Disease.SLUGGISH, "spd", -0.15,
                AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.SLUGGISH));
        DiseaseEffects.mod(p, Attributes.ATTACK_SPEED, Disease.SLUGGISH, "aspd", -0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL, s.contains(Disease.SLUGGISH));
    }

    private static boolean solid(ServerLevel l, BlockPos pos) {
        return l.getBlockState(pos).isCollisionShapeFullBlock(l, pos);
    }

    private static void damage(ItemStack st) {
        if (st.isDamageableItem() && st.getDamageValue() < st.getMaxDamage() - 2) {
            st.setDamageValue(st.getDamageValue() + 1);
        }
    }

    static void tick(ServerPlayer p, EnumSet<Disease> s, PlayerState st) {
        int t = p.tickCount;
        RandomSource r = p.getRandom();
        ServerLevel lvl = p.serverLevel();
        boolean sec = t % 20 == 0;
        BlockPos pos = p.blockPosition();
        AABB box = p.getBoundingBox();

        // 51 Гемофилия
        if (st.bleed > 0) {
            if (s.contains(Disease.HEMOPHILIA)) {
                st.bleed--;
                if (st.bleed % 20 == 0) {
                    if (p.getHealth() > 2f) p.setHealth(p.getHealth() - 1f);
                    lvl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 3, 0.2, 0.3, 0.2, 0.1);
                }
            } else {
                st.bleed = 0;
            }
        }

        // 52 Токсикоз
        if (s.contains(Disease.TOXICOSIS) && sec && r.nextInt(90) == 0) {
            DiseaseEffects.eff(p, MobEffects.POISON, 100, 0, true);
        }

        // 53 Пыльцевая аллергия
        if (s.contains(Disease.ALLERGY) && sec) {
            boolean near = false;
            for (int dx = -3; dx <= 3 && !near; dx++)
                for (int dy = -2; dy <= 2 && !near; dy++)
                    for (int dz = -3; dz <= 3 && !near; dz++)
                        if (lvl.getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.FLOWERS)) near = true;
            if (near) {
                DiseaseEffects.eff(p, MobEffects.CONFUSION, 100, 0, true);
                DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true);
                if (r.nextInt(4) == 0) DiseaseEffects.msg(p, "§eАпчхи!");
            }
        }

        // 54 Светобоязнь
        if (s.contains(Disease.PHOTOPHOBIA) && sec && lvl.isDay() && !lvl.isRaining() && lvl.canSeeSky(pos)) {
            DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
            DiseaseEffects.eff(p, MobEffects.DIG_SLOWDOWN, 60, 0, true);
        }

        // 55 Гидрофобия
        if (s.contains(Disease.HYDROPHOBIA) && sec && p.isInWater()) {
            DiseaseEffects.eff(p, MobEffects.CONFUSION, 80, 0, true);
            DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 80, 1, true);
            DiseaseEffects.eff(p, MobEffects.HUNGER, 80, 0, true);
        }

        // 56 Аритмия
        if (s.contains(Disease.ARRHYTHMIA) && sec && r.nextInt(100) == 0) {
            Sounds.notify(p, "entity.warden.heartbeat", 1f, 1.2f);
            if (p.getHealth() > 6f) p.hurt(lvl.damageSources().magic(), 2f);
            DiseaseEffects.eff(p, MobEffects.WEAKNESS, 100, 0, true);
            DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 100, 0, true);
            DiseaseEffects.msg(p, "§cСердце пропустило удар...");
        }

        // 58 Диабет
        if (s.contains(Disease.DIABETES) && t % 40 == 0) {
            FoodData fd = p.getFoodData();
            if (fd.getSaturationLevel() > 0f) fd.setSaturation(Math.max(0f, fd.getSaturationLevel() - 1f));
            if (fd.getFoodLevel() < 8) {
                DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
                DiseaseEffects.eff(p, MobEffects.DIG_SLOWDOWN, 60, 0, true);
            }
        }

        // 60 Лучевая болезнь
        if (s.contains(Disease.RADIATION) && sec && r.nextInt(120) == 0) {
            DiseaseEffects.eff(p, MobEffects.HUNGER, 200, 1, true);
        }

        // 62 Куриная слепота
        if (s.contains(Disease.NIGHT_BLINDNESS) && sec && lvl.getMaxLocalRawBrightness(pos) < 6) {
            DiseaseEffects.eff(p, MobEffects.DARKNESS, 80, 0, false);
        }

        // 71 Клаустрофобия
        if (s.contains(Disease.CLAUSTROPHOBIA) && sec) {
            boolean roof = solid(lvl, pos.above(2));
            int walls = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (solid(lvl, pos.relative(d))) walls++;
            }
            if (roof && walls >= 3) {
                DiseaseEffects.eff(p, MobEffects.CONFUSION, 100, 0, true);
                DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
                DiseaseEffects.msg(p, "§7Тесно... нечем дышать...");
            }
        }

        // 72 Агорафобия
        if (s.contains(Disease.AGORAPHOBIA) && sec && lvl.canSeeSky(pos)) {
            boolean wall = false;
            for (int i = 0; i < 8 && !wall; i++) {
                double a = i * Math.PI / 4;
                BlockPos q = BlockPos.containing(p.getX() + Math.cos(a) * 5, p.getY() + 1, p.getZ() + Math.sin(a) * 5);
                if (solid(lvl, q)) wall = true;
            }
            if (!wall) {
                DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true);
                DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
            }
        }

        // 73 Социофобия
        if (s.contains(Disease.SOCIOPHOBIA) && sec) {
            boolean crowd = !lvl.getEntitiesOfClass(Player.class, box.inflate(10), o -> o != p && !o.isSpectator()).isEmpty()
                    || !lvl.getEntitiesOfClass(Villager.class, box.inflate(8)).isEmpty();
            if (crowd) {
                DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
                DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true);
            }
        }

        // 76 Эйфория
        if (s.contains(Disease.EUPHORIA)) {
            int phase = (t / 1200) % 3;
            if (t % 1200 == 0) {
                DiseaseEffects.msg(p, phase < 2 ? "§aЭйфория! Вы чувствуете себя непобедимым." : "§8Спад... силы уходят.");
            }
            if (sec) {
                if (phase < 2) {
                    DiseaseEffects.eff(p, MobEffects.MOVEMENT_SPEED, 60, 1, true);
                    DiseaseEffects.eff(p, MobEffects.DIG_SPEED, 60, 1, true);
                    DiseaseEffects.eff(p, MobEffects.JUMP, 60, 1, true);
                } else {
                    DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true);
                    DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
                    DiseaseEffects.eff(p, MobEffects.DIG_SLOWDOWN, 60, 1, true);
                    DiseaseEffects.eff(p, MobEffects.CONFUSION, 60, 0, true);
                }
            }
        }

        // 77 Апатия: добыча медленнее (бег отключается на клиенте)
        if (s.contains(Disease.APATHY) && t % 40 == 0) DiseaseEffects.eff(p, MobEffects.DIG_SLOWDOWN, 100, 0, false);

        // 78 Тревожность
        if (s.contains(Disease.ANXIETY) && sec
                && !lvl.getEntitiesOfClass(Monster.class, box.inflate(20)).isEmpty()) {
            Sounds.notify(p, "entity.warden.heartbeat", 0.8f, 1f);
            DiseaseEffects.eff(p, MobEffects.WEAKNESS, 60, 0, true);
        }

        // 79 Ложные воспоминания
        if (s.contains(Disease.FALSE_MEMORIES) && sec && r.nextInt(180) == 0) {
            String[] things = {"сундук с алмазами", "ваш тайный склад", "дом с кроватью", "шахта с железом", "запас эндер-жемчуга"};
            int x = (int) p.getX() + r.nextInt(801) - 400;
            int z = (int) p.getZ() + r.nextInt(801) - 400;
            p.sendSystemMessage(Component.literal("§7Вы точно помните: " + things[r.nextInt(things.length)]
                    + " — " + x + " " + (40 + r.nextInt(40)) + " " + z));
        }

        // 83 Слабость хвата
        if (s.contains(Disease.WEAK_GRIP) && p.isUsingItem() && r.nextInt(80) == 0) {
            p.stopUsingItem();
            DiseaseEffects.msg(p, "§7Пальцы разжались...");
        }

        // 91 Коррозия снаряжения
        if (s.contains(Disease.CORROSION) && t % 600 == 0) {
            damage(p.getMainHandItem());
            for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                damage(p.getItemBySlot(es));
            }
        }

        // 92 Вампиризм
        if (s.contains(Disease.VAMPIRISM) && sec) {
            if (lvl.isDay() && !lvl.isRaining() && lvl.canSeeSky(pos.above()) && !p.isInWaterOrRain()
                    && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                p.setSecondsOnFire(4);
            }
            if (lvl.isNight()) DiseaseEffects.eff(p, MobEffects.NIGHT_VISION, 260, 0, false);
        }

        // 94 Сглаз
        if (s.contains(Disease.EVIL_EYE) && t % 40 == 0) {
            for (Animal a : lvl.getEntitiesOfClass(Animal.class, box.inflate(10))) a.setLastHurtByMob(p);
        }

        // 95 Громоотвод
        if (s.contains(Disease.LIGHTNING_ROD) && sec && lvl.canSeeSky(pos)
                && ((lvl.isThundering() && r.nextInt(90) == 0) || (lvl.isRaining() && r.nextInt(600) == 0))) {
            strike(lvl, p.position());
        }

        // 96 Зов бездны
        if (s.contains(Disease.ABYSS_CALL) && sec && p.getY() < 20 && r.nextInt(30) == 0) {
            DiseaseEffects.eff(p, MobEffects.DARKNESS, 100, 0, false);
            DiseaseEffects.eff(p, MobEffects.CONFUSION, 60, 0, true);
            Sounds.notify(p, "ambient.cave", 0.8f, 0.5f);
        }

        // 97 Ночные гости
        if (s.contains(Disease.NIGHT_GUESTS) && sec && lvl.isNight() && lvl.canSeeSky(pos) && r.nextInt(100) == 0
                && lvl.getEntitiesOfClass(Phantom.class, box.inflate(40)).size() < 3) {
            Phantom ph = EntityType.PHANTOM.create(lvl);
            if (ph != null) {
                ph.moveTo(p.getX() + r.nextInt(11) - 5, p.getY() + 8 + r.nextInt(5), p.getZ() + r.nextInt(11) - 5, 0f, 0f);
                ph.setTarget(p);
                lvl.addFreshEntity(ph);
            }
        }

        // 98 Тёмная метка
        if (s.contains(Disease.DARK_MARK) && t % 40 == 0) {
            DiseaseEffects.eff(p, MobEffects.GLOWING, 100, 0, false);
            for (Monster m : lvl.getEntitiesOfClass(Monster.class, box.inflate(16))) {
                if (m.getTarget() == null) m.setTarget(p);
            }
        }

        // 99 Разрыв реальности
        if (s.contains(Disease.REALITY_RIFT) && sec && r.nextInt(120) == 0) {
            for (int i = 0; i < 8; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = 3 + r.nextInt(6);
                Vec3 target = new Vec3(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                Vec3 delta = target.subtract(p.position());
                BlockPos below = BlockPos.containing(target).below();
                if (lvl.noCollision(p, box.move(delta)) && !lvl.getBlockState(below).isAir()) {
                    lvl.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.5);
                    p.teleportTo(target.x, target.y, target.z);
                    p.fallDistance = 0f;
                    DiseaseEffects.msg(p, "§5Реальность треснула...");
                    break;
                }
            }
        }

        // 100 Апокалипсис
        if (s.contains(Disease.APOCALYPSE)) {
            if (t % 300 == 0) {
                DiseaseEffects.eff(p, MobEffects.DARKNESS, 100, 0, false);
                Sounds.notify(p, "entity.wither.ambient", 0.7f, 0.5f);
            }
            if (t % 200 == 100) DiseaseEffects.eff(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true);
            if (t % 1200 == 600) {
                double a = r.nextDouble() * Math.PI * 2;
                strike(lvl, p.position().add(Math.cos(a) * 8, 0, Math.sin(a) * 8));
                DiseaseEffects.msg(p, "§4Небо рушится...");
            }
        }
    }

    private static void strike(ServerLevel lvl, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(lvl);
        if (b != null) {
            b.moveTo(at);
            lvl.addFreshEntity(b);
        }
    }
}
