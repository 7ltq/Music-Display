using NAudio.CoreAudioApi;
using System.Text.Json;
using Windows.Media.Control;
using Windows.Storage.Streams;


var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DualMusicHUD");
Directory.CreateDirectory(dir);
var output = args.Length > 0 ? Path.GetFullPath(args[0]) : Path.Combine(dir, "media.json");
var commandFile = Path.Combine(dir, "command.txt");
var commandQueue = Path.Combine(dir, "commands");
var heartbeatFile = Path.Combine(dir, "bridge-queue-heartbeat.txt");
Directory.CreateDirectory(commandQueue);

using var instanceMutex = new System.Threading.Mutex(true, "Local\\MusicDisplay_Bridge_26_3", out bool isFirstBridge);
if (!isFirstBridge) {
    Console.Error.WriteLine("Music Display: another media bridge is already running.");
    return;
}
bool repeatEnabled = false;
string lastRepeatedTrack = "";
var logDir = Path.Combine(dir, "logs");
Directory.CreateDirectory(logDir);
var logFile = Path.Combine(logDir, "windows-media.log");
var lastStatus = "";
string lastArtworkKey = "";
var lastStatusAt = DateTime.MinValue;
var lastVolumeWriteAt = DateTime.MinValue;
void Log(string message, bool force = false)
{
    if (!force && message == lastStatus && (DateTime.UtcNow - lastStatusAt).TotalSeconds < 15) return;
    lastStatus = message; lastStatusAt = DateTime.UtcNow;
    var line = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff") + " " + message;
    Console.WriteLine(line);
    try
    {
        if (File.Exists(logFile) && new FileInfo(logFile).Length > 3_000_000)
            File.Move(logFile, Path.Combine(logDir, "windows-media.previous.log"), true);
        File.AppendAllText(logFile, line + Environment.NewLine);
    }
    catch (Exception e) { Console.Error.WriteLine("Could not write log: " + e.Message); }
}

Log("BRIDGE_START output=" + output + " log=" + logFile, true);

GlobalSystemMediaTransportControlsSessionManager manager;
try
{
    manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
    Log("GSMTC_INITIALIZED", true);
}
catch (Exception e)
{
    Log("GSMTC_INITIALIZATION_FAILED " + e, true);
    return;
}

