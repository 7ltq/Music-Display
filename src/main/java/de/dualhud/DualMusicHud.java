package de.dualhud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;


public final class DualMusicHud implements ClientModInitializer {
    private static HudConfig config;
    private static KeyMapping settingsKey, controlsKey;
    public static HudConfig config(){return config;}

    @Override public void onInitializeClient(){
        config=new HudConfig(FabricLoader.getInstance().getConfigDir().resolve("dual-music-hud.properties"));
        DiagnosticLog.record("MOD_STARTED gameDir=" + FabricLoader.getInstance().getGameDir() + " configDir=" + FabricLoader.getInstance().getConfigDir());
        BridgeSupervisor.start();
        KeyMapping.Category cat=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dualmusichud","music_hud"));
        settingsKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.dualmusichud.settings",InputConstants.Type.KEYBOARD,InputConstants.KEY_O,cat));
        controlsKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.dualmusichud.controls",InputConstants.Type.KEYBOARD,InputConstants.KEY_I,cat));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            while(settingsKey.consumeClick())mc.gui.setScreen(WalksyNativeSettings.create(mc.gui.screen()));
            while(controlsKey.consumeClick()){
                if(mc.gui.screen() instanceof MediaControlScreen)mc.gui.setScreen(null);
                else mc.gui.setScreen(new MediaControlScreen(mc.gui.screen()));
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("dualmusichud","music"),DualMusicHud::render);
    }

    private static void render(GuiGraphicsExtractor ctx,DeltaTracker tick){
        if(config==null||!config.visible)return;
        var openScreen=Minecraft.getInstance().gui.screen();
        if(openScreen instanceof HudSettingsScreen || (openScreen!=null && openScreen.getClass().getName().equals("main.walksy.lib.core.gui.WalksyLibPanelScreen")) || openScreen instanceof HudDragScreen || openScreen instanceof MediaControlScreen
            || openScreen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)return;
        if(!config.showWhenIdle && MediaBridge.get(config)==null)return;
        int h=145;
        int sw=ctx.guiWidth(),sh=ctx.guiHeight();
        float fit=Math.min(config.scale, Math.min((sw-16f)/config.width,(sh-16f)/h));
        int w=Math.round(config.width*fit), boxH=Math.round(h*fit);
        int x="FREE".equals(config.anchor)?config.x:(config.anchor.endsWith("RIGHT")?sw-w-config.x:config.x);
        int y="FREE".equals(config.anchor)?config.y:(config.anchor.startsWith("BOTTOM")?sh-boxH-config.y:config.y);
        x=Math.max(8, Math.min(x, Math.max(8,sw-w-8)));
        y=Math.max(8, Math.min(y, Math.max(8,sh-boxH-8)));
        renderCard(ctx,Minecraft.getInstance(),x,y,false);
        
    }

    static void drawPreview(GuiGraphicsExtractor ctx,Minecraft mc,int x,int y){renderCard(ctx,mc,x,y,true);}
    static int hudHeight(){return 145;}
    static float fitScale(int guiWidth,int guiHeight){return Math.max(.1f,Math.min(config.scale,Math.min((guiWidth-16f)/config.width,(guiHeight-16f)/hudHeight())));}
    static int[] hudBounds(int guiWidth,int guiHeight){
        int w=Math.round(config.width*fitScale(guiWidth,guiHeight));
        int h=Math.round(hudHeight()*fitScale(guiWidth,guiHeight));
        int x="FREE".equals(config.anchor)?config.x:(config.anchor.endsWith("RIGHT")?guiWidth-w-config.x:config.x);
        int y="FREE".equals(config.anchor)?config.y:(config.anchor.startsWith("BOTTOM")?guiHeight-h-config.y:config.y);
        return new int[]{Math.max(0,Math.min(x,Math.max(0,guiWidth-w))),Math.max(0,Math.min(y,Math.max(0,guiHeight-h))),w,h};
    }
    static void drawEditorHud(GuiGraphicsExtractor ctx,Minecraft mc,int x,int y){renderCard(ctx,mc,x,y,true);}

    
    private static void renderCard(GuiGraphicsExtractor ctx,Minecraft mc,int x,int y,boolean preview){
        if(config==null)return;
        MediaBridge.Track track=MediaBridge.get(config);
        if(!preview)LyricsService.update(track);
        boolean live=track!=null;
        boolean hasLyric=config.lyricsEnabled&&!"HIDDEN".equals(config.lyricsLayout);
        int h=hasLyric?145:106,w=config.width;
        String provider=live?track.provider():"Waiting for music";
        String title=live?track.title():"No track playing";
        String artist=live?track.artist():"Start Spotify, Apple Music or SoundCloud";
        long duration=live?track.durationMs():0;
        long position=live?track.positionMs():0;
        double fraction=duration>0?Math.min(1,Math.max(0,position/(double)duration)):0;

        AlbumPalette providerPalette=live?switch(provider){
            case "Spotify"->AlbumPalette.SPOTIFY;
            case "Apple Music"->AlbumPalette.APPLE;
            case "SoundCloud"->new AlbumPalette(0xFF713A23,0xFFB66430,0xFFFF8A36);
            default->AlbumPalette.DEMO;
        }:AlbumPalette.DEMO;
        AlbumPalette palette=(live && config.autoAccent)?ArtworkTexture.palette(mc,providerPalette):providerPalette;

        
        float fit=Math.max(.1f, Math.min(config.scale, Math.min((ctx.guiWidth()-16f)/w,(ctx.guiHeight()-16f)/h)));
        boolean newGlass=true;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x,y);
        ctx.pose().scale(fit,fit);
        
        
        CloudBackdrop.drawWalksyCard(ctx,mc,palette,w,h,Math.min(28,Math.max(10,config.cornerRadius+6)),
                Math.round(config.miniOpacity*2.55f));


