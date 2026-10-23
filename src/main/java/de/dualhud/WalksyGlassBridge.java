package de.dualhud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;


final class WalksyGlassBridge {
    private static boolean warned;
    private WalksyGlassBridge() {}

    static boolean available() {
        return FabricLoader.getInstance().isModLoaded("walksylib");
    }

    
    static boolean draw(GuiGraphicsExtractor extractor, float x, float y, float width, float height,
                        float radius, HudConfig config, int tintRgb) {
        if (!available()) return false;
        try {
            Class<?> contextClass = Class.forName("main.walksy.lib.core.render.GuiContext");
            Object graphics = contextClass.getMethod("graphics", GuiGraphicsExtractor.class).invoke(null, extractor);
            Class<?> styleClass = Class.forName("main.walksy.lib.core.render.glass.api.GlassStyle");
            Object style = styleClass.getMethod("create").invoke(null);
            style = apply(styleClass, style, "tint", new Class<?>[]{int.class, float.class},
                    tintRgb & 0x00ffffff, .14f * (config.glassOpacity / 100f));
            style = apply(styleClass, style, "opacity", new Class<?>[]{float.class}, config.glassOpacity / 100f);
            style = apply(styleClass, style, "blurRadius", new Class<?>[]{int.class},
                    Math.max(2, Math.round(config.blurStrength * ("FROSTED".equals(config.glassPreset)?.45f:.28f))));
            float refraction=switch(config.glassPreset){
                case "SOFT" -> .19f;
                case "FROSTED" -> .28f;
                default -> .37f;
            };
            style = apply(styleClass, style, "refractionStrength", new Class<?>[]{float.class}, refraction);
            style = apply(styleClass, style, "refractionFactor", new Class<?>[]{float.class}, 1.06f);
            style = apply(styleClass, style, "refractionDispersion", new Class<?>[]{float.class}, .16f);
            style = apply(styleClass, style, "bevel", new Class<?>[]{float.class}, 2.5f);
            style = apply(styleClass, style, "rimLight", new Class<?>[]{int.class, float.class},
                    0xFFFFFF, config.glassHighlights ? .75f : 0f);
            style = apply(styleClass, style, "glareFactor", new Class<?>[]{float.class},
                    config.glassHighlights ? .7f : 0f);
            Object builder = graphics.getClass().getMethod("glass").invoke(graphics);
            
            
            builder = builder.getClass().getMethod("screenSpace").invoke(builder);
            builder = builder.getClass().getMethod("dimensions", float.class,float.class,float.class,float.class)
                    .invoke(builder, x,y,width,height);
            builder = builder.getClass().getMethod("cornerRadius", float.class).invoke(builder, radius);
            builder = builder.getClass().getMethod("style", styleClass).invoke(builder, style);
            builder.getClass().getMethod("extract").invoke(builder);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (!warned) {
                warned = true;
                System.err.println("[Music Display] WalksyLib glass API unavailable/incompatible: " + ex);
            }
            return false;
        }
    }

    static boolean drawSettings(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                float radius, HudConfig config, boolean panel, boolean active,
                                boolean hover, String role) {
        if (!available() || !config.liquidGlass || w < 1f || h < 1f) return false;
        try {
            Class<?> contextClass = Class.forName("main.walksy.lib.core.render.GuiContext");
            Object graphics = contextClass.getMethod("graphics", GuiGraphicsExtractor.class).invoke(null, g);
            Class<?> styleClass = Class.forName("main.walksy.lib.core.render.glass.api.GlassStyle");
            Object style;
            
            if (panel) {
                Class<?> walksyPanel=Class.forName("main.walksy.lib.core.gui.WalksyLibPanelScreen");
                style=walksyPanel.getMethod("panelStyle").invoke(null);
                
                style=apply(styleClass, style, "blurRadius", new Class<?>[]{int.class},
                        Math.max(2,Math.min(8,config.blurStrength/12)));
                style=apply(styleClass, style, "opacity", new Class<?>[]{float.class},
                        0.93f);
            } else {
                Class<?> knob=Class.forName("main.walksy.lib.core.gui.widget.options.control.LiquidKnob");
                Class<?> panelClass=Class.forName("main.walksy.lib.core.gui.WalksyLibPanelScreen");
                switch(role) {
                    case "TRACK" -> style=knob.getMethod("grayTrackStyle").invoke(null);
                    case "FILL" -> style=knob.getMethod("accentFillStyle").invoke(null);
                    case "KNOB" -> style=knob.getMethod("bevelKnobStyle",int.class).invoke(null,9);
                    case "ROW" -> style=panelClass.getMethod("sidebarStyle").invoke(null);
                    default -> { 
                        Class<?> button=Class.forName("main.walksy.lib.core.gui.widget.ResetButton");
                        style=button.getMethod("knobStyle",int.class).invoke(null,8);
                    }
                }
                
                
                int rgb=switch(role) {
                    case "FILL" -> 0xD6EDFF;
                    case "KNOB" -> 0xF1F9FF;
                    case "TRACK" -> active?0xB6D9F4:0x263445;
                    case "ROW" -> 0x243449;
                    default -> 0xEAF3FF;
                };
                float alpha=switch(role) {
                    case "FILL" -> 0.78f;
                    case "KNOB" -> 0.52f;
                    case "TRACK" -> active?0.46f:0.35f;
                    case "ROW" -> hover?0.27f:0.18f;
                    default -> active?0.36f:(hover?0.25f:0.16f);
                };
                style=apply(styleClass,style,"tint",new Class<?>[]{int.class,float.class},rgb,alpha);
                style=apply(styleClass,style,"blurRadius",new Class<?>[]{int.class},0);
                if(!"TRACK".equals(role) && !"FILL".equals(role)) {
                    style=apply(styleClass,style,"brightEdge",new Class<?>[]{float.class},
                            "KNOB".equals(role)?1.4f:(hover||active?1.1f:0.85f));
                }
                if("ROW".equals(role)) {
                    
                    style=apply(styleClass,style,"refractionStrength",new Class<?>[]{float.class},0.4f);
                }
                style=apply(styleClass,style,"opacity",new Class<?>[]{float.class},1f);
            }
            Object builder=graphics.getClass().getMethod("glass").invoke(graphics);
            Class<?> bc=builder.getClass();
            
            builder=bc.getMethod("dimensions",float.class,float.class,float.class,float.class).invoke(builder,x,y,w,h);
            builder=bc.getMethod("cornerRadius",float.class).invoke(builder,Math.min(radius,Math.min(w,h)/2f));
            builder=bc.getMethod("style",styleClass).invoke(builder,style);
            builder=bc.getMethod("hover",float.class).invoke(builder,hover?1f:0f);
            bc.getMethod("extract").invoke(builder);
            return true;
        } catch(ReflectiveOperationException | LinkageError | RuntimeException e){
            if (!warned){ warned=true; System.err.println("[Music Display] WalksyLib controls fallback: "+e); }
            return false;
        }
    }

    private static Object apply(Class<?> styleClass, Object style, String method, Class<?>[] types, Object... values)
            throws ReflectiveOperationException {
        Method m = styleClass.getMethod(method, types);
        return m.invoke(style, values);
    }
}
