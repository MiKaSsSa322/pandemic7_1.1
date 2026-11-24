package com.pandemic7;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.Random;

/** HUD слева сверху (таймер + список) и экранные эффекты болезней. */
public final class PandemicHud {
    private PandemicHud() {}

    private static final Random RND = new Random();

    public static void renderFx(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.set.isEmpty()) return;
        if (ClientEffects.flickerAlpha > 0f) {
            g.fill(0, 0, w, h, ((int) (ClientEffects.flickerAlpha * 255) << 24));
        }
        if (ClientEffects.flashAlpha > 0f) {
            g.fill(0, 0, w, h, ((int) (ClientEffects.flashAlpha * 255) << 24) | 0xAA0000);
        }
        if (ClientState.has(Disease.FLOATERS)) {
            for (int i = 0; i < ClientEffects.FX.length; i++) {
                int x = (int) (ClientEffects.FX[i] * w);
                int y = (int) (ClientEffects.FY[i] * h);
                int sz = 3 + (i % 3) * 2;
                g.fill(x, y, x + sz + 3, y + sz, 0x77101010);
            }
        }
        if (ClientState.has(Disease.VISUAL_SNOW)) {
            for (int i = 0; i < 160; i++) {
                int x = RND.nextInt(w), y = RND.nextInt(h);
                g.fill(x, y, x + 1, y + 1, RND.nextBoolean() ? 0x66FFFFFF : 0x66000000);
            }
        }
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.renderDebug || !ClientState.hudVisible) return;
        Font f = mc.font;

        List<Disease> list = ClientState.list;
        boolean showList = ClientState.listVisible && !list.isEmpty();
        int sec = Math.max(0, ClientState.timer / 20);
        String head = "Новая болезнь через " + String.format("%d:%02d", sec / 60, sec % 60);
        String count = "Болезни: " + list.size() + "/" + Disease.values().length;
        int named = showList ? Math.min(8, list.size()) : 0;
        int older = showList ? Math.max(0, list.size() - named) : 0;
        int rowsOlder = (older + 11) / 12;

        int w = Math.max(Math.max(f.width(head), f.width(count)), 112) + 8;
        for (int i = 0; i < named; i++) {
            Disease d = list.get(list.size() - 1 - i);
            w = Math.max(w, 14 + f.width(d.getName()) + 8);
        }
        int h = 3 + 10 + 6 + 3;
        if (!list.isEmpty()) h += 11 + named * 12 + (older > 0 ? 1 + rowsOlder * 9 : 0);

        int x = 4, y = 4;
        g.fill(x, y, x + w, y + h, 0x77000000);
        int cx = x + 4, cy = y + 3;

        g.drawString(f, head, cx, cy, sec < 30 ? 0xFFFF5555 : 0xFFFFFFFF, true);
        cy += 10;

        int bw = w - 8;
        g.fill(cx, cy, cx + bw, cy + 3, 0xFF333333);
        float frac = Math.min(1f, ClientState.timer / (float) PandemicData.INTERVAL);
        g.fill(cx, cy, cx + (int) (bw * frac), cy + 3, 0xFFB03030);
        cy += 6;

        if (!list.isEmpty()) {
            g.drawString(f, count, cx, cy, 0xFFAAAAAA, true);
            cy += 11;
            for (int i = 0; i < named; i++) {
                Disease d = list.get(list.size() - 1 - i);
                icon(g, f, cx, cy, d, 10);
                g.drawString(f, d.getName(), cx + 14, cy + 1, 0xFF000000 | d.getCat().color, true);
                cy += 12;
            }
            if (older > 0) {
                cy += 1;
                for (int i = 0; i < older; i++) {
                    Disease d = list.get(i);
                    int col = i % 12, row = i / 12;
                    int ix = cx + col * 9, iy = cy + row * 9;
                    g.fill(ix, iy, ix + 7, iy + 7, 0xFF000000 | d.getCat().color);
                }
            }
        }
    }

    private static void icon(GuiGraphics g, Font f, int x, int y, Disease d, int size) {
        g.fill(x, y, x + size, y + size, 0xFF000000 | d.getCat().color);
        g.renderOutline(x, y, size, size, 0xFF000000);
        String n = String.valueOf(d.getId());
        float sc = n.length() > 2 ? 0.5f : 0.6f;
        float tw = f.width(n) * sc;
        g.pose().pushPose();
        g.pose().translate(x + (size - tw) / 2f, y + (size - 8 * sc) / 2f + 0.2f, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(f, n, 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }
}
