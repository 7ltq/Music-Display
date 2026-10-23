package de.dualhud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Constructor;


public final class WalksyNativeSettings {
    private WalksyNativeSettings() {}

    public static Screen create(Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("walksylib")) {
            try {
                Class<?> panel=Class.forName("main.walksy.lib.core.gui.WalksyLibPanelScreen");
                Constructor<?> constructor=panel.getConstructor(Screen.class,String.class);
                Object screen=constructor.newInstance(parent,"Music Display");
                screen=panel.getMethod("preselect",String.class).invoke(screen,"dualmusichud");
                return (Screen)screen;
            } catch (ReflectiveOperationException | LinkageError problem) {
                System.err.println("[Music Display] WalksyLib settings could not open: " + problem);
            }
        }
        return new HudSettingsScreen(parent);
    }

    public static void open(Screen parent){Minecraft.getInstance().gui.setScreen(create(parent));}

    public static void openAppearance() {
        Minecraft mc=Minecraft.getInstance();
        if (FabricLoader.getInstance().isModLoaded("walksylib")) try {
            Class<?> panel=Class.forName("main.walksy.lib.core.gui.WalksyLibPanelScreen");
            Object page=panel.getConstructor(Screen.class,String.class).newInstance(mc.gui.screen(),"WalksyLib Appearance");
            page=panel.getMethod("preselectLibraryPage",String.class).invoke(page,"Appearance");
            mc.gui.setScreen((Screen)page);
            return;
        } catch (ReflectiveOperationException | LinkageError problem) {
            System.err.println("[Music Display] Could not open WalksyLib Appearance: " + problem);
        }
    }
}
