package de.dualhud;

import net.minecraft.client.Minecraft;

final class UiTheme {
    private UiTheme() {}
    private static AlbumPalette palette() {
        try {
            if(DualMusicHud.config()!=null && DualMusicHud.config().neutralCards)return null;
            Minecraft mc = Minecraft.getInstance();
            MediaBridge.Track track = DualMusicHud.config() != null ? MediaBridge.get(DualMusicHud.config()) : null;
            if (mc != null && track != null) return ArtworkTexture.palette(mc, AlbumPalette.DEMO);
        } catch (Exception ignored) {}
        return null;
    }
    static int accent(HudConfig config) {
        AlbumPalette pal = palette();
        return pal != null ? pal.accent() : 0xFFF4F6FB;
    }
    static int dim(HudConfig config) {
        int accent = accent(config);
        int dark = AlbumPalette.mix(0xFF1B2430, accent, 0.20f);
        return 0x88000000 | (dark & 0x00FFFFFF);
    }
    static int cardTop(HudConfig cfg) {
        AlbumPalette pal=palette();
        return pal==null?0x663D4759:AlbumPalette.alpha(AlbumPalette.mix(0xFF25303D,pal.primary(),.43f),150);
    }
    static int cardBottom(HudConfig cfg) {
        AlbumPalette pal=palette();
        return pal==null?0x55313C4C:AlbumPalette.alpha(AlbumPalette.mix(0xFF1C2836,pal.secondary(),.40f),140);
    }
    static int cardHover(HudConfig cfg) {
        AlbumPalette pal=palette();
        return pal==null?0x88556576:AlbumPalette.alpha(AlbumPalette.mix(0xFF324356,pal.tertiary(),.52f),186);
    }
}
