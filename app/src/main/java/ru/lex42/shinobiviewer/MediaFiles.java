package ru.lex42.shinobiviewer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-requested media exports. No tokens, URLs, or credentials enter file names or logs. */
public final class MediaFiles implements AutoCloseable {
    public interface Progress {
        void onProgress(long bytes,long total);
        void onComplete(Uri uri);
        void onError(String message);
    }
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    public MediaFiles(Activity activity){this.activity=activity;}
    public void screenshot(Bitmap bitmap,String name){screenshot(bitmap,name,false,null);}
    public void screenshot(Bitmap bitmap,String name,boolean share,Progress callback){
        if(closed)return;
        final Bitmap copy;
        try{copy=bitmap.copy(Bitmap.Config.ARGB_8888,false);if(copy==null)throw new IOException();}
        catch(Exception e){failure(callback,"Не удалось сохранить снимок.");return;}
        worker.execute(()->{
            Target target=null;
            try{
                target=new Target(safeName(name,".png"),"image/png");
                try(OutputStream out=target.open()){if(!copy.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}
                target.finish();success(target.uri,"image/png",share,callback);
            }catch(Exception e){if(target!=null)target.remove();failure(callback,"Не удалось сохранить снимок.");}
            finally{copy.recycle();}
        });
    }
    public void download(String url,String name,boolean share,Progress callback){
        if(closed)return;
        worker.execute(()->{
            Target target=null;HttpURLConnection connection=null;
            try{
                URL origin=new URL(url),next=origin;
                if(!("https".equals(origin.getProtocol())||"http".equals(origin.getProtocol()))||origin.getUserInfo()!=null)throw new IOException();
                for(int attempt=0;attempt<4;attempt++){
                    connection=(HttpURLConnection)next.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestProperty("Accept-Encoding","identity");
                    int code=connection.getResponseCode();
                    if(code==200)break;
                    if(code!=301&&code!=302&&code!=303&&code!=307&&code!=308)throw new IOException();
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException();
                    next=new URL(next,location);
                    if(!sameOrigin(origin,next)||next.getUserInfo()!=null)throw new IOException();
                    connection.disconnect();connection=null;
                }
                if(connection==null||connection.getResponseCode()!=200)throw new IOException();
                String type=connection.getContentType();
                if(type!=null&&!type.toLowerCase(Locale.ROOT).startsWith("video/")&&!type.toLowerCase(Locale.ROOT).startsWith("application/octet-stream"))throw new IOException();
                long total=connection.getContentLengthLong();if(total==0)throw new IOException();
                target=new Target(safeName(name,".mp4"),"video/mp4");long count=0,last=0;
                try(InputStream in=connection.getInputStream();OutputStream out=target.open()){
                    byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1){
                        if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                        out.write(buffer,0,n);count+=n;long now=System.currentTimeMillis();
                        if(now-last>=300){long done=count;ui(()->{if(callback!=null)callback.onProgress(done,total);});last=now;}
                    }
                }
                if(count==0||(total>=0&&count!=total))throw new IOException();
                target.finish();success(target.uri,"video/mp4",share,callback);
            }catch(Exception e){if(target!=null)target.remove();failure(callback,"Не удалось скачать запись. Проверьте соединение и свободное место.");}
            finally{if(connection!=null)connection.disconnect();}
        });
    }
    public static void share(Activity activity,Uri uri,String mime){
        Intent intent=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri)
             .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Запись",uri));
        activity.startActivity(Intent.createChooser(intent,"Отправить"));
    }
    private void success(Uri uri,String mime,boolean share,Progress callback){ui(()->{
        if(callback!=null)callback.onComplete(uri);else Toast.makeText(activity,"Сохранено",Toast.LENGTH_SHORT).show();
        if(share)try{share(activity,uri,mime);}catch(Exception e){Toast.makeText(activity,"Файл сохранён. Нет приложения для отправки.",Toast.LENGTH_LONG).show();}
    });}
    private void failure(Progress callback,String message){ui(()->{if(callback!=null)callback.onError(message);else Toast.makeText(activity,message,Toast.LENGTH_LONG).show();});}
    private void ui(Runnable action){main.post(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    @Override public void close(){closed=true;worker.shutdownNow();}
    static String safeName(String value,String extension){
        String name=value==null?"Камеры":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        if(name.toLowerCase(Locale.ROOT).endsWith(extension))name=name.substring(0,name.length()-extension.length());
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="Камеры";
        return name.substring(0,Math.min(100,name.length()))+extension;
    }
    private static boolean sameOrigin(URL a,URL b){return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?a.getDefaultPort():a.getPort())==(b.getPort()<0?b.getDefaultPort():b.getPort());}
    private final class Target {
        final Uri uri;final String mime;File file;
        Target(String name,String mime)throws IOException{
            this.mime=mime;
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.IS_PENDING,1);
                boolean picture=mime.startsWith("image/");values.put(MediaStore.MediaColumns.RELATIVE_PATH,(picture?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_DOWNLOADS)+"/Камеры");
                uri=activity.getContentResolver().insert(picture?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(uri==null)throw new IOException();
            }else{
                File external=activity.getExternalFilesDir(null);if(external==null)throw new IOException();
                File dir=new File(external,"exports");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException();
                file=new File(dir,UUID.randomUUID().toString().substring(0,8)+"_"+name);
                uri=new Uri.Builder().scheme("content").authority(activity.getPackageName()+".media").appendPath(file.getName()).build();
            }
        }
        OutputStream open()throws IOException{OutputStream out=file!=null?new FileOutputStream(file):activity.getContentResolver().openOutputStream(uri,"w");if(out==null)throw new IOException();return out;}
        void finish()throws IOException{
            if(closed)throw new InterruptedIOException();
            if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(activity.getContentResolver().update(uri,values,null,null)!=1)throw new IOException();}
        }
        void remove(){if(file!=null)file.delete();else try{activity.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
    }
}
