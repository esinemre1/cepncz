package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class CadView(context: Context) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(255,170,55); strokeWidth=2f; style=Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(70,255,170,55); style=Paint.Style.FILL }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(255,210,90); style=Paint.Style.FILL }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=30f }
    private var entities:List<NczEntity> = emptyList()
    private var layers:List<String> = emptyList()
    private val hiddenLayers=mutableSetOf<Int>()
    private var filled=true
    private var showAreas=true
    private var zoom=1f
    private var offsetX=0f; private var offsetY=0f
    private var lastX=0f; private var lastY=0f; private var moved=false
    private var minX=0.0; private var maxX=1.0; private var minY=0.0; private var maxY=1.0

    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean {
            val old=zoom
            zoom=(zoom*d.scaleFactor).coerceIn(0.15f,80f)
            val fx=d.focusX; val fy=d.focusY
            if(old>0f){ val ratio=zoom/old; offsetX=fx-(fx-offsetX)*ratio; offsetY=fy-(fy-offsetY)*ratio }
            invalidate(); return true
        }
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDoubleTap(e:MotionEvent):Boolean { fitToScreen(); return true }
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean { inspect(e.x,e.y); return true }
    })

    fun setEntities(items:List<NczEntity>,layerNames:List<String> = emptyList()){ entities=items;layers=layerNames;hiddenLayers.clear();computeBounds();fitToScreen() }
    private fun computeBounds(){ val ps=entities.flatMap{it.points};if(ps.isNotEmpty()){minX=ps.minOf{it.x};maxX=ps.maxOf{it.x};minY=ps.minOf{it.y};maxY=ps.maxOf{it.y}} }
    fun fitToScreen(){ zoom=1f;offsetX=0f;offsetY=0f;invalidate() }
    fun zoomIn(){ zoom=(zoom*1.35f).coerceAtMost(80f);invalidate() }
    fun zoomOut(){ zoom=(zoom/1.35f).coerceAtLeast(0.15f);invalidate() }
    fun setFilled(v:Boolean){filled=v;invalidate()}
    fun isFilled()=filled
    fun setShowAreas(v:Boolean){showAreas=v;invalidate()}
    fun isShowAreas()=showAreas
    fun setLayerVisible(layer:Int,visible:Boolean){if(visible)hiddenLayers.remove(layer)else hiddenLayers.add(layer);invalidate()}
    fun setAllLayersVisible(visible:Boolean){hiddenLayers.clear();if(!visible)entities.map{it.layer}.distinct().forEach{hiddenLayers.add(it)};invalidate()}
    fun isLayerVisible(layer:Int)=layer !in hiddenLayers
    fun layerName(layer:Int)=layers.getOrNull(layer)?.takeIf{it.isNotBlank()}?:"Tabaka $layer"
    fun layerCounts():Map<Int,Int> = entities.groupingBy{it.layer}.eachCount()

    private fun baseScale():Float { if(width<=80||height<=80)return 1f;return min((width-70f)/(maxX-minX).coerceAtLeast(.001).toFloat(),(height-70f)/(maxY-minY).coerceAtLeast(.001).toFloat()) }
    private fun sx(x:Double)=((35f+(x-minX).toFloat()*baseScale()-width/2f)*zoom+width/2f+offsetX)
    private fun sy(y:Double)=((35f+(maxY-y).toFloat()*baseScale()-height/2f)*zoom+height/2f+offsetY)

    private fun polygonArea(ps:List<NczPoint>):Double { if(ps.size<3)return 0.0;var s=0.0;for(i in ps.indices){val a=ps[i];val b=ps[(i+1)%ps.size];s+=a.x*b.y-b.x*a.y};return abs(s)/2.0 }
    private fun polygonPath(ps:List<NczPoint>):Path { val p=Path();p.moveTo(sx(ps[0].x),sy(ps[0].y));ps.drop(1).forEach{p.lineTo(sx(it.x),sy(it.y))};p.close();return p }

    override fun onDraw(c:Canvas){
        super.onDraw(c);c.drawColor(Color.rgb(24,27,31))
        if(entities.isEmpty()){c.drawText("NCZ dosyası açın",28f,50f,infoPaint);return}
        for(e in entities){
            if(e.layer in hiddenLayers||e.points.isEmpty())continue
            when(e.kind){
                "Polygon"->{val path=polygonPath(e.points);if(filled)c.drawPath(path,fillPaint);c.drawPath(path,linePaint)
                    if(showAreas){val a=polygonArea(e.points);if(a>0.01){val cx=e.points.map{it.x}.average();val cy=e.points.map{it.y}.average();infoPaint.textSize=24f;c.drawText("%.2f m²".format(a),sx(cx),sy(cy),infoPaint)}}}
                "Circle"->{val p=e.points[0];c.drawCircle(sx(p.x),sy(p.y),(e.radius*baseScale()*zoom).toFloat(),linePaint)}
                "Arc"->{val p=e.points[0];val r=(e.radius*baseScale()*zoom).toFloat();c.drawArc(RectF(sx(p.x)-r,sy(p.y)-r,sx(p.x)+r,sy(p.y)+r),e.startAngle.toFloat(),(e.endAngle-e.startAngle).toFloat(),false,linePaint)}
                "Text"->{val p=e.points[0];pointPaint.textSize=(e.textHeight*baseScale()*zoom).toFloat().coerceIn(9f,48f);c.save();c.rotate((-e.rotation).toFloat(),sx(p.x),sy(p.y));c.drawText(e.text,sx(p.x),sy(p.y),pointPaint);c.restore()}
                else->{if(e.points.size==1){val p=e.points[0];c.drawCircle(sx(p.x),sy(p.y),3f,pointPaint)}else{val p=Path();p.moveTo(sx(e.points[0].x),sy(e.points[0].y));e.points.drop(1).forEach{p.lineTo(sx(it.x),sy(it.y))};c.drawPath(p,linePaint)}}
            }
        }
    }

    private fun inspect(x:Float,y:Float){
        var best:NczEntity?=null;var dist=Double.MAX_VALUE
        for(e in entities)if(e.layer !in hiddenLayers)for(p in e.points){val d=hypot((sx(p.x)-x).toDouble(),(sy(p.y)-y).toDouble());if(d<dist){dist=d;best=e}}
        best?.takeIf{dist<50}?.let{e->val p=e.points[0];val area=if(e.kind=="Polygon")" • %.2f m²".format(polygonArea(e.points)) else "";Toast.makeText(context,e.kind+" • "+layerName(e.layer)+area+"\nX: %.3f  Y: %.3f".format(p.x,p.y),Toast.LENGTH_LONG).show()}
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        scaleDetector.onTouchEvent(e);gestures.onTouchEvent(e)
        if(e.pointerCount==1&&!scaleDetector.isInProgress)when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y;moved=false}
            MotionEvent.ACTION_MOVE->{val dx=e.x-lastX;val dy=e.y-lastY;if(abs(dx)+abs(dy)>4)moved=true;if(moved){offsetX+=dx;offsetY+=dy;invalidate()};lastX=e.x;lastY=e.y}
        }
        return true
    }
}
