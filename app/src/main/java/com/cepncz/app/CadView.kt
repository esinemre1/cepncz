package com.cepncz.app
import android.content.Context
import android.graphics.*
import android.view.*

class CadView(c:Context):View(c){
 private val orange=Paint(1).apply{color=Color.rgb(255,152,0);strokeWidth=2f}
 private val grid=Paint(1).apply{color=Color.rgb(50,55,60);strokeWidth=1f}
 private var zoom=1f; private var ox=0f; private var oy=0f; private var lx=0f; private var ly=0f
 private var points:List<NczPoint> = emptyList()\n private var objects:List<NczObject> = emptyList()
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val scaleDetector=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(.25f,30f);invalidate();return true}})
 fun setNczObjects(o:List<NczObject>){objects=o;points=o.map{it.anchor};if(o.isNotEmpty()){minX=o.minOf{it.minX};maxX=o.maxOf{it.maxX};minY=o.minOf{it.minY};maxY=o.maxOf{it.maxY}};zoom=1f;ox=0f;oy=0f;invalidate()}
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(25,28,32))
  val s=50f*zoom;var gx=ox%s;while(gx<width){c.drawLine(gx,0f,gx,height.toFloat(),grid);gx+=s};var gy=oy%s;while(gy<height){c.drawLine(0f,gy,width.toFloat(),gy,grid);gy+=s}
  if(points.isNotEmpty()){val pad=35f;val sx=(width-2*pad)/(maxX-minX).toFloat();val sy=(height-2*pad)/(maxY-minY).toFloat();val base=minOf(sx,sy)
   for(obj in objects){val x1=(pad+(obj.minX-minX).toFloat()*base-width/2f)*zoom+width/2f+ox;val y1=(pad+(maxY-obj.maxY).toFloat()*base-height/2f)*zoom+height/2f+oy;val x2=(pad+(obj.maxX-minX).toFloat()*base-width/2f)*zoom+width/2f+ox;val y2=(pad+(maxY-obj.minY).toFloat()*base-height/2f)*zoom+height/2f+oy;if(kotlin.math.abs(x2-x1)>1f||kotlin.math.abs(y2-y1)>1f)c.drawRect(x1,y1,x2,y2,grid);val x=(pad+(obj.anchor.x-minX).toFloat()*base-width/2f)*zoom+width/2f+ox;val y=(pad+(maxY-obj.anchor.y).toFloat()*base-height/2f)*zoom+height/2f+oy;c.drawCircle(x,y,2.2f,orange)}
  } else {orange.textSize=34f;c.drawText("NCZ dosyası açın",28f,48f,orange)}
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{scaleDetector.onTouchEvent(e);if(e.pointerCount==1&&!scaleDetector.isInProgress){when(e.actionMasked){MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y};MotionEvent.ACTION_MOVE->{ox+=e.x-lx;oy+=e.y-ly;lx=e.x;ly=e.y;invalidate()}}};return true}
}
