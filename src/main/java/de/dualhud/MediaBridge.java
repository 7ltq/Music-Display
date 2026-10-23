package de.dualhud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


public final class MediaBridge {
    public record Track(String provider,String title,String artist,String album,long positionMs,long durationMs,boolean playing) {}
    private static final Path APPDATA_DIR = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "DualMusicHUD");
    private static final Path SHARED = APPDATA_DIR.resolve("media.json");
    private static final Path COMMAND = APPDATA_DIR.resolve("command.txt");
    private static final Path COMMAND_QUEUE = APPDATA_DIR.resolve("commands");
    private static final Path BRIDGE_HEARTBEAT = APPDATA_DIR.resolve("bridge-queue-heartbeat.txt");
    private static final Path LEGACY = FabricLoader.getInstance().getConfigDir().resolve("dual-music-hud-media.json");
    private static long lastRead;
    private static Track latest;
    private static String lastIdentity="";
    private static long basePosition, baseWallTime, lastSourcePosition;
    private MediaBridge() {}
    public static Track get(HudConfig config) {
        long now = System.currentTimeMillis();
        long tick = System.nanoTime() / 1_000_000L;
        if (tick - lastRead < 100) return extrapolate(tick);
        lastRead = tick;
        try {
            Path state = Files.isRegularFile(SHARED) ? SHARED : LEGACY;
            if (!Files.isRegularFile(state)) { latest = null; lastIdentity=""; return null; }
            long age = now - Files.getLastModifiedTime(state).toMillis();
            if (age > 8000) { latest = null; lastIdentity=""; return null; }
            JsonObject obj = JsonParser.parseString(Files.readString(state, StandardCharsets.UTF_8)).getAsJsonObject();
            String provider = str(obj,"provider","");
            if ((provider.equals("Spotify") && !config.detectSpotify) || (provider.equals("Apple Music") && !config.detectAppleMusic) || (provider.equals("SoundCloud") && !config.detectSoundCloud)) return null;
            if (!"AUTO".equals(config.providerMode) && !config.providerMode.equals(provider.toUpperCase().replace(' ','_'))) return null;
            String title = str(obj,"title","");
            if (title.isBlank()) { latest = null; lastIdentity=""; return null; }
            String artist=str(obj,"artist","");
            String album=str(obj,"album","");
            long sourcePosition=number(obj,"positionMs");
            boolean playing=obj.has("playing") && obj.get("playing").getAsBoolean();
            String identity=provider+""+title+""+artist;
            if(!identity.equals(lastIdentity) || latest==null || playing!=latest.playing()) {
                lastIdentity=identity;
                basePosition=sourcePosition;
                baseWallTime=tick;
            } else {
                long predicted=playing?basePosition+Math.max(0,tick-baseWallTime):basePosition;
                if(sourcePosition!=lastSourcePosition) {
                    long delta=sourcePosition-predicted;
                    
                    if(Math.abs(delta)>2200) basePosition=sourcePosition; 
                    else basePosition=Math.max(0,predicted+Math.round(delta*0.20));
                    baseWallTime=tick;
                }
            }
            lastSourcePosition=sourcePosition;
            latest = new Track(provider,title,artist,album,sourcePosition,number(obj,"durationMs"),playing);
        } catch (Exception e) { latest = null; lastIdentity=""; }
        return extrapolate(tick);
    }
    public static void togglePlayPause(){sendCommand("toggle");}
    public static void next(){sendCommand("next");}
    public static void previous(){sendCommand("previous");}
    public static void stop(){sendCommand("stop");}
    public static void repeat(boolean enabled){sendCommand("repeat:"+(enabled?"on":"off"));}
    public static void setSystemVolume(int percent){sendCommand("volume:"+Math.max(0,Math.min(100,percent)));}
    public static void seek(long positionMs){sendCommand("seek:"+Math.max(0,positionMs));}
    private static void sendCommand(String cmd){
        try {
            
            
            boolean modern = Files.isRegularFile(BRIDGE_HEARTBEAT) &&
                System.currentTimeMillis()-Files.getLastModifiedTime(BRIDGE_HEARTBEAT).toMillis()<2500L;
            if (modern) {
                Files.createDirectories(COMMAND_QUEUE);
                Path temp=Files.createTempFile(COMMAND_QUEUE,"pending-",".tmp");
                Files.writeString(temp,cmd,StandardCharsets.UTF_8);
                Path ready=temp.resolveSibling(temp.getFileName().toString().replace(".tmp",".txt"));
                Files.move(temp,ready,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                DiagnosticLog.record("MEDIA_COMMAND_QUEUED " + cmd);
            } else {
                Files.createDirectories(COMMAND.getParent());
                Files.writeString(COMMAND,cmd,StandardCharsets.UTF_8);
                DiagnosticLog.record("MEDIA_COMMAND_LEGACY " + cmd);
            }
        } catch (Exception e) {
            DiagnosticLog.record("MEDIA_COMMAND_ERROR " + e.getMessage());
        }
    }
    private static Track extrapolate(long tick) {
        Track t=latest;
        if(t==null || !t.playing())return t;
        long pos=basePosition+Math.max(0,tick-baseWallTime);
        if(t.durationMs()>0)pos=Math.min(t.durationMs(),pos);
        return new Track(t.provider(),t.title(),t.artist(),t.album(),pos,t.durationMs(),t.playing());
    }
    private static String str(JsonObject o,String key,String fallback){try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}catch(Exception e){return fallback;}}
    private static long number(JsonObject o,String key){try{return Math.max(0,o.get(key).getAsLong());}catch(Exception e){return 0;}}
}
