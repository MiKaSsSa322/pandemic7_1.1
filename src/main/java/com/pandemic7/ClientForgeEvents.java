package com.pandemic7;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PandemicMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase == TickEvent.Phase.START) {
            ClientEffects.preTick(mc);
            return;
        }
        while (Keys.TOGGLE_LIST.consumeClick()) {
            ClientState.listVisible = !ClientState.listVisible;
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(
                        ClientState.listVisible ? "Список болезней: показан" : "Список болезней: скрыт"), true);
            }
        }
        while (Keys.TOGGLE_HUD.consumeClick()) {
            ClientState.hudVisible = !ClientState.hudVisible;
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(
                        ClientState.hudVisible ? "HUD мода: показан" : "HUD мода: скрыт"), true);
            }
        }
        ClientEffects.tick(mc);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        ClientEffects.onInput(e.getInput());
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        double off = ClientEffects.fovOffset();
        if (off != 0.0) e.setFOV(Math.max(10.0, e.getFOV() + off));
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float roll = ClientEffects.roll();
        if (roll != 0f) e.setRoll(e.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent e) {
        SoundInstance si = e.getSound();
        if (si == null) return;
        SoundSource src = si.getSource();
        if (src == SoundSource.MASTER || src == SoundSource.MUSIC || src == SoundSource.RECORDS) return;
        if (ClientEffects.deafNow()) {
            e.setSound(null);
            return;
        }
        if (ClientState.has(Disease.SYNESTHESIA)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.random.nextInt(2) == 0) {
                mc.level.addParticle(ParticleTypes.NOTE, si.getX(), si.getY() + 1.0, si.getZ(),
                        mc.level.random.nextDouble(), 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (!ClientState.has(Disease.NUMBNESS)) return;
        if (e.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || e.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || e.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.clear();
        ClientEffects.reset();
    }
}
