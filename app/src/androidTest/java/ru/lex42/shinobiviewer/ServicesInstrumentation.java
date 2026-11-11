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
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
            result.putString("stream","PASS: Keystore round trip, server scope, encrypted persistence, credential removal, PNG MediaStore save/read/pixel round trip\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally {if(activity!=null){Activity target=activity;runOnMainSync(target::finish);}}
    }
    private android.view.View find(android.view.View root,String label){
        if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)root;if(label.contentEquals(t.getText())||(t.getHint()!=null&&label.contentEquals(t.getHint())))return root;}
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){android.view.View found=find(g.getChildAt(i),label);if(found!=null)return found;}}return null;
    }
    private void capture(Activity a,String name)throws Exception{
        Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"UI screenshot");try(OutputStream out=new FileOutputStream(new File(a.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
    private void checkUi(Activity a)throws Exception{
        capture(a,"login");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();((android.widget.EditText)find(root,"Электронная почта")).setText("demo@example.test");((android.widget.EditText)find(root,"Пароль")).setText("demo");find(root,"Войти").performClick();});
        long until=System.currentTimeMillis()+30000;AtomicBoolean home=new AtomicBoolean();
        while(System.currentTimeMillis()<until&&!home.get()){runOnMainSync(()->home.set(find(a.getWindow().getDecorView(),"Записи")!=null));Thread.sleep(250);}
        check(home.get(),"mock login and camera list");capture(a,"cameras");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();check(find(root,"Переключить сетку и ленту")!=null,"single wall toggle");find(root,"Переключить сетку и ленту").performClick();find(root,"Настройки").performClick();});
        capture(a,"settings");
        runOnMainSync(()->{android.view.View root=a.getWindow().getDecorView();check(find(root,"Тема приложения")!=null,"dedicated settings page");find(root,"Тёмная").performClick();check(find(a.getWindow().getDecorView(),"Системная")!=null,"theme setting survives redraw");find(a.getWindow().getDecorView(),"Системная").performClick();find(a.getWindow().getDecorView(),"Назад").performClick();});
        runOnMainSync(()->{check(find(a.getWindow().getDecorView(),"Крыльцо")!=null,"authorized camera tile");find(a.getWindow().getDecorView(),"Записи").performClick();});
        Thread.sleep(2000);capture(a,"viewer");
        runOnMainSync(()->check(find(a.getWindow().getDecorView(),"Выберите камеры")!=null,"viewer navigation"));
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
