package de.dualhud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public record AlbumPalette(int primary, int secondary, int accent, int tertiary, int quaternary) {
    public AlbumPalette(int primary, int secondary, int accent) {
        this(primary,secondary,accent,mix(primary,secondary,.54f),mix(secondary,accent,.38f));
    }
    public static final AlbumPalette DEMO = new AlbumPalette(0xFF522275,0xFFCA4DA8,0xFFF58AD5);
    public static final AlbumPalette SPOTIFY = new AlbumPalette(0xFF16452F,0xFF237851,0xFF1ED760);
    public static final AlbumPalette APPLE = new AlbumPalette(0xFF53253C,0xFF9E385B,0xFFFA6079);

    
    public int gradient(float position) {
        float p=Math.max(0f,Math.min(1f,position))*4f;
        int segment=Math.min(3,(int)p);
        int[] stops={primary,tertiary,secondary,quaternary,mix(0xFF0F1017,accent,.65f)};
        float t=p-segment;
        t=t*t*(3-2*t);
        return mix(stops[segment],stops[segment+1],t);
    }

    public static AlbumPalette fromImage(byte[] bytes, AlbumPalette fallback) {
        if(bytes==null||bytes.length==0||bytes.length>8_000_000)return fallback;
        try {
            BufferedImage img=ImageIO.read(new ByteArrayInputStream(bytes));
            if(img==null||img.getWidth()<1||img.getHeight()<1||
                    (long)img.getWidth()*img.getHeight()>12_000_000L)return fallback;
            
            
            int[] weights=new int[512], reds=new int[512],greens=new int[512],blues=new int[512];
            int stride=Math.max(1,(int)Math.sqrt((long)img.getWidth()*img.getHeight()/3200.0));
            for(int y=0;y<img.getHeight();y+=stride)for(int x=0;x<img.getWidth();x+=stride){
                int rgb=img.getRGB(x,y);
                int alpha=rgb>>>24;
                if(alpha<160)continue;
                int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
                int hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));
                
                int weight=2+3*(hi-lo)/255;
                int bin=((r>>>5)<<6)|((g>>>5)<<3)|(b>>>5);
                weights[bin]+=weight;
                reds[bin]+=r*weight; greens[bin]+=g*weight; blues[bin]+=b*weight;
            }
            int[] colors=new int[512];
            for(int i=0;i<512;i++)if(weights[i]>0){
                colors[i]=0xFF000000|((reds[i]/weights[i])<<16)|((greens[i]/weights[i])<<8)|(blues[i]/weights[i]);
            }
            int[] selected=new int[5];int selectedCount=0;
            for(int step=0;step<5;step++){
                int winner=-1;double best=-1;
                for(int i=0;i<512;i++){
                    if(weights[i]==0)continue;
                    double distance=1.0;
                    for(int j=0;j<selectedCount;j++){
                        double d=colorDistance(colors[i],colors[selected[j]]);
                        
                        distance=Math.min(distance,Math.min(1.0,d/105.0));
                    }
                    if(selectedCount>0 && distance<.31)continue;
                    double score=weights[i]*(.18+.82*distance);
                    if(score>best){best=score;winner=i;}
                }
                if(winner<0)break;
                selected[selectedCount++]=winner;
            }
            if(selectedCount==0)return fallback;
            int[] raw=new int[5];
            for(int i=0;i<5;i++)raw[i]=colors[selected[Math.min(i,selectedCount-1)]];
            if(selectedCount<2)raw[1]=mix(raw[0],fallback.secondary(),.40f);
            if(selectedCount<3)raw[2]=mix(raw[0],fallback.accent(),.55f);
            if(selectedCount<4)raw[3]=mix(raw[1],raw[2],.5f);
            if(selectedCount<5)raw[4]=mix(raw[0],raw[3],.45f);
            
            int dark=0xFF0F1017;
            return new AlbumPalette(
                mix(dark,raw[0],.69f), mix(dark,raw[1],.69f),
                mix(raw[4],0xFFFFFFFF,.15f),
                mix(dark,raw[2],.69f), mix(dark,raw[3],.69f));
        } catch(Exception ignored){return fallback;}
    }
    private static double colorDistance(int a,int b){
        int dr=((a>>>16)&255)-((b>>>16)&255);
        int dg=((a>>>8)&255)-((b>>>8)&255);
        int db=(a&255)-(b&255);
        return Math.sqrt(.30*dr*dr+.59*dg*dg+.11*db*db);
    }
    public static int mix(int a,int b,float amount){
        float t=Math.max(0f,Math.min(1f,amount));
        int r=Math.round(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t);
        int g=Math.round(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t);
        int bl=Math.round((a&255)*(1-t)+(b&255)*t);
        return 0xFF000000|(r<<16)|(g<<8)|bl;
    }
    public static int alpha(int color,int alpha){return (Math.max(0,Math.min(255,alpha))<<24)|(color&0xFFFFFF);}
}