var scFile = Path.Combine(dir, "soundcloud-local.json");
var scCommandFile = Path.Combine(dir, "soundcloud-command.txt");
_ = Task.Run(async () => {
    using var listener = new System.Net.HttpListener();
    listener.Prefixes.Add("http://127.0.0.1:43871/");
    try { listener.Start(); Log("SOUNDCLOUD_LOCAL_LISTENER_READY port=43871", true); }
    catch (Exception e) { Log("SOUNDCLOUD_LOCAL_LISTENER_FAILED " + e.Message, true); return; }
    while (listener.IsListening) {
        try {
            var ctx = await listener.GetContextAsync();
            _ = Task.Run(async () => {
                try {
                    var req = ctx.Request; var res = ctx.Response;
                    var origin = req.Headers["Origin"] ?? "";
                    
                    var accepted = origin.StartsWith("moz-extension://", StringComparison.OrdinalIgnoreCase) &&
                                   Uri.TryCreate(origin, UriKind.Absolute, out var originUri) &&
                                   originUri.Scheme == "moz-extension" && originUri.Host.Length > 0;
                    if (!accepted || req.Headers["Host"] != "127.0.0.1:43871") { res.StatusCode = 403; res.Close(); return; }
                    res.Headers.Add("Access-Control-Allow-Origin", origin);
                    res.Headers.Add("Vary", "Origin");
                    res.Headers.Add("Access-Control-Allow-Methods", "POST, OPTIONS");
                    res.Headers.Add("Access-Control-Allow-Headers", "Content-Type, X-Music-Display");
                    if (req.HttpMethod == "OPTIONS") { res.StatusCode = 204; res.Close(); return; }
                    if (req.HttpMethod != "POST" || req.Headers["X-Music-Display"] != "soundcloud-waterfox-v1" || req.ContentLength64 > 8192) { res.StatusCode = 400; res.Close(); return; }
                    using var sr = new StreamReader(req.InputStream);
                    var json = await sr.ReadToEndAsync();
                    using var doc = JsonDocument.Parse(json);
                    if (!doc.RootElement.TryGetProperty("title", out var titleEl) || titleEl.GetString() is not { Length: > 0 and < 241 }) { res.StatusCode = 400; res.Close(); return; }
                    var temp = scFile + ".tmp";
                    
                    using var payload = JsonDocument.Parse(json);
                    var obj = new Dictionary<string, object?> {
                        ["provider"]="SoundCloud", ["title"]=titleEl.GetString(),
                        ["artist"]=payload.RootElement.TryGetProperty("artist", out var ar) ? ar.GetString() : "",
                        ["album"]="",
                        ["positionMs"]=payload.RootElement.TryGetProperty("positionMs", out var pos) && pos.TryGetInt64(out var p) ? Math.Clamp(p,0,100000000) : 0,
                        ["durationMs"]=payload.RootElement.TryGetProperty("durationMs", out var dur) && dur.TryGetInt64(out var d) ? Math.Clamp(d,0,100000000) : 0,
                        ["playing"]=payload.RootElement.TryGetProperty("playing", out var play) && play.ValueKind == JsonValueKind.True,
                        ["artworkUrl"]=payload.RootElement.TryGetProperty("artworkUrl", out var cover) ? cover.GetString() : "",
                        ["receivedUtcMs"]=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                    };
                    await File.WriteAllTextAsync(temp, JsonSerializer.Serialize(obj));
                    File.Move(temp, scFile, true);
                    var cmd = File.Exists(scCommandFile) ? (await File.ReadAllTextAsync(scCommandFile)).Trim() : "";
                    if (File.Exists(scCommandFile)) File.Delete(scCommandFile);
                    res.ContentType = "application/json";
                    var bytes = System.Text.Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new {command=cmd}));
                    res.ContentLength64 = bytes.Length;
                    await res.OutputStream.WriteAsync(bytes);
                    res.Close();
                } catch (Exception e) { Log("SOUNDCLOUD_LOCAL_REQUEST_ERROR "+e.Message); try { ctx.Response.StatusCode=500;ctx.Response.Close(); }catch{} }
            });
        } catch(Exception e) { Log("SOUNDCLOUD_LOCAL_ACCEPT_ERROR " + e.Message); }
    }
});

