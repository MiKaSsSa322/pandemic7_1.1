package com.pandemic7;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/** Клиентская часть: камера, звуки, частицы, управление. Движение игрока клиент-авторитарно, поэтому оно здесь. */
public final class ClientEffects {
    private ClientEffects() {}

    private record Delayed(int due, Runnable run) {}

    private static final String[] AUDIO = {
            "entity.creeper.primed", "entity.zombie.ambient", "block.wooden_door.open", "entity.skeleton.ambient",
            "entity.enderman.stare", "block.chest.open", "entity.spider.ambient", "entity.generic.explode",
            "block.stone.break", "entity.player.hurt"
    };
    private static final String[] SCARY = {
            "entity.ghast.scream", "entity.phantom.swoop", "entity.zombie_villager.converted",
            "ambient.cave", "entity.wither.ambient", "entity.elder_guardian.ambient"
    };
    private static final String[] WHISPERS = {
            "…они смотрят…", "…оглянись…", "…ты не один…", "…беги…", "…тише…", "…он уже рядом…", "…не засыпай…"
    };

    private static int t;
    private static int seizure, tripTicks, imbRoll;
    private static int gravMode, gravUntil;
    private static int silTicks;
    private static double silX, silY, silZ;
    private static boolean silMoving;
    private static int postFx; // 0 нет, 1 инверсия, 2 двоение
    private static Entity enderman, spider;
    private static int flickerTicks, ritual, sleepwalk;
    private static boolean fxInit;

    // значения для оверлея (читает PandemicHud)
    public static float flickerAlpha, flashAlpha;
    public static final float[] FX = new float[8];
    public static final float[] FY = new float[8];

    private static final List<Delayed> delayed = new ArrayList<>();
    private static final ArrayDeque<Vec3> trail = new ArrayDeque<>();

    public static void reset() {
        t = seizure = tripTicks = imbRoll = gravMode = gravUntil = silTicks = 0;
        flickerTicks = ritual = sleepwalk = 0;
        flickerAlpha = flashAlpha = 0f;
        postFx = 0;
        enderman = null;
        spider = null;
        fxInit = false;
        delayed.clear();
        trail.clear();
    }

    private static double jit(RandomSource r, double k) {
        return (r.nextFloat() - 0.5) * k;
    }

    private static void later(int ticks, Runnable r) {
        delayed.add(new Delayed(t + ticks, r));
    }

