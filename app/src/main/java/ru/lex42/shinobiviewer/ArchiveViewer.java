package ru.lex42.shinobiviewer;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native synchronized archive wall. Each player follows the same absolute timestamp. */
public final class ArchiveViewer extends LinearLayout {
    public interface Listener {
        void onDownload(ApiClient.Recording recording, boolean high, boolean share);
        default void onFocus(ApiClient.Camera camera, long archiveTime) {}
    }
    private final ApiClient api;
    private final List<ApiClient.Camera> cameras;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private final LinkedHashMap<String,Slot> slots=new LinkedHashMap<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Listener listener;
    private final int foreground,secondary,surface,accent=Color.rgb(81,191,179);
    private final LinearLayout wall;
    private final TextView clock,status;
    private final Button play,quality,date,choose;
    private final TimelineView timeline;
    private final SimpleDateFormat clockFormat=new SimpleDateFormat("dd MMM yyyy · HH:mm:ss",new Locale("ru"));
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("dd MMMM",new Locale("ru"));
    private long dayStart,dayEnd,time,lastTick;
    private boolean high,playing,active=true,disposed,loading;
    private int generation;
    private float speed=1;
    private String focused;
    private final Runnable ticker=new Runnable(){public void run(){if(disposed)return;tick();ui.postDelayed(this,250);}};
    private static final class Slot {
        ApiClient.Camera camera; List<ApiClient.Recording> rows=Collections.emptyList();
        ApiClient.Recording row; VideoView video; LinearLayout tile; TextView message;
        MediaPlayer player; int version, seekVersion; boolean ready,loading,buffering,failed,seeking;
    }

