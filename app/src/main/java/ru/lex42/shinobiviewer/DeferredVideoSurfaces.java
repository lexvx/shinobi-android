package ru.lex42.shinobiviewer;

import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** UI-thread-only surface ownership during asynchronous LibVLC stop.
 * Returning false from onSurfaceTextureDestroyed keeps the producer alive until native stop ends.
 * LibVLC's original destruction callback would synchronously disable the native video track. */
final class DeferredVideoSurfaces {
 private final List<Guard> guards=new ArrayList<>();
 DeferredVideoSurfaces(View root){capture(root);}
 private void capture(View view){
  if(view instanceof TextureView){TextureView texture=(TextureView)view;
   TextureView.SurfaceTextureListener original=texture.getSurfaceTextureListener();
   if(original!=null){Guard guard=new Guard(texture);guards.add(guard);texture.setSurfaceTextureListener(guard);}
  }
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)capture(group.getChildAt(i));}
 }
 /** Call after MediaPlayer.detachViews on main. Never release a texture still owned by a View. */
 void releaseRetained(){for(Guard guard:guards){if(guard.view.getSurfaceTextureListener()==guard)guard.view.setSurfaceTextureListener(null);for(SurfaceTexture texture:guard.retained)texture.release();guard.retained.clear();}guards.clear();}
 private static final class Guard implements TextureView.SurfaceTextureListener {
  final TextureView view;final List<SurfaceTexture> retained=new ArrayList<>();
  Guard(TextureView view){this.view=view;}
  public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){}
  public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
  public void onSurfaceTextureUpdated(SurfaceTexture texture){}
  public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){if(!retained.contains(texture))retained.add(texture);return false;}
 }
}