        if(config.showAlbumArt) {
            int artSize=82;
            if (!newGlass || !WalksyUiBridge.rounded(ctx,12,12,artSize,artSize,10,palette.primary(),palette.secondary()))
                ctx.fillGradient(12,12,12+artSize,12+artSize,palette.primary(),palette.secondary());
            if(live && ArtworkTexture.available(mc)) ArtworkTexture.draw(ctx,mc,12,12,artSize);
            else drawIdleNote(ctx,48,45,0xFFFFFFFF);
        }

        int tx=config.showAlbumArt?107:16, right=w-14, max=Math.max(65,right-tx);
        hudText(ctx,mc,trim(mc,title,max),tx,17,0xFFFFFFFF,true);
        if(config.showArtist)hudText(ctx,mc,trim(mc,artist,max),tx,33,0xFFE3DBE9,false);
        hudText(ctx,mc,provider+(live?(track.playing()?" · Playing":" · Paused"):""),tx,51,0xFFBBABC7,false);
        if(config.showProgress) {
            ctx.fill(tx,73,right,77,0x69414456);
            int progressEnd=tx+(int)Math.round((right-tx)*fraction);
            if(progressEnd>tx)ctx.fill(tx,73,progressEnd,77,palette.accent());
            hudText(ctx,mc,clock(position),tx,85,0xFFC9BBD2,false);
            String end=clock(duration);
            hudText(ctx,mc,end,right-textWidth(mc,end),85,0xFFC9BBD2,false);
        }


