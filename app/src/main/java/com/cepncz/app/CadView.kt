package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

data class CadSelection(val kind:String,val layer:Int,val layerName:String,val x:Double,val y:Double,val area:Double,val perimeter:Double,val length:Double=0.0,val queryMode:String="SELECT")

class CadView(context:Context):View(context){
 enum class FillMode{NONE,SOLID,HATCH}
 enum class QueryMode{SELECT,AREA,LENGTH,DISTANCE,POLYLINE,COORDINATE}
 var onSelectionChanged:((CadSelection?)->Unit)?=null
 var onMeasureInfo:((String)->Unit)?=null
 var onNavigationInfo:((String)->Unit)?=null
 private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
 private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
 private val hatch=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1f}
 private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=24f}
 private val selectedPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=5f}
 private var entities:List<NczEntity> = emptyList(); private var layers:List<String> = emptyList()
 private data class Meta(val e:NczEntity,val minX:Double,val maxX:Double,val minY:Double,val maxY:Double,val area:Double,val perimeter:Double,val cx:Double,val cy:Double)
 private var meta:List<Meta> = emptyList()
 private val grid=HashMap<Long,MutableList<Meta>>();private var gridSize=1.0
 private val hidden=mutableSetOf<Int>(); private var selected:NczEntity?=null
 private var fillMode=FillMode.NONE; private var showAreas=false; private var showPoints=false; private var showEdgeLengths=false; private var queryMode=QueryMode.SELECT
 private var performanceMode=false
 private var zoom=1f;private var ox=0f;private var oy=0f;private var lx=0f;private var ly=0f;private var moved=false;private var multiTouch=false;private var suppressTap=false
 private var snapEnabled=true;private val measurePts=mutableListOf<NczPoint>()
 private data class SnapHit(val p:NczPoint,val kind:String,val distance:Double)
 private var snapHit:SnapHit?=null
 private var lastSnapAt=0L
 private var zoomWindow=false;private var zoomWindowStart:NczPoint?=null;private var zoomWindowNow:NczPoint?=null
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val maxZoom=5000f
 private var lastWorldX:Double?=null;private var lastWorldY:Double?=null
 private val palette=intArrayOf(Color.rgb(255,170,55),Color.rgb(80,200,255),Color.rgb(110,220,130),Color.rgb(255,110,130),Color.rgb(210,150,255),Color.rgb(255,220,90),Color.rgb(100,230,220))

 private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScale(d:ScaleGestureDetector):Boolean{
   zoomAt(d.focusX,d.focusY,d.scaleFactor.coerceIn(.75f,1.33f));return true
  }
 })
 private val gesture=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
  override fun onDoubleTap(e:MotionEvent):Boolean{zoomAt(e.x,e.y,2.0f);return true}
  override fun onDown(e:MotionEvent)=true
  override fun onLongPress(e:MotionEvent){}
  override fun onSingleTapConfirmed(e:MotionEvent):Boolean{if(queryMode==QueryMode.DISTANCE||queryMode==QueryMode.POLYLINE||queryMode==QueryMode.COORDINATE)measureTap(e.x,e.y)else selectAt(e.x,e.y);return true}
 })

 fun setEntities(v:List<NczEntity>,names:List<String> = emptyList()){
  entities=v;layers=names;hidden.clear();selected=null;bounds()
  performanceMode=v.size>2500||v.sumOf{it.points.size}>50000
  if(performanceMode){fillMode=FillMode.NONE;showAreas=false;showPoints=false;showEdgeLengths=false}
  meta=v.map{e->val ps=e.points;val ar=if(e.kind=="Polygon")area(ps)else 0.0;val per=if(e.kind=="Polygon")perimeter(ps)else 0.0
   Meta(e,ps.minOfOrNull{it.x}?:0.0,ps.maxOfOrNull{it.x}?:0.0,ps.minOfOrNull{it.y}?:0.0,ps.maxOfOrNull{it.y}?:0.0,ar,per,if(ps.isEmpty())0.0 else ps.sumOf{it.x}/ps.size,if(ps.isEmpty())0.0 else ps.sumOf{it.y}/ps.size)}
  buildGrid();fitToScreen()
 }
 private fun gridKey(x:Int,y:Int)=(x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
 private fun buildGrid(){
  grid.clear();if(meta.isEmpty())return
  val span=max(maxX-minX,maxY-minY).coerceAtLeast(1.0);gridSize=(span/64.0).coerceAtLeast(.01)
  for(m in meta){
   val x0=floor((m.minX-minX)/gridSize).toInt();val x1=floor((m.maxX-minX)/gridSize).toInt()
   val y0=floor((m.minY-minY)/gridSize).toInt();val y1=floor((m.maxY-minY)/gridSize).toInt()
   if((x1-x0+1)*(y1-y0+1)>64){grid.getOrPut(gridKey(-1,-1)){mutableListOf()}.add(m);continue}
   for(x in x0..x1)for(y in y0..y1)grid.getOrPut(gridKey(x,y)){mutableListOf()}.add(m)
  }
 }
 private fun visibleMeta():List<Meta>{
  if(!performanceMode||grid.isEmpty()||width<=0||height<=0)return meta
  val corners=listOf(worldAt(-160f,-160f),worldAt(width+160f,-160f),worldAt(-160f,height+160f),worldAt(width+160f,height+160f))
  val wx0=corners.minOf{it.x};val wx1=corners.maxOf{it.x};val wy0=corners.minOf{it.y};val wy1=corners.maxOf{it.y}
  var x0=floor((wx0-minX)/gridSize).toInt()-1;var x1=floor((wx1-minX)/gridSize).toInt()+1
  var y0=floor((wy0-minY)/gridSize).toInt()-1;var y1=floor((wy1-minY)/gridSize).toInt()+1
  x0=x0.coerceIn(-2,66);x1=x1.coerceIn(-2,66);y0=y0.coerceIn(-2,66);y1=y1.coerceIn(-2,66)
  val out=LinkedHashSet<Meta>();grid[gridKey(-1,-1)]?.let{out.addAll(it)}
  for(x in x0..x1)for(y in y0..y1)grid[gridKey(x,y)]?.let{out.addAll(it)}
  return if(out.isEmpty())meta else out.toList()
 }
 private fun bounds(){val p=entities.flatMap{it.points};if(p.isNotEmpty()){minX=p.minOf{it.x};maxX=p.maxOf{it.x};minY=p.minOf{it.y};maxY=p.maxOf{it.y}}}
 fun fitToScreen(){zoomWindow=false;zoomWindowStart=null;zoomWindowNow=null;zoom=1f;ox=0f;oy=0f;navUpdate();postInvalidateOnAnimation()}
 private fun navUpdate(){val x=lastWorldX;val y=lastWorldY;onNavigationInfo?.invoke("ZOOM %.2fx%s".format(zoom,if(x==null||y==null)"" else "  •  X %.2f  Y %.2f".format(x,y)))}
 private fun zoomAt(fx:Float,fy:Float,factor:Float){
  val old=zoom;val next=(zoom*factor).coerceIn(.05f,maxZoom);if(next==old)return
  val r=next/old;zoom=next;ox=fx-(fx-ox)*r;oy=fy-(fy-oy)*r;navUpdate();postInvalidateOnAnimation()
 }
 fun zoomIn(){zoomAt(width/2f,height/2f,1.5f)}
 fun zoomOut(){zoomAt(width/2f,height/2f,1f/1.5f)}
 fun startZoomWindow(){zoomWindow=true;zoomWindowStart=null;zoomWindowNow=null;onMeasureInfo?.invoke("ZOOM PENCERE • alanı sürükle")}
 fun cancelZoomWindow(){zoomWindow=false;zoomWindowStart=null;zoomWindowNow=null;invalidate()}
 fun zoomToSelected():Boolean{
  val e=selected?:return false;val ps=e.points;if(ps.isEmpty())return false
  animateToBounds(ps.minOf{it.x},ps.maxOf{it.x},ps.minOf{it.y},ps.maxOf{it.y});return true
 }
 private fun animateToBounds(x0:Double,x1:Double,y0:Double,y1:Double){
  val oldZ=zoom;val oldX=ox;val oldY=oy
  zoomToBounds(x0,x1,y0,y1);val tz=zoom;val tx=ox;val ty=oy
  zoom=oldZ;ox=oldX;oy=oldY
  ValueAnimator.ofFloat(0f,1f).apply{
   duration=320;interpolator=DecelerateInterpolator()
   addUpdateListener{v->val t=v.animatedValue as Float;zoom=oldZ+(tz-oldZ)*t;ox=oldX+(tx-oldX)*t;oy=oldY+(ty-oldY)*t;navUpdate();postInvalidateOnAnimation()}
   start()
  }
 }
 private fun zoomToBounds(x0:Double,x1:Double,y0:Double,y1:Double){
  val w=(x1-x0).coerceAtLeast(.001);val h=(y1-y0).coerceAtLeast(.001);val base=bs().coerceAtLeast(.000001f)
  zoom=min((width*.82)/(w*base),(height*.82)/(h*base)).toFloat().coerceIn(.05f,maxZoom)
  val cx=(x0+x1)/2.0;val cy=(y0+y1)/2.0
  ox=width/2f-((35f+(cx-minX).toFloat()*base-width/2f)*zoom+width/2f)
  oy=height/2f-((35f+(maxY-cy).toFloat()*base-height/2f)*zoom+height/2f)
  postInvalidateOnAnimation()
 }
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
 fun setQueryMode(mode:QueryMode){queryMode=mode;measurePts.clear();clearSelection();onMeasureInfo?.invoke(queryModeName())}
 fun toggleSnap():Boolean{snapEnabled=!snapEnabled;return snapEnabled}
 fun clearMeasure(){measurePts.clear();invalidate();onMeasureInfo?.invoke("Ölçüm temizlendi")}
 fun undoMeasure(){if(measurePts.isNotEmpty())measurePts.removeAt(measurePts.lastIndex);invalidate();updateMeasureInfo()}
 fun queryModeName()=when(queryMode){QueryMode.SELECT->"Seçim";QueryMode.AREA->"Kapalı Alan";QueryMode.LENGTH->"Uzunluk";QueryMode.DISTANCE->"2 Nokta";QueryMode.POLYLINE->"Kırık Hat";QueryMode.COORDINATE->"Koordinat"}
 fun clearSelection(){selected=null;onSelectionChanged?.invoke(null);invalidate()}

 private fun bs():Float{if(width<80||height<80)return 1f;return min((width-70f)/(maxX-minX).coerceAtLeast(.001).toFloat(),(height-70f)/(maxY-minY).coerceAtLeast(.001).toFloat())}
 private fun sx(x:Double,y:Double)=((35f+(y-minY).toFloat()*bs()-width/2f)*zoom+width/2f+ox)
 private fun sy(x:Double,y:Double)=((35f+(maxX-x).toFloat()*bs()-height/2f)*zoom+height/2f+oy)
 private fun area(p:List<NczPoint>):Double{if(p.size<3)return 0.0;var s=0.0;for(i in p.indices){val a=p[i];val b=p[(i+1)%p.size];s+=a.x*b.y-b.x*a.y};return abs(s)/2}
 private fun length(p:List<NczPoint>,closed:Boolean=false):Double{if(p.size<2)return 0.0;var s=0.0;for(i in 0 until p.size-1){val a=p[i];val b=p[i+1];s+=hypot(a.x-b.x,a.y-b.y)};if(closed&&p.size>2){val a=p.last();val b=p.first();s+=hypot(a.x-b.x,a.y-b.y)};return s}
 private fun perimeter(p:List<NczPoint>)=length(p,true)
 private fun segmentDistance(px:Float,py:Float,ax:Float,ay:Float,bx:Float,by:Float):Double{val vx=bx-ax;val vy=by-ay;val wx=px-ax;val wy=py-ay;val vv=vx*vx+vy*vy;if(vv<=.0001f)return hypot((px-ax).toDouble(),(py-ay).toDouble());val t=((wx*vx+wy*vy)/vv).coerceIn(0f,1f);return hypot((px-(ax+t*vx)).toDouble(),(py-(ay+t*vy)).toDouble())}
 private fun screenDistanceToEntity(x:Float,y:Float,e:NczEntity):Double{if(e.points.size<2)return e.points.minOfOrNull{hypot((sx(it.x,it.y)-x).toDouble(),(sy(it.x,it.y)-y).toDouble())}?:Double.MAX_VALUE;var best=Double.MAX_VALUE;for(i in 0 until e.points.size-1){val a=e.points[i];val b=e.points[i+1];best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};if(e.kind=="Polygon"){val a=e.points.last();val b=e.points.first();best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};return best}
 private fun path(p:List<NczPoint>,close:Boolean):Path{val q=Path();q.moveTo(sx(p[0].x,p[0].y),sy(p[0].x,p[0].y));p.drop(1).forEach{q.lineTo(sx(it.x,it.y),sy(it.x,it.y))};if(close)q.close();return q}
 private fun color(layer:Int)=palette[abs(layer)%palette.size]

 private fun drawScaleBar(c:Canvas){
  if(width<100||height<100)return
  val metersPerPx=1.0/(bs().coerceAtLeast(.000001f)*zoom)
  val target=metersPerPx*120.0
  val pow10=10.0.pow(floor(log10(target.coerceAtLeast(.000001))))
  val n=target/pow10
  val nice=(if(n<2)1.0 else if(n<5)2.0 else if(n<10)5.0 else 10.0)*pow10
  val px=(nice/metersPerPx).toFloat()
  val y=height-54f;val x=18f
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;strokeWidth=3f;style=Paint.Style.STROKE}
  c.drawLine(x,y,x+px,y,p);c.drawLine(x,y-6,x,y+6,p);c.drawLine(x+px,y-6,x+px,y+6,p)
  val tp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=22f}
  val label=if(nice>=1000)"%.1f km".format(nice/1000.0) else if(nice>=1)"%.0f m".format(nice) else "%.2f m".format(nice)
  c.drawText(label,x,y-10,tp)
 }

 private fun hatchPolygon(c:Canvas,p:Path){
  c.save();c.clipPath(p);hatch.color=Color.argb(130,255,190,70)
  val step=18f;var x=-height.toFloat();while(x<width+height){c.drawLine(x,0f,x+height,height.toFloat(),hatch);x+=step};c.restore()
 }

 private fun visible(m:Meta):Boolean{
  val l=sx(m.minX,m.minY);val r=sx(m.maxX,m.maxY);val t=sy(m.minX,m.maxY);val b=sy(m.maxX,m.minY)
  return r>=-80f&&l<=width+80f&&b>=-80f&&t<=height+80f
 }
 override fun onDraw(c:Canvas){
  c.drawColor(Color.rgb(24,27,31))
  if(entities.isEmpty()){text.textSize=28f;c.drawText("NCZ dosyası açın",28f,50f,text);return}
  val detail=zoom>=0.55f
  val labels=showAreas&&!performanceMode&&zoom>=0.8f
  val allowText=!performanceMode||zoom>=2.5f
  val allowFill=!performanceMode||zoom>=2.0f
  line.strokeWidth=if(performanceMode)1f else 2f
  line.isAntiAlias=!performanceMode
  for(m in visibleMeta()){
   val e=m.e
   if(e.layer in hidden||e.points.isEmpty()||!visible(m))continue
   line.color=color(e.layer);fill.color=Color.argb(55,Color.red(line.color),Color.green(line.color),Color.blue(line.color))
   when(e.kind){
    "Polygon"->{val p=path(e.points,true);if(allowFill)when(fillMode){FillMode.SOLID->c.drawPath(p,fill);FillMode.HATCH->hatchPolygon(c,p);else->{}};c.drawPath(p,line)
     if(labels&&m.area>.01){text.textSize=22f;c.drawText("%.2f m²".format(m.area),sx(m.cx,m.cy),sy(m.cx,m.cy),text)}}
    "Circle"->{val p=e.points[0];c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),(e.radius*bs()*zoom).toFloat(),line)}
    "Arc"->{val p=e.points[0];val r=(e.radius*bs()*zoom).toFloat();c.drawArc(RectF(sx(p.x,p.y)-r,sy(p.x,p.y)-r,sx(p.x,p.y)+r,sy(p.x,p.y)+r),e.startAngle.toFloat(),(e.endAngle-e.startAngle).toFloat(),false,line)}
    "Text"->{if(!allowText)continue;val p=e.points[0];text.color=line.color;text.textSize=(e.textHeight*bs()*zoom).toFloat().coerceIn(9f,44f);c.save();c.rotate((-e.rotation).toFloat(),sx(p.x,p.y),sy(p.x,p.y));c.drawText(e.text,sx(p.x,p.y),sy(p.x,p.y),text);c.restore();text.color=Color.WHITE}
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
  if(zoomWindow){
   val a=zoomWindowStart;val b=zoomWindowNow
   if(a!=null&&b!=null){
    selectedPaint.color=Color.MAGENTA;selectedPaint.strokeWidth=3f
    val l=min(sx(a.x,a.y),sx(b.x,b.y));val r=max(sx(a.x,a.y),sx(b.x,b.y))
    val t=min(sy(a.x,a.y),sy(b.x,b.y));val bot=max(sy(a.x,a.y),sy(b.x,b.y))
    c.drawRect(l,t,r,bot,selectedPaint)
    selectedPaint.color=Color.YELLOW;selectedPaint.strokeWidth=5f
   }
  }
  snapHit?.let{s->
   val x=sx(s.p.x,s.p.y);val y=sy(s.p.x,s.p.y)
   val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.GREEN;style=Paint.Style.STROKE;strokeWidth=3f}
   when(s.kind){
    "KÖŞE"->c.drawRect(x-9,y-9,x+9,y+9,sp)
    "ORTA"->{val q=Path();q.moveTo(x,y-11);q.lineTo(x+11,y+9);q.lineTo(x-11,y+9);q.close();c.drawPath(q,sp)}
    "KESİŞİM"->{c.drawLine(x-10,y-10,x+10,y+10,sp);c.drawLine(x+10,y-10,x-10,y+10,sp)}
    else->c.drawCircle(x,y,9f,sp)
   }
   val lp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.GREEN;textSize=18f};c.drawText(s.kind,x+13,y-12,lp)
  }
  if(measurePts.isNotEmpty()){
   selectedPaint.color=Color.CYAN;selectedPaint.strokeWidth=4f
   if(measurePts.size>1)c.drawPath(path(measurePts,false),selectedPaint)
   for(p in measurePts)c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),8f,selectedPaint)
   selectedPaint.color=Color.YELLOW;selectedPaint.strokeWidth=5f
  }
 }

 private fun worldAt(px:Float,py:Float):NczPoint{
  val s=bs().coerceAtLeast(.000001f)
  val x=minX+((px-width/2f-ox)/zoom+width/2f-35f)/s
  val y=maxY-((py-height/2f-oy)/zoom+height/2f-35f)/s
  return NczPoint(x,y)
 }
 private fun screenDist(p:NczPoint,px:Float,py:Float)=hypot((sx(p.x,p.y)-px).toDouble(),(sy(p.x,p.y)-py).toDouble())
 private fun nearestOnSegment(p:NczPoint,a:NczPoint,b:NczPoint):NczPoint{
  val vx=b.x-a.x;val vy=b.y-a.y;val l2=vx*vx+vy*vy;if(l2<1e-18)return a
  val t=(((p.x-a.x)*vx+(p.y-a.y)*vy)/l2).coerceIn(0.0,1.0);return NczPoint(a.x+t*vx,a.y+t*vy)
 }
 private fun intersection(a:NczPoint,b:NczPoint,c:NczPoint,d:NczPoint):NczPoint?{
  val den=(a.x-b.x)*(c.y-d.y)-(a.y-b.y)*(c.x-d.x);if(abs(den)<1e-12)return null
  val t=((a.x-c.x)*(c.y-d.y)-(a.y-c.y)*(c.x-d.x))/den
  val u=-((a.x-b.x)*(a.y-c.y)-(a.y-b.y)*(a.x-c.x))/den
  if(t !in 0.0..1.0||u !in 0.0..1.0)return null
  return NczPoint(a.x+t*(b.x-a.x),a.y+t*(b.y-a.y))
 }
 private fun snapPoint(px:Float,py:Float):NczPoint{
  val raw=worldAt(px,py);if(!snapEnabled){snapHit=null;return raw}
  val candidates=ArrayList<SnapHit>();val segments=ArrayList<Pair<NczPoint,NczPoint>>()
  for(m in meta){
   val e=m.e;if(e.layer in hidden||!visible(m)||e.kind=="Text")continue
   for(p in e.points){val d=screenDist(p,px,py);if(d<=26)candidates.add(SnapHit(p,"KÖŞE",d))}
   if(e.points.size>1){
    val n=if(e.kind=="Polygon")e.points.size else e.points.size-1
    for(i in 0 until n){
     val p1=e.points[i];val p2=e.points[(i+1)%e.points.size];segments.add(p1 to p2)
     val mid=NczPoint((p1.x+p2.x)/2.0,(p1.y+p2.y)/2.0);val md=screenDist(mid,px,py);if(md<=24)candidates.add(SnapHit(mid,"ORTA",md))
     val near=nearestOnSegment(raw,p1,p2);val nd=screenDist(near,px,py);if(nd<=18)candidates.add(SnapHit(near,"YAKIN",nd))
    }
   }
  }
  val nearby=segments.filter{(p1,p2)->min(screenDist(p1,px,py),screenDist(p2,px,py))<100||screenDist(nearestOnSegment(raw,p1,p2),px,py)<32}.take(30)
  for(i in nearby.indices)for(j in i+1 until nearby.size){val q=intersection(nearby[i].first,nearby[i].second,nearby[j].first,nearby[j].second)?:continue;val d=screenDist(q,px,py);if(d<=24)candidates.add(SnapHit(q,"KESİŞİM",d))}
  snapHit=candidates.minWithOrNull(compareBy<SnapHit>{when(it.kind){"KESİŞİM"->0;"KÖŞE"->1;"ORTA"->2;else->3}}.thenBy{it.distance})
  return snapHit?.p?:raw
 }
 private fun updateMeasureInfo(){
  val total=length(measurePts,false)
  onMeasureInfo?.invoke(when(queryMode){
   QueryMode.DISTANCE->if(measurePts.size<2)"İkinci noktayı seç" else "Mesafe %.3f m".format(total)
   QueryMode.POLYLINE->"Kırık hat • %d nokta • %.3f m".format(measurePts.size,total)
   else->queryModeName()
  })
 }
 private fun measureTap(px:Float,py:Float){
  val p=snapPoint(px,py)
  when(queryMode){
   QueryMode.COORDINATE->onMeasureInfo?.invoke("X %.3f • Y %.3f".format(p.x,p.y))
   QueryMode.DISTANCE->{if(measurePts.size>=2)measurePts.clear();measurePts.add(p);updateMeasureInfo()}
   QueryMode.POLYLINE->{measurePts.add(p);updateMeasureInfo()}
   else->{}
  }
  invalidate()
 }

 private fun pointInPolygon(x:Float,y:Float,p:List<NczPoint>):Boolean{var inside=false;var j=p.size-1;for(i in p.indices){val xi=sx(p[i].x,p[i].y);val yi=sy(p[i].x,p[i].y);val xj=sx(p[j].x,p[j].y);val yj=sy(p[j].x,p[j].y);if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi+0.00001f)+xi)inside=!inside;j=i};return inside}
 private fun selectAt(x:Float,y:Float){
  var chosen:Meta?=null
  when(queryMode){
   QueryMode.AREA->chosen=meta.asReversed().firstOrNull{m->m.e.layer !in hidden&&visible(m)&&m.e.kind=="Polygon"&&m.e.points.size>=3&&pointInPolygon(x,y,m.e.points)}
   QueryMode.LENGTH->{var best=35.0;for(m in meta){if(m.e.layer in hidden||!visible(m)||m.e.kind=="Text"||m.e.points.size<2)continue;val d=screenDistanceToEntity(x,y,m.e);if(d<best){best=d;chosen=m}}}
   QueryMode.DISTANCE,QueryMode.POLYLINE,QueryMode.COORDINATE->{}
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

 override fun onTouchEvent(e:MotionEvent):Boolean{
  parent?.requestDisallowInterceptTouchEvent(true)
  val wp=worldAt(e.x,e.y);lastWorldX=wp.x;lastWorldY=wp.y;val now=System.currentTimeMillis();if(snapEnabled&&!zoomWindow&&e.pointerCount==1&&(!performanceMode||now-lastSnapAt>80)){snapPoint(e.x,e.y);lastSnapAt=now}else if(!snapEnabled||zoomWindow)snapHit=null;navUpdate();postInvalidateOnAnimation()
  if(zoomWindow){
   when(e.actionMasked){
    MotionEvent.ACTION_DOWN->{zoomWindowStart=worldAt(e.x,e.y);zoomWindowNow=zoomWindowStart;invalidate()}
    MotionEvent.ACTION_MOVE->{zoomWindowNow=worldAt(e.x,e.y);postInvalidateOnAnimation()}
    MotionEvent.ACTION_UP->{
     val a=zoomWindowStart;val b=worldAt(e.x,e.y)
     if(a!=null&&hypot((e.x-lx).toDouble(),(e.y-ly).toDouble())>=0.0){
      if(abs(a.x-b.x)>0.001&&abs(a.y-b.y)>0.001)zoomToBounds(min(a.x,b.x),max(a.x,b.x),min(a.y,b.y),max(a.y,b.y))
     }
     zoomWindow=false;zoomWindowStart=null;zoomWindowNow=null;onMeasureInfo?.invoke("Zoom pencere tamamlandı")
    }
   }
   return true
  }
  if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN){multiTouch=true;suppressTap=true}
  scaler.onTouchEvent(e)
  if(!multiTouch&&!suppressTap)gesture.onTouchEvent(e)
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y;moved=false;multiTouch=false;suppressTap=false;gesture.onTouchEvent(e)}
   MotionEvent.ACTION_MOVE->if(e.pointerCount==1&&!scaler.isInProgress&&!multiTouch){
    val dx=e.x-lx;val dy=e.y-ly
    if(hypot(dx.toDouble(),dy.toDouble())>3.0){moved=true;suppressTap=true}
    if(moved){ox+=dx;oy+=dy;postInvalidateOnAnimation()}
    lx=e.x;ly=e.y
   }
   MotionEvent.ACTION_POINTER_UP->{val keep=if(e.actionIndex==0)1 else 0;if(keep<e.pointerCount){lx=e.getX(keep);ly=e.getY(keep)};suppressTap=true}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(!moved&&!suppressTap)gesture.onTouchEvent(e);multiTouch=false;moved=false;suppressTap=false}
  }
  return true
 }
}
