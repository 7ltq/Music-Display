package de.dualhud;

import main.walksy.lib.api.WalksyLibApi;
import main.walksy.lib.api.config.Config;
import main.walksy.lib.api.config.option.Category;
import main.walksy.lib.api.config.option.OptionGroup;
import main.walksy.lib.api.config.option.Options;
import main.walksy.lib.api.config.value.Preview;
import main.walksy.lib.api.render.PreviewCanvas;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class WalksyMusicConfig implements WalksyLibApi {
    private static final Identifier ART = Identifier.fromNamespaceAndPath("dualmusichud", "current_artwork");
    private static HudConfig earlyConfig;
    public WalksyMusicConfig() {}

    private static HudConfig cfg() {
        HudConfig ready=DualMusicHud.config();
        if(ready!=null)return ready;
        if(earlyConfig==null)earlyConfig=new HudConfig(FabricLoader.getInstance().getConfigDir().resolve("dual-music-hud.properties"));
        return earlyConfig;
    }
    private static void update(Runnable change) { change.run(); cfg().save(); }
    private static int scalePercent(){return Math.round(cfg().scale*100f);}

    public enum Glass { CRYSTAL, SOFT, FROSTED }
    public enum Motion { SPRING, SMOOTH, OFF }
    private static Glass glass(){try{return Glass.valueOf(cfg().glassPreset);}catch(Exception ignored){return Glass.CRYSTAL;}}
    private static Motion motion(){try{return Motion.valueOf(cfg().motionPreset);}catch(Exception ignored){return Motion.SPRING;}}

    private static OptionGroup livePreviewGroup() {
        return OptionGroup.builder("Live Preview")
            .option(Options.ofPreview("Now Playing", (Preview)WalksyMusicConfig::drawPreview).build())
            .build();
    }

    @Override public Config define() {
        return Config.builder()
            .path(FabricLoader.getInstance().getConfigDir().resolve("musicdisplay-walksylib.json"))
            .onSave(() -> cfg().save())
            .category(Category.builder("Appearance")
                .group(OptionGroup.builder("General Options")
                    .option(Options.ofBoolean("Music HUD Enabled",()->cfg().visible,true,v->update(()->cfg().visible=v)).build())
                    .option(Options.ofBoolean("Neutral Glass Cards",()->cfg().neutralCards,false,v->update(()->cfg().neutralCards=v)).build())
                    .option(Options.ofBoolean("Animated Album Colors",()->cfg().animatedGradient,true,v->update(()->cfg().animatedGradient=v)).build())
                    .option(Options.ofBoolean("Glass Highlights",()->cfg().glassHighlights,true,v->update(()->cfg().glassHighlights=v)).build())
                    .build())
                .group(livePreviewGroup())
                .group(OptionGroup.builder("Glass")
                    .option(Options.ofNumber("Background Opacity",()->cfg().glassOpacity,100,v->update(()->cfg().glassOpacity=v)).range(0,100,1).build())
                    .option(Options.ofEnum("Glass Style",WalksyMusicConfig::glass,Glass.CRYSTAL,v->update(()->cfg().glassPreset=v.name()),Glass.class).build())
                    .option(Options.ofNumber("Glass Blur",()->cfg().blurStrength,48,v->update(()->cfg().blurStrength=v)).range(0,100,1).build())
                    .option(Options.ofNumber("Corner Radius",()->cfg().cornerRadius,16,v->update(()->cfg().cornerRadius=v)).range(6,24,1).build())
                    .option(Options.ofButton("WalksyLib Panel Tint / Light Mode", WalksyNativeSettings::openAppearance).build())
                    .build())
                .build())
            .category(Category.builder("Edit HUD")
                .group(OptionGroup.builder("HUD Layout")
                    .option(Options.ofButton("Edit HUD Position", () -> {
                        Minecraft mc=Minecraft.getInstance();
                        mc.gui.setScreen(new HudDragScreen(mc.gui.screen()));
                    }).build())
                    .option(Options.ofNumber("Mini HUD Width",()->cfg().width,280,v->update(()->cfg().width=v)).range(240,380,5).build())
                    .option(Options.ofNumber("HUD Scale",WalksyMusicConfig::scalePercent,80,v->update(()->cfg().scale=v/100f)).range(60,250,5).build())
                    .option(Options.ofNumber("Mini HUD Opacity",()->cfg().miniOpacity,100,v->update(()->cfg().miniOpacity=v)).range(0,100,5).build())
                    .option(Options.ofButton("Reset HUD Position",()->update(()->{cfg().anchor="BOTTOM_RIGHT";cfg().x=12;cfg().y=12;})).build())
                    .build())
                .group(livePreviewGroup())
                .build())
            .category(Category.builder("Lyrics")
                .group(OptionGroup.builder("Lyrics Options")
                    .option(Options.ofBoolean("Karaoke Wave",()->cfg().karaokeWave,true,v->update(()->cfg().karaokeWave=v)).build())
                    .option(Options.ofBoolean("Lyrics Focus",()->cfg().lyricsFocus,false,v->update(()->cfg().lyricsFocus=v)).build())
                    .build())
                .group(livePreviewGroup())
                .build())
            .category(Category.builder("Animations")
                .group(OptionGroup.builder("Animations")
                    .option(Options.ofEnum("Motion Style",WalksyMusicConfig::motion,Motion.SPRING,v->update(()->cfg().motionPreset=v.name()),Motion.class).build())
                    .option(Options.ofBoolean("Animated Gradient",()->cfg().dynamicGradient,true,v->update(()->cfg().dynamicGradient=v)).build())
                    .option(Options.ofBoolean("Light Border",()->cfg().lightBorder,true,v->update(()->cfg().lightBorder=v)).build())
                    .build())
                .group(livePreviewGroup())
                .build())
            .build();
    }

    private static String previewTrackId="";
    private static String previewActiveKey="";
    private static String previewActiveText="";
    private static String previewFadeText="";
    private static long previewFadeStartNs;
    private static final long PREVIEW_FADE_NS=650_000_000L;

    private static void drawPreview(PreviewCanvas canvas){
        Minecraft mc=Minecraft.getInstance();
        HudConfig config=cfg();
        MediaBridge.Track track=MediaBridge.get(config);
        LyricsService.update(track);
        float scale=canvas.uiScale();
        float x=canvas.x()+8f*scale,y=canvas.y()+8f*scale;
        float w=Math.max(120f,canvas.width()-16f*scale);
        float h=Math.min(134f*scale,canvas.height()-16f*scale);
        float radius=Math.min(16f*scale,h*.24f);
        int from=0xCC27394B,to=0xC41B2334;
        int highlight=0xFFF5F9FF;
        if(track!=null && ArtworkTexture.available(mc)){
            AlbumPalette palette=ArtworkTexture.palette(mc,AlbumPalette.DEMO);
            from=AlbumPalette.alpha(palette.primary(),205);
            to=AlbumPalette.alpha(palette.secondary(),208);
            highlight=AlbumPalette.mix(0xFFFFFFFF,palette.accent(),.25f);
        }
        canvas.graphics().fillRoundedGradient(x,y,x+w,y+h,from,from,to,to,radius);
        float art=Math.min(h-16f*scale,88f*scale);
        if(track!=null && ArtworkTexture.available(mc)){
            canvas.blit(ART,x+8f*scale,y+8f*scale,art,art,0f,0f,1f,1f,0xFFFFFFFF);
        }
        float tx=x+art+18f*scale;
        float remaining=Math.max(50f,w-(tx-x)-12f*scale);
        String title=track==null?"No track playing":track.title();
        String artist=track==null?"Start Spotify or Apple Music":track.artist();
        float titleSize=12f*scale,subSize=10f*scale;
        canvas.text(truncate(canvas,title,titleSize,remaining),tx,y+27f*scale,titleSize,0xFFFFFFFF);
        canvas.text(truncate(canvas,artist,subSize,remaining),tx,y+47f*scale,subSize,0xFFDDE7EE);

        String trackId=track==null?"":track.title()+"\u0001"+track.artist()+"\u0001"+track.durationMs();
        if(!trackId.equals(previewTrackId)){
            previewTrackId=trackId;
            previewActiveKey="";
            previewActiveText="";
            previewFadeText="";
        }
        if(track==null)return;

        long ms=Math.max(0,track.positionMs()+config.lyricOffsetMs);
        LyricsService.TimedLine active=LyricsService.timedLine(ms,track.durationMs());
        boolean vocal=config.lyricsEnabled && LyricsService.vocalActive(active,ms);
        String key=vocal?trackId+"\u0001"+active.startMs():"";
        long now=System.nanoTime();
        if(!key.equals(previewActiveKey)){
            if(!previewActiveKey.isEmpty()&&!previewActiveText.isBlank()){
                previewFadeText=previewActiveText;
                previewFadeStartNs=now;
            }
            previewActiveKey=key;
            previewActiveText=vocal?active.text():"";
        }

        float lyricSize=11f*scale;
        float lyricY=y+78f*scale;
        if(vocal){
            String line=truncate(canvas,active.text(),lyricSize,remaining);
            if(config.karaokeWave){
                float frontier=active.wordTimes().isEmpty()
                    ? wordPacedFrontier(line,LyricsService.highlightFraction(active,ms))
                    : LyricsService.frontierForWords(active,ms,line);
                drawKaraoke(canvas,line,tx,lyricY,lyricSize,frontier,highlight);
            } else {
                canvas.text(line,tx,lyricY,lyricSize,0xFFE7ECF5);
            }
        }

        if(!previewFadeText.isBlank()){
            float progress=(now-previewFadeStartNs)/(float)PREVIEW_FADE_NS;
            if(progress>=1f){
                previewFadeText="";
            } else {
                float ease=Math.max(0f,Math.min(1f,progress));
                ease=ease*ease*(3f-2f*ease);
                int alpha=Math.round(205f*(1f-ease));
                int color=(alpha<<24)|0x00FFFFFF;
                String old=truncate(canvas,previewFadeText,10f*scale,remaining);
                float fadeY=y+(vocal?102f:78f)*scale-(4f*scale*ease);
                canvas.text(old,tx,fadeY,10f*scale,color);
            }
        }
    }

    private static void drawKaraoke(PreviewCanvas canvas,String line,float x,float y,float size,float frontier,int accent){
        float cursor=x;
        for(int i=0;i<line.length();i++){
            String glyph=line.substring(i,i+1);
            float passed=frontier-i;
            float highlighted=smoothstep(.05f,.95f,passed);
            float distance=(i+.5f)-frontier;
            float bump=(float)Math.exp(-distance*distance/3.2f)*Math.min(1f,Math.max(0f,frontier));
            float glyphScale=1f+.023f*highlighted+.014f*bump;
            float lift=(.12f*highlighted+.40f*bump)*size/12f;
            int tint=AlbumPalette.mix(0xFFAAA0B7,AlbumPalette.mix(0xFFFFFFFF,accent,.20f),highlighted);
            float width=canvas.textWidth(glyph,size);
            canvas.text(glyph,cursor+width*(1f-glyphScale)*.5f,y-lift,size*glyphScale,tint);
            cursor+=width;
        }
    }

    private static float wordPacedFrontier(String line,float progress){
        if(line.isEmpty())return 0f;
        float[] weights=new float[line.length()];
        float total=0f;
        for(int i=0;i<line.length();){
            int first=i;
            boolean space=Character.isWhitespace(line.charAt(i));
            while(i<line.length()&&Character.isWhitespace(line.charAt(i))==space)i++;
            int count=i-first;
            float each=space?.30f:(float)(.7+.8/Math.sqrt(Math.max(1,count)));
            for(int k=first;k<i;k++){weights[k]=each;total+=each;}
        }
        float remaining=Math.max(0f,Math.min(1f,progress))*total;
        for(int i=0;i<weights.length;i++){
            if(remaining<=weights[i])return i+remaining/Math.max(.001f,weights[i]);
            remaining-=weights[i];
        }
        return line.length();
    }

    private static float smoothstep(float low,float high,float input){
        float f=Math.max(0f,Math.min(1f,(input-low)/(high-low)));
        return f*f*(3f-2f*f);
    }

    private static String truncate(PreviewCanvas c,String value,float size,float maxWidth){
        if(value==null)return "";
        if(c.textWidth(value,size)<=maxWidth)return value;
        while(value.length()>1 && c.textWidth(value+"…",size)>maxWidth)value=value.substring(0,value.length()-1);
        return value+"…";
    }
}
