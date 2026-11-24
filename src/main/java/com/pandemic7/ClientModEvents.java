package com.pandemic7;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PandemicMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("pandemic_fx", PandemicHud::renderFx);
        e.registerAboveAll("pandemic_hud", PandemicHud::render);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) {
        e.register(Keys.TOGGLE_LIST);
        e.register(Keys.TOGGLE_HUD);
    }
}
