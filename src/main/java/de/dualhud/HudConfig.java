package de.dualhud;

import java.nio.file.*;
import java.util.Properties;


public final class HudConfig {
    public int x=12, y=12, width=280, lyricFontScale=100, lyricOffsetMs=0;
    public String anchor="BOTTOM_RIGHT";
    public float scale=0.8f;
    public boolean visible=true, autoAccent=true, lyricsEnabled=true;
    public boolean showWhenIdle=false, showAlbumArt=true, showArtist=true, showProgress=true;
    public String lyricsLayout="BELOW";
    public String hudStyle="CLASSIC";
    public boolean liquidGlass=true, glassHighlights=true, karaokeWave=true, lyricsFocus=false, inWorldLyrics=false;
    public String glassPreset="CRYSTAL", motionPreset="SPRING", theme="SPOTIFY";
    public String providerMode="AUTO";
    public boolean detectSpotify=true, detectAppleMusic=true, detectSoundCloud=true;
    public int systemVolume=50;
    public int miniOpacity=100, glassOpacity=100, glowStrength=36, colorIntensity=62, cornerRadius=16, blurStrength=48;
    public boolean dynamicGradient=true, lightBorder=true, animatedGradient=true;
    public boolean neutralCards=false;
    private final Path path;
    public HudConfig(Path path) { this.path=path; load(); }
    public void load() {
        if (!Files.exists(path)) return;
        Properties p=new Properties();
        try (var in=Files.newInputStream(path)) { p.load(in);
            anchor=p.getProperty("anchor",anchor);
            liquidGlass=Boolean.parseBoolean(p.getProperty("liquidGlass","true"));
            glassHighlights=Boolean.parseBoolean(p.getProperty("glassHighlights","true"));
            karaokeWave=Boolean.parseBoolean(p.getProperty("karaokeWave","true"));
            lyricsFocus=Boolean.parseBoolean(p.getProperty("lyricsFocus","false"));
            inWorldLyrics=Boolean.parseBoolean(p.getProperty("inWorldLyrics","false"));
            
            glassPreset=p.getProperty("glassPreset","CRYSTAL");
            theme="APPLE_MUSIC".equals(p.getProperty("theme"))?"APPLE_MUSIC":"SPOTIFY";
            motionPreset=p.getProperty("motionPreset","SPRING");
            lyricOffsetMs=integer(p,"lyricOffsetMs",0,-5000,5000);
            
            
            if(!"2".equals(p.getProperty("lyricsTimingVersion")) && lyricOffsetMs==1500)lyricOffsetMs=0;
            
            x=integer(p,"x",x,-10000,10000); y=integer(p,"y",y,-10000,10000);
            width=integer(p,"width",width,240,380);
            if(width>=350 && width<=360)width=308; 
            lyricFontScale=integer(p,"lyricFontScale",lyricFontScale,75,200);
            scale=Math.max(.6f,Math.min(2.5f,Float.parseFloat(p.getProperty("scale","0.8"))));
            visible=Boolean.parseBoolean(p.getProperty("visible","true"));
            showWhenIdle=Boolean.parseBoolean(p.getProperty("showWhenIdle","false"));
            showAlbumArt=Boolean.parseBoolean(p.getProperty("showAlbumArt","true"));
            showArtist=Boolean.parseBoolean(p.getProperty("showArtist","true"));
            showProgress=Boolean.parseBoolean(p.getProperty("showProgress","true"));
            autoAccent=Boolean.parseBoolean(p.getProperty("autoAccent","true"));
            lyricsEnabled=true;
            lyricsLayout="BELOW";
            hudStyle="COMPACT".equals(p.getProperty("hudStyle","CLASSIC"))?"COMPACT":"CLASSIC";
            providerMode="AUTO";
            detectSpotify=true;
            detectAppleMusic=true;
            detectSoundCloud=true;
            systemVolume=integer(p,"systemVolume",systemVolume,0,100);
            glassOpacity=integer(p,"glassOpacity",glassOpacity,0,100);
            miniOpacity=integer(p,"miniOpacity",miniOpacity,0,100);
            glowStrength=integer(p,"glowStrength",glowStrength,0,100);
            colorIntensity=integer(p,"colorIntensity",colorIntensity,0,100);
            cornerRadius=integer(p,"cornerRadius",cornerRadius,6,24);
            blurStrength=integer(p,"blurStrength",blurStrength,0,100);
            dynamicGradient=Boolean.parseBoolean(p.getProperty("dynamicGradient","true"));
            animatedGradient=Boolean.parseBoolean(p.getProperty("animatedGradient","true"));
            neutralCards=Boolean.parseBoolean(p.getProperty("neutralCards","false"));
            lightBorder=Boolean.parseBoolean(p.getProperty("lightBorder","true"));
        } catch (Exception e) { System.err.println("[DualMusicHUD] Could not read config: "+e); }
    }
    private static int integer(Properties p,String k,int def,int min,int max) { try {return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(k,Integer.toString(def)))));} catch (Exception e) {return def;} }
    public void save() {
        Properties p=new Properties();
        
        p.setProperty("lyricOffsetMs",""+lyricOffsetMs);
        p.setProperty("lyricsTimingVersion","2");
        p.setProperty("anchor",anchor);p.setProperty("x",""+x);p.setProperty("y",""+y);p.setProperty("width",""+width);
        p.setProperty("scale",""+scale);p.setProperty("visible",""+visible);
        p.setProperty("showWhenIdle",""+showWhenIdle);p.setProperty("showAlbumArt",""+showAlbumArt);
        p.setProperty("showArtist",""+showArtist);p.setProperty("showProgress",""+showProgress);
        p.setProperty("autoAccent",""+autoAccent);p.setProperty("lyricsEnabled",""+lyricsEnabled);
        p.setProperty("lyricFontScale",""+lyricFontScale);p.setProperty("lyricsLayout",lyricsLayout);
        p.setProperty("hudStyle",hudStyle);
        p.setProperty("karaokeWave",""+karaokeWave);
        p.setProperty("lyricsFocus",""+lyricsFocus);
        p.setProperty("inWorldLyrics",""+inWorldLyrics);
        p.setProperty("glassPreset",glassPreset);p.setProperty("theme",theme);
        p.setProperty("motionPreset",motionPreset);p.setProperty("liquidGlass",""+liquidGlass);p.setProperty("glassHighlights",""+glassHighlights);p.setProperty("providerMode","AUTO");
        p.setProperty("detectAppleMusic",""+detectAppleMusic);
        p.setProperty("detectSoundCloud",""+detectSoundCloud);
        p.setProperty("systemVolume",""+systemVolume);p.setProperty("glassOpacity",""+glassOpacity);
        p.setProperty("miniOpacity",""+miniOpacity);
        p.setProperty("glowStrength",""+glowStrength);p.setProperty("colorIntensity",""+colorIntensity);
        p.setProperty("cornerRadius",""+cornerRadius);p.setProperty("dynamicGradient",""+dynamicGradient);
        p.setProperty("animatedGradient",""+animatedGradient);
        p.setProperty("neutralCards",""+neutralCards);
        p.setProperty("lightBorder",""+lightBorder);p.setProperty("blurStrength",""+blurStrength);
        try { Files.createDirectories(path.getParent());try(var out=Files.newOutputStream(path)){p.store(out,"Dual Music HUD");} }
        catch(Exception e) {System.err.println("[DualMusicHUD] Could not save config: "+e);}
    }
}