        if(hasLyric){
            ctx.fill(14,104,w-14,105,AlbumPalette.alpha(palette.accent(),70));
            long lyricTime=Math.max(0,position+config.lyricOffsetMs);
            LyricsService.TimedLine active=live?LyricsService.timedLine(lyricTime,duration):null;
            String line=active!=null?active.text():"";
            if(!live)line="";
            
            
            boolean singingNow=live && active!=null && LyricsService.vocalActive(active,lyricTime);
            var upcoming=live?LyricsService.upcoming(lyricTime,duration,2):java.util.List.<LyricsService.TimedLine>of();
            long nextStart=Long.MAX_VALUE;
            for(var entry:upcoming){ if(entry.startMs()>lyricTime){ nextStart=entry.startMs(); break; } }
            boolean likelyInstrumental=live && (upcoming.isEmpty() || nextStart==Long.MAX_VALUE || nextStart-lyricTime>2200);
            boolean instrumental=live && !singingNow && likelyInstrumental;
            if(!singingNow)line="";
            String[] wrapped=wrap(mc,line,w-28);
            if(singingNow) {
                float remaining=Math.max(0f,Math.min(1f,(LyricsService.vocalEndMs(active)-lyricTime)/480f));
                float lyricScale=.87f+.13f*remaining;
                ctx.pose().pushMatrix();
                ctx.pose().translate(14f,108f);
                ctx.pose().scale(lyricScale,lyricScale);
                ctx.pose().translate(-14f,-108f);
                float progress=LyricsService.highlightFraction(active,lyricTime);
                float frontier=active.wordTimes().isEmpty()?wordPacedFrontier(wrapped[0]+wrapped[1],progress):LyricsService.frontierForWords(active,lyricTime,wrapped[0]+wrapped[1]);
                if(config.karaokeWave) drawWave(ctx,mc,wrapped[0],14,108,frontier,palette.accent());
                else hudText(ctx,mc,wrapped[0],14,108,0xFFEFE8F4,true);
                if(!wrapped[1].isBlank()) {
                    if(config.karaokeWave)drawWave(ctx,mc,wrapped[1],14,modernText()?123:119,frontier-wrapped[0].length(),palette.accent());
                    else hudText(ctx,mc,wrapped[1],14,modernText()?123:119,0xFFEFE8F4,true);
                }
                ctx.pose().popMatrix();
            } else if(instrumental) {
                
                double seconds=System.nanoTime()/1_000_000_000.0;
                float pulse=(float)(0.96+0.04*Math.sin(seconds*3.3));
                ctx.pose().pushMatrix();
                ctx.pose().translate(18f,109f);
                ctx.pose().scale(pulse,pulse);
                drawIdleNote(ctx,0,0,AlbumPalette.mix(0xFFFFFFFF,palette.accent(),0.18f));
                ctx.pose().popMatrix();
            } else if(!line.isBlank()) {
                hudText(ctx,mc,wrapped[0],14,108,0xFFE8E1F0,true);
                if(!wrapped[1].isBlank())hudText(ctx,mc,wrapped[1],14,modernText()?123:119,0xFFE8E1F0,true);
            }
        }
        ctx.pose().popMatrix();
    }

    
    static void drawIdleNote(GuiGraphicsExtractor g,float x,float y,int color){
        int ix=Math.round(x), iy=Math.round(y);
        
        g.fill(ix,iy+7,ix+5,iy+10,color);
        g.fill(ix+1,iy+6,ix+4,iy+11,color);
        
        g.fill(ix+4,iy+1,ix+6,iy+8,color);
        
        g.fill(ix+4,iy+1,ix+9,iy+3,color);
        g.fill(ix+7,iy+2,ix+9,iy+5,color);
        
        g.fill(ix+1,iy+8,ix+2,iy+9,0x00000000);
    }

    private static boolean modernText() {
        return config != null && WalksyGlassBridge.available();
    }
    private static int textWidth(Minecraft mc,String s) {
        if(modernText()){
            float w=WalksyUiBridge.width(s,12f,false);
            if(w>=0)return Math.round(w);
        }
        return mc.font.width(s);
    }
    private static void hudText(GuiGraphicsExtractor ctx,Minecraft mc,String text,int x,int y,int color,boolean bold) {
        if(modernText() && WalksyUiBridge.text(ctx,text,x,y-1,12f,color,bold))return;
        ctx.text(mc.font,text,x,y,color,false);
    }

    private static void drawWave(GuiGraphicsExtractor ctx,Minecraft mc,String text,int x,int y,float frontier,int accent){
        if(text.isBlank())return;
        int advance=0;
        for(int i=0;i<text.length();i++){
            String glyph=text.substring(i,i+1);
            float passed=frontier-i;
            float highlight=smoothstep(0.05f,0.95f,passed);
            
            float dist=(i+0.5f)-frontier;
            float bump=(float)Math.exp(-dist*dist/3.2f)*Math.min(1f,Math.max(0f,frontier));
            float scale=1.0f+0.023f*highlight+0.014f*bump;
            float raise=0.12f*highlight+0.40f*bump;
            int color=AlbumPalette.mix(0xFFAAA0B7,AlbumPalette.mix(0xFFFFFFFF,accent,0.20f),highlight);
            
            int charWidth=textWidth(mc,glyph);
            ctx.pose().pushMatrix();
            ctx.pose().translate(x+advance+(charWidth*(1f-scale)*0.5f),y-raise);
            ctx.pose().scale(scale,scale);
            hudText(ctx,mc,glyph,0,0,color,false);
            ctx.pose().popMatrix();
            advance+=charWidth;
        }
    }

    private static float wordPacedFrontier(String text,float progress){
        if(text.isEmpty())return 0f;
        float[] weight=new float[text.length()];float total=0f;
        int n=text.length();int i=0;
        while(i<n){
            int start=i;
            boolean space=Character.isWhitespace(text.charAt(i));
            while(i<n && Character.isWhitespace(text.charAt(i))==space)i++;
            int len=i-start;
            float perChar=space?0.30f:(float)(0.7+0.8/Math.sqrt(Math.max(1,len)));
            for(int j=start;j<i;j++){weight[j]=perChar;total+=perChar;}
        }
        float target=Math.max(0f,Math.min(1f,progress))*total;
        for(int j=0;j<n;j++){
            if(target<=weight[j])return j+target/Math.max(.001f,weight[j]);
            target-=weight[j];
        }
        return n;
    }

    private static float smoothstep(float a,float b,float v){
        float t=Math.max(0f,Math.min(1f,(v-a)/(b-a)));
        return t*t*(3f-2f*t);
    }
    private static String trim(Minecraft mc,String text,int maxWidth){
        if(textWidth(mc,text)<=maxWidth)return text;
        while(text.length()>1&&textWidth(mc,text+"…")>maxWidth)text=text.substring(0,text.length()-1);
        return text+"…";
    }

    
    private static String[] wrap(Minecraft mc,String input,int maxWidth){
        String text=input == null ? "" : input.strip().replaceAll("\\s+", " ");
        if(text.isEmpty())return new String[]{"",""};
        if(textWidth(mc,text)<=maxWidth)return new String[]{text,""};
        String[] words=text.split(" ");
        StringBuilder first=new StringBuilder();
        StringBuilder second=new StringBuilder();
        for(String word:words){
            if(first.isEmpty()){
                first.append(word);
            } else if(second.isEmpty() && textWidth(mc,first+" "+word)<=maxWidth){
                first.append(" ").append(word);
            } else if(second.isEmpty()) {
                second.append(word);
            } else if(textWidth(mc,second+" "+word)<=maxWidth) {
                second.append(" ").append(word);
            } else {
                second.append(" ").append(word);
                break;
            }
        }
        return new String[]{trim(mc,first.toString(),maxWidth),trim(mc,second.toString(),maxWidth)};
    }

    private static String clock(long ms){long seconds=Math.max(0,ms/1000);return (seconds/60)+":"+String.format("%02d",seconds%60);}
}
