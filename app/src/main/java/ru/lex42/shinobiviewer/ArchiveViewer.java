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
    private final HorizontalScrollView timelineScroll;
    private boolean timelineTouched;
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
        ApiClient.Recording row; VideoView video; LinearLayout tile; TextView message; String stateText="";
        MediaPlayer player; int version, seekVersion; boolean ready,loading,buffering,failed,seeking;
    }

    public ArchiveViewer(Context context,ApiClient api,List<ApiClient.Camera> cameras,boolean dark,Listener listener){
        super(context);this.api=api;this.cameras=new ArrayList<>(cameras);this.listener=listener;
        foreground=dark?Color.rgb(237,243,249):Color.rgb(24,39,52);secondary=dark?Color.rgb(152,170,184):Color.rgb(96,114,128);surface=dark?Color.rgb(25,42,56):Color.WHITE;
        setOrientation(VERTICAL);setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout options=row();choose=button("Камеры",this::chooseCameras);date=button("Дата",this::pickDate);quality=button("SD",()->{});quality.setOnClickListener(v->{high=!high;quality.setText(high?"4К":"SD");for(Slot s:slots.values())loadSlot(s,ArchiveTimeline.at(s.rows,time));});
        options.addView(choose,weight());options.addView(date,weight());options.addView(quality);addView(options);
        clock=text("",14);clock.setSingleLine(true);clock.setEllipsize(android.text.TextUtils.TruncateAt.END);clock.setPadding(0,dp(6),0,dp(6));clock.setOnClickListener(v->pickTime());clock.setContentDescription("Выбрать время архива");addView(clock);
        ScrollView scroll=new ScrollView(context);wall=new LinearLayout(context);wall.setOrientation(VERTICAL);scroll.addView(wall);addView(scroll,new LayoutParams(-1,0,1));
        status=text("",12);status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setTextColor(secondary);addView(status);
        timeline=new TimelineView(context);
        timelineScroll=new HorizontalScrollView(context){
            @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);timeline.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,w)*4,-1));if(!timelineTouched)post(()->centerTimeline(false));}
            @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_MOVE)timelineTouched=true;return super.onTouchEvent(e);}
        };
        timelineScroll.setHorizontalScrollBarEnabled(false);timelineScroll.setOverScrollMode(OVER_SCROLL_NEVER);timelineScroll.addView(timeline,new FrameLayout.LayoutParams(dp(1200),-1));addView(timelineScroll,new LayoutParams(-1,dp(76)));
        LinearLayout controls=row();controls.addView(button("−10",()->seek(time-10000)),weight());play=button("▶",()->{playing=!playing;lastTick=SystemClock.elapsedRealtime();sync();});play.setContentDescription("Воспроизвести или приостановить все камеры");controls.addView(play,weight());controls.addView(button("+10",()->seek(time+10000)),weight());
        Button speedButton=button("1×",()->{});speedButton.setOnClickListener(v->{String[] labels={"0,5×","1×","1,5×","2×"};float[] values={.5f,1,1.5f,2};new AlertDialog.Builder(context).setTitle("Скорость воспроизведения").setItems(labels,(d,n)->{speed=values[n];speedButton.setText(labels[n]);for(Slot s:slots.values())if(s.ready)applySpeed(s);}).show();});controls.addView(speedButton,weight());addView(controls);
        Calendar today=Calendar.getInstance();setDay(today);lastTick=SystemClock.elapsedRealtime();reload();ui.post(ticker);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int sp){TextView v=new TextView(getContext());v.setText(value);v.setTextColor(foreground);v.setTextSize(sp);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LayoutParams weight(){LayoutParams p=new LayoutParams(0,dp(44),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    private Button button(String label,Runnable action){Button b=new Button(getContext());b.setText(label);b.setTextColor(foreground);b.setAllCaps(false);b.setTextSize(14);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(8));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}
    private void setDay(Calendar day){timelineTouched=false;day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);dayStart=day.getTimeInMillis();day.add(Calendar.DAY_OF_MONTH,1);dayEnd=day.getTimeInMillis();time=ArchiveTimeline.clamp(System.currentTimeMillis()-300000,dayStart,dayEnd);date.setText(dateFormat.format(new Date(dayStart)));ui.post(()->centerTimeline(false));}
    private void pickDate(){Calendar c=Calendar.getInstance();c.setTimeInMillis(dayStart);new DatePickerDialog(getContext(),(v,y,m,d)->{Calendar chosen=Calendar.getInstance();chosen.set(y,m,d);setDay(chosen);reload();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void pickTime(){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);new TimePickerDialog(getContext(),(v,h,m)->{Calendar chosen=Calendar.getInstance();chosen.setTimeInMillis(dayStart);chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,m);seek(chosen.getTimeInMillis());centerTimeline(true);},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();}
    private void chooseCameras(){String[] names=new String[cameras.size()];boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++){names[i]=cameras.get(i).name;checked[i]=selected.contains(cameras.get(i).id);}new AlertDialog.Builder(getContext()).setTitle("Камеры · записи").setMultiChoiceItems(names,checked,(d,n,on)->checked[n]=on).setNegativeButton("Отмена",null).setPositiveButton("Показать",(d,w)->{selected.clear();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(cameras.get(i).id);focused=null;reload();}).show();}
    private void reload(){
        if(disposed)return;
        final int request=++generation;loading=true;playing=false;clearSlots();wall.removeAllViews();choose.setText("Камеры · "+selected.size());status.setText("Загрузка записей…");
        for(ApiClient.Camera c:cameras)if(selected.contains(c.id)){Slot s=new Slot();s.camera=c;slots.put(c.id,s);}
        if(slots.isEmpty()){loading=false;status.setText("Выберите камеры");update();return;}
        buildTiles();final int[] remaining={slots.size()};final List<String> errors=new ArrayList<>();final long from=dayStart,to=dayEnd;
        for(Slot s:slots.values())workers.execute(()->{List<ApiClient.Recording> rows;String failure=null;try{rows=api.recordings(s.camera.id,from,to);}catch(Exception e){rows=Collections.emptyList();failure="Не удалось загрузить записи";}final List<ApiClient.Recording> result=rows;final String problem=failure;ui.post(()->{if(disposed||request!=generation)return;s.rows=result;if(problem!=null){errors.add(s.camera.name);state(s,problem);}if(--remaining[0]==0){loading=false;long latest=dayStart;boolean any=false;for(Slot slot:slots.values())for(ApiClient.Recording r:slot.rows){latest=Math.max(latest,r.startMs);any=true;}if(any)time=latest;if(!timelineTouched)centerTimeline(false);status.setText(errors.isEmpty()?"":"Не загружены: "+join(errors));sync();timeline.invalidate();}});});
    }
    private String join(List<String> values){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append(", ");b.append(v);}return b.toString();}
    private void buildTiles(){wall.removeAllViews();for(Slot s:slots.values()){
        if(focused!=null&&!focused.equals(s.camera.id))continue;
        LinearLayout tile=new LinearLayout(getContext());s.tile=tile;tile.setOrientation(VERTICAL);
        LayoutParams lp=new LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));wall.addView(tile,lp);
        FrameLayout stage=new FrameLayout(getContext()){
            @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(width*9/16,MeasureSpec.EXACTLY));}
        };
        GradientDrawable background=new GradientDrawable();background.setColor(Color.BLACK);background.setCornerRadius(dp(14));stage.setBackground(background);stage.setClipToOutline(true);stage.setClipChildren(true);stage.setClipToPadding(true);tile.addView(stage,new LayoutParams(-1,-2));
        VideoView video=new VideoView(getContext());s.video=video;stage.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        stage.addView(new ZoomGestureOverlay(getContext(),video,()->focus(s.camera.id),()->recordingActions(s)),new FrameLayout.LayoutParams(-1,-1));
        TextView name=text(s.camera.name,13);name.setTextColor(Color.WHITE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(10),dp(6),dp(10),dp(6));name.setBackgroundColor(0x88000000);name.setContentDescription(s.camera.name+". Действия с записью");name.setOnClickListener(v->recordingActions(s));
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);label.rightMargin=dp(44);stage.addView(name,label);
        s.message=text("●",12);s.message.setGravity(Gravity.CENTER);s.message.setBackgroundColor(0x55000000);FrameLayout.LayoutParams dot=new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP|Gravity.RIGHT);stage.addView(s.message,dot);s.message.setOnClickListener(v->Toast.makeText(getContext(),s.stateText,Toast.LENGTH_SHORT).show());state(s,"Загрузка…");
    }}
    private void state(Slot s,String message){
        s.stateText=message;if(s.message==null)return;
        boolean ok="SD".equals(message)||"4К".equals(message);
        boolean waiting=message.contains("Загруз")||message.contains("Буфериза");
        s.message.setTextColor(ok?accent:waiting?Color.rgb(245,190,91):Color.rgb(225,129,111));
        s.message.setContentDescription(s.camera.name+": "+message);s.message.setTooltipText(message);
    }
    private void focus(String id){focused=id.equals(focused)?null:id;for(Slot s:slots.values())clearPlayer(s);buildTiles();sync();}
    private void recordingActions(Slot s){if(disposed)return;if(s.row==null){Toast.makeText(getContext(),"В этот момент нет записи",Toast.LENGTH_SHORT).show();return;}final ApiClient.Recording chosen=s.row;String[] actions={"Скачать SD","Скачать 4К","Отправить SD","Отправить 4К","На весь экран"};new AlertDialog.Builder(getContext()).setTitle(s.camera.name).setItems(actions,(d,n)->{if(disposed||s.row==null)return;if(n==4){listener.onFocus(s.camera,time);return;}listener.onDownload(chosen,n%2==1,n>=2);}).show();}
    private void clearPlayer(Slot s){++s.version;if(s.video!=null){s.video.stopPlayback();s.video.setVisibility(INVISIBLE);}s.player=null;++s.seekVersion;s.ready=s.loading=s.buffering=s.failed=s.seeking=false;s.row=null;}
    private void clearSlots(){for(Slot s:slots.values())clearPlayer(s);slots.clear();}
    private boolean visible(Slot s){return focused==null||focused.equals(s.camera.id);}
    private void loadSlot(Slot s,ApiClient.Recording row){
        clearPlayer(s);s.row=row;if(!visible(s))return;
        if(row==null){state(s,"Нет записи в этот момент");return;}
        final int version=s.version,request=generation;final boolean requestedHigh=high;s.loading=true;state(s,"Загрузка…");
        workers.execute(()->{String url=null,problem=null;try{if(requestedHigh)url=row.url;else{ApiClient.Variants v=api.variants(row);url=v.lowUrl;if(url==null||url.isEmpty())problem="SD отсутствует · выберите 4К";}}catch(Exception e){problem="Не удалось открыть запись";}final String mediaUrl=url,error=problem;ui.post(()->{
            if(disposed||request!=generation||version!=s.version)return;
            if(error!=null){s.loading=false;s.failed=true;state(s,error);return;}
            s.video.setOnPreparedListener(mp->{if(!valid(s,request,version))return;s.player=mp;s.ready=true;s.loading=false;mp.setVolume(0,0);mp.setOnSeekCompleteListener(done->{if(valid(s,request,version)){s.seeking=false;syncPlayback();}});applySpeed(s,mp);seekSlot(s,ArchiveTimeline.offset(row,time));state(s,requestedHigh?"4К":"SD");syncPlayback();});
            s.video.setOnInfoListener((mp,what,extra)->{if(!valid(s,request,version))return false;if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START){s.buffering=true;state(s,"Буферизация…");}else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END){s.buffering=false;state(s,requestedHigh?"4К":"SD");}syncPlayback();return false;});
            s.video.setOnCompletionListener(mp->{if(!valid(s,request,version)||!active||!playing||s.seeking)return;time=ArchiveTimeline.clamp(Math.max(time,row.endMs),dayStart,dayEnd);if(time>=dayEnd-1)playing=false;sync();});
            s.video.setOnErrorListener((mp,what,extra)->{if(valid(s,request,version)){s.loading=false;s.buffering=false;s.failed=true;state(s,"Ошибка видео · смените качество");}return true;});
            s.video.setVisibility(VISIBLE);s.video.setVideoURI(Uri.parse(mediaUrl));ui.postDelayed(()->{if(valid(s,request,version)&&s.loading){s.video.stopPlayback();s.loading=false;s.failed=true;state(s,"Сервер не ответил · нажмите SD/4К");}},25000);
        });});
    }
    private boolean valid(Slot s,int request,int version){return !disposed&&generation==request&&s.version==version;}
    private void applySpeed(Slot s){if(s.ready&&s.player!=null){applySpeed(s,s.player);syncPlayback();}}
    private void applySpeed(Slot s,MediaPlayer mp){try{boolean wasPlaying=mp.isPlaying();mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));if(!wasPlaying)mp.pause();}catch(Exception e){state(s,"Скорость не поддерживается");}}
    private void seekSlot(Slot s,int offset){if(!s.ready||s.failed)return;s.seeking=true;final int seek=++s.seekVersion,v=s.version;s.video.seekTo(offset);syncPlayback();ui.postDelayed(()->{if(!disposed&&s.version==v&&s.seekVersion==seek&&s.seeking){s.seeking=false;s.failed=true;s.video.pause();state(s,"Переход не выполнен · смените качество");syncPlayback();}},15000);}
    private boolean busy(){for(Slot s:slots.values())if(visible(s)&&s.row!=null&&!s.failed&&(s.loading||s.buffering||s.seeking))return true;return false;}
    private void syncPlayback(){boolean run=active&&playing&&!loading&&!busy();for(Slot s:slots.values())if(visible(s)&&s.ready&&!s.failed){if(run&&!s.video.isPlaying())s.video.start();else if(!run&&s.video.isPlaying())s.video.pause();}}
    private void sync(){if(disposed||loading)return;for(Slot s:slots.values()){if(!visible(s))continue;ApiClient.Recording row=ArchiveTimeline.at(s.rows,time);if(row!=s.row)loadSlot(s,row);else if(row==null)state(s,"Нет записи в этот момент");else if(row!=null&&s.ready&&!s.failed&&!s.seeking){int offset=ArchiveTimeline.offset(row,time);if(Math.abs(s.video.getCurrentPosition()-offset)>1500)seekSlot(s,offset);}}syncPlayback();update();}
    private void update(){clock.setText(clockFormat.format(new Date(time))+"  ▾");play.setText(playing?"Ⅱ":"▶");timeline.invalidate();}
    private void seek(long absolute){time=ArchiveTimeline.clamp(absolute,dayStart,dayEnd);lastTick=SystemClock.elapsedRealtime();sync();}
    private void tick(){long now=SystemClock.elapsedRealtime();if(active&&!loading&&playing&&!busy()){time+=(long)(Math.min(1000,now-lastTick)*speed);if(time>=dayEnd){time=dayEnd-1;playing=false;}}lastTick=now;if(active&&!loading){sync();if(playing)status.setText(busy()?"Буферизация…":"");}}
    public void suspend(){active=false;syncPlayback();}
    public void resume(){if(disposed)return;active=true;lastTick=SystemClock.elapsedRealtime();sync();}
    public void destroy(){if(disposed)return;disposed=true;++generation;ui.removeCallbacksAndMessages(null);clearSlots();workers.shutdownNow();}

    private void centerTimeline(boolean explicit){
        if(disposed||(!explicit&&timelineTouched))return;
        int x=ArchiveTimeline.centeredScroll(ArchiveTimeline.position(time,timeline.getWidth(),dayStart,dayEnd),timeline.getWidth(),timelineScroll.getWidth());
        timelineScroll.scrollTo(x,0);
    }
    private final class TimelineView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        long preview=-1;float downX,downY;boolean pressed,scrubbing,moved;
        final int slop=android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final Runnable hold=()->{if(pressed&&!moved){scrubbing=true;timelineTouched=true;getParent().requestDisallowInterceptTouchEvent(true);preview=ArchiveTimeline.timeAt(downX,getWidth(),dayStart,dayEnd);invalidate();}};
        TimelineView(Context context){super(context);setContentDescription("Записи за сутки. Прокрутите шкалу; коснитесь времени для перехода. Удерживайте или тяните указатель для точного выбора.");setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),top=dp(8),bottom=h-dp(27);paint.setColor(surface);canvas.drawRect(0,top,w,bottom,paint);
            int lane=0,n=Math.max(1,slots.size());
            for(Slot slot:slots.values()){paint.setColor(accent);for(ApiClient.Recording r:slot.rows){float x1=(float)ArchiveTimeline.position(Math.max(dayStart,r.startMs),w,dayStart,dayEnd),x2=(float)ArchiveTimeline.position(Math.min(dayEnd,r.endMs),w,dayStart,dayEnd);if(x2>x1)canvas.drawRect(x1,top+lane*(bottom-top)/n+1,x2,top+(lane+1)*(bottom-top)/n-1,paint);}lane++;}
            paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));
            Calendar tick=Calendar.getInstance();tick.setTimeInMillis(dayStart);
            while(tick.getTimeInMillis()<=dayEnd){long instant=tick.getTimeInMillis();float x=(float)ArchiveTimeline.position(instant,w,dayStart,dayEnd);paint.setColor(secondary);canvas.drawLine(x,bottom,x,bottom+dp(5),paint);String label=instant==dayEnd?"24:00":String.format(Locale.ROOT,"%02d:00",tick.get(Calendar.HOUR_OF_DAY));canvas.drawText(label,Math.max(0,Math.min(w-paint.measureText(label),x-paint.measureText(label)/2)),h-dp(4),paint);tick.add(Calendar.HOUR_OF_DAY,1);}
            paint.setColor(foreground);float cursor=(float)ArchiveTimeline.position(preview>=0?preview:time,w,dayStart,dayEnd);paint.setStrokeWidth(dp(2));canvas.drawLine(cursor,top-3,cursor,bottom+3,paint);canvas.drawCircle(cursor,top,dp(4),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){pressed=true;moved=false;downX=event.getX();downY=event.getY();scrubbing=Math.abs(downX-ArchiveTimeline.position(time,getWidth(),dayStart,dayEnd))<=dp(14);if(scrubbing){timelineTouched=true;getParent().requestDisallowInterceptTouchEvent(true);preview=ArchiveTimeline.timeAt(downX,getWidth(),dayStart,dayEnd);}else ui.postDelayed(hold,android.view.ViewConfiguration.getLongPressTimeout());return true;}
            if(action==MotionEvent.ACTION_MOVE){if(Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop){moved=true;ui.removeCallbacks(hold);}if(scrubbing){preview=ArchiveTimeline.timeAt(event.getX(),getWidth(),dayStart,dayEnd);invalidate();}return true;}
            if(action==MotionEvent.ACTION_UP){ui.removeCallbacks(hold);pressed=false;if(scrubbing||!moved){timelineTouched=true;seek(ArchiveTimeline.timeAt(event.getX(),getWidth(),dayStart,dayEnd));performClick();}preview=-1;scrubbing=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;}
            if(action==MotionEvent.ACTION_CANCEL){ui.removeCallbacks(hold);pressed=false;preview=-1;scrubbing=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
