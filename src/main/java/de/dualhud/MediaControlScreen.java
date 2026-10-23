package de.dualhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


public final class MediaControlScreen extends Screen {
    private static final Identifier ICON_CLOSE = Identifier.fromNamespaceAndPath("dualmusichud","gui/icon/close.png");
    private final Screen parent;
    private String lastActiveKey="";
    private String fadingText="";
    private String previousShownText="";
    private boolean wasSinging=false;
    private long fadeBegan=0;
    private static final long FADE_DURATION_MS=950;

    public MediaControlScreen(Screen parent){super(Component.literal("Music Display"));this.parent=parent;}

    private boolean seekDragging;
    private float seekFraction;
    private int artSize() {
        int result=Math.min(Math.max(84,height-150),Math.max(84,width/3));
        return 22+result>width-20?Math.max(64,width-55):result;
    }
    private int barY(){return 35+artSize()+62;}
    private int buttonY(){return Math.min(height-62,barY()+34);}
    private float clamp(float f){return Math.max(0f,Math.min(1f,f));}
    private boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    private float position(double mx,int start,int span){return clamp((float)((mx-start)/(double)Math.max(1,span)));}

    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mouseX,int mouseY,float partialTick){
        Minecraft mc=Minecraft.getInstance();
        MediaBridge.Track track=MediaBridge.get(DualMusicHud.config());
        if(track!=null)LyricsService.update(track);
        AlbumPalette fallback=track!=null?switch(track.provider()){
            case "Spotify" -> AlbumPalette.SPOTIFY;
            case "Apple Music" -> AlbumPalette.APPLE;
            case "SoundCloud" -> new AlbumPalette(0xFF713A23,0xFFB66430,0xFFFF8A36);
            default -> AlbumPalette.DEMO;
        }:AlbumPalette.DEMO;
        AlbumPalette pal=track!=null?ArtworkTexture.palette(mc,fallback):fallback;

        
        CloudBackdrop.draw(ctx, mc, pal, width, height);

        int margin=22;
        int artSize=artSize();
        int artX=margin, artY=35;
        if (!WalksyUiBridge.rounded(ctx,artX,artY,artSize,artSize,16,pal.primary(),pal.secondary()))
            ctx.fillGradient(artX,artY,artX+artSize,artY+artSize,pal.primary(),pal.secondary());
        if(track!=null && ArtworkTexture.available(mc))ArtworkTexture.draw(ctx,mc,artX,artY,artSize);
        else DualMusicHud.drawIdleNote(ctx,artX+artSize/2-4,artY+artSize/2-5,0xFFFFFFFF);
        String title=track!=null?track.title():"No song playing";
        String artist=track!=null?track.artist():"Start Spotify or Apple Music";
        info(ctx,ellipsize(title,artSize),artX,artY+artSize+8,0xFFFFFFFF,true);
        info(ctx,ellipsize(artist,artSize),artX,artY+artSize+22,0xFFDCCFE2,false);
        long pos=track!=null?track.positionMs():0,duration=track!=null?track.durationMs():0;
        int barY=barY();
        float progress=duration>0?clamp((float)(pos/(double)duration)):0f;
        if(seekDragging)progress=seekFraction;
        int accent=UiTheme.accent(DualMusicHud.config());
        round(ctx,artX,barY,artSize,6,3,0x67768691);
        if(progress>0.005f)round(ctx,artX,barY,Math.max(3,artSize*progress),6,3,accent);
        round(ctx,artX+artSize*progress-5,barY-3,10,12,6,0xFFF2F6FF);
        ctx.text(font,clock(seekDragging?(long)(duration*progress):pos),artX,barY+12,0xFFECE6F0,false);
        String end=clock(duration);
        ctx.text(font,end,artX+artSize-font.width(end),barY+12,0xFFECE6F0,false);
        int lyricX=artX+artSize+Math.max(48,width/14);
        int lyricWidth=Math.max(55,width-lyricX-18);
        long lyricTime=Math.max(0,pos+DualMusicHud.config().lyricOffsetMs);
        LyricsService.TimedLine active=track!=null?LyricsService.timedLine(lyricTime,duration):null;
        
        
        java.util.List<LyricsService.TimedLine> visible=LyricsService.upcoming(lyricTime,duration,8);
        int lyricY=Math.max(48,height/8);
        int lyricStep=Math.max(20,Math.min(34,(height-lyricY-40)/8));
        String trackKey=track==null?"":track.title()+"|"+track.artist();
        String activeKey=trackKey+"|"+(visible.isEmpty()?"":visible.get(0).startMs());
        if(!activeKey.equals(lastActiveKey)){
            if(!lastActiveKey.isEmpty() && lastActiveKey.startsWith(trackKey+"|")){
                fadingText=previousShownText;
                fadeBegan=System.nanoTime();
            } else fadingText="";
            lastActiveKey=activeKey;
        }
        if(!visible.isEmpty())previousShownText=visible.get(0).text();
        boolean singing=track!=null && active!=null && LyricsService.vocalActive(active,lyricTime);
        if(wasSinging && !singing && !previousShownText.isBlank()) {
            fadingText=previousShownText;
            fadeBegan=System.nanoTime();
        }
        wasSinging=singing;
        var upcoming=track!=null?LyricsService.upcoming(lyricTime,track.durationMs(),2):java.util.List.<LyricsService.TimedLine>of();
        long nextStart=Long.MAX_VALUE;
        for(var entry:upcoming){ if(entry.startMs()>lyricTime){ nextStart=entry.startMs(); break; } }
        boolean instrumental=!singing && (upcoming.isEmpty() || nextStart==Long.MAX_VALUE || nextStart-lyricTime>2200);
        if(instrumental){
            float pulse=(float)(0.98+0.035*Math.sin(pos*.008));
            ctx.pose().pushMatrix();ctx.pose().translate(lyricX+12,lyricY+8);
            ctx.pose().scale(pulse*1.0f,pulse*1.0f);
            DualMusicHud.drawIdleNote(ctx,0,0,AlbumPalette.mix(0xFFFFFFFF,pal.accent(),0.26f));
            ctx.pose().popMatrix();
        }

        for(int i=0;i<visible.size();i++){
            LyricsService.TimedLine entry=visible.get(i);
            boolean focus=DualMusicHud.config().lyricsFocus;
            float scale=i==0?Math.max(1.32f,Math.min(1.84f,lyricWidth/195f)):(focus?1.10f:1.22f);
            String shown=ellipsize(entry.text(),(int)(lyricWidth/scale));
            int yy=lyricY+i*lyricStep;
            if(instrumental && i==0)continue;
            if(i==0 && lyricTime>=entry.startMs()){
                float frontier=entry.wordTimes().isEmpty()
                    ? shown.length()*(float)Math.max(0,Math.min(1,(lyricTime-entry.startMs())/(double)Math.max(1,LyricsService.vocalEndMs(entry)-entry.startMs())))
                    : LyricsService.frontierForWords(entry,lyricTime,shown);
                if(DualMusicHud.config().karaokeWave)drawKaraokeLine(ctx,shown,lyricX,yy,pal.accent(),scale,frontier);
                else info(ctx,shown,lyricX,yy,0xFFFAF5FF,true);
            } else {
                info(ctx,shown,lyricX,yy,i==0?0xFFEFE7F4:(focus?0x65E2D5E9:0x94E2D5E9),false);
            }
        }
        
        
        if(!fadingText.isBlank()){
            float fraction=Math.max(0f,1f-(System.nanoTime()-fadeBegan)/((float)FADE_DURATION_MS*1_000_000f));
            fraction=fraction*fraction*(3f-2f*fraction);
            if(fraction<=0)fadingText="";
            else{
                int alpha=Math.max(0,Math.min(255,Math.round(160*fraction)));
                ctx.pose().pushMatrix();
                ctx.pose().translate(lyricX,lyricY-13-(1f-fraction)*7f);
                float zoom=1f+(1f-fraction)*.14f;
                ctx.pose().scale(zoom,zoom);
                info(ctx,ellipsize(fadingText,lyricWidth),0,0,(alpha<<24)|0x00FFFFFF,false);
                ctx.pose().popMatrix();
            }
        }
        
        
        {
            int buttonsY=buttonY();
            int gap=6, bw=Math.max(36,(artSize-2*gap)/3);
            control(ctx,artX,buttonsY,bw,20,"PREV",mouseX,mouseY);
            control(ctx,artX+bw+gap,buttonsY,bw,20,"PLAY",mouseX,mouseY);
            control(ctx,artX+2*(bw+gap),buttonsY,bw,20,"NEXT",mouseX,mouseY);
            drawCloseButton(ctx,width-35,8,27,27,mouseX,mouseY);
        }
    }
    private void drawCloseButton(GuiGraphicsExtractor g,int x,int y,int w,int h,int mx,int my){
        boolean hovered=mx>=x && mx<x+w && my>=y && my<y+h;
        int surface=hovered?UiTheme.accent(DualMusicHud.config()):0x78677382;
        round(g,x,y,w,h,h/2f,surface);
        int c=hovered?0xFF18202C:0xFFEDEFF7;
        blitIcon(g,ICON_CLOSE,x+(w-12)/2,y+(h-12)/2,12,c);
    }
    private void blitIcon(GuiGraphicsExtractor g,Identifier id,int x,int y,int size,int color){
        g.blit(RenderPipelines.GUI_TEXTURED,id,x,y,0,0,size,size,16,16,16,16,color);
    }


    private void round(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,int color){
        if(!WalksyUiBridge.rounded(g,x,y,w,h,radius,color,color))
            g.fill((int)x,(int)y,(int)(x+w),(int)(y+h),color);
    }
    private void text(GuiGraphicsExtractor g,String value,int x,int y,float size,int color,boolean bold){
        if(!WalksyUiBridge.text(g,value,x,y,size,color,bold))g.text(font,value,x,y,color,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=1 && event.button()!=0)return super.mouseClicked(event,doubleClick); 
        double x=event.x(), y=event.y();
        int aw=artSize(), bx=22, gap=6,bw=Math.max(36,(aw-2*gap)/3);
        if(inside(x,y,width-34,8,26,25)){onClose();return true;}
        if(inside(x,y,bx,barY()-10,aw,28)){
            MediaBridge.Track current=MediaBridge.get(DualMusicHud.config());
            if(current!=null&&current.durationMs()>0){seekDragging=true;seekFraction=position(x,bx,aw);return true;}
        }
        if(inside(x,y,bx,buttonY(),bw,20)){DiagnosticLog.record("CONTROL_CLICK_PREV");MediaBridge.previous();return true;}
        if(inside(x,y,bx+bw+gap,buttonY(),bw,20)){DiagnosticLog.record("CONTROL_CLICK_PLAY");MediaBridge.togglePlayPause();return true;}
        if(inside(x,y,bx+2*(bw+gap),buttonY(),bw,20)){DiagnosticLog.record("CONTROL_CLICK_NEXT");MediaBridge.next();return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(seekDragging){seekFraction=position(event.x(),22,artSize());return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(seekDragging){
            seekDragging=false;
            MediaBridge.Track current=MediaBridge.get(DualMusicHud.config());
            if(current!=null&&current.durationMs()>0)MediaBridge.seek((long)(seekFraction*current.durationMs()));
            return true;
        }
        return super.mouseReleased(event);
    }
    private void info(GuiGraphicsExtractor g,String s,int x,int y,int color,boolean bold){
        if (!WalksyUiBridge.text(g,s,x,y,14,color,bold))
            g.text(font,s,x,y,color,false);
    }
    private void control(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,int mx,int my){
        boolean hovered=mx>=x && mx<x+w && my>=y && my<y+h;
        int surface=hovered?UiTheme.accent(DualMusicHud.config()):0x78677382;
        if(!WalksyUiBridge.rounded(g,x,y,w,h,h/2f,surface,surface)){
            g.fill(x,y,x+w,y+h,surface);
        }
        int txt=hovered?0xFF18202C:0xFFF3F3FA;
        float size=11f;
        float measure=WalksyUiBridge.width(label,size,false);
        int tx=x+Math.round((w-(measure>=0?measure:font.width(label)))/2);
        if(!WalksyUiBridge.text(g,label,tx,y+4,size,txt,false))
            g.text(font,label,tx,y+5,txt,false);
    }
    
    private void drawKaraokeLine(GuiGraphicsExtractor ctx,String line,int x,int y,int accent,float scale,float frontier){
        ctx.pose().pushMatrix();
        ctx.pose().translate(x,y);
        ctx.pose().scale(scale,scale);
        int cursor=0;
        final boolean smooth = WalksyGlassBridge.available();
        for(int i=0;i<line.length();i++){
            String glyph=line.substring(i,i+1);
            float z=Math.max(0f,Math.min(1f,frontier-i));
            float fade=z*z*(3-2*z);
            int color=AlbumPalette.mix(0xFFA8A0B4,AlbumPalette.mix(0xFFFFFFFF,accent,.15f),fade);
            float distance=(i+.5f)-frontier;
            float bump=(float)Math.exp(-distance*distance/4.0f);
            ctx.pose().pushMatrix();
            ctx.pose().translate(cursor, -0.28f*bump);
            if(!smooth || !WalksyUiBridge.text(ctx,glyph,0,0,12.5f,color,true))
                ctx.text(font,glyph,0,0,color,false);
            ctx.pose().popMatrix();
            float adv=smooth?WalksyUiBridge.width(glyph,12.5f,true):-1;
            cursor+=adv>=0?Math.round(adv):font.width(glyph);
        }
        ctx.pose().popMatrix();
    }
    private String ellipsize(String s,int max){
        if(font.width(s)<=max)return s;
        while(s.length()>1&&font.width(s+"…")>max)s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private String clock(long time){long s=Math.max(0,time)/1000;return (s/60)+":"+String.format("%02d",s%60);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
