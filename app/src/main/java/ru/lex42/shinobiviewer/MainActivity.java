package ru.lex42.shinobiviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/** A small Shinobi API client. Credentials and monitor configurations are kept in memory only. */
public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<MediaPlayer> players = new ArrayList<>();
    private LibVLC vlc;
    private LinearLayout root;
    private String server, auth, group, uid;
    private Socket socket;
    private volatile boolean socketReady;
    private final Set<String> watched = ConcurrentHashMap.newKeySet();
    private volatile int generation = 0;
    private boolean resumeConnection;
    private static final int BG = Color.rgb(16,22,29), CARD = Color.rgb(28,38,49), TEXT = Color.WHITE;
    private final ArrayList<Camera> cameras = new ArrayList<>();

    private static final class Camera {
        String id, name, mainType, subType;
        boolean primaryIsLow, dynamicAvailable;
        int dynamicChannel;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vlc = new LibVLC(this, new ArrayList<>(Arrays.asList("--network-caching=300", "--drop-late-frames", "--quiet")));
        showLogin();
    }
    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout layout() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16),dp(16),dp(16),dp(16)); l.setBackgroundColor(BG); return l;
    }
    private void screen(LinearLayout l) { releasePlayers(); unwatchAll(); root=l; setContentView(l); }
    private TextView text(String s, int size) { TextView t=new TextView(this); t.setText(s);t.setTextSize(size);t.setTextColor(TEXT); return t; }
    private Button button(String s, Runnable click) { Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->click.run());return b; }
    private EditText field(String hint, String value, int type) { EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);e.setInputType(type);e.setTextColor(TEXT);e.setHintTextColor(Color.LTGRAY);return e; }
    private void error(String s) { if(!isFinishing()) new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Понятно",null).show(); }
    private void showLogin() {
        generation++; LinearLayout l=layout();screen(l);l.addView(text("Камеры Shinobi",28));l.addView(text("Подключение к серверу",16));
        EditText url=field("Адрес сервера", "https://cam.lex42.ru", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText mail=field("Электронная почта", "", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass=field("Пароль", "", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(url);l.addView(mail);l.addView(pass);
        TextView status=text("",16);
        Button enter=button("Войти",()->{
            String address=url.getText().toString().trim(), username=mail.getText().toString().trim(), password=pass.getText().toString();
            if(address.isEmpty()||username.isEmpty()||password.isEmpty()){error("Заполните адрес, почту и пароль.");return;}
            try { Uri u=Uri.parse(address); if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null) throw new Exception(); }
            catch(Exception ex){error("Введите корректный адрес сервера с http:// или https://");return;}
            server=address.replaceAll("/+$", ""); int request=++generation;
            Button loginButton=(Button)status.getTag();loginButton.setEnabled(false);status.setText("Вход в Shinobi…");
            io.execute(()->{try {
                String data="mail="+enc(username)+"&pass="+enc(password)+"&function=dash&machineID="+enc(machineId());
                JSONObject j=new JSONObject(request("POST",server+"/?json=true",data));
                JSONObject user=j.optJSONObject("$user"); if(user==null) user=j;
                String a=user.optString("auth_token",user.optString("auth","")), g=user.optString("ke","");
                String u=user.optString("uid","");
                if(a.isEmpty()&&!g.isEmpty()&&!u.isEmpty()){ui(request,()->{loginButton.setEnabled(true);status.setText("");promptFactor(g,u,request,loginButton,status);});return;}
                if(a.isEmpty()||g.isEmpty()||u.isEmpty()) throw new IOException("Не удалось войти. Проверьте данные.");
                auth=a;group=g;uid=u;ui(request,()->status.setText("Подключение камер…"));connectSocket();loadCameras(request);
            } catch(Exception ex){closeSocket();auth=null;group=null;uid=null;ui(request,()->{loginButton.setEnabled(true);status.setText("");error(safeError(ex));});}});
        });status.setTag(enter);l.addView(enter);l.addView(status);
    }
    private String machineId(){String s=getPreferences(MODE_PRIVATE).getString("machine_id","");if(s.isEmpty()){s=UUID.randomUUID().toString();getPreferences(MODE_PRIVATE).edit().putString("machine_id",s).apply();}return s;}
    private void promptFactor(String ke,String userId,int requestId,Button loginButton,TextView status){
        EditText code=field("Код подтверждения","",InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("Подтверждение входа").setView(code).setNegativeButton("Отмена",null).setPositiveButton("Войти",(dialog,which)->{
            String value=code.getText().toString().trim();loginButton.setEnabled(false);status.setText("Подтверждение входа…");io.execute(()->{try{String data="ke="+enc(ke)+"&id="+enc(userId)+"&machineID="+enc(machineId())+"&factorAuthKey="+enc(value)+"&function=dash";JSONObject j=new JSONObject(request("POST",server+"/?json=true",data));JSONObject u=j.optJSONObject("$user");if(u==null)u=j;String a=u.optString("auth_token","");if(a.isEmpty())throw new IOException("Не удалось подтвердить вход.");auth=a;group=u.optString("ke",ke);uid=u.optString("uid",userId);ui(requestId,()->status.setText("Подключение камер…"));connectSocket();loadCameras(requestId);}catch(Exception e){closeSocket();auth=null;group=null;uid=null;ui(requestId,()->{loginButton.setEnabled(true);status.setText("");error(safeError(e));});}});
        }).show();
    }
    private void connectSocket() throws Exception {
        closeSocket();CountDownLatch ready=new CountDownLatch(1);IO.Options options=IO.Options.builder().setPath("/socket.io").setTransports(new String[]{"websocket"}).build();Socket s=IO.socket(server,options);socket=s;
        s.on(Socket.EVENT_CONNECT,args->{try{s.emit("f",new JSONObject().put("f","init").put("ke",group).put("uid",uid).put("auth",auth));}catch(JSONException ignored){}});
        s.on("f",args->{if(args.length>0&&args[0] instanceof JSONObject&&"init_success".equals(((JSONObject)args[0]).optString("f"))){socketReady=true;ready.countDown();}});
        s.on(Socket.EVENT_DISCONNECT,args->{socketReady=false;watched.clear();});s.connect();
        if(!ready.await(12,TimeUnit.SECONDS)){closeSocket();throw new IOException("Не удалось подключить просмотр камер.");}
    }
    private void watch(String id) throws Exception {
        Socket s=socket;if(s==null||!socketReady||!s.connected())throw new IOException("Нет соединения просмотра");
        if(watched.add(id)){
            CountDownLatch acknowledged=new CountDownLatch(1);
            Emitter.Listener listener=args->{if(args.length>0&&args[0] instanceof JSONObject){JSONObject event=(JSONObject)args[0];if("monitor_watch_on".equals(event.optString("f"))&&id.equals(event.optString("id")))acknowledged.countDown();}};
            s.on("f",listener);
            try{s.emit("f",new JSONObject().put("f","monitor").put("ff","watch_on").put("id",id).put("ke",group).put("uid",uid));if(!acknowledged.await(8,TimeUnit.SECONDS)){watched.remove(id);throw new IOException("Сервер не подтвердил просмотр");}}
            finally{s.off("f",listener);}
        }
    }
    private void unwatchAll(){Socket s=socket;if(s!=null&&s.connected())for(String id:new ArrayList<>(watched)){try{s.emit("f",new JSONObject().put("f","monitor").put("ff","watch_off").put("id",id).put("ke",group).put("uid",uid));}catch(JSONException ignored){}}watched.clear();}
    private void closeSocket(){unwatchAll();if(socket!=null){socket.disconnect();socket.close();socket=null;}socketReady=false;}
    private String request(String method,String url,String body) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
        try {if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Сервер ответил: HTTP "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("Ответ сервера слишком большой");out.write(b,0,n);}return out.toString("UTF-8");}
        } finally {c.disconnect();}
    }
    private static String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s,"UTF-8"); }
    private String api(String path) {return server+"/"+Uri.encode(auth)+"/"+path;}
    private static String safeError(Exception e) { return e instanceof IOException && e.getMessage()!=null && (e.getMessage().startsWith("Сервер ответил")||e.getMessage().startsWith("Не удалось")) ? e.getMessage() : "Не удалось связаться с Shinobi или обработать ответ."; }
    private void ui(int request,Runnable r){runOnUiThread(()->{if(request==generation&&!isFinishing())r.run();});}
    private static JSONObject jsonObject(Object o){try {if(o instanceof JSONObject)return (JSONObject)o;if(o instanceof String)return new JSONObject((String)o);}catch(Exception ignored){}return new JSONObject();}
    private static JSONArray jsonArray(Object o){try {if(o instanceof JSONArray)return (JSONArray)o;if(o instanceof String&&!((String)o).isEmpty())return new JSONArray((String)o);}catch(Exception ignored){}return new JSONArray();}
    private static String channel(String s){Matcher m=Pattern.compile("(?i)channels/(\\d+)").matcher(s);return m.find()?m.group(1):"";}
    private Camera parseCamera(JSONObject j) {
        Camera c=new Camera();c.id=j.optString("mid");c.name=j.optString("name",c.id);JSONObject d=jsonObject(j.opt("details"));c.mainType=d.optString("stream_type");JSONObject sub=jsonObject(d.opt("substream"));JSONObject input=jsonObject(sub.opt("input")),output=jsonObject(sub.opt("output"));c.subType=output.optString("stream_type");c.dynamicAvailable=!c.subType.isEmpty();
        JSONArray channels=jsonArray(d.opt("stream_channels"));c.dynamicChannel=channels.length()+1;
        String primary=j.optString("path","");JSONObject choices=jsonObject(d.opt("input_map_choices"));JSONArray map=jsonArray(choices.opt("stream"));String idx=map.optJSONObject(0)==null?"0":map.optJSONObject(0).optString("map","0");
        if(idx.startsWith("1")){JSONArray inputs=jsonArray(d.opt("input_maps"));JSONObject first=inputs.optJSONObject(0);if(first!=null)primary=first.optString("fulladdress","");}
        String mainCh=channel(primary),subCh=channel(input.optString("fulladdress",""));
        c.primaryIsLow="102".equals(mainCh) || (!"101".equals(mainCh) && "101".equals(subCh));
        return c;
    }
    private void loadCameras(int request) throws Exception {
        Object parsed=new JSONTokener(request("GET",api("monitor/"+Uri.encode(group)),null)).nextValue();if(!(parsed instanceof JSONArray))throw new IOException("Не удалось получить список камер.");
        JSONArray a=(JSONArray)parsed;ArrayList<Camera> found=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null){Camera c=parseCamera(j);if(!c.id.isEmpty())found.add(c);}}
        ui(request,()->{cameras.clear();cameras.addAll(found);showGrid();});
    }
    private void showGrid(){
        int current=++generation;LinearLayout page=layout();screen(page);LinearLayout bar=new LinearLayout(this);bar.addView(text("Камеры",24),new LinearLayout.LayoutParams(0,-2,1));bar.addView(button("Обновить",()->{int g=++generation;io.execute(()->{try{loadCameras(g);}catch(Exception e){ui(g,()->error(safeError(e)));}});}));bar.addView(button("Выход",()->{closeSocket();auth=null;group=null;uid=null;showLogin();}));page.addView(bar);
        ScrollView scroll=new ScrollView(this);LinearLayout rows=layout();rows.setPadding(0,0,0,0);scroll.addView(rows);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(cameras.isEmpty())rows.addView(text("Камеры не найдены",18));
        for(int i=0;i<cameras.size();i+=2){LinearLayout row=new LinearLayout(this);rows.addView(row);for(int k=i;k<Math.min(i+2,cameras.size());k++){Camera c=cameras.get(k);LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setBackgroundColor(CARD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,lp);VLCVideoLayout v=new VLCVideoLayout(this);tile.addView(v,new LinearLayout.LayoutParams(-1,dp(115)));TextView label=text(c.name,15);label.setPadding(dp(8),dp(6),dp(8),dp(6));tile.addView(label);tile.setOnClickListener(w->showCamera(c,false));loadStream(c,false,v,current);}}
    }
    private String mainUrl(Camera c) {String ext="hls".equals(c.mainType)?"s.m3u8":"s.mp4";return api(c.mainType+"/"+Uri.encode(group)+"/"+Uri.encode(c.id)+"/"+ext);}
    private String subUrl(Camera c) {String ext="hls".equals(c.subType)?"s.m3u8":"s.mp4";return api(c.subType+"/"+Uri.encode(group)+"/"+Uri.encode(c.id)+"/"+c.dynamicChannel+"/"+ext);}
    private void loadStream(Camera c,boolean high,VLCVideoLayout view,int current){
        io.execute(()->{try {if(current!=generation)return;watch(c.id);boolean useSub=c.dynamicAvailable&&(high==c.primaryIsLow);
            if(useSub){JSONArray a=new JSONArray(request("GET",api("monitor/"+Uri.encode(group)),null));JSONObject m=null;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&c.id.equals(x.optString("mid"))){m=x;break;}}
                if(m==null)throw new IOException("Не удалось найти камеру.");if(current!=generation)return;if(!m.optBoolean("subStreamActive",false)){JSONObject result=new JSONObject(request("GET",api("toggleSubstream/"+Uri.encode(group)+"/"+Uri.encode(c.id)),null));if(!result.optBoolean("ok",false))throw new IOException("Не удалось включить поток.");Thread.sleep(1300);}}
            String url=useSub?subUrl(c):mainUrl(c);ui(current,()->attach(view,url));
        }catch(Exception e){ui(current,()->Toast.makeText(this,"Поток «"+c.name+"» недоступен",Toast.LENGTH_SHORT).show());}});
    }
    private void attach(VLCVideoLayout view,String url){attach(view,url,null,0);}
    private void attach(VLCVideoLayout view,String url,Boolean hardware,long startTime){
        MediaPlayer p=new MediaPlayer(vlc);players.add(p);p.attachViews(view,null,false,false);
        int[] attempts={0};boolean[] seekPending={startTime>0};Handler handler=new Handler(Looper.getMainLooper());
        p.setEventListener(event->{if(event.type==MediaPlayer.Event.Playing&&seekPending[0]){seekPending[0]=false;p.setTime(startTime);}else if(event.type==MediaPlayer.Event.EncounteredError){handler.postDelayed(()->{if(!players.contains(p))return;if(attempts[0]++<3){p.stop();p.play();}else Toast.makeText(this,"Не удалось воспроизвести видео",Toast.LENGTH_SHORT).show();},2000);}});
        Media m=new Media(vlc,Uri.parse(url));if(hardware!=null)m.setHWDecoderEnabled(hardware,false);p.setMedia(m);m.release();p.setVolume(0);p.play();
    }
    private void showCamera(Camera c,boolean high){int g=++generation;LinearLayout page=layout();screen(page);LinearLayout bar=new LinearLayout(this);bar.addView(button("← Камеры",this::showGrid));bar.addView(text(c.name,21),new LinearLayout.LayoutParams(0,-2,1));page.addView(bar);VLCVideoLayout video=new VLCVideoLayout(this);page.addView(video,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.addView(button(high?"Обычное качество":"Полное качество",()->showCamera(c,!high)));actions.addView(button("Архив",()->showArchive(c)));page.addView(actions);loadStream(c,high,video,g);}
    private void showArchive(Camera c){int g=++generation;LinearLayout page=layout();screen(page);page.addView(button("← Камера",()->showCamera(c,false)));page.addView(text("Архив · "+c.name,22));ScrollView sv=new ScrollView(this);LinearLayout list=layout();sv.addView(list);page.addView(sv,new LinearLayout.LayoutParams(-1,0,1));TextView status=text("Загрузка…",16);list.addView(status);
        io.execute(()->{try{JSONObject result=new JSONObject(request("GET",api("videos/"+Uri.encode(group)+"/"+Uri.encode(c.id)+"?limit=100"),null));if(!result.optBoolean("ok",true))throw new IOException("Не удалось получить архив.");JSONArray videos=result.optJSONArray("videos");if(videos==null)throw new IOException("Не удалось получить архив.");ui(g,()->{list.removeAllViews();if(videos.length()==0)list.addView(text("Записей нет",16));for(int i=0;i<videos.length();i++){JSONObject v=videos.optJSONObject(i);if(v==null)continue;String href=v.optString("href","");String time=v.optString("time","Запись");if(href.isEmpty())continue;try{URL base=new URL(server+"/");URL resolved=new URL(base,href);if(!resolved.getHost().equalsIgnoreCase(base.getHost())||resolved.getPort()!=base.getPort()||!resolved.getProtocol().equals(base.getProtocol()))continue;String play=resolved.toString();list.addView(button(time,()->showRecording(c,time,play)));}catch(Exception ignored){}}});}catch(Exception e){ui(g,()->status.setText("Не удалось загрузить архив"));}});
    }
    private void showRecording(Camera c,String title,String url){showRecording(c,title,url,false,0);}
    private void showRecording(Camera c,String title,String url,boolean hardware,long startTime){++generation;LinearLayout page=layout();screen(page);page.addView(button("← Архив",()->showArchive(c)));page.addView(text(title,16));VLCVideoLayout video=new VLCVideoLayout(this);page.addView(video,new LinearLayout.LayoutParams(-1,0,1));attach(video,url,hardware,startTime);LinearLayout controls=new LinearLayout(this);controls.addView(button("−10 с",()->seek(-10000)));controls.addView(button("Пауза / пуск",()->{if(!players.isEmpty()){MediaPlayer p=players.get(0);if(p.isPlaying())p.pause();else p.play();}}));controls.addView(button("+10 с",()->seek(10000)));page.addView(controls);page.addView(button(hardware?"Режим видео: аппаратный":"Режим видео: совместимый",()->{long time=players.isEmpty()?0:Math.max(0,players.get(0).getTime());showRecording(c,title,url,!hardware,time);}));}
    private void seek(long delta){if(!players.isEmpty()){MediaPlayer p=players.get(0);p.setTime(Math.max(0,p.getTime()+delta));}}
    private void releasePlayers(){for(MediaPlayer p:players){try{p.stop();p.detachViews();p.release();}catch(Exception ignored){}}players.clear();}
    @Override public void onBackPressed(){if(auth!=null)showGrid();else super.onBackPressed();}
    @Override protected void onStop(){super.onStop();if(auth!=null){generation++;releasePlayers();closeSocket();resumeConnection=true;}}
    @Override protected void onStart(){super.onStart();if(resumeConnection&&auth!=null){resumeConnection=false;int g=++generation;io.execute(()->{try{connectSocket();loadCameras(g);}catch(Exception e){ui(g,()->{showLogin();error("Сессия завершена. Войдите снова.");});}});}}
    @Override protected void onDestroy(){generation++;releasePlayers();closeSocket();io.shutdownNow();vlc.release();super.onDestroy();}
}
