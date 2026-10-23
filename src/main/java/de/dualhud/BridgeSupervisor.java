package de.dualhud;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public final class BridgeSupervisor {
    private static final String RESOURCE = "/dualmusichud/native/WindowsMediaBridge.exe";
    private static Process child;
    private static volatile boolean stopping;

    private BridgeSupervisor() {}

    public static void start() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            DiagnosticLog.record("AUTO_BRIDGE_UNAVAILABLE_NON_WINDOWS");
            return;
        }
        Thread worker = new Thread(BridgeSupervisor::watch, "DualMusicHUD-BridgeSupervisor");
        worker.setDaemon(true);
        worker.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            Process p = child;
            if (p != null && p.isAlive()) p.destroy();
        }, "DualMusicHUD-BridgeShutdown"));
    }

    private static void watch() {
        Path exe;
        try (InputStream input = BridgeSupervisor.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                DiagnosticLog.record("AUTO_BRIDGE_MISSING_RESOURCE: rebuild with .NET 8 SDK using build.bat");
                return;
            }
            Path dir = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "DualMusicHUD", "helper");
            Files.createDirectories(dir);
            exe = dir.resolve("WindowsMediaBridge.exe");
            Path temp = dir.resolve("WindowsMediaBridge.exe.new");
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, exe, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            DiagnosticLog.record("AUTO_BRIDGE_EXTRACTION_FAILED " + e.getClass().getSimpleName() + " " + e.getMessage());
            return;
        }
        while (!stopping) {
            try {
                Path logDir = exe.getParent().getParent().resolve("logs");
                Files.createDirectories(logDir);
                Path out = logDir.resolve("bridge-process.log");
                ProcessBuilder pb = new ProcessBuilder(exe.toString());
                pb.directory(exe.getParent().toFile());
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(out.toFile()));
                child = pb.start();
                DiagnosticLog.record("AUTO_BRIDGE_STARTED pid=" + child.pid());
                int code = child.waitFor();
                DiagnosticLog.record("AUTO_BRIDGE_EXIT exitCode=" + code + " restartingAfter5Seconds");
            } catch (Exception e) {
                DiagnosticLog.record("AUTO_BRIDGE_PROCESS_ERROR " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
            if (!stopping) {
                try { TimeUnit.SECONDS.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}
