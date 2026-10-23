package de.dualhud;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;


final class VolumeStatus {
    private static final Path FILE=Path.of(System.getenv().getOrDefault("APPDATA",System.getProperty("user.home")),"DualMusicHUD","volume.json");
    private static long lastCheck;
    private static int lastKnown=-1;
    private VolumeStatus() {}
    static int read() {
        long now=System.currentTimeMillis();
        if(now-lastCheck<500)return lastKnown;
        lastCheck=now;
        try {
            if(!Files.isRegularFile(FILE) || now-Files.getLastModifiedTime(FILE).toMillis()>4000) return lastKnown;
            int value=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().get("volume").getAsInt();
            lastKnown=Math.max(0,Math.min(100,value));
        } catch(Exception ignored) {}
        return lastKnown;
    }
}
