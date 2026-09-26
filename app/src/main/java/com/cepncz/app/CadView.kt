package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

data class CadSelection(val kind:String,val layer:Int,val layerName:String,val x:Double,val y:Double,val area:Double,val perimeter:Double)

class CadView(context:Context):View(context){
 enum class FillMode{NONE,SOLID,HATCH}
 var onSelectionChanged:((CadSelection?)->Unit)?=null
 private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
 private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
 private val hatch=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1f}
 private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=24f}
 private val selectedPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=5f}
 private var entities:List<NczEntity> = emptyList(); private var layers:List<String> = emptyList()
 private val hidden=mutableSetOf<Int>(); private var selected:NczEntity?=null
 private var fillMode=FillMode.HATCH; private var showAreas=true; private var showPoints=false
 private var zoom=1f;private var ox=0f;private var oy=0f;private var lx=0f;private var ly=0f;private var moved=false
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val palette=intArrayOf(Color.rgb(255,170,55),Color.rgb(80,200,255),Color.rgb(110,220,130),Color.rgb(255,110,130),Color.rgb(210,150,255),Color.rgb(255,220,90),Color.rgb(100,230,220))

 private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScale(d:ScaleGestureDetector):Boolean{val old=zoom;zoom=(zoom*d.scaleFactor).coerceIn(.1f,100f);val r=zoom/old;ox=d.focusX-(d.focusX-ox)*r;oy=d.focusY-(d.focusY-oy)*r;invalidate();return true}
 })
 private val gesture=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
  override fun onDoubleTap(e:MotionEvent):Boolean{fitToScreen();return true}
  override fun onSingleTapConfirmed(e:MotionEvent):Boolean{selectAt(e.x,e.y);return true}
 })

 fun setEntities(v:List<NczEntity>,names:List<String> = emptyList()){entities=v;layers=names;hidden.clear();selected=null;bounds();fitToScreen()}
 private fun bounds(){val p=entities.flatMap{it.points};if(p.isNotEmpty()){minX=p.minOf{it.x};maxX=p.maxOf{it.x};minY=p.minOf{it.y};maxY=p.maxOf{it.y}}}
 fun fitToScreen(){zoom=1f;ox=0f;oy=0f;invalidate()}
 fun zoomIn(){zoom=(zoom*1.4f).coerceAtMost(100f);invalidate()}
 fun zoomOut(){zoom=(zoom/1.4f).coerceAtLeast(.1f);invalidate()}
 fun cycleFillMode():FillMode{fillMode=when(fillMode){FillMode.NONE->FillMode.SOLID;FillMode.SOLID->FillMode.HATCH;FillMode.HATCH->FillMode.NONE};invalidate();return fillMode}
 fun fillModeName()=when(fillMode){FillMode.NONE->"Yok";FillMode.SOLID->"Dolu";FillMode.HATCH->"Taralı"}
 fun setShowAreas(v:Boolean){showAreas=v;invalidate()};fun isShowAreas()=showAreas
 fun setShowPoints(v:Boolean){showPoints=v;invalidate()};fun isShowPoints()=showPoints
 fun setLayerVisible(i:Int,v:Boolean){if(v)hidden.remove(i)else hidden.add(i);invalidate()}
 fun setAllLayersVisible(v:Boolean){hidden.clear();if(!v)entities.map{it.layer}.distinct().forEach{hidden.add(it)};invalidate()}
 fun isLayerVisible(i:Int)=i !in hidden
 fun layerName(i:Int)=layers.getOrNull(i)?.takeIf{it.isNotBlank()}?:"Tabaka $i"
 fun layerCounts():Map<Int,Int> = entities.groupingBy{it.layer}.eachCount()
 fun clearSelection(){selected=null;onSelectionChanged?.invoke(null);invalidate()}

 private fun bs():Float{if(width<80||height<80)return 1f;return min((width-70f)/(maxX-minX).coerceAtLeast(.001).toFloat(),(height-70f)/(maxY-minY).coerceAtLeast(.001).toFloat())}
 private fun sx(x:Double)=((35f+(x-minX).toFloat()*bs()-width/2f)*zoom+width/2f+ox)
 private fun sy(y:Double)=((35f+(maxY-y).toFloat()*bs()-height/2f)*zoom+height/2f+oy)
 private fun area(p:List<NczPoint>):Double{if(p.size<3)return 0.0;var s=0.0;for(i in p.indices){val a=p[i];val b=p[(i+1)%p.size];s+=a.x*b.y-b.x*a.y};return abs(s)/2}
 private fun perimeter(p:List<NczPoint>):Double{if(p.size<2)return 0.0;var s=0.0;for(i in p.indices){val a=p[i];val b=p[(i+1)%p.size];s+=hypot(a.x-b.x,a.y-b.y)};return s}
 private fun path(p:List<NczPoint>,close:Boolean):Path{val q=Path();q.moveTo(sx(p[0].x),sy(p[0].y));p.drop(1).forEach{q.lineTo(sx(it.x),sy(it.y))};if(close)q.close();return q}
 private fun color(layer:Int)=palette[abs(layer)%palette.size]

 private fun hatchPolygon(c:Canvas,p:Path){
  c.save();c.clipPath(p);hatch.color=Color.argb(130,255,190,70)
  val step=18f;var x=-height.toFloat();while(x<width+height){c.drawLine(x,0f,x+height,height.toFloat(),hatch);x+=step};c.restore()
 }

 override fun onDraw(c:Canvas){
  c.drawColor(Color.rgb(24,27,31))
  if(entities.isEmpty()){text.textSize=28f;c.drawText("NCZ dosyası açın",28f,50f,text);return}
  for(e in entities){
   if(e.layer in hidden||e.points.isEmpty())continue
   line.color=color(e.layer);fill.color=Color.argb(55,Color.red(line.color),Color.green(line.color),Color.blue(line.color))
   when(e.kind){
    "Polygon"->{val p=path(e.points,true);when(fillMode){FillMode.SOLID->c.drawPath(p,fill);FillMode.HATCH->hatchPolygon(c,p);else->{}};c.drawPath(p,line)
     if(showAreas){val a=area(e.points);if(a>.01){text.textSize=22f;val cx=e.points.map{it.x}.average();val cy=e.points.map{it.y}.average();c.drawText("%.2f m²".format(a),sx(cx),sy(cy),text)}}}
    "Circle"->{val p=e.points[0];c.drawCircle(sx(p.x),sy(p.y),(e.radius*bs()*zoom).toFloat(),line)}
    "Arc"->{val p=e.points[0];val r=(e.radius*bs()*zoom).toFloat();c.drawArc(RectF(sx(p.x)-r,sy(p.y)-r,sx(p.x)+r,sy(p.y)+r),e.startAngle.toFloat(),(e.endAngle-e.startAngle).toFloat(),false,line)}
    "Text"->{val p=e.points[0];text.color=line.color;text.textSize=(e.textHeight*bs()*zoom).toFloat().coerceIn(9f,44f);c.save();c.rotate((-e.rotation).toFloat(),sx(p.x),sy(p.y));c.drawText(e.text,sx(p.x),sy(p.y),text);c.restore();text.color=Color.WHITE}
    else->{if(e.points.size==1){val p=e.points[0];c.drawCircle(sx(p.x),sy(p.y),if(showPoints)6f else 3f,line)}else c.drawPath(path(e.points,false),line)}
   }
   if(showPoints&&e.kind!="Text")e.points.forEach{c.drawCircle(sx(it.x),sy(it.y),4f,line)}
   if(e===selected){when{e.kind=="Polygon"->c.drawPath(path(e.points,true),selectedPaint);e.points.size>1->c.drawPath(path(e.points,false),selectedPaint);else->c.drawCircle(sx(e.points[0].x),sy(e.points[0].y),12f,selectedPaint)}}
  }
 }

 private fun pointInPolygon(x:Float,y:Float,p:List<NczPoint>):Boolean{var inside=false;var j=p.size-1;for(i in p.indices){val xi=sx(p[i].x);val yi=sy(p[i].y);val xj=sx(p[j].x);val yj=sy(p[j].y);if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi+0.00001f)+xi)inside=!inside;j=i};return inside}
 private fun selectAt(x:Float,y:Float){
  var hit=entities.asReversed().firstOrNull{it.layer !in hidden&&it.kind=="Polygon"&&it.points.size>=3&&pointInPolygon(x,y,it)}
  if(hit==null){var best=Double.MAX_VALUE;for(e in entities)if(e.layer !in hidden)for(p in e.points){val d=hypot((sx(p.x)-x).toDouble(),(sy(p.y)-y).toDouble());if(d<best&&d<45){best=d;hit=e}}}
  selected=hit
  onSelectionChanged?.invoke(hit?.let{val p=it.points.first();CadSelection(it.kind,it.layer,layerName(it.layer),p.x,p.y,if(it.kind=="Polygon")area(it.points)else 0.0,if(it.kind=="Polygon")perimeter(it.points)else 0.0)})
  invalidate()
 }

 override fun onTouchEvent(e:MotionEvent):Boolean{
  scaler.onTouchEvent(e);gesture.onTouchEvent(e)
  if(e.pointerCount==1&&!scaler.isInProgress)when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y;moved=false}
   MotionEvent.ACTION_MOVE->{val dx=e.x-lx;val dy=e.y-ly;if(abs(dx)+abs(dy)>5)moved=true;if(moved){ox+=dx;oy+=dy;invalidate()};lx=e.x;ly=e.y}
  };return true
 }
}
