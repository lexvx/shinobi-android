package ru.lex42.shinobiviewer;

import org.json.*;
import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Authenticated Shinobi API. Blocking operations belong on a worker thread. */
public final class ApiClient {
    private final String server, auth, group;
    private volatile Map<String,Camera> allowed = Collections.emptyMap();
    public ApiClient(String server,String auth,String group,String uid) {
        URI u=URI.create(server.trim());
        if (!Arrays.asList("https","http").contains(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null) throw new IllegalArgumentException("Некорректный адрес сервера");
        this.server=server.trim().replaceAll("/+$",""); this.auth=auth; this.group=group;
    }
    public static final class Camera {
        public final String id,name,mainType,subType;
        public final boolean primaryIsLow,dynamicAvailable,subStreamActive,hasLowArchive;
        public final int dynamicChannel;
        Camera(JSONObject row) {
            id=row.optString("mid"); name=row.optString("name",id);
            JSONObject d=object(row.opt("details")), sub=object(d.opt("substream")), input=object(sub.opt("input")), output=object(sub.opt("output"));
            mainType=d.optString("stream_type","mp4"); subType=output.optString("stream_type","mp4");
            JSONArray channels=array(d.opt("stream_channels")); dynamicChannel=channels.length()+1;
            String primary=row.optString("path"); JSONObject choices=object(d.opt("input_map_choices")); JSONArray stream=array(choices.opt("stream")), maps=array(d.opt("input_maps"));
            if(stream.length()>0 && stream.optJSONObject(0)!=null && stream.optJSONObject(0).optString("map").startsWith("1") && maps.optJSONObject(0)!=null) primary=maps.optJSONObject(0).optString("fulladdress");
            String main=channel(primary), secondary=channel(input.optString("fulladdress"));
            primaryIsLow="102".equals(main)||(!"101".equals(main)&&"101".equals(secondary));
            dynamicAvailable=input.length()>0 && output.length()>0; subStreamActive=row.optBoolean("subStreamActive",false);
            hasLowArchive=!d.optString("archivePreviewMonitor").isEmpty();
        }
    }
    public static final class Recording implements ArchiveTimeline.Interval {
        public final String cameraId,title,url;
        public final long startMs,endMs;
        public Recording(String cameraId,String title,String url,long startMs,long endMs) {this.cameraId=cameraId;this.title=title;this.url=url;this.startMs=startMs;this.endMs=endMs;}
        public long startMs(){return startMs;}
        public long endMs(){return endMs;}
    }
    public static final class Variants {
        public final String originalUrl,lowUrl;
        Variants(String original,String low){originalUrl=original;lowUrl=low;}
        public boolean hasLow(){return lowUrl!=null;}
    }
    public synchronized List<Camera> getCameras() throws IOException {
        try {
            JSONArray rows=new JSONArray(get(path("monitor"))); List<Camera> result=new ArrayList<>(); Map<String,Camera> next=new HashMap<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!object(row.opt("details")).optString("archivePrimaryMonitor").isEmpty())continue;Camera c=new Camera(row);if(!c.id.isEmpty()){result.add(c);next.put(c.id,c);}}
            allowed=Collections.unmodifiableMap(next);return result;
        }catch(JSONException e){throw new IOException("Не удалось получить список камер.");}
    }
    public List<Recording> recordings(String cameraId,long fromMs,long toMs) throws IOException {
        require(cameraId);
        if(fromMs>0&&toMs>0&&toMs<=fromMs)throw new IOException("Некорректный период архива.");
        String url=path("videos")+"/"+enc(cameraId)+(fromMs>0&&toMs>0?"?noLimit=1":"?limit=1000");
        // Query by start time with overlap allowance for recordings crossing midnight.
        if(fromMs>0)url+="&start="+enc(Instant.ofEpochMilli(fromMs-86400000L).toString());
        if(toMs>0)url+="&end="+enc(Instant.ofEpochMilli(toMs).toString())+"&endIsStartTo=1";
        try {JSONObject response=new JSONObject(get(url));if(response.has("ok")&&!response.optBoolean("ok"))throw new IOException("Нет доступа к архиву камеры.");JSONArray rows=response.optJSONArray("videos");List<Recording> result=new ArrayList<>();if(rows==null)return result;
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!cameraId.equals(row.optString("mid",cameraId)))continue;long start=parseTime(row.optString("time")),end=parseTime(row.optString("end"));if(start<=0||end<=start||(fromMs>0&&end<=fromMs)||(toMs>0&&start>=toMs))continue;String href=row.optString("href");if(href.isEmpty())continue;result.add(new Recording(cameraId,row.optString("time"),safeUrl(href),start,end));}
            result.sort((a,b)->Long.compare(b.startMs,a.startMs));return result;
        }catch(JSONException e){throw new IOException("Некорректный ответ архива.");}
    }
    public Variants variants(Recording recording) throws IOException {
        require(recording.cameraId);String url=safeUrl(recording.url);
        try{JSONObject response=new JSONObject(get(url+(url.contains("?")?"&":"?")+"variants=1"));if(response.optInt("version")!=1)throw new IOException("Неизвестный формат вариантов записи.");ArchiveVariants parsed=ArchiveVariants.parse(URI.create(url),response);return new Variants(parsed.original.toString(),parsed.low==null?null:parsed.low.toString());}catch(JSONException e){throw new IOException("Не удалось получить качество записи.");}
    }
    public boolean requiresDynamic(Camera camera,boolean high){return camera.dynamicAvailable&&(high==camera.primaryIsLow);}
    public String liveUrl(Camera camera,boolean high) throws IOException {
        require(camera.id);boolean sub=requiresDynamic(camera,high);String type=sub?camera.subType:camera.mainType;
        if(!type.equals("mp4")&&!type.equals("hls"))throw new IOException("Неподдерживаемый формат потока камеры.");
        return path(type)+"/"+enc(camera.id)+(sub?"/"+camera.dynamicChannel:"")+"/s."+(type.equals("hls")?"m3u8":"mp4");
    }
    public synchronized void ensureDynamic(Camera camera) throws IOException {
        require(camera.id);getCameras();Camera current=allowed.get(camera.id);require(camera.id);if(current.subStreamActive)return;
        try{JSONObject response=new JSONObject(get(path("toggleSubstream")+"/"+enc(camera.id)));if(!response.optBoolean("ok"))throw new IOException("Не удалось включить поток камеры.");}catch(JSONException e){throw new IOException("Не удалось включить поток камеры.");}
    }
    private void require(String camera) throws IOException {if(!allowed.containsKey(camera))throw new IOException("Камера недоступна этому пользователю.");}
    private String path(String type){return server+"/"+enc(auth)+"/"+type+"/"+enc(group);}
    private String safeUrl(String value) throws IOException {
        try{URI base=URI.create(server+"/"),u=base.resolve(value);if(!Objects.equals(base.getScheme(),u.getScheme())||!base.getHost().equalsIgnoreCase(u.getHost()==null?"":u.getHost())||port(base)!=port(u)||u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("Недопустимый адрес записи.");return u.toString();}catch(IllegalArgumentException e){throw new IOException("Некорректный адрес записи.");}
    }
    private static int port(URI u){return u.getPort()!=-1?u.getPort():"https".equals(u.getScheme())?443:80;}
    static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException e){throw new AssertionError(e);}}
    static JSONObject object(Object v){if(v instanceof JSONObject)return (JSONObject)v;try{return new JSONObject(String.valueOf(v));}catch(Exception e){return new JSONObject();}}
    static JSONArray array(Object v){if(v instanceof JSONArray)return (JSONArray)v;try{return new JSONArray(String.valueOf(v));}catch(Exception e){return new JSONArray();}}
    static String channel(String s){Matcher m=Pattern.compile("channels/(\\d+)",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group(1):"";}
    public static long parseTime(String text){try{return Instant.parse(text).toEpochMilli();}catch(Exception e){try{return LocalDateTime.parse(text.replace(' ','T')).toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception ignored){return 0;}}}
    private String get(String url) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");
        try{int code=c.getResponseCode();if(code==401||code==403)throw new IOException("Сессия истекла. Войдите снова.");if(code!=200)throw new IOException("Сервер недоступен ("+code+").");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("Слишком большой ответ сервера.");out.write(buf,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
}
