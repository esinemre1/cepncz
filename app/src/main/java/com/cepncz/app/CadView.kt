package com.cepncz.app
import android.content.Context
import android.graphics.*
import android.view.*

class CadView(c:Context):View(c){
 private val orange=Paint(1).apply{color=Color.rgb(255,152,0);strokeWidth=2f}
 private val grid=Paint(1).apply{color=Color.rgb(50,55,60);strokeWidth=1f}
 private var zoom=1f; private var ox=0f; private var oy=0f; private var lx=0f; private var ly=0f
 private var points:List<NczPoint> = emptyList()
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val scaleDetector=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(.25f,30f);invalidate();return true}})
 fun setNczPoints(p:List<NczPoint>){points=p;if(p.isNotEmpty()){minX=p.minOf{it.x};maxX=p.maxOf{it.x};minY=p.minOf{it.y};maxY=p.maxOf{it.y}};zoom=1f;ox=0f;oy=0f;invalidate()}
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(25,28,32))
  val s=50f*zoom;var gx=ox%s;while(gx<width){c.drawLine(gx,0f,gx,height.toFloat(),grid);gx+=s};var gy=oy%s;while(gy<height){c.drawLine(0f,gy,width.toFloat(),gy,grid);gy+=s}
  if(points.isNotEmpty()){val pad=35f;val sx=(width-2*pad)/(maxX-minX).toFloat();val sy=(height-2*pad)/(maxY-minY).toFloat();val base=minOf(sx,sy)
   for(pt in points){val x=(pad+(pt.x-minX).toFloat()*base-width/2f)*zoom+width/2f+ox;val y=(pad+(maxY-pt.y).toFloat()*base-height/2f)*zoom+height/2f+oy;c.drawCircle(x,y,2.4f,orange)}
  } else {orange.textSize=34f;c.drawText("NCZ dosyası açın",28f,48f,orange)}
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{scaleDetector.onTouchEvent(e);if(e.pointerCount==1&&!scaleDetector.isInProgress){when(e.actionMasked){MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y};MotionEvent.ACTION_MOVE->{ox+=e.x-lx;oy+=e.y-ly;lx=e.x;ly=e.y;invalidate()}}};return true}
}
