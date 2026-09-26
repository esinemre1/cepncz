package com.cepncz.app
import android.content.Context
import android.graphics.*
import android.view.*
class CadView(c:Context):View(c){
 private val orange=Paint(1).apply{color=Color.rgb(255,152,0);strokeWidth=2f}
 private val grid=Paint(1).apply{color=Color.rgb(50,55,60);strokeWidth=1f}
 private var zoom=1f; private var ox=0f; private var oy=0f; private var lx=0f; private var ly=0f
 private val scaleDetector=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(.25f,20f);invalidate();return true}})
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(25,28,32));val s=50f*zoom;var x=ox%s;while(x<width){c.drawLine(x,0f,x,height.toFloat(),grid);x+=s};var y=oy%s;while(y<height){c.drawLine(0f,y,width.toFloat(),y,grid);y+=s};orange.textSize=34f;c.drawText("CepNCZ",28f,48f,orange)}
 override fun onTouchEvent(e:MotionEvent):Boolean{scaleDetector.onTouchEvent(e);if(e.pointerCount==1&&!scaleDetector.isInProgress){when(e.actionMasked){MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y};MotionEvent.ACTION_MOVE->{ox+=e.x-lx;oy+=e.y-ly;lx=e.x;ly=e.y;invalidate()}}};return true}
}
