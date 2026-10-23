package de.dualhud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;


public final class CloudBackdrop {
    private static final Identifier ID=Identifier.fromNamespaceAndPath("dualmusichud","album_clouds");
    private static AlbumPalette cached;
    private static boolean ready;
    private static final Identifier CARD_ID=Identifier.fromNamespaceAndPath("dualmusichud","album_clouds_mini");
    private static AlbumPalette cardPalette;
    private static int cardWidth=-1,cardHeight=-1,cardRadius=-1,cardOpacity=-1;
    private static boolean cardCompact,cardReady;
    private static final Identifier WALK_ID=Identifier.fromNamespaceAndPath("dualmusichud","walksy_cloud_mini");
    private static AlbumPalette walkPalette;
    private static int walkWidth=-1,walkHeight=-1,walkOpacity=-1;
    private static boolean walkReady;

    public static void drawWalksyCard(GuiGraphicsExtractor ctx,Minecraft client,AlbumPalette palette,
                                      int width,int height,int radius,int opacity){
        if(!palette.equals(walkPalette)||width!=walkWidth||height!=walkHeight||opacity!=walkOpacity){
            walkPalette=palette;walkWidth=width;walkHeight=height;walkOpacity=opacity;
            walkReady=false;
            try{
                
                byte[] png=CloudPaletteRaster.cardPng(palette,width,height,0,opacity,false);
                NativeImage image=NativeImage.read(png);
                client.getTextureManager().release(WALK_ID);
                client.getTextureManager().register(WALK_ID,new DynamicTexture(()->"Music Display Walksy clouds",image));
                walkReady=true;
            }catch(Exception ex){DiagnosticLog.record("WALKSY_CLOUD_ERROR "+ex.getMessage());}
        }
        if(walkReady && WalksyUiBridge.blitRoundedCard(ctx,WALK_ID,width,height,radius))return;
        drawCard(ctx,client,palette,width,height,radius,opacity,false);
    }

    public static void drawCard(GuiGraphicsExtractor ctx,Minecraft client,AlbumPalette palette,int width,int height,int radius,int opacity,boolean compact){
        if(!palette.equals(cardPalette)||width!=cardWidth||height!=cardHeight||radius!=cardRadius||opacity!=cardOpacity||compact!=cardCompact){
            cardPalette=palette;cardWidth=width;cardHeight=height;cardRadius=radius;cardOpacity=opacity;cardCompact=compact;
            cardReady=false;
            try {
                byte[] png=CloudPaletteRaster.cardPng(palette,width,height,radius,opacity,compact);
                NativeImage image=NativeImage.read(png);
                client.getTextureManager().release(CARD_ID);
                client.getTextureManager().register(CARD_ID,new DynamicTexture(()->"Music Display mini album clouds",image));
                cardReady=true;
            } catch(Exception ex){
                DiagnosticLog.record("MINI_CLOUD_ERROR "+ex.getClass().getSimpleName()+" "+ex.getMessage());
            }
        }
        if(cardReady)ctx.blit(RenderPipelines.GUI_TEXTURED,CARD_ID,0,0,0,0,width,height,width,height,width,height);
        else ctx.fillGradient(0,0,width,height,palette.primary(),palette.secondary());
    }

    private CloudBackdrop() {}

    public static void draw(GuiGraphicsExtractor ctx, Minecraft client, AlbumPalette palette, int w, int h) {
        if(!palette.equals(cached)) {
            cached=palette;
            ready=false;
            try {
                byte[] png=CloudPaletteRaster.png(palette);
                NativeImage image=NativeImage.read(png);
                client.getTextureManager().release(ID);
                client.getTextureManager().register(ID,new DynamicTexture(()->"Music Display album clouds",image));
                ready=true;
            } catch(Exception ex) {
                DiagnosticLog.record("CLOUD_BACKDROP_ERROR: "+ex.getClass().getSimpleName()+": "+ex.getMessage());
            }
        }
        if(ready) {
            ctx.blit(RenderPipelines.GUI_TEXTURED,ID,0,0,0,0,w,h,
                CloudPaletteRaster.WIDTH,CloudPaletteRaster.HEIGHT,
                CloudPaletteRaster.WIDTH,CloudPaletteRaster.HEIGHT);
        } else {
            ctx.fillGradient(0,0,w,h,palette.primary(),palette.secondary());
        }
        
        ctx.fillGradient(0,0,w,h,0x09000000,0x28000000);
    }
}
