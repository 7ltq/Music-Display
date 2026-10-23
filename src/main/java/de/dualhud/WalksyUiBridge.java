package de.dualhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class WalksyUiBridge {
    private WalksyUiBridge() {}
    private static boolean tried;
    private static long lastAttempt;
    private static Method graphicsFactory, roundedGradient, textMethod, textWidth, roundedBlit;
    private static Object fontNormal, fontMedium, fontBold;
    private static boolean ready;
    private static boolean warned;

    private static void init() {
        if (ready) return;
        if (!WalksyGlassBridge.available()) return;
        long now = System.nanoTime();
        if(tried && now-lastAttempt<5_000_000_000L)return;
        lastAttempt=now;
        tried = true;
        try {
            Class<?> gfx = Class.forName("main.walksy.lib.core.render.WLGraphics2D");
            Class<?> context = Class.forName("main.walksy.lib.core.render.GuiContext");
            Class<?> fonts = Class.forName("main.walksy.lib.core.render.font.Fonts");
            Class<?> face = Class.forName("main.walksy.lib.core.render.font.msdf.MsdfFont");
            graphicsFactory = context.getMethod("graphics", GuiGraphicsExtractor.class);
            roundedGradient = gfx.getMethod("fillRoundedGradient", float.class, float.class, float.class, float.class,
                    int.class, int.class, int.class, int.class, float.class);
            roundedBlit = gfx.getMethod("blitRounded", Identifier.class,
                    float.class,float.class,float.class,float.class,float.class,float.class,
                    int.class,int.class,int.class,int.class,int.class,float.class);
            textMethod = face.getMethod("extract", gfx, String.class, float.class, float.class, float.class, int.class);
            textWidth = face.getMethod("width", String.class, float.class);
            fontNormal = fonts.getField("SFPRO_DISPLAY").get(null);
            fontMedium = fonts.getField("SFPRO_DISPLAY_MEDIUM").get(null);
            fontBold = fonts.getField("SFPRO_DISPLAY_BOLD").get(null);
            ready = fontNormal != null && fontMedium != null && fontBold != null;
        } catch (ReflectiveOperationException | LinkageError err) {
            if(!warned) { warned=true; System.err.println("[Music Display] Walksy UI disabled: "+err); }
        }
    }

    static boolean rounded(GuiGraphicsExtractor g, float x, float y, float w, float h, float rad,
                           int top, int bottom) {
        init();
        if (!ready || w<=0 || h<=0) return false;
        try {
            Object renderer = graphicsFactory.invoke(null, g);
            roundedGradient.invoke(renderer, x,y,x+w,y+h,top,top,bottom,bottom,rad);
            return true;
        } catch (ReflectiveOperationException | LinkageError err) { disable(err); return false; }
    }

    static boolean blitRoundedCard(GuiGraphicsExtractor g, Identifier texture,
                                   int width, int height, float radius) {
        init();
        if(!ready || width<=0 || height<=0)return false;
        try {
            Object renderer=graphicsFactory.invoke(null,g);
            roundedBlit.invoke(renderer,texture,0f,0f,0f,0f,
                    (float)width,(float)height,width,height,width,height,0xFFFFFFFF,radius);
            return true;
        } catch (ReflectiveOperationException | LinkageError err) { disable(err);return false; }
    }

    static boolean blitRounded(GuiGraphicsExtractor g, Identifier texture, int x, int y, int size,
                               int imageW, int imageH, float radius) {
        init();
        if (!ready) return false;
        try {
            Object renderer = graphicsFactory.invoke(null, g);
            roundedBlit.invoke(renderer, texture, (float)x, (float)y, 0f, 0f,
                    (float)size, (float)size, imageW,imageH,imageW,imageH, 0xFFFFFFFF, radius);
            return true;
        } catch (ReflectiveOperationException | LinkageError err) { disable(err); return false; }
    }

    static boolean text(GuiGraphicsExtractor g, String content, float x, float y, float size, int color, boolean bold) {
        init();
        if (!ready || content == null || content.isEmpty()) return false;
        try {
            Object renderer = graphicsFactory.invoke(null, g);
            textMethod.invoke(bold?fontBold:fontNormal, renderer, content,x,y,size,color);
            return true;
        } catch (ReflectiveOperationException | LinkageError err) { disable(err); return false; }
    }

    static float width(String str, float size, boolean bold) {
        init();
        if (!ready || str==null) return -1;
        try { return ((Number)textWidth.invoke(bold?fontBold:fontNormal,str,size)).floatValue(); }
        catch (ReflectiveOperationException | LinkageError err) { disable(err); return -1; }
    }

    
    static boolean playerFace(GuiGraphicsExtractor g, int x, int y, int size) {
        try {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null)return false;
            Object skin=null;
            for(String name : new String[]{"getSkin","getSkinTextures"}) {
                try { skin=mc.player.getClass().getMethod(name).invoke(mc.player);break; }
                catch(ReflectiveOperationException ignored) {}
            }
            if(skin==null){
                
                Object manager=mc.getClass().getMethod("getSkinManager").invoke(mc);
                Object profile=mc.player.getClass().getMethod("getGameProfile").invoke(mc.player);
                for(String name:new String[]{"getInsecureSkin","getSkin","getSkinTextures"}){
                    for(Method candidate:manager.getClass().getMethods()){
                        if(candidate.getName().equals(name) && candidate.getParameterCount()==1){
                            try {skin=candidate.invoke(manager,profile);break;}catch(ReflectiveOperationException ignored){}
                        }
                    }
                    if(skin!=null)break;
                }
            }
            if(skin==null)return false;
            Object textureObj=skin;
            if(!(textureObj instanceof Identifier)) {
                for(String method : new String[]{"texture","getTexture"}) {
                    try { textureObj=skin.getClass().getMethod(method).invoke(skin);break; }
                    catch(ReflectiveOperationException ignored) {}
                }
            }
            if(!(textureObj instanceof Identifier tex))return false;
            
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8, 8, size, size, 64, 64, 64, 64);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40, 8, size, size, 64, 64, 64, 64);
            return true;
        } catch(ReflectiveOperationException | LinkageError | RuntimeException ignored) { return false; }
    }

    private static void disable(Throwable ex) {
        ready=false;
        if(!warned) { warned=true; System.err.println("[Music Display] Walksy UI fallback: "+ex); }
    }
}
