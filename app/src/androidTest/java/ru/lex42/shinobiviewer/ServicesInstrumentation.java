package ru.lex42.shinobiviewer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Standalone on-device checks; never contacts a real camera or uses real credentials. */
public class ServicesInstrumentation extends Instrumentation {
    private boolean uiSmoke;
    @Override public void onCreate(Bundle args){super.onCreate(args);uiSmoke=args!=null&&"true".equals(args.getString("uiSmoke"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle(); Activity activity=null;
        try {
            Context context=getTargetContext();
            CredentialsStore store=new CredentialsStore(context);
            store.clear();store.save("https://example.invalid/","test-user","test-secret-only");
            CredentialsStore.Credential loaded=store.load("https://EXAMPLE.invalid:443");
            check(loaded!=null&&loaded.username.equals("test-user")&&loaded.password.equals("test-secret-only"),"Keystore round trip");
            check(store.load("https://other.invalid")==null,"server scope");
            String disk=context.getSharedPreferences("encrypted_login",0).getString("record","");
            check(!disk.contains("test-secret-only")&&!disk.contains("test-user"),"encrypted persistence");
            store.clear();check(store.load("https://example.invalid")==null,"credential removal");
            if(uiSmoke)context.getSharedPreferences("MainActivity",0).edit().putString("server","http://10.0.2.2:18765").commit();
            android.content.SharedPreferences themePrefs=context.getSharedPreferences("MainActivity",0);
            themePrefs.edit().putString("theme","dark").remove("system_theme_migrated_v1").commit();
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check("system".equals(themePrefs.getString("theme",""))&&themePrefs.getBoolean("system_theme_migrated_v1",false),"legacy theme migrates to system once");
            themePrefs.edit().putString("theme","light").commit();
            Activity first=activity;runOnMainSync(first::finish);waitForIdleSync();
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check("light".equals(themePrefs.getString("theme","")),"explicit post-migration theme survives restart");
            Activity host=activity;
            runOnMainSync(()->{
                ArchiveViewer viewer=new ArchiveViewer(host,null,java.util.Collections.emptyList(),true,new ArchiveViewer.Listener(){public void onDownload(ApiClient.Recording r,boolean h,boolean s){}public void onFocus(ApiClient.Camera c,long t){}});
                try{
                    int width=360,height=640;
                    viewer.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));viewer.layout(0,0,width,height);
                    android.widget.HorizontalScrollView scroll=findTimelineScroll(viewer);check(scroll!=null,"timeline scroll exists with no selected cameras");
                    android.view.View timeline=scroll.getChildAt(0);
                    check(timeline.getMeasuredWidth()==8*scroll.getMeasuredWidth(),"timeline retains eight viewport width under UNSPECIFIED child measure");
                    check(timeline.getMeasuredHeight()==(int)(76*host.getResources().getDisplayMetrics().density+.5f),"timeline nonzero fixed height");
                }finally{viewer.destroy();}
            });
            runOnMainSync(()->checkArchiveFocus(host));
            MediaFiles media=new MediaFiles(activity);
            CountDownLatch latch=new CountDownLatch(1);AtomicReference<Uri> saved=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
            Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.MAGENTA);
            media.screenshot(bitmap,"instrumentation-test",false,new MediaFiles.Progress(){
                public void onProgress(long b,long t){}
                public void onComplete(Uri uri){saved.set(uri);latch.countDown();}
                public void onError(String message){error.set(message);latch.countDown();}
            });
            check(latch.await(30,TimeUnit.SECONDS),"screenshot completion timeout");
            check(error.get()==null&&saved.get()!=null,"screenshot save");
            try(InputStream in=context.getContentResolver().openInputStream(saved.get())){
                Bitmap decoded=BitmapFactory.decodeStream(in);
                check(decoded!=null&&decoded.getWidth()==32&&decoded.getHeight()==24&&decoded.getPixel(0,0)==Color.MAGENTA,"PNG export pixel round trip");
                decoded.recycle();
            } finally {context.getContentResolver().delete(saved.get(),null,null);media.close();bitmap.recycle();}
            if(uiSmoke)checkUi(activity);
            result.putString("stream","PASS: Keystore round trip, server scope, encrypted persistence, credential removal, PNG MediaStore save/read/pixel round trip, Android timeline measurement and theme migration\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally {if(activity!=null){Activity target=activity;runOnMainSync(target::finish);}}
    }
    private android.view.View find(android.view.View root,String label){
        if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)root;if(label.contentEquals(t.getText())||(t.getHint()!=null&&label.contentEquals(t.getHint())))return root;}
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){android.view.View found=find(g.getChildAt(i),label);if(found!=null)return found;}}return null;
    }
    private void checkArchiveFocus(Activity host){
        AtomicReference<ApiClient.Recording> focused=new AtomicReference<>();AtomicLong at=new AtomicLong();AtomicBoolean high=new AtomicBoolean();
        ArchiveViewer viewer=new ArchiveViewer(host,null,java.util.Collections.emptyList(),true,new ArchiveViewer.Listener(){
            public void onDownload(ApiClient.Recording r,boolean h,boolean s){}
            public void onFocus(ApiClient.Camera camera,ApiClient.Recording record,long time,boolean quality){focused.set(record);at.set(time);high.set(quality);}
        });
        try{
            Class<?> slotType=Class.forName("ru.lex42.shinobiviewer.ArchiveViewer$Slot");java.lang.reflect.Constructor<?> ctor=slotType.getDeclaredConstructor();ctor.setAccessible(true);Object slot=ctor.newInstance();
            ApiClient.Camera camera=new ApiClient.Camera(new org.json.JSONObject().put("mid","test-camera").put("name","Test"));
            ApiClient.Recording record=new ApiClient.Recording(camera.id,"test","https://example.invalid/test.mp4",1000,9000);
            android.widget.VideoView video=new android.widget.VideoView(host);
            field(slotType,slot,"camera",camera);field(slotType,slot,"rows",java.util.Collections.singletonList(record));field(slotType,slot,"row",record);field(slotType,slot,"video",video);field(slotType,slot,"version",7);
            java.lang.reflect.Field slots=ArchiveViewer.class.getDeclaredField("slots");slots.setAccessible(true);((java.util.Map)slots.get(viewer)).put(camera.id,slot);
            field(ArchiveViewer.class,viewer,"time",4000L);field(ArchiveViewer.class,viewer,"high",true);field(ArchiveViewer.class,viewer,"loading",true);
            java.lang.reflect.Method focus=ArchiveViewer.class.getDeclaredMethod("focus",String.class);focus.setAccessible(true);focus.invoke(viewer,camera.id);
            check(focused.get()==record&&at.get()==4000&&high.get(),"focus passes selected interval, time and quality while loading");
            check(read(slotType,slot,"video")==video&&read(slotType,slot,"row")==record&&((Integer)read(slotType,slot,"version"))==7,"focus never clears/rebuilds player");
            focused.set(null);field(slotType,slot,"rows",java.util.Collections.emptyList());focus.invoke(viewer,camera.id);
            check(focused.get()==null&&read(slotType,slot,"video")==video&&((Integer)read(slotType,slot,"version"))==7,"missing interval leaves existing player intact");
        }catch(Exception failure){throw new AssertionError("archive focus regression",failure);}finally{viewer.destroy();}
    }
    private static void field(Class<?> type,Object owner,String name,Object value)throws Exception{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object read(Class<?> type,Object owner,String name)throws Exception{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private android.widget.HorizontalScrollView findTimelineScroll(android.view.View root){
        if(root instanceof android.widget.HorizontalScrollView)return (android.widget.HorizontalScrollView)root;
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){android.widget.HorizontalScrollView found=findTimelineScroll(g.getChildAt(i));if(found!=null)return found;}}return null;
    }
    private android.view.View description(android.view.View root,String label){
        if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.view.View found=description(group.getChildAt(i),label);if(found!=null)return found;}}return null;
    }
    private void capture(Activity a,String name)throws Exception{
        Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"UI screenshot");try(OutputStream out=new FileOutputStream(new File(a.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
    private void checkUi(Activity a)throws Exception{
        capture(a,"login");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();android.widget.EditText login=(android.widget.EditText)find(root,"Логин"),password=(android.widget.EditText)find(root,"Пароль");check(login!=null&&password!=null,"username and password fields");check((login.getInputType()&android.text.InputType.TYPE_MASK_VARIATION)!=android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,"login permits non-email username");check(login.getImeOptions()==android.view.inputmethod.EditorInfo.IME_ACTION_NEXT&&password.getImeOptions()==android.view.inputmethod.EditorInfo.IME_ACTION_DONE,"keyboard next and done actions");login.setText("demo@example.test");login.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);check(password.hasFocus(),"Next focuses password");password.setText("demo");password.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);});
        long until=System.currentTimeMillis()+30000;AtomicBoolean home=new AtomicBoolean();
        while(System.currentTimeMillis()<until&&!home.get()){runOnMainSync(()->home.set(find(a.getWindow().getDecorView(),"Записи")!=null));Thread.sleep(250);}
        check(home.get(),"mock login and camera list");capture(a,"cameras");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();check(find(root,"Переключить сетку и ленту")==null,"feed-only wall has no layout toggle");find(root,"Настройки").performClick();});
        capture(a,"settings");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();check(find(root,"Тема приложения")!=null,"dedicated settings page");find(root,"Тёмная").performClick();check(find(a.getWindow().getDecorView(),"Системная")!=null,"theme setting survives redraw");find(a.getWindow().getDecorView(),"Системная").performClick();find(a.getWindow().getDecorView(),"Назад").performClick();});
        runOnMainSync(()->{
            android.view.View root=a.getWindow().getDecorView();android.view.View records=description(root,"Записи камеры Крыльцо");check(records!=null,"direct camera recordings action");records.performClick();
            root=a.getWindow().getDecorView();check(description(root,"Назад к списку")!=null,"tile recordings preserve home origin");description(root,"Назад к списку").performClick();check(description(a.getWindow().getDecorView(),"Записи камеры Крыльцо")!=null,"records back returns camera feed");
        });
        runOnMainSync(()->{check(find(a.getWindow().getDecorView(),"Крыльцо")!=null,"authorized camera tile");description(a.getWindow().getDecorView(),"Записи").performClick();});
        Thread.sleep(2000);capture(a,"viewer");
        runOnMainSync(()->check(find(a.getWindow().getDecorView(),"Выберите камеры")!=null,"viewer navigation"));
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
