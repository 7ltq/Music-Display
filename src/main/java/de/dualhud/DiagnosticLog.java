package de.dualhud;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public final class DiagnosticLog {
    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("DualMusicHUD");
    private static final Path FILE = DIR.resolve("minecraft-media.log");
    private static String previous = "";
    private static long previousAt;
    private DiagnosticLog() {}
    public static synchronized void record(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(previous) && now - previousAt < 15000) return;
        previous = message; previousAt = now;
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE) && Files.size(FILE) > 3_000_000) {
                Files.move(FILE, DIR.resolve("minecraft-media.previous.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            String line = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + message + System.lineSeparator();
            Files.writeString(FILE, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
}
