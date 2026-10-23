package de.dualhud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class WalksySettingsGlass {
    private WalksySettingsGlass() {}

    static boolean draw(GuiGraphicsExtractor g, float x, float y, float w, float h,
                        float radius, boolean active, boolean hover, boolean panel, String role) {
        if (w <= 0 || h <= 0 || !WalksyGlassBridge.available()) return false;
        HudConfig c = DualMusicHud.config();
        return WalksyGlassBridge.drawSettings(g,x,y,w,h,radius,c,panel,active,hover,role);
    }
}