    private static void playAt(Level lvl, double x, double y, double z, String id, float vol, float pitch) {
        SoundEvent s = Sounds.get(id);
        if (s != null) lvl.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol, pitch, false);
    }

    private static void playAround(LocalPlayer p, String id, double min, double max, float vol, float pitch) {
        RandomSource r = p.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        double d = min + r.nextDouble() * (max - min);
        playAt(p.level(), p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d, id, vol, pitch);
    }

    private static void setPostFx(Minecraft mc, Level lvl, int want) {
        if (want == 0) {
            if (postFx != 0) mc.gameRenderer.shutdownEffect();
            postFx = 0;
            return;
        }
        Entity e;
        if (want == 1) {
            if (enderman == null || enderman.level() != lvl) enderman = EntityType.ENDERMAN.create(lvl);
            e = enderman;
        } else {
            if (spider == null || spider.level() != lvl) spider = EntityType.SPIDER.create(lvl);
            e = spider;
        }
        if (e != null) {
            mc.gameRenderer.checkEntityPostEffect(e);
            postFx = want;
        }
    }

    public static boolean deafNow() {
        return ClientState.has(Disease.DEAFNESS) && (t % 1200) < 500;
    }

    /** Вызывается в начале клиентского тика (до управления). */
    public static void preTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p != null && ClientState.has(Disease.APATHY)) {
            mc.options.keySprint.setDown(false);
            p.setSprinting(false);
        }
    }

    // ---------- основной клиентский тик ----------
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (t != 0) reset();
            return;
        }
        if (mc.isPaused()) return;
        t++;
        ClientState.tickTimer();

        Iterator<Delayed> it = delayed.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Delayed d = it.next();
            if (d.due() <= t) {
                due.add(d.run());
                it.remove();
            }
        }
        due.forEach(Runnable::run);

        EnumSet<Disease> s = ClientState.set;
        if (s.isEmpty()) {
            setPostFx(mc, mc.level, 0);
            flickerAlpha = flashAlpha = 0f;
            trail.clear();
            return;
        }
        RandomSource r = p.getRandom();
        Level lvl = mc.level;
        Vec3 dm = p.getDeltaMovement();

        // экранные эффекты (оверлей)
        if (flickerTicks > 0) {
            flickerTicks--;
            flickerAlpha = r.nextBoolean() ? 0.9f : 0f;
        } else {
            flickerAlpha = 0f;
            if (s.contains(Disease.FLICKER) && r.nextInt(80) == 0) flickerTicks = 2 + r.nextInt(6);
        }
        if (flashAlpha > 0f) flashAlpha = Math.max(0f, flashAlpha - 0.04f);
        if (!fxInit) {
            for (int i = 0; i < FX.length; i++) {
                FX[i] = r.nextFloat();
                FY[i] = r.nextFloat();
            }
            fxInit = true;
        }
        if (s.contains(Disease.FLOATERS)) {
            for (int i = 0; i < FX.length; i++) {
                FY[i] += 0.0015f;
                FX[i] += (r.nextFloat() - 0.5f) * 0.003f;
                if (FY[i] > 1.1f) {
                    FY[i] = -0.1f;
                    FX[i] = r.nextFloat();
                }
            }
        }

        // 4 Тремор / 37 Дрожание рук / 85 Тики
        if (s.contains(Disease.TREMOR)) p.turn(jit(r, 6), jit(r, 6));
        if (s.contains(Disease.HAND_TREMOR) && (p.isUsingItem() || p.swinging)) p.turn(jit(r, 12), jit(r, 12));
        if (s.contains(Disease.TICS) && r.nextInt(120) == 0) p.turn(jit(r, 110), jit(r, 70));

        // 15 Цветовая инверсия / 68 Двоение (ванильные шейдеры через «взгляд» моба)
        int want = 0;
        if (s.contains(Disease.COLOR_INVERSION) && (t % 1200) < 260) want = 1;
        else if (s.contains(Disease.DOUBLE_VISION) && (t % 900) < 300) want = 2;
        if (want != postFx) setPostFx(mc, lvl, want);

        // звуки и галлюцинации — раз в секунду
        if (t % 20 == 0) {
            if (s.contains(Disease.AUDIO_HALLUC) && r.nextInt(18) == 0) {
                playAround(p, AUDIO[r.nextInt(AUDIO.length)], 5, 12, 0.9f, 0.85f + r.nextFloat() * 0.3f);
            }
            if (s.contains(Disease.WHISPER) && r.nextInt(12) == 0) {
                playAround(p, "ambient.cave", 1, 3, 0.7f, 0.5f);
                p.displayClientMessage(Component.literal("§8§o" + WHISPERS[r.nextInt(WHISPERS.length)]), true);
            }
            if (s.contains(Disease.NIGHTMARE) && r.nextInt(35) == 0) {
                playAround(p, SCARY[r.nextInt(SCARY.length)], 6, 14, 0.8f, 0.6f + r.nextFloat() * 0.3f);
            }
            if (s.contains(Disease.PARANOIA) && r.nextInt(18) == 0) footstepsBehind(p, lvl);
            if (s.contains(Disease.VISUAL_HALLUC) && r.nextInt(25) == 0 && silTicks <= 0) startSilhouette(p, true);
            if (s.contains(Disease.SILHOUETTES) && r.nextInt(25) == 0 && silTicks <= 0) startSilhouette(p, false);
            if (s.contains(Disease.TINNITUS) && r.nextInt(30) == 0) {
                for (int i = 0; i < 10; i++) {
                    later(i * 5, () -> playAt(lvl, p.getX(), p.getY(), p.getZ(), "block.note_block.chime", 0.35f, 2.0f));
                }
            }
            if (s.contains(Disease.DELIRIUM) && r.nextInt(30) == 0) {
                flashAlpha = 0.5f;
                playAt(lvl, p.getX(), p.getY(), p.getZ(), "entity.player.hurt", 0.8f, 1f);
                p.turn(jit(r, 40), jit(r, 20));
            }
        }

        // 74 Навязчивые ритуалы
        if (s.contains(Disease.RITUALS)) {
            if (ritual > 0) {
                ritual--;
                p.turn(16, 0);
            } else if (r.nextInt(1800) == 0) {
                ritual = 100;
                p.displayClientMessage(Component.literal("§7Нужно... оглядеться..."), true);
            }
        }

        // 93 Лунатизм
        if (s.contains(Disease.SLEEPWALKING)) {
            if (sleepwalk > 0) {
                sleepwalk--;
                p.turn(jit(r, 8), 0);
            } else if (lvl.isNight() && r.nextInt(900) == 0) {
                sleepwalk = 120;
                p.displayClientMessage(Component.literal("§8Ноги идут сами..."), true);
            }
        } else {
            sleepwalk = 0;
        }

        // 86 Гиперкинез
        if (s.contains(Disease.HYPERKINESIS) && r.nextInt(400) == 0) {
            Vec3 l = p.getLookAngle();
            p.setDeltaMovement(dm.x + l.x * 0.7, dm.y, dm.z + l.z * 0.7);
        }

        // 17 Эхо восприятия
        if (s.contains(Disease.ECHO) && t % 12 == 0 && p.onGround() && dm.horizontalDistanceSqr() > 0.003) {
            BlockState bs = lvl.getBlockState(p.blockPosition().below());
            SoundEvent step = bs.getSoundType().getStepSound();
            for (int i = 1; i <= 3; i++) {
                final int k = i;
                later(10 * k, () -> lvl.playLocalSound(p.getX(), p.getY(), p.getZ(), step,
                        SoundSource.PLAYERS, 0.5f / k, 0.8f, false));
            }
        }

        // 18 Миражи
        if (s.contains(Disease.MIRAGES) && t % 4 == 0 && r.nextInt(3) == 0) {
            Vec3 look = p.getLookAngle();
            double d = 10 + r.nextInt(12);
            double x = p.getX() + look.x * d + r.nextGaussian() * 3;
            double z = p.getZ() + look.z * d + r.nextGaussian() * 3;
            lvl.addParticle(ParticleTypes.SPLASH, x, p.getY() + 0.1, z, 0, 0.1, 0);
            lvl.addParticle(ParticleTypes.BUBBLE_POP, x, p.getY() + 0.2, z, 0, 0, 0);
        }

        // 14 / 20 Силуэты
        if (silTicks > 0) {
            silTicks--;
            double dx = p.getX() - silX, dz = p.getZ() - silZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 4 || silTicks == 0) {
                if (silMoving && silTicks > 0) playAround(p, "entity.enderman.scream", 2, 4, 0.5f, 0.5f);
                silTicks = 0;
            } else {
                if (silMoving) {
                    silX += dx / dist * 0.05;
                    silZ += dz / dist * 0.05;
                }
                if (t % 2 == 0) {
                    for (double h = 0; h < 1.8; h += 0.3) {
                        lvl.addParticle(ParticleTypes.SMOKE, silX + jit(r, 0.2), silY + h, silZ + jit(r, 0.2), 0, 0, 0);
                    }
                    lvl.addParticle(ParticleTypes.LARGE_SMOKE, silX, silY + 1.7, silZ, 0, 0, 0);
                }
            }
        }

        // 19 Нарушение равновесия
        if (imbRoll > 0) imbRoll--;
        if (s.contains(Disease.IMBALANCE) && r.nextInt(180) == 0 && p.onGround()) {
            double a = r.nextDouble() * Math.PI * 2;
            p.setDeltaMovement(dm.x + Math.cos(a) * 0.3, dm.y, dm.z + Math.sin(a) * 0.3);
            imbRoll = 30;
        }

        // 34 Судороги
        if (s.contains(Disease.SEIZURES)) {
            if (seizure > 0) {
                seizure--;
                if (seizure % 2 == 0) {
                    p.setDeltaMovement(dm.x * 0.5 + jit(r, 0.25), dm.y, dm.z * 0.5 + jit(r, 0.25));
                    p.turn(jit(r, 90), jit(r, 50));
                }
            } else if (r.nextInt(1800) == 0) {
                seizure = 70;
                p.displayClientMessage(Component.literal("§cСудороги!"), true);
            }
        } else {
            seizure = 0;
        }

        // 38 Нарушение походки
        if (s.contains(Disease.GAIT)) {
            if (tripTicks > 0) tripTicks--;
            else if (p.isSprinting() && r.nextInt(500) == 0) tripTicks = 15;
        }

        // 39 Нервный спазм
        if (s.contains(Disease.NERVOUS_SPASM) && r.nextInt(500) == 0) {
            if (p.onGround()) p.setDeltaMovement(dm.x, 0.42, dm.z);
            p.turn(jit(r, 160), jit(r, 60));
        }

        // 49 Нестабильная гравитация
        if (s.contains(Disease.GRAVITY)) {
            if (t >= gravUntil) {
                gravMode = r.nextInt(4);
                gravUntil = t + (gravMode == 3 ? 60 + r.nextInt(40) : 120 + r.nextInt(200));
                String[] names = {"", "§bГравитация слабеет...", "§cГравитация усиливается...", "§dНевесомость..."};
                if (gravMode != 0) p.displayClientMessage(Component.literal(names[gravMode]), true);
            }
            if (!p.onGround() && !p.isInWater() && !p.isFallFlying() && !p.getAbilities().flying) {
                Vec3 v = p.getDeltaMovement();
                switch (gravMode) {
                    case 1 -> p.setDeltaMovement(v.x, v.y + 0.045, v.z);
                    case 2 -> p.setDeltaMovement(v.x, Math.max(v.y - 0.05, -3.0), v.z);
                    case 3 -> p.setDeltaMovement(v.x, v.y + 0.08, v.z);
                    default -> { }
                }
            }
        }

        // 48 Фантомное отражение
        trail.addLast(p.position());
        if (trail.size() > 50) trail.removeFirst();
        if (s.contains(Disease.REFLECTION) && trail.size() >= 50 && t % 2 == 0) {
            Vec3 o = trail.peekFirst();
            for (double h = 0.2; h < 1.8; h += 0.4) {
                lvl.addParticle(ParticleTypes.END_ROD, o.x, o.y + h, o.z, 0, 0, 0);
            }
        }
    }

    private static void footstepsBehind(LocalPlayer p, Level lvl) {
        Vec3 look = p.getLookAngle();
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 0.01) return;
        final double nx = lx / len, nz = lz / len;
        BlockState bs = lvl.getBlockState(p.blockPosition().below());
        SoundEvent step = bs.getSoundType().getStepSound();
        for (int i = 0; i < 6; i++) {
            final double d = 7 - i;
            later(8 * i, () -> lvl.playLocalSound(p.getX() - nx * d, p.getY(), p.getZ() - nz * d, step,
                    SoundSource.PLAYERS, 0.8f, 0.9f, false));
        }
    }

    private static void startSilhouette(LocalPlayer p, boolean moving) {
        RandomSource r = p.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        double d = moving ? 9 : 14;
        silX = p.getX() + Math.cos(a) * d;
        silZ = p.getZ() + Math.sin(a) * d;
        silY = p.getY();
        silTicks = 200;
        silMoving = moving;
    }

    // ---------- управление ----------
    public static void onInput(Input in) {
        EnumSet<Disease> s = ClientState.set;
        if (s.isEmpty()) return;
        if (seizure > 0) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.jumping = false;
            in.shiftKeyDown = false;
            return;
        }
        if (sleepwalk > 0) {
            in.forwardImpulse = 1f;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
        if (s.contains(Disease.DISORIENTATION) && (t % 900) < 150) {
            in.forwardImpulse = -in.forwardImpulse;
            in.leftImpulse = -in.leftImpulse;
        }
        if (s.contains(Disease.COORDINATION)) {
            in.leftImpulse += (float) (Math.sin(t * 0.08) * 0.4);
        }
        if (s.contains(Disease.LAME_LEFT)) {
            in.forwardImpulse *= 0.8f;
            in.leftImpulse += 0.18f;
        }
        if (s.contains(Disease.LAME_RIGHT)) {
            in.forwardImpulse *= 0.8f;
            in.leftImpulse -= 0.18f;
        }
        if (s.contains(Disease.GAIT)) {
            if (tripTicks > 0) {
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
            } else {
                float m = (float) (0.55 + 0.45 * Math.abs(Math.sin(t * 0.22)));
                in.forwardImpulse *= m;
                in.leftImpulse *= m;
            }
        }
        if (s.contains(Disease.PARALYSIS)) in.jumping = false;
        if (s.contains(Disease.STIFFNESS)) in.shiftKeyDown = false;
    }

    // ---------- камера ----------
    public static float roll() {
        EnumSet<Disease> s = ClientState.set;
        float roll = 0f;
        if (s.contains(Disease.STRABISMUS)) roll += (float) (Math.sin(t * 0.03) * 3.0);
        if (imbRoll > 0) roll += (float) (Math.sin(t * 0.5) * imbRoll * 0.15);
        if (s.contains(Disease.VERTIGO)) roll += (float) (Math.sin(t * 0.012) * 9.0);
        if (s.contains(Disease.DEREALIZATION)) roll += (float) (Math.sin(t * 0.04) * 1.5);
        return roll;
    }

    public static double fovOffset() {
        EnumSet<Disease> s = ClientState.set;
        double off = 0.0;
        if (s.contains(Disease.STRABISMUS)) off += Math.sin(t * 0.021) * 5.0;
        if (s.contains(Disease.TUNNEL_VISION)) off -= 20.0;
        if (s.contains(Disease.DEREALIZATION)) off += Math.sin(t * 0.05) * 8.0;
        return off;
    }
}
