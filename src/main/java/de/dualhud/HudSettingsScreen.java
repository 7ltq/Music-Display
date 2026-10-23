package de.dualhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class HudSettingsScreen extends Screen {
    private final Screen parent;
    private String tab="Appearance";
    private String expanded="";
    private final List<Hit> clickAreas=new ArrayList<>();
    private final List<SliderHit> sliderAreas=new ArrayList<>();
    private SliderHit dragging;
    private final Map<String,Spring> anim=new HashMap<>();
    private long frameNs=System.nanoTime();
    private float dt=.016f;
    private long lastVolumeInteraction;
    private final Map<String,Long> pulseUntil=new HashMap<>();
    private String previewLastKey="";
    private String previewFadeLine="";
    private long previewFadeStartedNs;
    private int px,py,pw,ph,sidebar,cx,cw;
    private boolean compact;

    private record Hit(float x,float y,float w,float h,Runnable run){
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    private record SliderHit(float x,float y,float w,float h,int min,int max,IntConsumer apply){
        boolean contains(double mx,double my){return mx>=x-8&&mx<x+w+8&&my>=y&&my<y+h;}
        void set(double mx){
            float f=Math.max(0f,Math.min(1f,(float)((mx-x)/Math.max(1,w))));
            apply.accept(Math.round(min+f*(max-min)));
        }
    }
    private static final class Spring {
        float value,velocity;
        Spring(float initial){value=initial;}
        float step(float target,float dt){
            int steps=Math.max(1,(int)Math.ceil(dt/.008f));
            float h=dt/steps;
            for(int i=0;i<steps;i++){
                float accel=(target-value)*200f-velocity*25f;
                velocity+=accel*h;value+=velocity*h;
            }
            return value;
        }
    }
    public HudSettingsScreen(Screen parent){super(Component.literal("Music Display"));this.parent=parent;}
    private HudConfig cfg(){return DualMusicHud.config();}
    private float spring(String key,float target){
        Spring a=anim.computeIfAbsent(key,k->new Spring(target));
        if("OFF".equals(cfg().motionPreset)){a.value=target;a.velocity=0;return target;}
        if("SMOOTH".equals(cfg().motionPreset)){
            a.value+=(target-a.value)*(1f-(float)Math.exp(-dt*15f));
            return a.value;
        }
        return a.step(target,dt);
    }
    private void register(float x,float y,float w,float h,Runnable action){clickAreas.add(new Hit(x,y,w,h,action));}
    private void changed(Runnable action){action.run();cfg().save();}
    private void switchTab(String name){if(!tab.equals(name)){tab=name;expanded="";anim.put("openTab",new Spring(0f));}}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        long ns=System.nanoTime();dt=Math.max(.001f,Math.min(.040f,(ns-frameNs)/1_000_000_000f));frameNs=ns;
        clickAreas.clear();sliderAreas.clear();
        pw=Math.min(710,Math.max(285,width-22));ph=Math.min(465,Math.max(255,height-22));
        px=(width-pw)/2;py=(height-ph)/2;
        compact=ph<390;
        sidebar=Math.min(165,Math.max(96,pw/4));
        cx=px+sidebar+17;cw=Math.max(160,pw-sidebar-29);

        float radius=19f;
        boolean glass=cfg().liquidGlass && WalksySettingsGlass.draw(g,px,py,pw,ph,radius,false,false,true,"PANEL");
        if(!glass) {
            round(g,px,py,pw,ph,radius,0xAA202936,0xA91C2635);
        }
        g.fill(px+sidebar+5,py+47,px+sidebar+6,py+ph-21,0x22FFFFFF);
        g.fill(px+14,py+47,px+pw-14,py+48,0x22FFFFFF);
        text(g,"MUSIC DISPLAY",px+19,py+13,16,0xFFF9FBFF,true);
        text(g,"Personalize your player",px+19,py+32,12,0xFFA3B3C5,false);
        button(g,"Done",px+pw-78,py+13,62,24,mx,my,false,()->onClose());

        int navY=py+62,tabStep=compact?29:37;
        String[] tabs={"Appearance","Edit HUD","Lyrics","Animations"};
        for(int i=0;i<tabs.length;i++){
            String name=tabs[i];
            button(g,name,px+12,navY+i*tabStep,sidebar-20,compact?26:33,mx,my,tab.equals(name),()->switchTab(name));
        }
        
        int rescueY = py + ph - (compact ? 119 : 131);
        button(g, cfg().liquidGlass ? "Glass: ON" : "Glass: OFF", px+12, rescueY,
                sidebar-20, compact?24:28, mx, my, cfg().liquidGlass,
                () -> changed(() -> cfg().liquidGlass = !cfg().liquidGlass));
        
        int skinY=py+ph-72;
        text(g,"PLAYER",px+19,skinY-18,11,0xFF92A9C0,false);
        liquidControl(g,px+13,skinY,sidebar-22,48,12,false,false);
        boolean face=WalksyUiBridge.playerFace(g,px+20,skinY+9,30);
        if(!face) text(g,"◈",px+27,skinY+12,18,0xFFD4EBFA,true);
        text(g,ign(),px+58,skinY+13,13,0xFFF2F7FF,true);

        float slide=spring("openTab",1f);
        int top=py+63+(int)((1f-slide)*10f);
        text(g,tab.toUpperCase(),cx,top,13,0xFFFFFFFF,true);
        text(g,"ARTWORK / AUTO",cx+Math.max(140,cw-123),top,10,0xFF8DA5BA,false);
        int y=top+(compact?21:26);
        int step=compact?27:43;
        switch(tab){
            case "Appearance" -> {
                toggle(g,"neutralCards","Neutral glass cards","ON: neutral glass / OFF: artwork colors",cfg().neutralCards,
                    ()->changed(()->cfg().neutralCards=!cfg().neutralCards),cx,y,cw,step,mx,my);y+=step;
                slider(g,"opacity","Background opacity",cfg().glassOpacity,0,100,"%",v->cfg().glassOpacity=v,
                    cx,y,cw,step,mx,my);y+=step;
                select(g,"glassPreset","Glass style",cfg().glassPreset,cx,y,cw,step,mx,my);y+=step;
                slider(g,"blur","Glass blur",cfg().blurStrength,0,100,"%",v->cfg().blurStrength=v,cx,y,cw,step,mx,my);y+=step;
                slider(g,"corner","Corner radius",cfg().cornerRadius,6,24," px",v->cfg().cornerRadius=v,cx,y,cw,step,mx,my);
            }
            case "Edit HUD" -> {
                row(g,"Edit HUD position","Drag and drop","EDIT",cx,y,cw,step,mx,my,
                    ()->minecraft.gui.setScreen(new HudDragScreen(this)));y+=step;
                slider(g,"width","Mini HUD width",cfg().width,240,380," px",v->cfg().width=v,cx,y,cw,step,mx,my);y+=step;
                slider(g,"scale","HUD scale",Math.round(cfg().scale*100f),60,250,"%",v->cfg().scale=v/100f,cx,y,cw,step,mx,my);y+=step;
                slider(g,"corner","Corner radius",cfg().cornerRadius,6,24,"",v->cfg().cornerRadius=v,cx,y,cw,step,mx,my);y+=step;
                slider(g,"miniOpacity","Mini HUD opacity",cfg().miniOpacity,0,100,"%",v->cfg().miniOpacity=v,cx,y,cw,step,mx,my);y+=step;
                row(g,"Reset position","Bottom right","RESET",cx,y,cw,step,mx,my,
                    ()->changed(()->{cfg().anchor="BOTTOM_RIGHT";cfg().x=12;cfg().y=12;}));
            }
            case "Lyrics" -> {
                toggle(g,"karaoke","Karaoke Wave","Smooth word-by-word highlights",cfg().karaokeWave,
                    ()->changed(()->cfg().karaokeWave=!cfg().karaokeWave),cx,y,cw,step,mx,my);y+=step;
                toggle(g,"lyricsFocus","Lyrics Focus","Dim upcoming lines, highlight current lyrics",cfg().lyricsFocus,
                    ()->changed(()->cfg().lyricsFocus=!cfg().lyricsFocus),cx,y,cw,step,mx,my);y+=step;
                toggle(g,"inWorldLyrics","In-World Lyrics","World-space particles (renderer not ready)",cfg().inWorldLyrics,
                    ()->changed(()->cfg().inWorldLyrics=!cfg().inWorldLyrics),cx,y,cw,step,mx,my);y+=step;
                text(g,"Lyrics follow your view; F5 placement is experimental.",cx+8,y+9,11,0xFFA3B3C5,false);
            }
            case "Animations" -> {
                select(g,"motionPreset","Motion style",cfg().motionPreset,cx,y,cw,step,mx,my);y+=step;
                toggle(g,"gradient","Animated album colors","Smooth color transitions",cfg().animatedGradient,
                    ()->changed(()->cfg().animatedGradient=!cfg().animatedGradient),cx,y,cw,step,mx,my);y+=step;

            }

        }
        if(ph>=445){
            int previewY=py+ph-100;
            g.fill(cx,previewY-9,cx+cw,previewY-8,0x32FFFFFF);
            text(g,"LIVE PREVIEW",cx,previewY,11,0xFFA9B9C9,false);
            preview(g,cx,previewY+17,cw,61);
        }
        text(g,"Music Display  /  7ltq x codex",px+18,py+ph-15,11,0xFFA6B6C5,false);
        
        if(!expanded.isBlank())drawDropdown(g,mx,my);
    }

    private int displayVolume(){
        
        if (dragging!=null || System.currentTimeMillis()-lastVolumeInteraction<1400L)
            return cfg().systemVolume;
        int live=VolumeStatus.read();
        if(live>=0)cfg().systemVolume=live;
        return cfg().systemVolume;
    }

    private String ign(){
        try {return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : Minecraft.getInstance().getUser().getName();}
        catch(Exception e){return "7ltq";}
    }
    private int dropX,dropY,dropW;
    private String[] choices(String key){return "motionPreset".equals(key)?new String[]{"SPRING","SMOOTH","OFF"}:new String[]{"CRYSTAL","FROSTED","SOFT"};}
    
    private void liquidControl(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                               boolean active,boolean hover){
        liquidControl(g,x,y,w,h,radius,active,hover,"ROW");
    }
    private void liquidControl(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                               boolean active,boolean hover,String role){
        if(cfg().liquidGlass && WalksySettingsGlass.draw(g,x,y,w,h,radius,active,hover,false,role))return;
        
        int top=active?0xA86A809A:hover?0x90647790:0x79374459;
        int bottom=active?0xA8526886:hover?0x90607790:0x792C384D;
        round(g,x,y,w,h,radius,top,bottom);
    }
    private void liquidPill(GuiGraphicsExtractor g,float x,float y,float w,float h,boolean active,boolean hover){
        liquidControl(g,x,y,w,h,h/2f,active,hover,"BUTTON");
    }
    private void select(GuiGraphicsExtractor g,String key,String label,String chosen,int x,int y,int w,int step,int mx,int my){
        int rh=step-4;
        liquidControl(g,x,y,w,rh,11,false,inside(mx,my,x,y,w,rh));
        liquidPill(g,x+w-128,y+Math.max(3,(rh-24)/2),118,24,false,inside(mx,my,x,y,w,rh));
        text(g,label,x+12,y+7,13,0xFFF1F6FF,false);
        text(g,chosen+"  ˅",x+w-115,y+9,12,0xFFEAF6FF,true);
        register(x,y,w,rh,()->{expanded=expanded.equals(key)?"":key;anim.put("dropdown",new Spring(0f));});
        if(expanded.equals(key)){dropX=x+Math.max(60,w-155);dropY=y+rh+3;dropW=Math.min(148,w-65);}
    }
    private void drawDropdown(GuiGraphicsExtractor g,int mx,int my){
        float openness=spring("dropdown",1f);
        String[] arr=choices(expanded);
        int rh=30,visible=Math.max(0,Math.round(arr.length*rh*openness));
        if(visible<=0)return;
        liquidControl(g,dropX,dropY,dropW,visible,10,false,true);
        String key=expanded;
        for(int i=0;i<arr.length;i++){
            if(i*rh+rh>visible)break;
            String value=arr[i];int yy=dropY+i*rh;
            if(inside(mx,my,dropX,yy,dropW,rh))liquidControl(g,dropX+3,yy+2,dropW-6,rh-4,8,false,true);
            text(g,value,dropX+14,yy+10,12,0xFFEAF4FF,false);
            register(dropX,yy,dropW,rh,()->changed(()->{
                if("motionPreset".equals(key))cfg().motionPreset=value;
                else cfg().glassPreset=value;
                expanded="";
            }));
        }
    }
    private void toggle(GuiGraphicsExtractor g,String key,String title,String sub,boolean value,Runnable callback,
                        int x,int y,int w,int step,int mx,int my){
        int rh=step-4;
        liquidControl(g,x,y,w,rh,11,value,inside(mx,my,x,y,w,rh));
        text(g,title,x+12,y+6,13,0xFFF7F9FF,false);
        if(!compact)text(g,sub,x+12,y+22,11,0xFF9FB0C3,false);
        float trackW=39,trackH=18, tx=x+w-54,ty=y+(rh-trackH)/2f;
        float pos=spring("toggle:"+key,value?1f:0f);
        float color=spring("toggleColor:"+key,value?1f:0f);
        liquidControl(g,tx,ty,trackW,trackH,trackH/2f,value,inside(mx,my,x,y,w,rh),"TRACK");
        boolean pressed=System.nanoTime()<pulseUntil.getOrDefault(key,0L);
        float pressure=spring("press:"+key,pressed?1f:0f);
        float knobW=14+pressure*5;
        float kx=tx+2+(trackW-4-14)*Math.max(0f,Math.min(1f,pos));
        liquidControl(g,kx-knobW/2f+7,ty+2,knobW,14,7,true,false,"KNOB");
        
        register(x,y,w,rh,()->{pulseUntil.put(key,System.nanoTime()+140_000_000L);callback.run();});
    }
    private void slider(GuiGraphicsExtractor g,String key,String title,int value,int min,int max,String suffix,
                        IntConsumer apply,int x,int y,int w,int step,int mx,int my){
        int rh=step-4;
        liquidControl(g,x,y,w,rh,11,false,inside(mx,my,x,y,w,rh));
        text(g,title,x+12,y+9,13,0xFFF2F7FF,false);
        int usable=Math.min(188,Math.max(40,w-190));
        int sx=x+w-usable-56, sy=y+(rh-5)/2;
        float target=(value-min)/(float)Math.max(1,max-min);
        float smooth=spring("slider:"+key,target);
        float pressed=spring("sliderPress:"+key,dragging!=null&&dragging.y()==y?1f:0f);
        liquidControl(g,sx,sy-1,usable,7,3.5f,false,false,"TRACK");
        
        float fillW=usable*Math.max(0f,Math.min(1f,smooth));
        if(fillW>3f)liquidControl(g,sx,sy-1,fillW,7,3.5f,true,false,"FILL");
        float hx=sx+usable*smooth;
        liquidControl(g,hx-7-pressed*2,sy-6-pressed*2,14+pressed*4,17+pressed*4,8.5f,true,pressed>.1f,"KNOB");
        
        String val=value+suffix;
        text(g,val,x+w-48,y+9,12,0xFFEFF7FF,true);
        sliderAreas.add(new SliderHit(sx,y,usable,rh,min,max,apply));
    }
    private void row(GuiGraphicsExtractor g,String title,String subtitle,String action,int x,int y,int w,int step,
                     int mx,int my,Runnable callback){
        int h=step-4;
        boolean chosen="SELECTED".equals(action);
        liquidControl(g,x,y,w,h,11,chosen,inside(mx,my,x,y,w,h));
        text(g,title,x+12,y+6,13,0xFFFAFCFF,false);
        if(!compact)text(g,subtitle,x+12,y+23,11,0xFF9BB1C1,false);
        float actW=WalksyUiBridge.width(action,12f,true);
        if(actW<0)actW=font.width(action);
        text(g,action,x+w-12-actW,y+10,12,chosen?UiTheme.accent(cfg()):0xFFCDE8F9,true);
        register(x,y,w,h,callback);
    }
    private void button(GuiGraphicsExtractor g,String text,int x,int y,int w,int h,int mx,int my,boolean selected,Runnable callback){
        boolean hover=inside(mx,my,x,y,w,h);
        float strength=spring("button:"+text,selected?1f:0f);
        int fill=blend(hover?UiTheme.cardHover(cfg()):UiTheme.cardTop(cfg()),UiTheme.accent(cfg()),strength);
        liquidPill(g,x,y,w,h,selected,hover);
        int color=0xFFF2F7FF;
        float textW=WalksyUiBridge.width(text,12.5f,selected);
        if(textW<0)textW=font.width(text);
        text(g,text,x+(w-textW)/2f,y+(h-13)/2f,12.5f,color,selected);
        register(x,y,w,h,callback);
    }
    private void preview(GuiGraphicsExtractor g,int x,int y,int w,int h){
        Minecraft mc=Minecraft.getInstance();
        MediaBridge.Track track=MediaBridge.get(cfg());
        if(track!=null)LyricsService.update(track);
        AlbumPalette palette=track!=null?ArtworkTexture.palette(mc,AlbumPalette.DEMO):null;
        int accent=palette!=null?palette.accent():0xFFF4F6FB;
        g.pose().pushMatrix();
        g.pose().translate(x,y);
        if(palette!=null) CloudBackdrop.drawCard(g,mc,palette,w,h,Math.min(28,Math.max(10,cfg().cornerRadius+6)),255,true);
        else round(g,0,0,w,h,Math.min(28,Math.max(10,cfg().cornerRadius+6)),0xFF252B33,0xFF252B33);
        if(cfg().lightBorder) g.fill(Math.min(16,w/12),1,w-Math.min(16,w/12),2,AlbumPalette.alpha(accent,42));
        int art=h-14;
        if(palette!=null){
            round(g,7,7,art,art,10,AlbumPalette.alpha(palette.primary(),190),AlbumPalette.alpha(palette.secondary(),190));
        } else {
            round(g,7,7,art,art,10,0xFF34455C,0xFF223447);
        }
        if(track!=null&&ArtworkTexture.available(mc))ArtworkTexture.draw(g,mc,7,7,art);
        int tx=art+17,contentW=Math.max(50,w-art-30);
        String title=track==null?"No track playing":track.title();
        String artist=track==null?"Start Spotify or Apple Music":track.artist();
        text(g,truncate(title,contentW),tx,5,12,0xFFFFFFFF,true);
        text(g,truncate(artist,contentW),tx,20,11,0xFFC3D0E0,false);

        long ms=track==null?0:Math.max(0,track.positionMs()+cfg().lyricOffsetMs);
        LyricsService.TimedLine lyric=track==null?null:LyricsService.timedLine(ms,track.durationMs());
        boolean singing=lyric!=null && LyricsService.vocalActive(lyric,ms);
        String activeKey=track==null?"":track.title()+"|"+track.artist()+"|"+(singing?lyric.startMs():-1);
        if(singing && !activeKey.equals(previewLastKey)){
            if(!previewLastKey.isBlank() && !previewLastKey.endsWith("|-1") && lyric!=null) previewFadeLine=lyric.text();
            previewFadeStartedNs=System.nanoTime();
            previewLastKey=activeKey;
        }
        if(!singing && !previewLastKey.endsWith("|-1")){
            if(lyric!=null && !lyric.text().isBlank()) previewFadeLine=lyric.text();
            previewFadeStartedNs=System.nanoTime();
            previewLastKey=track==null?"":"idle|-1";
        }
        if (singing) {
            String line=truncate(lyric.text(),contentW);
            float frontier=cfg().karaokeWave ?
                (lyric.wordTimes().isEmpty()?line.length()*LyricsService.highlightFraction(lyric,ms)
                    :LyricsService.frontierForWords(lyric,ms,line)) : 0f;
            lyricPreview(g,line,tx,h-18,frontier,contentW,accent);
        } else if(track==null) {
            String demo="Music Display lyrics preview";
            float phase=(System.nanoTime()/1_000_000_000f)%5.0f;
            lyricPreview(g,demo,tx,h-18,cfg().karaokeWave?demo.length()*Math.min(1f,phase/4.1f):0f,contentW,0xFF7CF2A0);
        }
        if(!previewFadeLine.isBlank()){
            float fraction=Math.max(0f,1f-(System.nanoTime()-previewFadeStartedNs)/(650f*1_000_000f));
            fraction=fraction*fraction*(3f-2f*fraction);
            if(fraction<=0f) previewFadeLine="";
            else {
                int alpha=Math.max(0,Math.min(255,Math.round(180*fraction)));
                int color=(alpha<<24)|(accent&0x00FFFFFF);
                String fade=truncate(previewFadeLine,contentW);
                text(g,fade,tx,h-18-(1f-fraction)*8f,11,color,false);
            }
        }
        if(track!=null && !singing){
            var upcoming=LyricsService.upcoming(ms,track.durationMs(),2);
            long nextStart=Long.MAX_VALUE;
            for(var line:upcoming){ if(line.startMs()>ms){ nextStart=line.startMs(); break; } }
            boolean likelyInstrumental = upcoming.isEmpty() || nextStart==Long.MAX_VALUE || nextStart-ms>2200;
            if(likelyInstrumental && previewFadeLine.isBlank()) DualMusicHud.drawIdleNote(g,tx+1,h-24,AlbumPalette.mix(0xFFFFFFFF,accent,0.18f));
        }
        g.pose().popMatrix();
    }
    private void lyricPreview(GuiGraphicsExtractor g,String line,int x,int y,float frontier,int maxWidth,int accent){
        String clipped=truncate(line,maxWidth);
        float advance=0;
        for(int i=0;i<clipped.length();i++){
            String glyph=clipped.substring(i,i+1);
            float passed=Math.max(0f,Math.min(1f,frontier-i));
            float smooth=passed*passed*(3-2*passed);
            int tint=cfg().karaokeWave?blend(0xFFBBC5D0,0xFFFFFFFF,smooth):0xFFFFFFFF;
            float bump=cfg().karaokeWave?(float)Math.exp(-Math.pow(frontier-(i+.5f),2)/3.5f):0f;
            text(g,glyph,x+advance,y-.9f*bump,11,tint,false);
            float w=WalksyUiBridge.width(glyph,11,false);
            advance+=w>=0?w:font.width(glyph);
        }
    }
    private String truncate(String s,int max){
        if(s==null)return "";
        if(WalksyUiBridge.width(s,11,false)<max)return s;
        String word=s;
        while(word.length()>1&&WalksyUiBridge.width(word+"…",11,false)>max)word=word.substring(0,word.length()-1);
        return word+"…";
    }
    private void round(GuiGraphicsExtractor g,float x,float y,float w,float h,float r,int top,int bottom){
        if(w<=0||h<=0)return;
        if(WalksyUiBridge.rounded(g,x,y,w,h,r,top,bottom))return;
        g.fill((int)x,(int)y,(int)(x+w),(int)(y+h),top);
    }
    private static int blend(int a,int b,float t){return AlbumPalette.mix(a,b,Math.max(0f,Math.min(1f,t)));}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void text(GuiGraphicsExtractor g,String s,float x,float y,float size,int color,boolean bold){
        if(!WalksyUiBridge.text(g,s,x,y,size,color,bold))g.text(font,s,(int)x,(int)y,color,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(e.button()!=1)return super.mouseClicked(e,dbl);
        for(int i=clickAreas.size()-1;i>=0;i--){Hit hit=clickAreas.get(i);
            if(hit.contains(e.x(),e.y())){hit.run().run();return true;}
        }
        for(int i=sliderAreas.size()-1;i>=0;i--){SliderHit slider=sliderAreas.get(i);
            if(slider.contains(e.x(),e.y())){dragging=slider;dragging.set(e.x());return true;}
        }
        DiagnosticLog.record("SETTINGS_UNHANDLED_CLICK x="+e.x()+" y="+e.y()+" tab="+tab);
        return super.mouseClicked(e,dbl);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragging!=null){dragging.set(e.x());return true;}
        return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragging!=null){dragging.set(e.x());dragging=null;cfg().save();return true;}
        return super.mouseReleased(e);
    }
    @Override public void onClose(){cfg().save();minecraft.gui.setScreen(parent);}
}
