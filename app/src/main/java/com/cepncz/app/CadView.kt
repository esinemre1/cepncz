package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

data class CadSelection(val kind:String,val layer:Int,val layerName:String,val x:Double,val y:Double,val area:Double,val perimeter:Double,val length:Double=0.0,val queryMode:String="SELECT")

class CadView(context:Context):View(context){
 enum class FillMode{NONE,SOLID,HATCH}
 enum class QueryMode{SELECT,AREA,LENGTH}
 var onSelectionChanged:((CadSelection?)->Unit)?=null
 private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
 private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
 private val hatch=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1f}
 private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=24f}
 private val selectedPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=5f}
 private var entities:List<NczEntity> = emptyList(); private var layers:List<String> = emptyList()
 private data class Meta(val e:NczEntity,val minX:Double,val maxX:Double,val minY:Double,val maxY:Double,val area:Double,val perimeter:Double,val cx:Double,val cy:Double)
 private var meta:List<Meta> = emptyList()
 private val hidden=mutableSetOf<Int>(); private var selected:NczEntity?=null
 private var fillMode=FillMode.HATCH; private var showAreas=true; private var showPoints=false; private var showEdgeLengths=true; private var queryMode=QueryMode.SELECT
 private var zoom=1f;private var ox=0f;private var oy=0f;private var lx=0f;private var ly=0f;private var moved=false
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val maxZoom=5000f
 private val palette=intArrayOf(Color.rgb(255,170,55),Color.rgb(80,200,255),Color.rgb(110,220,130),Color.rgb(255,110,130),Color.rgb(210,150,255),Color.rgb(255,220,90),Color.rgb(100,230,220))

 private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScale(d:ScaleGestureDetector):Boolean{val old=zoom;zoom=(zoom*d.scaleFactor).coerceIn(.05f,maxZoom);val r=zoom/old;ox=d.focusX-(d.focusX-ox)*r;oy=d.focusY-(d.focusY-oy)*r;invalidate();return true}
 })
 private val gesture=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
  override fun onDoubleTap(e:MotionEvent):Boolean{zoomAt(e.x,e.y,2.5f);return true}
  override fun onLongPress(e:MotionEvent){fitToScreen()}
  override fun onSingleTapConfirmed(e:MotionEvent):Boolean{selectAt(e.x,e.y);return true}
 })

 fun setEntities(v:List<NczEntity>,names:List<String> = emptyList()){
  entities=v;layers=names;hidden.clear();selected=null;bounds()
  meta=v.map{e->val ps=e.points;val ar=if(e.kind=="Polygon")area(ps)else 0.0;val per=if(e.kind=="Polygon")perimeter(ps)else 0.0
   Meta(e,ps.minOfOrNull{it.x}?:0.0,ps.maxOfOrNull{it.x}?:0.0,ps.minOfOrNull{it.y}?:0.0,ps.maxOfOrNull{it.y}?:0.0,ar,per,if(ps.isEmpty())0.0 else ps.sumOf{it.x}/ps.size,if(ps.isEmpty())0.0 else ps.sumOf{it.y}/ps.size)}
  fitToScreen()
 }
 private fun bounds(){val p=entities.flatMap{it.points};if(p.isNotEmpty()){minX=p.minOf{it.x};maxX=p.maxOf{it.x};minY=p.minOf{it.y};maxY=p.maxOf{it.y}}}
 fun fitToScreen(){zoom=1f;ox=0f;oy=0f;invalidate()}
 private fun zoomAt(fx:Float,fy:Float,factor:Float){val old=zoom;zoom=(zoom*factor).coerceIn(.05f,maxZoom);val r=zoom/old;ox=fx-(fx-ox)*r;oy=fy-(fy-oy)*r;invalidate()}
 fun zoomIn(){zoomAt(width/2f,height/2f,1.8f)}
 fun zoomOut(){zoomAt(width/2f,height/2f,1f/1.8f)}
 fun cycleFillMode():FillMode{fillMode=when(fillMode){FillMode.NONE->FillMode.SOLID;FillMode.SOLID->FillMode.HATCH;FillMode.HATCH->FillMode.NONE};invalidate();return fillMode}
 fun fillModeName()=when(fillMode){FillMode.NONE->"Yok";FillMode.SOLID->"Dolu";FillMode.HATCH->"Taralı"}
 fun setShowAreas(v:Boolean){showAreas=v;invalidate()};fun isShowAreas()=showAreas
 fun setShowPoints(v:Boolean){showPoints=v;invalidate()};fun isShowPoints()=showPoints
 fun setShowEdgeLengths(v:Boolean){showEdgeLengths=v;invalidate()};fun isShowEdgeLengths()=showEdgeLengths
 fun setLayerVisible(i:Int,v:Boolean){if(v)hidden.remove(i)else hidden.add(i);invalidate()}
 fun setAllLayersVisible(v:Boolean){hidden.clear();if(!v)entities.map{it.layer}.distinct().forEach{hidden.add(it)};invalidate()}
 fun isLayerVisible(i:Int)=i !in hidden
 fun layerName(i:Int)=layers.getOrNull(i)?.takeIf{it.isNotBlank()}?:"Tabaka $i"
 fun layerCounts():Map<Int,Int> = entities.groupingBy{it.layer}.eachCount()
 fun setQueryMode(mode:QueryMode){queryMode=mode;clearSelection()}
 fun queryModeName()=when(queryMode){QueryMode.SELECT->"Seçim";QueryMode.AREA->"Kapalı Alan";QueryMode.LENGTH->"Uzunluk"}
 fun clearSelection(){selected=null;onSelectionChanged?.invoke(null);invalidate()}

 private fun bs():Float{if(width<80||height<80)return 1f;return min((width-70f)/(maxY-minY).coerceAtLeast(.001).toFloat(),(height-70f)/(maxX-minX).coerceAtLeast(.001).toFloat())}
 private fun sx(x:Double,y:Double)=((35f+(y-minY).toFloat()*bs()-width/2f)*zoom+width/2f+ox)
 private fun sy(x:Double,y:Double)=((35f+(maxX-x).toFloat()*bs()-height/2f)*zoom+height/2f+oy)
 private fun area(p:List<NczPoint>):Double{if(p.size<3)return 0.0;var s=0.0;for(i in p.indices){val a=p[i];val b=p[(i+1)%p.size];s+=a.x*b.y-b.x*a.y};return abs(s)/2}
 private fun length(p:List<NczPoint>,closed:Boolean=false):Double{if(p.size<2)return 0.0;var s=0.0;for(i in 0 until p.size-1){val a=p[i];val b=p[i+1];s+=hypot(a.x-b.x,a.y-b.y)};if(closed&&p.size>2){val a=p.last();val b=p.first();s+=hypot(a.x-b.x,a.y-b.y)};return s}
 private fun perimeter(p:List<NczPoint>)=length(p,true)
 private fun segmentDistance(px:Float,py:Float,ax:Float,ay:Float,bx:Float,by:Float):Double{val vx=bx-ax;val vy=by-ay;val wx=px-ax;val wy=py-ay;val vv=vx*vx+vy*vy;if(vv<=.0001f)return hypot((px-ax).toDouble(),(py-ay).toDouble());val t=((wx*vx+wy*vy)/vv).coerceIn(0f,1f);return hypot((px-(ax+t*vx)).toDouble(),(py-(ay+t*vy)).toDouble())}
 private fun screenDistanceToEntity(x:Float,y:Float,e:NczEntity):Double{if(e.points.size<2)return e.points.minOfOrNull{hypot((sx(it.x,it.y)-x).toDouble(),(sy(it.x,it.y)-y).toDouble())}?:Double.MAX_VALUE;var best=Double.MAX_VALUE;for(i in 0 until e.points.size-1){val a=e.points[i];val b=e.points[i+1];best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};if(e.kind=="Polygon"){val a=e.points.last();val b=e.points.first();best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};return best}
 private fun path(p:List<NczPoint>,close:Boolean):Path{val q=Path();q.moveTo(sx(p[0].x,p[0].y),sy(p[0].x,p[0].y));p.drop(1).forEach{q.lineTo(sx(it.x,it.y),sy(it.x,it.y))};if(close)q.close();return q}
 private fun color(layer:Int)=palette[abs(layer)%palette.size]

 private fun hatchPolygon(c:Canvas,p:Path){
  c.save();c.clipPath(p);hatch.color=Color.argb(130,255,190,70)
  val step=18f;var x=-height.toFloat();while(x<width+height){c.drawLine(x,0f,x+height,height.toFloat(),hatch);x+=step};c.restore()
 }

 private fun visible(m:Meta):Boolean{
  val l=sx(m.minX,m.minY);val r=sx(m.maxX,m.maxY);val t=sy(m.maxX,m.maxY);val b=sy(m.minX,m.minY)
  return r>=-80f&&l<=width+80f&&b>=-80f&&t<=height+80f
 }
 override fun onDraw(c:Canvas){
  c.drawColor(Color.rgb(24,27,31))
  if(entities.isEmpty()){text.textSize=28f;c.drawText("NCZ dosyası açın",28f,50f,text);return}
  val detail=zoom>=0.55f
  val labels=showAreas&&zoom>=0.8f
  for(m in meta){
   val e=m.e
   if(e.layer in hidden||e.points.isEmpty()||!visible(m))continue
   line.color=color(e.layer);fill.color=Color.argb(55,Color.red(line.color),Color.green(line.color),Color.blue(line.color))
   when(e.kind){
    "Polygon"->{val p=path(e.points,true);when(fillMode){FillMode.SOLID->c.drawPath(p,fill);FillMode.HATCH->hatchPolygon(c,p);else->{}};c.drawPath(p,line)
     if(labels&&m.area>.01){text.textSize=22f;c.drawText("%.2f m²".format(m.area),sx(m.cx,m.cy),sy(m.cx,m.cy),text)}}
    "Circle"->{val p=e.points[0];c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),(e.radius*bs()*zoom).toFloat(),line)}
    "Arc"->{val p=e.points[0];val r=(e.radius*bs()*zoom).toFloat();c.drawArc(RectF(sx(p.x,p.y)-r,sy(p.x,p.y)-r,sx(p.x,p.y)+r,sy(p.x,p.y)+r),e.startAngle.toFloat(),(e.endAngle-e.startAngle).toFloat(),false,line)}
    "Text"->{val p=e.points[0];text.color=line.color;text.textSize=(e.textHeight*bs()*zoom).toFloat().coerceIn(9f,44f);c.save();c.rotate((-e.rotation).toFloat(),sx(p.x,p.y),sy(p.x,p.y));c.drawText(e.text,sx(p.x,p.y),sy(p.x,p.y),text);c.restore();text.color=Color.WHITE}
    else->{if(e.points.size==1){val p=e.points[0];c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),if(showPoints)6f else 3f,line)}else c.drawPath(path(e.points,false),line)}
   }
   if(showPoints&&detail&&e.kind!="Text"&&e.points.size<500)e.points.forEach{c.drawCircle(sx(it.x,it.y),sy(it.x,it.y),4f,line)}
   if(e===selected){
    when{e.kind=="Polygon"->c.drawPath(path(e.points,true),selectedPaint);e.points.size>1->c.drawPath(path(e.points,false),selectedPaint);else->c.drawCircle(sx(e.points[0].x,e.points[0].y),sy(e.points[0].x,e.points[0].y),12f,selectedPaint)}
    if(showEdgeLengths&&zoom>=1.4f&&e.kind=="Polygon"&&e.points.size in 2..120){
     text.textSize=19f;text.color=Color.YELLOW
     for(i in e.points.indices){val a=e.points[i];val b=e.points[(i+1)%e.points.size];val len=hypot(a.x-b.x,a.y-b.y);val mx=(a.x+b.x)/2.0;val my=(a.y+b.y)/2.0;c.drawText("%.2f m".format(len),sx(mx,my),sy(mx,my),text)}
     text.color=Color.WHITE
    }
   }
  }
 }

 private fun pointInPolygon(x:Float,y:Float,p:List<NczPoint>):Boolean{var inside=false;var j=p.size-1;for(i in p.indices){val xi=sx(p[i].x,p[i].y);val yi=sy(p[i].x,p[i].y);val xj=sx(p[j].x,p[j].y);val yj=sy(p[j].x,p[j].y);if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi+0.00001f)+xi)inside=!inside;j=i};return inside}
 private fun selectAt(x:Float,y:Float){
  var chosen:Meta?=null
  when(queryMode){
   QueryMode.AREA->chosen=meta.asReversed().firstOrNull{m->m.e.layer !in hidden&&visible(m)&&m.e.kind=="Polygon"&&m.e.points.size>=3&&pointInPolygon(x,y,m.e.points)}
   QueryMode.LENGTH->{var best=35.0;for(m in meta){if(m.e.layer in hidden||!visible(m)||m.e.kind=="Text"||m.e.points.size<2)continue;val d=screenDistanceToEntity(x,y,m.e);if(d<best){best=d;chosen=m}}}
   QueryMode.SELECT->{
    chosen=meta.asReversed().firstOrNull{m->m.e.layer !in hidden&&visible(m)&&m.e.kind=="Polygon"&&m.e.points.size>=3&&pointInPolygon(x,y,m.e.points)}
    if(chosen==null){var best=45.0;for(m in meta){if(m.e.layer in hidden||!visible(m))continue;val d=screenDistanceToEntity(x,y,m.e);if(d<best){best=d;chosen=m}}}
   }
  }
  selected=chosen?.e
  onSelectionChanged?.invoke(chosen?.let{m->
   val p=m.e.points.first()
   val len=when(m.e.kind){"Polygon"->m.perimeter;"Circle"->2.0*Math.PI*m.e.radius;else->length(m.e.points,false)}
   CadSelection(m.e.kind,m.e.layer,layerName(m.e.layer),p.x,p.y,m.area,m.perimeter,len,queryMode.name)
  })
  invalidate()
 }

