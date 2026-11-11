package ru.lex42.shinobiviewer;

import android.content.ClipData;
import android.content.Context;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/** Repositions existing video surfaces: a drag never detaches or recreates a player. */
public final class LiveWall extends ViewGroup {
 public interface Listener { void onOrderChanged(List<String> ids); }
 private final List<View> order=new ArrayList<>();
 private final Listener listener;
 private int columns=2,gap,cellWidth,cellHeight;
 private View dragged;
 private boolean animateLayout;
 public LiveWall(Context context,Listener listener){super(context);this.listener=listener;gap=Math.round(6*getResources().getDisplayMetrics().density);setClipChildren(false);setOnDragListener((v,event)->drag(event));}
 public void addCamera(View view,String id){view.setTag(id);order.add(view);addView(view);}
 public void setColumns(int count){if(columns==count)return;columns=count;animateLayout=true;requestLayout();}
 public void beginDrag(View view){if(!order.contains(view))return;if(view.startDragAndDrop(ClipData.newPlainText("camera",String.valueOf(view.getTag())),new DragShadowBuilder(view),view,0)){dragged=view;view.setAlpha(.3f);performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);}}
 private boolean drag(DragEvent e){switch(e.getAction()){
  case DragEvent.ACTION_DRAG_STARTED:return e.getLocalState() instanceof View&&order.contains(e.getLocalState());
  case DragEvent.ACTION_DRAG_LOCATION:
   if(dragged==null&&e.getLocalState() instanceof View)dragged=(View)e.getLocalState();
   int target=Math.min(order.size()-1,Math.max(0,(int)(e.getY()/(cellHeight+gap))*columns+Math.min(columns-1,Math.max(0,(int)(e.getX()/(cellWidth+gap))))));
   int from=order.indexOf(dragged);if(from>=0&&target!=from){order.remove(from);order.add(target,dragged);animateLayout=true;requestLayout();}
   if(getParent() instanceof ScrollView){ScrollView scroll=(ScrollView)getParent();float y=e.getY()-scroll.getScrollY();int edge=gap*10;if(y<edge)scroll.smoothScrollBy(0,-edge);else if(y>scroll.getHeight()-edge)scroll.smoothScrollBy(0,edge);}return true;
  case DragEvent.ACTION_DROP:return true;
  case DragEvent.ACTION_DRAG_ENDED:
   if(dragged!=null){dragged.setAlpha(1);dragged=null;List<String> ids=new ArrayList<>();for(View child:order)ids.add(String.valueOf(child.getTag()));listener.onOrderChanged(ids);}return true;
  default:return true;
 }}
 @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);cellWidth=Math.max(1,(width-gap*(columns-1))/columns);cellHeight=Math.round(cellWidth*9f/16f);for(View child:order)child.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(cellHeight,MeasureSpec.EXACTLY));int rows=(order.size()+columns-1)/columns;setMeasuredDimension(width,Math.max(0,rows*(cellHeight+gap)-gap));}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<order.size();i++){View child=order.get(i);float oldX=child.getX(),oldY=child.getY();int x=(i%columns)*(cellWidth+gap),y=(i/columns)*(cellHeight+gap);boolean laidOut=child.isLaidOut();child.animate().cancel();child.layout(x,y,x+cellWidth,y+cellHeight);if(animateLayout&&laidOut){child.setTranslationX(oldX-x);child.setTranslationY(oldY-y);child.animate().translationX(0).translationY(0).setDuration(180).start();}else{child.setTranslationX(0);child.setTranslationY(0);}}animateLayout=false;}
}
