package de.dualhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class CloudUiCard {
    private CloudUiCard() {}
    static void draw(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,boolean selected) {
        if(w<=0 || h<=0)return;
        HudConfig config=DualMusicHud.config();
        float r=Math.min(Math.min(w,h)/2f,Math.max(7f,radius));
        int top,bottom;
        if(config!=null && !config.neutralCards){
            Minecraft mc=Minecraft.getInstance();
            MediaBridge.Track track=MediaBridge.get(config);
            if(track!=null && ArtworkTexture.available(mc)){
                AlbumPalette palette=ArtworkTexture.palette(mc,AlbumPalette.DEMO);
                int base=0xFF20272D;
                int first=AlbumPalette.mix(base,palette.primary(),selected?.66f:.50f);
                int second=AlbumPalette.mix(base,palette.secondary(),selected?.58f:.43f);
                top=AlbumPalette.alpha(first,selected?202:147);
                bottom=AlbumPalette.alpha(second,selected?200:144);
            } else {
                top=selected?0xA0525A65:0x7A3B4653;
                bottom=selected?0xA047505E:0x7A303D4B;
            }
        } else {
            int color=(selected?0x85:0x55)<<24 | 0x001B222B;
            top=color;
            bottom=color;
        }
        if(!WalksyUiBridge.rounded(g,x,y,w,h,r,top,bottom)) {
            g.fill(x,y,x+w,y+h,top);
        }
    }
}
