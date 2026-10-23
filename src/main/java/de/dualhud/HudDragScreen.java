package de.dualhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;


public final class HudDragScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private double grabX, grabY;
    private int x, y;
    public HudDragScreen(Screen parent){super(Component.literal("Music Display – Move HUD"));this.parent=parent;}
    @Override protected void init(){
        int[] bounds=DualMusicHud.hudBounds(width,height);
        x=bounds[0];y=bounds[1];
    }
    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mouseX,int mouseY,float partialTick){
        DualMusicHud.drawEditorHud(ctx,Minecraft.getInstance(),x,y);
        {
            int cx=width/2, row=height-59;
            editorButton(ctx,cx-100,row,42,20,"LEFT",mouseX,mouseY);
            editorButton(ctx,cx-52,row,42,20,"UP",mouseX,mouseY);
            editorButton(ctx,cx+6,row,42,20,"DOWN",mouseX,mouseY);
            editorButton(ctx,cx+58,row,42,20,"RIGHT",mouseX,mouseY);
            editorButton(ctx,width/2-75,height-31,150,20,"SAVE POSITION",mouseX,mouseY);
        }
        if(!WalksyUiBridge.text(ctx,"Drag the HUD or use arrow keys  |  Shift = 10px",12,12,12f,0xFFFFFFFF,false))
            ctx.text(font,"Drag or use arrow keys (Shift = 10 px), then save",12,12,0xFFFFFFFF,false);
        int[] size=DualMusicHud.hudBounds(width,height);
        int w=size[2], h=size[3];
        ctx.fill(x,y,x+w,y+1,0xA4FFFFFF);
        ctx.fill(x,y+h-1,x+w,y+h,0xA4FFFFFF);
        ctx.fill(x,y,x+1,y+h,0xA4FFFFFF);
        ctx.fill(x+w-1,y,x+w,y+h,0xA4FFFFFF);
    }
    private void editorButton(GuiGraphicsExtractor g,int x,int y,int w,int h,String text,int mx,int my){
        boolean hover=mx>=x&&mx<x+w&&my>=y&&my<y+h;
        int fill=hover?0xD8E5EEFA:0xA0414F63;
        if(!WalksyUiBridge.rounded(g,x,y,w,h,10,fill,fill))g.fill(x,y,x+w,y+h,fill);
        float tw=WalksyUiBridge.width(text,10f,false);
        int xx=x+Math.round((w-(tw>=0?tw:font.width(text)))/2);
        int color=hover?0xFF172231:0xFFF5F8FF;
        if(!WalksyUiBridge.text(g,text,xx,y+5,10f,color,false))g.text(font,text,xx,y+6,color,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        int[] bounds=DualMusicHud.hudBounds(width,height);
        if(event.button()==1&&event.y()<height-64&&event.x()>=x&&event.x()<x+bounds[2]&&event.y()>=y&&event.y()<y+bounds[3]){
            dragging=true;grabX=event.x()-x;grabY=event.y()-y;return true;
        }
        if(event.button()==1) {
            int cx=width/2, row=height-59;
            if(hit(event,cx-100,row,42,20)){nudge(-5,0);return true;}
            if(hit(event,cx-52,row,42,20)){nudge(0,-5);return true;}
            if(hit(event,cx+6,row,42,20)){nudge(0,5);return true;}
            if(hit(event,cx+58,row,42,20)){nudge(5,0);return true;}
            if(hit(event,width/2-75,height-31,150,20)){onClose();return true;}
        }
        return super.mouseClicked(event,doubleClick);
    }
    private boolean hit(MouseButtonEvent e,int bx,int by,int bw,int bh){
        return e.x()>=bx&&e.x()<bx+bw&&e.y()>=by&&e.y()<by+bh;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragging){
            int[] bounds=DualMusicHud.hudBounds(width,height);
            x=Math.max(0,Math.min((int)Math.round(event.x()-grabX),Math.max(0,width-bounds[2])));
            y=Math.max(0,Math.min((int)Math.round(event.y()-grabY),Math.max(0,height-bounds[3])));
            return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(dragging){dragging=false;persist();return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event){
        int step=(event.modifiers() & 0x0001)!=0 ? 10 : 1;
        int[] bounds=DualMusicHud.hudBounds(width,height);
        int maxX=Math.max(0,width-bounds[2]), maxY=Math.max(0,height-bounds[3]);
        boolean moved=true;
        switch(event.key()){
            case 263 -> x=Math.max(0,x-step);
            case 262 -> x=Math.min(maxX,x+step);
            case 265 -> y=Math.max(0,y-step);
            case 264 -> y=Math.min(maxY,y+step);
            default -> moved=false;
        }
        if(moved){persist();return true;}
        return super.keyPressed(event);
    }
    private void nudge(int dx,int dy){int[] sz=DualMusicHud.hudBounds(width,height);x=Math.max(0,Math.min(Math.max(0,width-sz[2]),x+dx));y=Math.max(0,Math.min(Math.max(0,height-sz[3]),y+dy));persist();}
    private void persist(){HudConfig c=DualMusicHud.config();c.anchor="FREE";c.x=x;c.y=y;c.save();}
    @Override public void onClose(){persist();minecraft.gui.setScreen(parent);}
}