    public ArchiveViewer(Context context,ApiClient api,List<ApiClient.Camera> cameras,boolean dark,Listener listener){
        super(context);this.api=api;this.cameras=new ArrayList<>(cameras);this.listener=listener;
        foreground=dark?Color.rgb(237,243,249):Color.rgb(24,39,52);secondary=dark?Color.rgb(152,170,184):Color.rgb(96,114,128);surface=dark?Color.rgb(24,37,49):Color.WHITE;
        setOrientation(VERTICAL);setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView heading=text("Просмотрщик",27);heading.setTypeface(null,1);addView(heading);
        TextView sub=text("Один момент · несколько камер",13);sub.setTextColor(secondary);addView(sub);
        LinearLayout options=row();choose=button("Камеры",this::chooseCameras);date=button("Дата",this::pickDate);quality=button("SD",()->{});quality.setOnClickListener(v->{high=!high;quality.setText(high?"4К":"SD");for(Slot s:slots.values())loadSlot(s,ArchiveTimeline.at(s.rows,time));});
        options.addView(choose,weight());options.addView(date,weight());options.addView(quality);addView(options);
        clock=text("",16);clock.setPadding(0,dp(6),0,dp(6));clock.setOnClickListener(v->pickTime());clock.setContentDescription("Выбрать время архива");addView(clock);
        ScrollView scroll=new ScrollView(context);wall=new LinearLayout(context);wall.setOrientation(VERTICAL);scroll.addView(wall);addView(scroll,new LayoutParams(-1,0,1));
        status=text("",12);status.setTextColor(secondary);addView(status);
        timeline=new TimelineView(context);addView(timeline,new LayoutParams(-1,dp(64)));
        LinearLayout controls=row();controls.addView(button("−10",()->seek(time-10000)),weight());play=button("▶",()->{playing=!playing;lastTick=SystemClock.elapsedRealtime();sync();});play.setContentDescription("Воспроизвести или приостановить все камеры");controls.addView(play,weight());controls.addView(button("+10",()->seek(time+10000)),weight());controls.addView(button("Время",this::pickTime),weight());
        Button speedButton=button("1×",()->{});speedButton.setOnClickListener(v->{String[] labels={"0,5×","1×","1,5×","2×"};float[] values={.5f,1,1.5f,2};new AlertDialog.Builder(context).setTitle("Скорость воспроизведения").setItems(labels,(d,n)->{speed=values[n];speedButton.setText(labels[n]);for(Slot s:slots.values())if(s.ready)applySpeed(s);}).show();});controls.addView(speedButton,weight());addView(controls);
        for(ApiClient.Camera c:cameras)selected.add(c.id);
        Calendar today=Calendar.getInstance();setDay(today);lastTick=SystemClock.elapsedRealtime();reload();ui.post(ticker);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp){TextView v=new TextView(getContext());v.setText(value);v.setTextColor(foreground);v.setTextSize(sp);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LayoutParams weight(){LayoutParams p=new LayoutParams(0,-2,1);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    private Button button(String label,Runnable action){Button b=new Button(getContext());b.setText(label);b.setTextColor(foreground);b.setAllCaps(false);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}
    private void setDay(Calendar day){day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);dayStart=day.getTimeInMillis();day.add(Calendar.DAY_OF_MONTH,1);dayEnd=day.getTimeInMillis();time=ArchiveTimeline.clamp(System.currentTimeMillis()-300000,dayStart,dayEnd);date.setText(dateFormat.format(new Date(dayStart)));}
    private void pickDate(){Calendar c=Calendar.getInstance();c.setTimeInMillis(dayStart);new DatePickerDialog(getContext(),(v,y,m,d)->{Calendar chosen=Calendar.getInstance();chosen.set(y,m,d);setDay(chosen);reload();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void pickTime(){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);new TimePickerDialog(getContext(),(v,h,m)->{Calendar chosen=Calendar.getInstance();chosen.setTimeInMillis(dayStart);chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,m);seek(chosen.getTimeInMillis());},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();}
    private void chooseCameras(){String[] names=new String[cameras.size()];boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++){names[i]=cameras.get(i).name;checked[i]=selected.contains(cameras.get(i).id);}new AlertDialog.Builder(getContext()).setTitle("Камеры в просмотрщике").setMultiChoiceItems(names,checked,(d,n,on)->checked[n]=on).setNegativeButton("Отмена",null).setPositiveButton("Показать",(d,w)->{selected.clear();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(cameras.get(i).id);focused=null;reload();}).show();}
    private void reload(){
        final int request=++generation;loading=true;playing=false;clearSlots();wall.removeAllViews();choose.setText("Камеры · "+selected.size());status.setText("Загрузка записей…");
        for(ApiClient.Camera c:cameras)if(selected.contains(c.id)){Slot s=new Slot();s.camera=c;slots.put(c.id,s);}
        if(slots.isEmpty()){loading=false;status.setText("Выберите камеры");update();return;}
        buildTiles();final int[] remaining={slots.size()};final List<String> errors=new ArrayList<>();final long from=dayStart,to=dayEnd;
        for(Slot s:slots.values())workers.execute(()->{List<ApiClient.Recording> rows;String failure=null;try{rows=api.recordings(s.camera.id,from,to);}catch(Exception e){rows=Collections.emptyList();failure="Не удалось загрузить записи";}final List<ApiClient.Recording> result=rows;final String problem=failure;ui.post(()->{if(disposed||request!=generation)return;s.rows=result;if(problem!=null){errors.add(s.camera.name);s.message.setText(problem);}if(--remaining[0]==0){loading=false;long latest=dayStart;boolean any=false;for(Slot slot:slots.values())for(ApiClient.Recording r:slot.rows){latest=Math.max(latest,r.startMs);any=true;}if(any)time=latest;status.setText(errors.isEmpty()?"Нажмите ▶ для синхронного просмотра":"Не загружены: "+join(errors));sync();timeline.invalidate();}});});
    }
    private String join(List<String> values){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append(", ");b.append(v);}return b.toString();}
    private void buildTiles(){wall.removeAllViews();LinearLayout line=null;int count=0;for(Slot s:slots.values()){
        if(focused!=null&&!focused.equals(s.camera.id))continue;
        if(line==null||count%2==0||focused!=null){line=row();wall.addView(line,new LayoutParams(-1,-2));}
        LinearLayout tile=new LinearLayout(getContext());s.tile=tile;tile.setOrientation(VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(14));tile.setBackground(bg);tile.setPadding(dp(5),dp(5),dp(5),dp(5));LayoutParams lp=new LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));line.addView(tile,lp);
        LinearLayout header=row();TextView name=text(s.camera.name,13);name.setMaxLines(1);header.addView(name,new LayoutParams(0,-2,1));Button more=button("⋮",()->recordingActions(s));more.setContentDescription("Действия с записью "+s.camera.name);header.addView(more,new LayoutParams(dp(36),dp(32)));tile.addView(header);
        FrameLayout stage=new FrameLayout(getContext());stage.setBackgroundColor(Color.BLACK);stage.setClipChildren(true);stage.setClipToPadding(true);tile.addView(stage,new LayoutParams(-1,dp(focused!=null?250:112)));VideoView video=new VideoView(getContext());s.video=video;FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);stage.addView(video,vp);
        stage.addView(new ZoomGestureOverlay(getContext(),video,()->focus(s.camera.id),null),new FrameLayout.LayoutParams(-1,-1));s.message=text("Загрузка…",11);s.message.setTextColor(secondary);tile.addView(s.message);count++;
    }}
    private void focus(String id){focused=id.equals(focused)?null:id;for(Slot s:slots.values())clearPlayer(s);buildTiles();sync();}
    private void recordingActions(Slot s){if(s.row==null){Toast.makeText(getContext(),"В этот момент нет записи",Toast.LENGTH_SHORT).show();return;}String[] actions={"Скачать SD","Скачать 4К","Отправить SD","Отправить 4К","На весь экран"};new AlertDialog.Builder(getContext()).setTitle(s.camera.name).setItems(actions,(d,n)->{if(n==4){listener.onFocus(s.camera,time);return;}listener.onDownload(s.row,n%2==1,n>=2);}).show();}
    private void clearPlayer(Slot s){++s.version;if(s.video!=null){s.video.stopPlayback();s.video.setVisibility(INVISIBLE);}s.player=null;++s.seekVersion;s.ready=s.loading=s.buffering=s.failed=s.seeking=false;s.row=null;}
    private void clearSlots(){for(Slot s:slots.values())clearPlayer(s);slots.clear();}
    private boolean visible(Slot s){return focused==null||focused.equals(s.camera.id);}
    private void loadSlot(Slot s,ApiClient.Recording row){
        clearPlayer(s);s.row=row;if(!visible(s))return;
        if(row==null){s.message.setText("Нет записи в этот момент");return;}
        final int version=s.version,request=generation;final boolean requestedHigh=high;s.loading=true;s.message.setText("Загрузка…");
        workers.execute(()->{String url=null,problem=null;try{if(requestedHigh)url=row.url;else{ApiClient.Variants v=api.variants(row);url=v.lowUrl;if(url==null||url.isEmpty())problem="SD отсутствует · выберите 4К";}}catch(Exception e){problem="Не удалось открыть запись";}final String mediaUrl=url,error=problem;ui.post(()->{
            if(disposed||request!=generation||version!=s.version)return;
            if(error!=null){s.loading=false;s.failed=true;s.message.setText(error);return;}
            s.video.setOnPreparedListener(mp->{if(!valid(s,request,version))return;s.player=mp;s.ready=true;s.loading=false;mp.setVolume(0,0);mp.setOnSeekCompleteListener(done->{if(valid(s,request,version)){s.seeking=false;syncPlayback();}});applySpeed(s,mp);seekSlot(s,ArchiveTimeline.offset(row,time));s.message.setText(requestedHigh?"4К":"SD");syncPlayback();});
            s.video.setOnInfoListener((mp,what,extra)->{if(!valid(s,request,version))return false;if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START){s.buffering=true;s.message.setText("Буферизация…");}else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END){s.buffering=false;s.message.setText(requestedHigh?"4К":"SD");}syncPlayback();return false;});
            s.video.setOnCompletionListener(mp->{if(!valid(s,request,version)||!active||!playing||s.seeking)return;time=ArchiveTimeline.clamp(Math.max(time,row.endMs),dayStart,dayEnd);if(time>=dayEnd-1)playing=false;sync();});
            s.video.setOnErrorListener((mp,what,extra)->{if(valid(s,request,version)){s.loading=false;s.buffering=false;s.failed=true;s.message.setText("Ошибка видео · смените качество");}return true;});
            s.video.setVisibility(VISIBLE);s.video.setVideoURI(Uri.parse(mediaUrl));ui.postDelayed(()->{if(valid(s,request,version)&&s.loading){s.video.stopPlayback();s.loading=false;s.failed=true;s.message.setText("Сервер не ответил · нажмите SD/4К");}},25000);
        });});
    }
    private boolean valid(Slot s,int request,int version){return !disposed&&generation==request&&s.version==version;}
    private void applySpeed(Slot s){if(s.ready&&s.player!=null){applySpeed(s,s.player);syncPlayback();}}
    private void applySpeed(Slot s,MediaPlayer mp){try{boolean wasPlaying=mp.isPlaying();mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));if(!wasPlaying)mp.pause();}catch(Exception e){s.message.setText("Скорость не поддерживается");}}
    private void seekSlot(Slot s,int offset){if(!s.ready||s.failed)return;s.seeking=true;final int seek=++s.seekVersion,v=s.version;s.video.seekTo(offset);syncPlayback();ui.postDelayed(()->{if(!disposed&&s.version==v&&s.seekVersion==seek&&s.seeking){s.seeking=false;s.failed=true;s.video.pause();s.message.setText("Переход не выполнен · смените качество");syncPlayback();}},15000);}
    private boolean busy(){for(Slot s:slots.values())if(visible(s)&&s.row!=null&&!s.failed&&(s.loading||s.buffering||s.seeking))return true;return false;}
    private void syncPlayback(){boolean run=active&&playing&&!loading&&!busy();for(Slot s:slots.values())if(visible(s)&&s.ready&&!s.failed){if(run&&!s.video.isPlaying())s.video.start();else if(!run&&s.video.isPlaying())s.video.pause();}}
    private void sync(){if(disposed||loading)return;for(Slot s:slots.values()){if(!visible(s))continue;ApiClient.Recording row=ArchiveTimeline.at(s.rows,time);if(row!=s.row)loadSlot(s,row);else if(row==null)s.message.setText("Нет записи в этот момент");else if(row!=null&&s.ready&&!s.failed&&!s.seeking){int offset=ArchiveTimeline.offset(row,time);if(Math.abs(s.video.getCurrentPosition()-offset)>1500)seekSlot(s,offset);}}syncPlayback();update();}
    private void update(){clock.setText(clockFormat.format(new Date(time))+"  ▾");play.setText(playing?"Ⅱ":"▶");timeline.invalidate();}
    private void seek(long absolute){time=ArchiveTimeline.clamp(absolute,dayStart,dayEnd);lastTick=SystemClock.elapsedRealtime();sync();}
    private void tick(){long now=SystemClock.elapsedRealtime();if(active&&!loading&&playing&&!busy()){time+=(long)(Math.min(1000,now-lastTick)*speed);if(time>=dayEnd){time=dayEnd-1;playing=false;}}lastTick=now;if(active&&!loading){sync();if(playing)status.setText(busy()?"Ожидаем загрузку · время синхронизировано":"Синхронное воспроизведение");}}
    public void suspend(){active=false;syncPlayback();}
    public void resume(){if(disposed)return;active=true;lastTick=SystemClock.elapsedRealtime();sync();}
    public void destroy(){if(disposed)return;disposed=true;++generation;ui.removeCallbacksAndMessages(null);clearSlots();workers.shutdownNow();}

    private final class TimelineView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        long preview=-1;
        TimelineView(Context context){super(context);setContentDescription("Общая шкала архива за выбранный день. Нажмите для перехода.");setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight(),top=dp(6),bottom=h-dp(20);paint.setColor(surface);canvas.drawRoundRect(new RectF(0,top,w,bottom),dp(5),dp(5),paint);int lane=0,n=Math.max(1,slots.size());for(Slot s:slots.values()){paint.setColor(accent);for(ApiClient.Recording r:s.rows){float x1=(float)(Math.max(dayStart,r.startMs)-dayStart)/(dayEnd-dayStart)*w,x2=(float)(Math.min(dayEnd,r.endMs)-dayStart)/(dayEnd-dayStart)*w;if(x2>x1)canvas.drawRect(x1,top+lane*(bottom-top)/n+1,x2,top+(lane+1)*(bottom-top)/n-1,paint);}lane++;}paint.setColor(foreground);float cursor=(float)((preview>=0?preview:time)-dayStart)/(dayEnd-dayStart)*w;paint.setStrokeWidth(dp(2));canvas.drawLine(cursor,top-2,cursor,bottom+2,paint);paint.setTextSize(dp(10));paint.setColor(secondary);for(int hour=0;hour<=24;hour+=6){String label=String.format(Locale.ROOT,"%02d:00",hour);float x=w*hour/24;canvas.drawText(label,Math.max(0,Math.min(w-paint.measureText(label),x-paint.measureText(label)/2)),h-dp(3),paint);}}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);preview=ArchiveTimeline.clamp(dayStart+(long)(Math.max(0,Math.min(1,event.getX()/Math.max(1,getWidth())))*(dayEnd-dayStart)),dayStart,dayEnd);invalidate();return true;}
            if(action==MotionEvent.ACTION_UP){long target=preview;preview=-1;if(target>=0)seek(target);performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(action==MotionEvent.ACTION_CANCEL){preview=-1;invalidate();getParent().requestDisallowInterceptTouchEvent(false);}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