while (true)
{
    try
    {
        
        
        File.WriteAllText(heartbeatFile,DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString());
        var sessions = manager.GetSessions();
        Log("SESSION_COUNT=" + sessions.Count);
        object? selected = null;
        GlobalSystemMediaTransportControlsSession? selectedSession = null;
        foreach (var session in sessions)
        {
            string source = session.SourceAppUserModelId ?? "";
            try
            {
                var media = await session.TryGetMediaPropertiesAsync();
                if (string.IsNullOrWhiteSpace(media?.Title))
                {
                    Log("SESSION_REJECT_NO_TITLE app=" + source);
                    continue;
                }
                string provider = SourceName(source, media.Title);
                var playback = session.GetPlaybackInfo();
                bool playing = playback.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
                Log("SESSION app=" + source + " provider=" + provider + " playing=" + playing + " title=" + Safe(media.Title));
                if (provider == "Other")
                {
                    Log("SESSION_REJECT_UNKNOWN_PROVIDER app=" + source + " (browser tabs may only expose Firefox/Chrome/Edge)");
                    continue;
                }
                var timeline = session.GetTimelineProperties();
                var artworkKey = source + "|" + media.Title + "|" + media.Artist;
                if (artworkKey != lastArtworkKey)
                {
                    lastArtworkKey = artworkKey;
                    try
                    {
                        var artPath = Path.Combine(dir, "artwork.bin");
                        if (media.Thumbnail is not null)
                        {
                            using var stream = await media.Thumbnail.OpenReadAsync();
                            if (stream.Size > 0 && stream.Size < 4_000_000)
                            {
                                using var reader = new DataReader(stream);
                                await reader.LoadAsync((uint)stream.Size);
                                var data = new byte[(int)stream.Size];
                                reader.ReadBytes(data);
                                await File.WriteAllBytesAsync(artPath + ".tmp", data);
                                File.Move(artPath + ".tmp", artPath, true);
                                Log("ARTWORK_WRITTEN bytes=" + data.Length + " path=" + artPath, true);
                            }
                            else { if (File.Exists(artPath)) File.Delete(artPath); Log("ARTWORK_UNAVAILABLE thumbnail empty", true); }
                        }
                        else { if (File.Exists(artPath)) File.Delete(artPath); Log("ARTWORK_UNAVAILABLE media thumbnail missing", true); }
                    }
                    catch (Exception e) { Log("ARTWORK_ERROR " + e.Message, true); }
                }
                var candidate = new {
                    provider,
                    title = media.Title ?? "",
                    artist = media.Artist ?? "",
                    album = media.AlbumTitle ?? "",
                    positionMs = Math.Max(0, (long)timeline.Position.TotalMilliseconds),
                    durationMs = Math.Max(0, (long)(timeline.EndTime - timeline.StartTime).TotalMilliseconds),
                    playing
                };
                if (selected == null || playing) { selected = candidate; selectedSession = session; }
                if (playing) break;
            }
            catch (Exception e) { Log("SESSION_ERROR app=" + source + " " + e.GetType().Name + ": " + e.Message, true); }
        }
        bool localSoundCloudSelected = false;
        if (File.Exists(scFile)) {
            try {
                using var localDoc = JsonDocument.Parse(await File.ReadAllTextAsync(scFile));
                var r = localDoc.RootElement;
                long received = r.GetProperty("receivedUtcMs").GetInt64();
                bool fresh = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - received < 3200;
                bool active = r.GetProperty("playing").GetBoolean();
                if (fresh && (active || selected == null)) {
                    selected = new {
                        provider="SoundCloud",
                        title=r.GetProperty("title").GetString() ?? "",
                        artist=r.GetProperty("artist").GetString() ?? "",
                        album="",
                        positionMs=r.GetProperty("positionMs").GetInt64(),
                        durationMs=r.GetProperty("durationMs").GetInt64(),
                        playing=active
                    };
                    selectedSession=null;
                    localSoundCloudSelected=true;
                    var url = r.GetProperty("artworkUrl").GetString() ?? "";
                    var artKey = "SoundCloud-Local|"+r.GetProperty("title").GetString()+"|"+url;
                    if (artKey != lastArtworkKey) {
                        lastArtworkKey=artKey;
                        var artPath=Path.Combine(dir,"artwork.bin");
                        try {
                            if (Uri.TryCreate(url,UriKind.Absolute,out var artUri) && artUri.Scheme == "https" &&
                                (artUri.Host.Equals("sndcdn.com",StringComparison.OrdinalIgnoreCase) || artUri.Host.EndsWith(".sndcdn.com",StringComparison.OrdinalIgnoreCase))) {
                                using var http = new HttpClient(){Timeout=TimeSpan.FromSeconds(4)};
                                var data = await http.GetByteArrayAsync(artUri);
                                if(data.Length>0 && data.Length<4_000_000){await File.WriteAllBytesAsync(artPath+".tmp",data);File.Move(artPath+".tmp",artPath,true);}
                            } else if(File.Exists(artPath)) File.Delete(artPath);
                        } catch(Exception e) {Log("SOUNDCLOUD_ART_ERROR "+e.Message);}
                    }
                    Log("SOUNDCLOUD_LOCAL_SELECTED title="+r.GetProperty("title").GetString());
                }
            } catch(Exception e){Log("SOUNDCLOUD_LOCAL_READ_ERROR "+e.Message);}
        }
        
        
        var pending = Directory.GetFiles(commandQueue,"*.txt").OrderBy(x=>x,StringComparer.Ordinal).Take(40).ToList();
        if(File.Exists(commandFile))pending.Add(commandFile);
        foreach(var commandPath in pending) {
            try {
                var command=(await File.ReadAllTextAsync(commandPath)).Trim().ToLowerInvariant();
                File.Delete(commandPath);
                if(command.StartsWith("volume:") && int.TryParse(command[7..],out int requestedVolume)) {
                    using var devices = new MMDeviceEnumerator();
                    using var endpoint=devices.GetDefaultAudioEndpoint(DataFlow.Render,Role.Multimedia);
                    endpoint.AudioEndpointVolume.MasterVolumeLevelScalar=Math.Clamp(requestedVolume,0,100)/100f;
                    Log("VOLUME_SET="+requestedVolume,true);
                } else if(command=="repeat:on") {repeatEnabled=true;Log("REPEAT_ENABLED",true);}
                else if(command=="repeat:off") {repeatEnabled=false;Log("REPEAT_DISABLED",true);}
                else if(localSoundCloudSelected) {
                    if(command is "toggle" or "next" or "previous") await File.WriteAllTextAsync(scCommandFile,command);
                    else Log("SOUNDCLOUD_UNSUPPORTED_COMMAND="+command,true);
                } else if(selectedSession!=null) {
                    if(command.StartsWith("seek:") && long.TryParse(command[5..],out long seekMs)) {
                        var timeline=selectedSession.GetTimelineProperties();
                        long lengthMs=Math.Max(0,(long)(timeline.EndTime-timeline.StartTime).TotalMilliseconds);
                        if(lengthMs>0){
                            bool ok=await selectedSession.TryChangePlaybackPositionAsync(TimeSpan.FromMilliseconds(Math.Clamp(seekMs,0,lengthMs)).Ticks);
                            Log("SEEK_REQUEST_MS="+seekMs+" succeeded="+ok,true);
                        } else Log("SEEK_UNAVAILABLE_NO_DURATION",true);
                    } else switch(command) {
                        case "toggle": await selectedSession.TryTogglePlayPauseAsync();break;
                        case "next": await selectedSession.TrySkipNextAsync();break;
                        case "previous": await selectedSession.TrySkipPreviousAsync();break;
                        case "stop": await selectedSession.TryPauseAsync();break;
                    }
                } else Log("MEDIA_COMMAND_NO_ACTIVE_SESSION="+command,true);
            }catch(Exception e){Log("COMMAND_ERROR "+e.Message,true);}
        }
        
        if(repeatEnabled && selectedSession!=null && selected!=null) {
            var timeline=selectedSession.GetTimelineProperties();
            var lengthMs=(long)(timeline.EndTime-timeline.StartTime).TotalMilliseconds;
            var positionMs=(long)timeline.Position.TotalMilliseconds;
            var identity=selectedSession.SourceAppUserModelId+"|"+lengthMs;
            if(lengthMs>1000 && positionMs>=lengthMs-200 && lastRepeatedTrack!=identity){
                bool ok=await selectedSession.TryChangePlaybackPositionAsync(0);
                if(ok)await selectedSession.TryPlayAsync();
                Log("REPEAT_ATTEMPT success="+ok,true);
                if(ok)lastRepeatedTrack=identity;
            }
            if(positionMs<lengthMs-2000)lastRepeatedTrack="";
        }
        if ((DateTime.UtcNow - lastVolumeWriteAt).TotalMilliseconds > 600)
        {
            lastVolumeWriteAt = DateTime.UtcNow;
            try
            {
                using var devices = new MMDeviceEnumerator();
                using var endpoint = devices.GetDefaultAudioEndpoint(DataFlow.Render,Role.Multimedia);
                int percent = (int)Math.Round(endpoint.AudioEndpointVolume.MasterVolumeLevelScalar * 100);
                string volumeOutput = Path.Combine(dir,"volume.json");
                await File.WriteAllTextAsync(volumeOutput + ".tmp", JsonSerializer.Serialize(new {volume=percent}));
                File.Move(volumeOutput + ".tmp",volumeOutput,true);
            }
            catch (Exception e) { Log("VOLUME_STATUS_ERROR " + e.Message); }
        }
        if (selected != null)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(output)!);
            string temp = output + ".tmp";
            await File.WriteAllTextAsync(temp, JsonSerializer.Serialize(selected));
            File.Move(temp, output, true);
            Log("MEDIA_WRITTEN path=" + output);
        }
        else
        {
            if (File.Exists(output)) File.Delete(output);
            Log("NO_SUPPORTED_MEDIA allSessions=" + sessions.Count);
        }
    }
    catch (Exception e) { Log("POLL_FAILED " + e, true); }
    await Task.Delay(100);
}

static string Safe(string text) => text.Length > 100 ? text[..100] + "..." : text;
static string SourceName(string source, string title)
{
    string s = source.ToLowerInvariant();
    if (s.Contains("spotify")) return "Spotify";
    if (s.Contains("applemusic") || s.Contains("apple.music") || s.Contains("appleinc.applemusic")) return "Apple Music";
    if (s.Contains("soundcloud")) return "SoundCloud";

    if (s.Contains("firefox")) return "SoundCloud";
    if ((s.Contains("chrome") || s.Contains("msedge")) &&
        title.Contains("SoundCloud", StringComparison.OrdinalIgnoreCase)) return "SoundCloud";
    return "Other";
}
