package de.dualhud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class LyricsService {
    public record Line(long ms,String text,List<Long> wordTimes) {}
    private static final Pattern WORD=Pattern.compile("<(\\d{2,3}):(\\d{2})(?:\\.(\\d{1,3}))?>");
    private static final Pattern LRC=Pattern.compile("\\[(\\d{2,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)");
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"DualMusicHUD-Lyrics");t.setDaemon(true);return t;});
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static volatile List<Line> lines=List.of();
    private static volatile String current="";
    private static volatile String requested="";
    private static volatile long retryAfterMs=0L;
    private LyricsService(){}
    public static void update(MediaBridge.Track track){
        if(track==null || track.title()==null || track.title().isBlank()){lines=List.of();current="";return;}
        String id=track.title()+"\u0001"+track.artist()+"\u0001"+track.durationMs();
        
        
        long now=System.currentTimeMillis();
        if(id.equals(current) && (!lines.isEmpty() || now<retryAfterMs))return;
        current=id;
        if(!id.equals(requested))lines=List.of();
        requested=id;
        retryAfterMs=now+60_000L;
        EXEC.submit(()->fetch(track,id));
    }
    public static boolean hasSyncedLyrics(){return !lines.isEmpty();}
    public static String currentLine(long ms){
        List<Line> all=lines;
        int lo=0,hi=all.size()-1,found=-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;if(all.get(mid).ms()<=ms){found=mid;lo=mid+1;}else hi=mid-1;}
        return found<0?"":all.get(found).text();
    }
    
    public static String nextLine(long ms) {
        List<Line> all=lines;
        int lo=0, hi=all.size()-1, next=all.size();
        while(lo<=hi){int mid=(lo+hi)>>>1;if(all.get(mid).ms()>ms){next=mid;hi=mid-1;}else lo=mid+1;}
        return next<all.size()?all.get(next).text():"";
    }
    
    public static java.util.List<TimedLine> upcoming(long ms,long durationMs,int limit) {
        java.util.List<Line> all=lines;
        if(all.isEmpty() || limit<=0)return java.util.List.of();
        int found=-1,lo=0,hi=all.size()-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;
            if(all.get(mid).ms()<=ms){found=mid;lo=mid+1;} else hi=mid-1;}
        int start=Math.max(0,found);
        java.util.List<TimedLine> result=new java.util.ArrayList<>();
        for(int i=start;i<all.size()&&result.size()<limit;i++){
            Line line=all.get(i);
            long end=i+1<all.size()?all.get(i+1).ms():durationMs;
            if(end<=line.ms())end=line.ms()+3500;
            if(end>ms)result.add(new TimedLine(line.text(),line.ms(),end,line.wordTimes()));
        }
        return result;
    }
    
    public record TimedLine(String text, long startMs, long endMs, List<Long> wordTimes) {}
    public static TimedLine timedLine(long ms, long durationMs) {
        List<Line> all = lines;
        int lo=0,hi=all.size()-1,found=-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;if(all.get(mid).ms()<=ms){found=mid;lo=mid+1;}else hi=mid-1;}
        if(found<0)return null;
        Line line=all.get(found);
        long end=found+1<all.size()?all.get(found+1).ms():durationMs;
        if(end<=line.ms())end=line.ms()+3500;
        return new TimedLine(line.text(),line.ms(),end,line.wordTimes());
    }
    private static void fetch(MediaBridge.Track track,String id){
        try{
            JsonObject data=lookupLyrics(track.title(),track.artist(),track.durationMs(),track.album());
            if(!id.equals(current))return;
            if(data==null){DiagnosticLog.record("LYRICS_NOT_FOUND "+track.title());return;}
            List<Line> parsed=new ArrayList<>();
            for(String line:data.get("syncedLyrics").getAsString().split("\\R")){
                Matcher m=LRC.matcher(line.strip());
                if(!m.matches())continue;
                long fraction=0;
                if(m.group(3)!=null){String s=m.group(3);fraction=Long.parseLong(s)*(s.length()==1?100:s.length()==2?10:1);}
                long ms=(Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*1000+fraction;
                String raw=m.group(4);
                String text=WORD.matcher(raw).replaceAll("").strip().replaceAll("\\s+"," ");
                if(!text.isBlank())parsed.add(new Line(ms,text,parseWordTimes(raw,text)));
            }
            parsed.sort(java.util.Comparator.comparingLong(Line::ms));
            if(id.equals(current)){lines=List.copyOf(parsed);DiagnosticLog.record("LYRICS_LINES="+parsed.size());}
        }catch(Exception ex){DiagnosticLog.record("LYRICS_ERROR "+ex.getClass().getSimpleName()+" "+ex.getMessage());}
    }
    
    private static JsonObject lookupLyrics(String title,String artist,long durationMs,String album) throws Exception {
        String base="https://lrclib.net/api/get?track_name="+enc(title)+"&artist_name="+enc(artist);
        String exact=base;
        if(album!=null&&!album.isBlank())exact+="&album_name="+enc(album);
        if(durationMs>0)exact+="&duration="+(durationMs/1000);
        JsonObject result=fetchOne(exact);
        if(hasLyrics(result))return result;
        result=fetchOne(base);
        if(hasLyrics(result))return result;
        String search="https://lrclib.net/api/search?track_name="+enc(title)+"&artist_name="+enc(artist);
        HttpRequest request=HttpRequest.newBuilder(URI.create(search))
            .header("User-Agent","MusicDisplay/1.0 (Minecraft Fabric)")
            .timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)return null;
        var results=JsonParser.parseString(response.body()).getAsJsonArray();
        String normalizedTitle=normalize(title);
        String normalizedArtist=normalize(artist);
        JsonObject best=null;
        long bestDiff=Long.MAX_VALUE;
        for(var element:results){
            if(!element.isJsonObject())continue;
            JsonObject entry=element.getAsJsonObject();
            if(!hasLyrics(entry))continue;
            String candidateTitle=entry.has("trackName")?entry.get("trackName").getAsString():"";
            String candidateArtist=entry.has("artistName")?entry.get("artistName").getAsString():"";
            if(!normalize(candidateTitle).equals(normalizedTitle))continue;
            String candidateArtists=normalize(candidateArtist);
            if(!normalizedArtist.isBlank() && !candidateArtists.contains(normalizedArtist)
               && !normalizedArtist.contains(candidateArtists))continue;
            long difference=0;
            if(durationMs>0 && entry.has("duration")&&!entry.get("duration").isJsonNull()){
                difference=Math.abs(durationMs/1000-entry.get("duration").getAsLong());
                if(difference>18)continue;
            }
            if(difference<bestDiff){best=entry;bestDiff=difference;}
        }
        return best;
    }
    private static JsonObject fetchOne(String url) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent","MusicDisplay/1.0 (Minecraft Fabric)")
            .timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)return null;
        var json=JsonParser.parseString(response.body());
        return json.isJsonObject()?json.getAsJsonObject():null;
    }
    private static boolean hasLyrics(JsonObject obj){
        return obj!=null && obj.has("syncedLyrics")&&!obj.get("syncedLyrics").isJsonNull()
            && !obj.get("syncedLyrics").getAsString().isBlank();
    }
    private static String normalize(String text){
        return text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\([^)]*\\)","")
            .replaceAll("\\[[^]]*\\]","").replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    
    private static List<Long> parseWordTimes(String raw, String clean) {
        Matcher m=WORD.matcher(raw);
        List<Long> starts=new ArrayList<>();
        while(m.find()) {
            String fraction=m.group(3);
            long part=fraction==null?0:Long.parseLong(fraction)*(fraction.length()==1?100:fraction.length()==2?10:1);
            starts.add((Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*1000+part);
        }
        int words=clean.split(" ").length;
        return starts.size()==words?List.copyOf(starts):List.of();
    }

    public static long vocalEndMs(TimedLine line) {
        if(line==null)return 0;
        long start=line.startMs();
        long end=Math.max(start+200,line.endMs());
        if(line.wordTimes()!=null && !line.wordTimes().isEmpty()) {
            long last=line.wordTimes().get(line.wordTimes().size()-1);
            int lastChars=1;
            String text=line.text()==null?"":line.text().strip();
            int space=text.lastIndexOf(' ');
            if(!text.isEmpty())lastChars=text.length()-space-1;
            long estimated=last+Math.max(240,Math.min(1250,150+lastChars*95L));
            return Math.min(end,Math.max(start+200,estimated));
        }
        long gap=end-start;
        if(gap<=9000) return Math.max(start+200,end-Math.min(170,gap/16));
        int chars=line.text()==null?0:line.text().length();
        long maxVocal=Math.max(4200,Math.min(8400,1000L+chars*165L));
        return Math.min(end-250,start+maxVocal);
    }
    public static boolean vocalActive(TimedLine line,long playbackMs) {
        return line!=null && playbackMs>=line.startMs() && playbackMs<vocalEndMs(line);
    }

    
    public static float highlightFraction(TimedLine line,long playbackMs) {
        if(line==null || line.text()==null || line.text().isBlank())return 0f;
        if(playbackMs<=line.startMs())return 0f;
        if(playbackMs>=vocalEndMs(line))return 1f;
        if(line.wordTimes()!=null && !line.wordTimes().isEmpty()) {
            return Math.max(0f,Math.min(1f,frontierForWords(line,playbackMs,line.text())/Math.max(1f,line.text().length())));
        }
        long length=Math.max(200L,vocalEndMs(line)-line.startMs());
        return Math.max(0f,Math.min(1f,(playbackMs-line.startMs())/(float)length));
    }

    public static float frontierForWords(TimedLine line,long ms,String renderedText) {
        if(line==null || line.wordTimes()==null || line.wordTimes().isEmpty())return 0f;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\S+").matcher(line.text());
        java.util.List<int[]> spans=new java.util.ArrayList<>();
        while(matcher.find())spans.add(new int[]{matcher.start(),matcher.end()});
        if(spans.size()!=line.wordTimes().size()){
            float t=highlightFractionWithoutWords(line,ms);
            return Math.max(0f,Math.min(renderedText.length(),t*renderedText.length()));
        }
        float frontier=0f;
        for(int i=0;i<spans.size();i++){
            int fromChar=spans.get(i)[0],toChar=spans.get(i)[1];
            long from=line.wordTimes().get(i);
            long next=i+1<spans.size()?line.wordTimes().get(i+1):line.endMs();
            if(ms<from)break;
            int len=toChar-fromChar;
            long expected=Math.max(230,Math.min(1350,150L+len*100L));
            long end=Math.max(from+1,Math.min(next,from+expected));
            float fraction=Math.max(0f,Math.min(1f,(ms-from)/(float)Math.max(1L,end-from)));
            
            frontier=fromChar+(toChar-fromChar)*fraction;
            if(fraction>=1f)frontier=toChar;
            if(ms<next)break;
            if(i+1<spans.size())frontier=spans.get(i+1)[0];
        }
        return Math.min(renderedText.length(),Math.max(0f,frontier));
    }
    private static float highlightFractionWithoutWords(TimedLine line,long playbackMs){
        long length=Math.max(200L,vocalEndMs(line)-line.startMs());
        return Math.max(0f,Math.min(1f,(playbackMs-line.startMs())/(float)length));
    }

    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
