package com.cepncz.app
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.Toast
import kotlin.math.*

class CadView(c:Context):View(c){
 private val line=Paint(1).apply{color=Color.rgb(255,170,55);strokeWidth=2f;style=Paint.Style.STROKE}
 private val point=Paint(1).apply{color=Color.rgb(255,210,90);style=Paint.Style.FILL}
 private var entities:List<NczEntity> = emptyList();private var zoom=1f;private var ox=0f;private var oy=0f;private var lx=0f;private var ly=0f;private var moved=false
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val sd=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(.2f,50f);invalidate();return true}})
 fun setEntities(e:List<NczEntity>){entities=e;val p=e.flatMap{it.points};if(p.isNotEmpty()){minX=p.minOf{it.x};maxX=p.maxOf{it.x};minY=p.minOf{it.y};maxY=p.maxOf{it.y}};zoom=1f;ox=0f;oy=0f;invalidate()}
 private fun base():Float=min((width-60f)/(maxX-minX).coerceAtLeast(.001).toFloat(),(height-60f)/(maxY-minY).coerceAtLeast(.001).toFloat())
 private fun sx(x:Double)=((30f+(x-minX).toFloat()*base()-width/2f)*zoom+width/2f+ox)
 private fun sy(y:Double)=((30f+(maxY-y).toFloat()*base()-height/2f)*zoom+height/2f+oy)
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(24,27,31));if(entities.isEmpty()){line.textSize=32f;c.drawText("NCZ dosyası açın",28f,50f,line);return}
  for(e in entities){if(e.points.size==1)c.drawCircle(sx(e.points[0].x),sy(e.points[0].y),3f,point) else {val path=Path();path.moveTo(sx(e.points[0].x),sy(e.points[0].y));for(i in 1 until e.points.size)path.lineTo(sx(e.points[i].x),sy(e.points[i].y));if(e.kind=="Polygon")path.close();c.drawPath(path,line)}}
 }
 private fun inspect(x:Float,y:Float){var best:NczEntity?=null;var bd=Double.MAX_VALUE;for(e in entities)for(p in e.points){val d=hypot((sx(p.x)-x).toDouble(),(sy(p.y)-y).toDouble());if(d<bd){bd=d;best=e}};if(best!=null&&bd<45){val p=best!!.points.first();Toast.makeText(context,best!!.kind+" • Tabaka "+best!!.layer+"\nX: %.3f  Y: %.3f".format(p.x,p.y),Toast.LENGTH_LONG).show()}}
 override fun onTouchEvent(e:MotionEvent):Boolean{sd.onTouchEvent(e);if(e.pointerCount==1&&!sd.isInProgress)when(e.actionMasked){MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y;moved=false};MotionEvent.ACTION_MOVE->{val dx=e.x-lx;val dy=e.y-ly;if(abs(dx)+abs(dy)>3)moved=true;ox+=dx;oy+=dy;lx=e.x;ly=e.y;invalidate()};MotionEvent.ACTION_UP->{if(!moved)inspect(e.x,e.y)}};return true}
}
