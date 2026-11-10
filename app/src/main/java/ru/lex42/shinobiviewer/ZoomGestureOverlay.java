package ru.lex42.shinobiviewer;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Touch overlay: pinch 1–5x, pan within bounds; optional double-tap/long-press actions. */
public final class ZoomGestureOverlay extends View {
    private final View target;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private float scale=1,dx,dy;
    public ZoomGestureOverlay(Context context, View target, Runnable doubleTap, Runnable longPress) {
        super(context);this.target=target;setContentDescription("Масштабирование видео двумя пальцами");
        scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){scale=Math.max(1,Math.min(5,scale*detector.getScaleFactor()));apply();return true;}
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onDoubleTap(MotionEvent e){if(scale>1.01f)reset();else if(doubleTap!=null)doubleTap.run();return true;}
            @Override public void onLongPress(MotionEvent e){if(longPress!=null&&scale==1)longPress.run();}
            @Override public boolean onScroll(MotionEvent e1,MotionEvent e2,float x,float y){if(scale<=1||scaleDetector.isInProgress())return false;dx-=x;dy-=y;apply();return true;}
        });
    }
    private void apply(){float mx=getWidth()*(scale-1)/2,my=getHeight()*(scale-1)/2;dx=Math.max(-mx,Math.min(mx,dx));dy=Math.max(-my,Math.min(my,dy));target.setScaleX(scale);target.setScaleY(scale);target.setTranslationX(dx);target.setTranslationY(dy);}
    public void reset(){scale=1;dx=dy=0;apply();}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getPointerCount()>1||scale>1)getParent().requestDisallowInterceptTouchEvent(true);scaleDetector.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();getParent().requestDisallowInterceptTouchEvent(false);}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
