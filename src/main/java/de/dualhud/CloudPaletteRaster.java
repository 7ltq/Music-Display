package de.dualhud;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class CloudPaletteRaster {
    public static final int WIDTH = 384;
    public static final int HEIGHT = 216;
    private CloudPaletteRaster() {}

    public static byte[] png(AlbumPalette palette) throws IOException {
        BufferedImage image = generate(palette);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", stream)) throw new IOException("PNG encoder unavailable");
        return stream.toByteArray();
    }

    public static BufferedImage generate(AlbumPalette p) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] tones = { p.primary(), p.secondary(), p.tertiary(), p.quaternary(), p.accent() };
        
        final double[] cx = { -.10, .37, .96, .13, .88 };
        final double[] cy = { .13, .54, .19, .95, .83 };
        final double[] sx = { .50, .48, .45, .41, .49 };
        final double[] sy = { .44, .51, .50, .42, .51 };
        final double[] strength = { 1.8, 2.1, 1.7, 1.5, 1.35 };
        final int base = 0xFF09070D;
        for (int y = 0; y < HEIGHT; y++) {
            double v = y / (double)(HEIGHT - 1);
            for (int x = 0; x < WIDTH; x++) {
                double u = x / (double)(WIDTH - 1);
                
                double darkWeight = .38;
                double red = ((base >>> 16) & 255) * darkWeight;
                double green = ((base >>> 8) & 255) * darkWeight;
                double blue = (base & 255) * darkWeight;
                double total = darkWeight;
                for (int k=0; k<tones.length; k++) {
                    double dx=(u-cx[k])/sx[k], dy=(v-cy[k])/sy[k];
                    double w=strength[k]*Math.exp(-1.52*(dx*dx+dy*dy));
                    red += ((tones[k] >>> 16)&255)*w;
                    green += ((tones[k] >>> 8)&255)*w;
                    blue += (tones[k]&255)*w;
                    total += w;
                }
                
                double edgeX = Math.abs(u-.5)*2, edgeY = Math.abs(v-.5)*2;
                double vignette = 1.0 - .30*Math.pow(Math.min(1.0, (edgeX*edgeX+edgeY*edgeY)*.55), 1.25);
                int r=clamp((int)Math.round(red/total*vignette));
                int g=clamp((int)Math.round(green/total*vignette));
                int b=clamp((int)Math.round(blue/total*vignette));
                image.setRGB(x,y,0xFF000000 | (r<<16)|(g<<8)|b);
            }
        }
        return image;
    }
    
    public static byte[] cardPng(AlbumPalette palette,int width,int height,int radius,int opacity,boolean compact) throws IOException {
        int w=Math.max(8,Math.min(1024,width));
        int h=Math.max(8,Math.min(512,height));
        int rad=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        BufferedImage cloud=generate(palette);
        BufferedImage card=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            
            double fx=x*(WIDTH-1.0)/Math.max(1,w-1), fy=y*(HEIGHT-1.0)/Math.max(1,h-1);
            int x0=(int)fx,y0=(int)fy,x1=Math.min(WIDTH-1,x0+1),y1=Math.min(HEIGHT-1,y0+1);
            double u=fx-x0,v=fy-y0;
            int a=cloud.getRGB(x0,y0),b=cloud.getRGB(x1,y0),c=cloud.getRGB(x0,y1),d=cloud.getRGB(x1,y1);
            int rgb=0xFF000000;
            for(int shift:new int[]{0,8,16}){
                double lo=((a>>>shift)&255)*(1-u)+((b>>>shift)&255)*u;
                double hi=((c>>>shift)&255)*(1-u)+((d>>>shift)&255)*u;
                int channel=clamp((int)Math.round(lo*(1-v)+hi*v));
                if(compact)channel=clamp((int)Math.round(channel*.78+10*.22));
                rgb|=channel<<shift;
            }
            
            
            double coverage=1.0;
            if(rad>0 && (x<rad || x>=w-rad) && (y<rad || y>=h-rad)){
                int inside=0;
                for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++){
                    double px=x+(sx+0.5)/4.0, py=y+(sy+0.5)/4.0;
                    double cx=Math.max(rad,Math.min(w-rad,px));
                    double cy=Math.max(rad,Math.min(h-rad,py));
                    double dx=px-cx,dy=py-cy;
                    if(dx*dx+dy*dy<=rad*(double)rad)inside++;
                }
                coverage=inside/16.0;
            }
            int finalAlpha=clamp((int)Math.round(opacity*coverage));
            
            card.setRGB(x,y,finalAlpha==0?0:(finalAlpha<<24)|(rgb&0xFFFFFF));
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(!ImageIO.write(card,"png",bytes))throw new IOException("PNG encoder unavailable");
        return bytes.toByteArray();
    }

    private static int clamp(int value) { return Math.min(255,Math.max(0,value)); }
}
