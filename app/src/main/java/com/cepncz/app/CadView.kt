package com.cepncz.app
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.Toast
import kotlin.math.*

class CadView(c:Context):View(c){
 private val orange=Paint(1).apply{color=Color.rgb(255,152,0);strokeWidth=2f}
 private val grid=Paint(1).apply{color=Color.rgb(50,55,60);strokeWidth=1f;style=Paint.Style.STROKE}
 private var zoom=1f;private var ox=0f;private var oy=0f;private var lx=0f;private var ly=0f
 private var objects:List<NczObject> = emptyList()
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private var moved=false
 private val scaleDetector=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(.25f,30f);invalidate();return true}})
 fun setNczObjects(o:List<NczObject>){objects=o;if(o.isNotEmpty()){minX=o.minOf{it.minX};maxX=o.maxOf{it.maxX};minY=o.minOf{it.minY};maxY=o.maxOf{it.maxY}};zoom=1f;ox=0f;oy=0f;invalidate()}
 private fun base():Float{if(width<=0||height<=0)return 1f;val dx=(maxX-minX).coerceAtLeast(.001);val dy=(maxY-minY).coerceAtLeast(.001);return min((width-70f)/dx.toFloat(),(height-70f)/dy.toFloat())}
 private fun sx(x:Double)=((35f+(x-minX).toFloat()*base()-width/2f)*zoom+width/2f+ox)
 private fun sy(y:Double)=((35f+(maxY-y).toFloat()*base()-height/2f)*zoom+height/2f+oy)
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(25,28,32))
  if(objects.isNotEmpty()){for(o in objects){val x1=sx(o.minX);val y1=sy(o.maxY);val x2=sx(o.maxX);val y2=sy(o.minY);if(abs(x2-x1)>1f||abs(y2-y1)>1f)c.drawRect(x1,y1,x2,y2,grid);c.drawCircle(sx(o.anchor.x),sy(o.anchor.y),2.5f,orange)}}else{orange.textSize=34f;c.drawText("NCZ dosyası açın",28f,48f,orange)}
 }
 private fun select(x:Float,y:Float){val hit=objects.minByOrNull{hypot((sx(it.anchor.x)-x).toDouble(),(sy(it.anchor.y)-y).toDouble())}?:return;val dist=hypot((sx(hit.anchor.x)-x).toDouble(),(sy(hit.anchor.y)-y).toDouble());if(dist<40)Toast.makeText(context,"Tip: "+hit.type+"\nX: %.3f  Y: %.3f".format(hit.anchor.x,hit.anchor.y),Toast.LENGTH_LONG).show()}
 override fun onTouchEvent(e:MotionEvent):Boolean{scaleDetector.onTouchEvent(e);if(e.pointerCount==1&&!scaleDetector.isInProgress){when(e.actionMasked){MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y;moved=false};MotionEvent.ACTION_MOVE->{val dx=e.x-lx;val dy=e.y-ly;if(abs(dx)+abs(dy)>3)moved=true;ox+=dx;oy+=dy;lx=e.x;ly=e.y;invalidate()};MotionEvent.ACTION_UP->{if(!moved)select(e.x,e.y)}}};return true}
}
