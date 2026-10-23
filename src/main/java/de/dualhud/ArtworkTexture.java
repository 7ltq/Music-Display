package de.dualhud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;


public final class ArtworkTexture {
    private static final Path ART = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "DualMusicHUD", "artwork.bin");
    private static final Identifier ID = Identifier.fromNamespaceAndPath("dualmusichud", "current_artwork");
    private static final Identifier BLUR_ID = Identifier.fromNamespaceAndPath("dualmusichud", "blurred_artwork");
    private static boolean blurAvailable = false;
    private static long lastCheck = 0;
    private static long lastModification = -1;
    private static boolean available = false;
    private static int textureWidth = 1, textureHeight = 1;
    private static AlbumPalette palette = AlbumPalette.DEMO;

    private ArtworkTexture() {}

    public static void draw(GuiGraphicsExtractor context, Minecraft minecraft, int x, int y, int size) {
        update(minecraft);
        if (!available) return;
        if (!WalksyUiBridge.blitRounded(context,ID,x,y,size,textureWidth,textureHeight,Math.min(12,size*.12f)))
            context.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, 0, 0, size, size, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    
    public static void drawBlurredBackdrop(GuiGraphicsExtractor context, Minecraft minecraft, int width, int height) {
        update(minecraft);
        if (blurAvailable) context.blit(RenderPipelines.GUI_TEXTURED, BLUR_ID, 0, 0, 0, 0, width, height, 64, 64, 64, 64);
    }

    public static boolean available(Minecraft minecraft) {
        update(minecraft);
        return available;
    }

    public static AlbumPalette palette(Minecraft minecraft, AlbumPalette fallback) {
        update(minecraft);
        return available ? palette : fallback;
    }

    private static void update(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 350) return;
        lastCheck = now;
        try {
            long modified = Files.isRegularFile(ART) ? Files.getLastModifiedTime(ART).toMillis() : -1;
            if (modified == lastModification) return;
            lastModification = modified;
            available = false;
            blurAvailable = false;
            palette = AlbumPalette.DEMO;
            minecraft.getTextureManager().release(ID);
            minecraft.getTextureManager().release(BLUR_ID);
            if (modified < 0 || Files.size(ART) > 4_000_000) return;
            byte[] raw = Files.readAllBytes(ART);
            palette = AlbumPalette.fromImage(raw, AlbumPalette.DEMO);
            NativeImage pixels = NativeImage.read(raw);
            if (pixels.getWidth() < 1 || pixels.getHeight() < 1 || pixels.getWidth() > 2048 || pixels.getHeight() > 2048) {
                pixels.close();
                return;
            }
            textureWidth = pixels.getWidth();
            textureHeight = pixels.getHeight();
            
            
            try {
                java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(raw));
                if (original != null) {
                    java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D graphics=scaled.createGraphics();
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original,0,0,64,64,null);graphics.dispose();
                    float[] kernel=new float[81];float sum=0;
                    for(int yy=-4;yy<=4;yy++)for(int xx=-4;xx<=4;xx++){
                        float value=(float)Math.exp(-(xx*xx+yy*yy)/12.0);
                        kernel[(yy+4)*9+(xx+4)]=value;sum+=value;
                    }
                    for(int i=0;i<kernel.length;i++)kernel[i]/=sum;
                    java.awt.image.ConvolveOp operation=new java.awt.image.ConvolveOp(new java.awt.image.Kernel(9,9,kernel),java.awt.image.ConvolveOp.EDGE_NO_OP,null);
                    java.awt.image.BufferedImage blurred=operation.filter(scaled,null);
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(blurred,"png",out);
                    NativeImage blurPixels=NativeImage.read(out.toByteArray());
                    minecraft.getTextureManager().register(BLUR_ID,new DynamicTexture(()->"DualMusicHUD blurred artwork",blurPixels));
                    blurAvailable=true;
                }
            } catch (Exception blurError) {DiagnosticLog.record("BLUR_UNAVAILABLE: "+blurError.getMessage());}
            DynamicTexture texture = new DynamicTexture(() -> "DualMusicHUD artwork", pixels);
            minecraft.getTextureManager().register(ID, texture);
            available = true;
            DiagnosticLog.record("ARTWORK_LOADED " + textureWidth + "x" + textureHeight + " palette=" + Integer.toHexString(palette.primary()) + "/" + Integer.toHexString(palette.secondary()));
        } catch (Exception e) {
            DiagnosticLog.record("ARTWORK_LOAD_ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
