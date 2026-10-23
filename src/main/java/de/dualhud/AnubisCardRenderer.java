package de.dualhud;

import net.minecraft.client.gui.GuiGraphicsExtractor;


public final class AnubisCardRenderer {
    private AnubisCardRenderer() {}
    public static void background(GuiGraphicsExtractor ctx,int w,int h,HudConfig c,AlbumPalette palette) {
        int r=Math.min(c.cornerRadius,22);
        int alpha=Math.max(0,Math.min(255,Math.round(c.glassOpacity*2.55f)));
        int base=0xFF101017;
        int top=c.autoAccent?AlbumPalette.mix(base,palette.primary(),Math.min(0.90f,0.35f+c.colorIntensity/120f)):base;
        int bottom=c.autoAccent?AlbumPalette.mix(base,palette.secondary(),Math.min(0.88f,0.34f+c.colorIntensity/125f)):base;
        
        for(int y=1;y<h-1;y++){
            int cut=0;
            int d=Math.min(y-1,h-2-y);
            if(d<r){double dy=r-d-.5;cut=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-dy*dy)));}
            int tone=c.autoAccent&&c.animatedGradient?palette.gradient((y-1f)/Math.max(1f,h-2f)):
                     (c.autoAccent?AlbumPalette.mix(top,bottom,(y-1f)/Math.max(1f,h-2f)):base);
            int color=AlbumPalette.mix(base,tone,Math.min(.90f,.35f+c.colorIntensity/120f));
            ctx.fill(1+cut,y,w-1-cut,y+1,AlbumPalette.alpha(color,alpha));
        }
        if(c.lightBorder){ctx.fill(r,1,w-r,2,0x3AFFFFFF);ctx.fill(r,h-2,w-r,h-1,0x1EFFFFFF);}
    }
    private static void rounded(GuiGraphicsExtractor ctx,int left,int top,int right,int bottom,int radius,int a,int b){
        int h=bottom-top;int r=Math.max(0,Math.min(radius,Math.min((right-left)/2,h/2)));
        for(int y=0;y<h;y++){
            int cut=0;
            if(r>0&&(y<r||y>=h-r)){
                double dy=y<r?r-y-0.5:y-(h-r)+0.5;
                cut=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-dy*dy)));
            }
            float t=h==1?0:(float)y/(h-1);
            int color=0;
            for(int shift:new int[]{0,8,16,24})color|=(Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift);
            ctx.fill(left+cut,top+y,right-cut,top+y+1,color);
        }
    }
}
