package com.cepncz.app

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

data class CadSelection(val kind:String,val layer:Int,val layerName:String,val x:Double,val y:Double,val area:Double,val perimeter:Double,val length:Double=0.0,val queryMode:String="SELECT")

class CadView(context:Context):View(context){
 enum class FillMode{NONE,SOLID,HATCH}
 enum class QueryMode{SELECT,AREA,LENGTH,DISTANCE,POLYLINE,COORDINATE,POINT_CAPTURE,LENGTH_LABEL}
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
 private var activePointerId=MotionEvent.INVALID_POINTER_ID;private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop.toFloat()
 private var snapEnabled=true;private val snapKinds=mutableSetOf("KÖŞE","ORTA","KESİŞİM","DİK","YAKIN");private val measurePts=mutableListOf<NczPoint>()
 private val savedPoints=mutableListOf<NczPoint>()
 private data class LengthLabel(val a:NczPoint,val b:NczPoint,val mid:NczPoint,val value:Double)
 private val lengthLabels=mutableListOf<LengthLabel>()
 private var navigating=false
 private var pendingInitialFit=false
 private data class SnapHit(val p:NczPoint,val kind:String,val distance:Double)
 private var snapHit:SnapHit?=null
 private var lastSnapAt=0L
 private var zoomWindow=false;private var zoomWindowStart:NczPoint?=null;private var zoomWindowNow:NczPoint?=null
 private var minX=0.0;private var maxX=1.0;private var minY=0.0;private var maxY=1.0
 private val maxZoom=5000f
 private var lastWorldX:Double?=null;private var lastWorldY:Double?=null
 private val palette=intArrayOf(Color.rgb(255,170,55),Color.rgb(80,200,255),Color.rgb(110,220,130),Color.rgb(255,110,130),Color.rgb(210,150,255),Color.rgb(255,220,90),Color.rgb(100,230,220))

 private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScaleBegin(d:ScaleGestureDetector):Boolean{navigating=true;snapHit=null;return true}
  override fun onScale(d:ScaleGestureDetector):Boolean{zoomAt(d.focusX,d.focusY,d.scaleFactor.coerceIn(.85f,1.18f));return true}
  override fun onScaleEnd(d:ScaleGestureDetector){navigating=false;postInvalidateOnAnimation()}
 })
 private val gesture=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
  override fun onDoubleTap(e:MotionEvent):Boolean{zoomAt(e.x,e.y,2.0f);return true}
  override fun onDown(e:MotionEvent)=true
  override fun onLongPress(e:MotionEvent){}
  override fun onSingleTapConfirmed(e:MotionEvent):Boolean{if(queryMode==QueryMode.LENGTH_LABEL)lengthLabelTap(e.x,e.y) else if(queryMode==QueryMode.POINT_CAPTURE)capturePoint(e.x,e.y) else if(queryMode==QueryMode.DISTANCE||queryMode==QueryMode.POLYLINE||queryMode==QueryMode.COORDINATE)measureTap(e.x,e.y)else selectAt(e.x,e.y);return true}
 })

 fun setEntities(v:List<NczEntity>,names:List<String> = emptyList()){
  entities=v;layers=names;hidden.clear();selected=null;bounds()
  performanceMode=v.size>2500||v.sumOf{it.points.size}>50000
  if(performanceMode){fillMode=FillMode.NONE;showAreas=false;showPoints=false;showEdgeLengths=false}
  meta=v.map{e->val ps=e.points;val ar=if(e.kind=="Polygon")area(ps)else 0.0;val per=if(e.kind=="Polygon")perimeter(ps)else 0.0
   Meta(e,ps.minOfOrNull{it.x}?:0.0,ps.maxOfOrNull{it.x}?:0.0,ps.minOfOrNull{it.y}?:0.0,ps.maxOfOrNull{it.y}?:0.0,ar,per,if(ps.isEmpty())0.0 else ps.sumOf{it.x}/ps.size,if(ps.isEmpty())0.0 else ps.sumOf{it.y}/ps.size)}
  pendingInitialFit=true
  if(width>0&&height>0){fitToScreen();pendingInitialFit=false}else requestLayout()
 }
 private fun bounds(){
  var found=false;var loX=Double.POSITIVE_INFINITY;var hiX=Double.NEGATIVE_INFINITY;var loY=Double.POSITIVE_INFINITY;var hiY=Double.NEGATIVE_INFINITY
  for(e in entities)for(p in e.points){found=true;if(p.x<loX)loX=p.x;if(p.x>hiX)hiX=p.x;if(p.y<loY)loY=p.y;if(p.y>hiY)hiY=p.y}
  if(found){minX=loX;maxX=hiX;minY=loY;maxY=hiY}else{minX=0.0;maxX=1.0;minY=0.0;maxY=1.0}
 }
 fun fitToScreen(){zoomWindow=false;zoomWindowStart=null;zoomWindowNow=null;zoom=1f;ox=0f;oy=0f;navUpdate();postInvalidateOnAnimation()}
 private fun navUpdate(){val x=lastWorldX;val y=lastWorldY;onNavigationInfo?.invoke("ZOOM %.2fx%s".format(zoom,if(x==null||y==null)"" else "  •  X %.2f  Y %.2f".format(x,y)))}
 private fun zoomAt(fx:Float,fy:Float,factor:Float){
  val old=zoom;val next=(zoom*factor).coerceIn(.05f,maxZoom);if(next==old)return
  val r=next/old;val cx=width/2f;val cy=height/2f;zoom=next
  ox=(fx-cx)-((fx-cx)-ox)*r;oy=(fy-cy)-((fy-cy)-oy)*r;navUpdate();postInvalidateOnAnimation()
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
  val projectCx=(minX+maxX)/2.0;val projectCy=(minY+maxY)/2.0
  ox=-((cx-projectCx).toFloat()*base*zoom)
  oy=((cy-projectCy).toFloat()*base*zoom)
  navUpdate();postInvalidateOnAnimation()
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
 fun isSnapKindEnabled(kind:String)=kind in snapKinds
 fun setSnapKindEnabled(kind:String,on:Boolean){if(on)snapKinds.add(kind)else snapKinds.remove(kind);invalidate()}
 fun startLengthLabelMode(){queryMode=QueryMode.LENGTH_LABEL;onMeasureInfo?.invoke("UZUNLUK YAZDIR • Bir kenara dokun");invalidate()}
 fun clearLengthLabels(){lengthLabels.clear();invalidate();onMeasureInfo?.invoke("Uzunluk yazıları temizlendi")}
 fun clearMeasure(){measurePts.clear();invalidate();onMeasureInfo?.invoke("Ölçüm temizlendi")}
 fun undoMeasure(){if(measurePts.isNotEmpty())measurePts.removeAt(measurePts.lastIndex);invalidate();updateMeasureInfo()}
 fun savedPointList():List<NczPoint> = savedPoints.toList()
 fun addSavedPoint(y:Double,x:Double):Int{savedPoints.add(NczPoint(x,y));invalidate();return savedPoints.size}
 fun savedPointCount():Int=savedPoints.size
 fun saveCurrentPoint():NczPoint?{
  val p=measurePts.lastOrNull()?:if(lastWorldX!=null&&lastWorldY!=null)NczPoint(lastWorldX!!,lastWorldY!!)else null
  if(p!=null){savedPoints.add(p);invalidate();onMeasureInfo?.invoke("N${savedPoints.size} • Y %.3f • X %.3f".format(p.y,p.x))}
  return p
 }
 fun removeSavedPoint(index:Int){if(index in savedPoints.indices){savedPoints.removeAt(index);invalidate()}}
 fun clearSavedPoints(){savedPoints.clear();invalidate()}
 fun zoomToSavedPoint(index:Int):Boolean{
  val p=savedPoints.getOrNull(index)?:return false
  val span=max(maxX-minX,maxY-minY).coerceAtLeast(1.0)/40.0
  animateToBounds(p.x-span,p.x+span,p.y-span,p.y+span);return true
 }
 fun queryModeName()=when(queryMode){QueryMode.SELECT->"Seçim";QueryMode.AREA->"Kapalı Alan";QueryMode.LENGTH->"Uzunluk";QueryMode.DISTANCE->"2 Nokta";QueryMode.POLYLINE->"Kırık Hat";QueryMode.COORDINATE->"Koordinat";QueryMode.POINT_CAPTURE->"Nokta Yakala";QueryMode.LENGTH_LABEL->"Uzunluk Yazdır"}
 fun clearSelection(){selected=null;onSelectionChanged?.invoke(null);invalidate()}
 fun selectedParcelSummary():String?{val e=selected?:return null;if(e.kind!="Polygon"||e.points.size<3)return null;return "Alan %.2f m² • Çevre %.2f m • %d köşe".format(area(e.points),perimeter(e.points),e.points.size)}
 fun labelSelectedParcelEdges():Int{val e=selected?:return 0;if(e.kind!="Polygon"||e.points.size<2)return 0;lengthLabels.clear();for(i in e.points.indices){val a=e.points[i];val b=e.points[(i+1)%e.points.size];val mid=NczPoint((a.x+b.x)/2.0,(a.y+b.y)/2.0);lengthLabels.add(LengthLabel(a,b,mid,hypot(a.x-b.x,a.y-b.y)))};invalidate();return lengthLabels.size}
 fun saveSelectedParcelCorners():Int{val e=selected?:return 0;if(e.kind!="Polygon")return 0;e.points.forEach{savedPoints.add(it)};invalidate();return e.points.size}

 private fun bs():Float{if(width<80||height<80)return 1f;return min((width-70f)/(maxX-minX).coerceAtLeast(.001).toFloat(),(height-70f)/(maxY-minY).coerceAtLeast(.001).toFloat())}
 private fun sx(x:Double,y:Double)=((35f+(x-minX).toFloat()*bs()-width/2f)*zoom+width/2f+ox)
 private fun sy(x:Double,y:Double)=((35f+(maxY-y).toFloat()*bs()-height/2f)*zoom+height/2f+oy)
 private fun area(p:List<NczPoint>):Double{if(p.size<3)return 0.0;var s=0.0;for(i in p.indices){val a=p[i];val b=p[(i+1)%p.size];s+=a.x*b.y-b.x*a.y};return abs(s)/2}
 private fun length(p:List<NczPoint>,closed:Boolean=false):Double{if(p.size<2)return 0.0;var s=0.0;for(i in 0 until p.size-1){val a=p[i];val b=p[i+1];s+=hypot(a.x-b.x,a.y-b.y)};if(closed&&p.size>2){val a=p.last();val b=p.first();s+=hypot(a.x-b.x,a.y-b.y)};return s}
 private fun perimeter(p:List<NczPoint>)=length(p,true)
 private fun segmentDistance(px:Float,py:Float,ax:Float,ay:Float,bx:Float,by:Float):Double{val vx=bx-ax;val vy=by-ay;val wx=px-ax;val wy=py-ay;val vv=vx*vx+vy*vy;if(vv<=.0001f)return hypot((px-ax).toDouble(),(py-ay).toDouble());val t=((wx*vx+wy*vy)/vv).coerceIn(0f,1f);return hypot((px-(ax+t*vx)).toDouble(),(py-(ay+t*vy)).toDouble())}
 private fun screenDistanceToEntity(x:Float,y:Float,e:NczEntity):Double{if(e.points.size<2)return e.points.minOfOrNull{hypot((sx(it.x,it.y)-x).toDouble(),(sy(it.x,it.y)-y).toDouble())}?:Double.MAX_VALUE;var best=Double.MAX_VALUE;for(i in 0 until e.points.size-1){val a=e.points[i];val b=e.points[i+1];best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};if(e.kind=="Polygon"){val a=e.points.last();val b=e.points.first();best=min(best,segmentDistance(x,y,sx(a.x,a.y),sy(a.x,a.y),sx(b.x,b.y),sy(b.x,b.y)))};return best}
 private fun path(p:List<NczPoint>,close:Boolean,fast:Boolean=false):Path{
  val q=Path();if(p.isEmpty())return q
  q.moveTo(sx(p[0].x,p[0].y),sy(p[0].x,p[0].y))
  val step=when{
   !fast||p.size<300->1
   zoom<0.5f->max(2,p.size/350)
   zoom<1.2f->max(2,p.size/700)
   else->max(1,p.size/1400)
  }
  var i=step
  while(i<p.size){val pt=p[i];q.lineTo(sx(pt.x,pt.y),sy(pt.x,pt.y));i+=step}
  if(p.size>1&&(p.size-1)%step!=0){val pt=p.last();q.lineTo(sx(pt.x,pt.y),sy(pt.x,pt.y))}
  if(close)q.close()
  return q
 }
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
  val fastFrame=navigating||performanceMode
  val detail=!fastFrame&&zoom>=0.55f
  val labels=showAreas&&!fastFrame&&zoom>=0.8f
  val allowText=!navigating&&(!performanceMode||zoom>=2.5f)
  val allowFill=!navigating&&(!performanceMode||zoom>=2.0f)
  val occupiedLabels=ArrayList<RectF>()
  fun freeLabel(box:RectF):Boolean{if(box.right<0||box.left>width||box.bottom<0||box.top>height)return false;if(occupiedLabels.any{RectF.intersects(it,box)})return false;occupiedLabels.add(box);return true}
  line.strokeWidth=if(performanceMode)1f else 2f
  line.isAntiAlias=!performanceMode
  for(m in meta){
   val e=m.e
   if(e.layer in hidden||e.points.isEmpty()||!visible(m))continue
   line.color=color(e.layer);fill.color=Color.argb(55,Color.red(line.color),Color.green(line.color),Color.blue(line.color))
   when(e.kind){
    "Polygon"->{val p=path(e.points,true,fastFrame);if(allowFill)when(fillMode){FillMode.SOLID->c.drawPath(p,fill);FillMode.HATCH->hatchPolygon(c,p);else->{}};c.drawPath(p,line)
     if(labels&&m.area>.01){text.textSize=22f;c.drawText("%.2f m²".format(m.area),sx(m.cx,m.cy),sy(m.cx,m.cy),text)}}
    "Circle"->{val p=e.points[0];c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),(e.radius*bs()*zoom).toFloat(),line)}
    "Arc"->{val p=e.points[0];val r=(e.radius*bs()*zoom).toFloat();c.drawArc(RectF(sx(p.x,p.y)-r,sy(p.x,p.y)-r,sx(p.x,p.y)+r,sy(p.x,p.y)+r),e.startAngle.toFloat(),(e.endAngle-e.startAngle).toFloat(),false,line)}
    "Text"->{if(!allowText||e.text.isBlank())continue;val p=e.points[0];text.color=line.color;text.textSize=(e.textHeight*bs()*zoom).toFloat().coerceIn(10f,42f);text.textAlign=Paint.Align.LEFT;val x=sx(p.x,p.y);val y=sy(p.x,p.y);val fm=text.fontMetrics;val w=text.measureText(e.text);val h=fm.descent-fm.ascent;val pad=if(zoom<1.5f)8f else 4f;val box=RectF(x-pad,y+fm.ascent-pad,x+w+pad,y+fm.descent+pad);if(freeLabel(box)){c.save();c.rotate((-e.rotation).toFloat(),x,y);c.drawText(e.text,x,y,text);c.restore()};text.textAlign=Paint.Align.LEFT;text.color=Color.WHITE}
    "Symbol"->{if(navigating)continue;val p=e.points[0];val x=sx(p.x,p.y);val y=sy(p.x,p.y);val r=(e.textHeight*bs()*zoom*0.45).toFloat().coerceIn(5f,22f);val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=line.color;style=Paint.Style.STROKE;strokeWidth=2f};c.save();c.rotate((-e.rotation).toFloat(),x,y);when(e.symbolCode%6){0->{c.drawCircle(x,y,r,sp);c.drawLine(x-r,y,x+r,y,sp);c.drawLine(x,y-r,x,y+r,sp)};1->{c.drawRect(x-r,y-r,x+r,y+r,sp);c.drawLine(x-r,y-r,x+r,y+r,sp);c.drawLine(x+r,y-r,x-r,y+r,sp)};2->{val q=Path();q.moveTo(x,y-r);q.lineTo(x+r,y+r);q.lineTo(x-r,y+r);q.close();c.drawPath(q,sp)};3->{c.drawCircle(x,y,r,sp);c.drawCircle(x,y,r*.35f,sp)};4->{c.drawLine(x-r,y,x+r,y,sp);c.drawLine(x,y-r,x,y+r,sp);c.drawLine(x-r*.7f,y-r*.7f,x+r*.7f,y+r*.7f,sp);c.drawLine(x+r*.7f,y-r*.7f,x-r*.7f,y+r*.7f,sp)};else->{val q=Path();q.moveTo(x,y-r);q.lineTo(x+r,y);q.lineTo(x,y+r);q.lineTo(x-r,y);q.close();c.drawPath(q,sp)}};c.restore()}
    "Block"->{if(navigating)continue;val p=e.points[0];val x=sx(p.x,p.y);val y=sy(p.x,p.y);val r=(8f*e.scale.toFloat().coerceIn(.5f,2.5f));val bp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=line.color;style=Paint.Style.STROKE;strokeWidth=2f};c.save();c.rotate((-e.rotation).toFloat(),x,y);val q=Path();q.moveTo(x,y-r);q.lineTo(x+r,y);q.lineTo(x,y+r);q.lineTo(x-r,y);q.close();c.drawPath(q,bp);c.drawLine(x-r,y,x+r,y,bp);c.drawLine(x,y-r,x,y+r,bp);c.restore();if(allowText&&zoom>=2f&&e.text.isNotBlank()){text.color=line.color;text.textSize=14f;val label=e.text;val w=text.measureText(label);val box=RectF(x+r+3,y-18,x+r+w+8,y+4);if(freeLabel(box))c.drawText(label,x+r+5,y,text);text.color=Color.WHITE}}
    else->{if(e.points.size==1){val p=e.points[0];c.drawCircle(sx(p.x,p.y),sy(p.x,p.y),if(showPoints)6f else 3f,line)}else c.drawPath(path(e.points,false,fastFrame),line)}
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
  if(lengthLabels.isNotEmpty()&&!navigating){
   val lp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;textSize=20f;textAlign=Paint.Align.CENTER}
   lengthLabels.forEach{l->val x=sx(l.mid.x,l.mid.y);val y=sy(l.mid.x,l.mid.y);val label="%.2f m".format(l.value);val w=lp.measureText(label);val box=RectF(x-w/2-5,y-lp.textSize-12,x+w/2+5,y+5);if(freeLabel(box)){val ang=Math.toDegrees(atan2((sy(l.b.x,l.b.y)-sy(l.a.x,l.a.y)).toDouble(),(sx(l.b.x,l.b.y)-sx(l.a.x,l.a.y)).toDouble())).toFloat();c.save();c.rotate(if(ang>90||ang<-90)ang+180 else ang,x,y);c.drawText(label,x,y-7,lp);c.restore()}}
  }
  if(savedPoints.isNotEmpty()){
   val pp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=3f}
   val pt=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.YELLOW;textSize=18f}
   savedPoints.forEachIndexed{i,p->val x=sx(p.x,p.y);val y=sy(p.x,p.y);c.drawCircle(x,y,7f,pp);c.drawLine(x-10,y,x+10,y,pp);c.drawLine(x,y-10,x,y+10,pp);c.drawText("N"+(i+1),x+9,y-9,pt)}
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
    "DİK"->{c.drawLine(x-10,y+9,x+9,y+9,sp);c.drawLine(x+9,y+9,x+9,y-10,sp);c.drawLine(x+2,y+2,x+9,y+2,sp)}
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
  val cx=(minX+maxX)/2.0;val cy=(minY+maxY)/2.0
  val x=cx+(px-width/2f-ox)/(s*zoom)
  val y=cy-(py-height/2f-oy)/(s*zoom)
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
   for(p in e.points){val d=screenDist(p,px,py);if("KÖŞE" in snapKinds&&d<=26)candidates.add(SnapHit(p,"KÖŞE",d))}
   if(e.points.size>1){
    val n=if(e.kind=="Polygon")e.points.size else e.points.size-1
    for(i in 0 until n){
     val p1=e.points[i];val p2=e.points[(i+1)%e.points.size];segments.add(p1 to p2)
     val mid=NczPoint((p1.x+p2.x)/2.0,(p1.y+p2.y)/2.0);val md=screenDist(mid,px,py);if("ORTA" in snapKinds&&md<=24)candidates.add(SnapHit(mid,"ORTA",md))
     val near=nearestOnSegment(raw,p1,p2);val nd=screenDist(near,px,py);if("YAKIN" in snapKinds&&nd<=18)candidates.add(SnapHit(near,"YAKIN",nd))
     if(measurePts.isNotEmpty()){
      val base=measurePts.last();val foot=nearestOnSegment(base,p1,p2)
      val vx=p2.x-p1.x;val vy=p2.y-p1.y;val seg2=vx*vx+vy*vy
      if(seg2>1e-12){
       val t=((foot.x-p1.x)*vx+(foot.y-p1.y)*vy)/seg2
       val fd=screenDist(foot,px,py)
       if("DİK" in snapKinds&&t>=0.0&&t<=1.0&&fd<=24)candidates.add(SnapHit(foot,"DİK",fd))
      }
     }
    }
   }
  }
  val nearby=segments.filter{(p1,p2)->min(screenDist(p1,px,py),screenDist(p2,px,py))<100||screenDist(nearestOnSegment(raw,p1,p2),px,py)<32}.take(30)
  for(i in nearby.indices)for(j in i+1 until nearby.size){val q=intersection(nearby[i].first,nearby[i].second,nearby[j].first,nearby[j].second)?:continue;val d=screenDist(q,px,py);if("KESİŞİM" in snapKinds&&d<=24)candidates.add(SnapHit(q,"KESİŞİM",d))}
  snapHit=candidates.minWithOrNull(compareBy<SnapHit>{when(it.kind){"KESİŞİM"->0;"KÖŞE"->1;"DİK"->2;"ORTA"->3;else->4}}.thenBy{it.distance})
  return snapHit?.p?:raw
 }
 private fun lengthLabelTap(px:Float,py:Float){
  val raw=worldAt(px,py);var best=28.0;var ba:NczPoint?=null;var bb:NczPoint?=null
  for(m in meta){val e=m.e;if(e.layer in hidden||!visible(m)||e.kind=="Text"||e.points.size<2)continue
   val n=if(e.kind=="Polygon")e.points.size else e.points.size-1
   for(i in 0 until n){val a=e.points[i];val b=e.points[(i+1)%e.points.size];val q=nearestOnSegment(raw,a,b);val d=screenDist(q,px,py);if(d<best){best=d;ba=a;bb=b}}
  }
  val a=ba;val b=bb;if(a==null||b==null){onMeasureInfo?.invoke("Kenar bulunamadı");return}
  val mid=NczPoint((a.x+b.x)/2.0,(a.y+b.y)/2.0);val value=hypot(a.x-b.x,a.y-b.y)
  val existing=lengthLabels.indexOfFirst{hypot(it.mid.x-mid.x,it.mid.y-mid.y)<0.001}
  if(existing>=0){lengthLabels.removeAt(existing);onMeasureInfo?.invoke("Uzunluk yazısı kaldırıldı")}else{lengthLabels.add(LengthLabel(a,b,mid,value));onMeasureInfo?.invoke("Uzunluk %.3f m yazdırıldı".format(value))}
  invalidate()
 }
 private fun capturePoint(px:Float,py:Float){
  val p=snapPoint(px,py);val kind=snapHit?.kind?:"SERBEST"
  savedPoints.add(p);invalidate()
  onMeasureInfo?.invoke("N${savedPoints.size} • $kind • Y %.3f • X %.3f".format(p.y,p.x))
 }
 fun startPointCapture(){measurePts.clear();queryMode=QueryMode.POINT_CAPTURE;snapEnabled=true;snapHit=null;onMeasureInfo?.invoke("NOKTA YAKALA • Köşe / Orta / Kesişim / Yakın");invalidate()}
 private fun updateMeasureInfo(){
  val total=length(measurePts,false)
  onMeasureInfo?.invoke(when(queryMode){
   QueryMode.DISTANCE->if(measurePts.size<2)"İkinci noktayı seç" else{
    val a=measurePts[0];val b=measurePts[1];val dx=b.x-a.x;val dy=b.y-a.y;val d=hypot(dx,dy)
    var az=Math.atan2(dx,dy)*200.0/Math.PI;if(az<0)az+=400.0
    "ΔX %.3f • ΔY %.3f • D %.3f m • Az %.4f gon".format(dx,dy,d,az)
   }
   QueryMode.POLYLINE->if(measurePts.size<2)"Kırık hat • %d nokta".format(measurePts.size) else{
    val a=measurePts[measurePts.lastIndex-1];val b=measurePts.last();val dx=b.x-a.x;val dy=b.y-a.y
    var az=Math.atan2(dx,dy)*200.0/Math.PI;if(az<0)az+=400.0
    "Kırık hat • %d nokta • Σ %.3f m • Son %.3f m • %.4f gon".format(measurePts.size,total,hypot(dx,dy),az)
   }
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
   QueryMode.DISTANCE,QueryMode.POLYLINE,QueryMode.COORDINATE,QueryMode.POINT_CAPTURE,QueryMode.LENGTH_LABEL->{}
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
  val wp=worldAt(e.x,e.y);lastWorldX=wp.x;lastWorldY=wp.y;if(!snapEnabled||zoomWindow||e.actionMasked==MotionEvent.ACTION_MOVE)snapHit=null;navUpdate()
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
  scaler.onTouchEvent(e)
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{
    activePointerId=e.getPointerId(0);lx=e.x;ly=e.y;moved=false;multiTouch=false;suppressTap=false;navigating=false
    gesture.onTouchEvent(e)
   }
   MotionEvent.ACTION_POINTER_DOWN->{
    multiTouch=true;suppressTap=true;navigating=true;snapHit=null
   }
   MotionEvent.ACTION_MOVE->{
    if(e.pointerCount==1&&!scaler.isInProgress&&!multiTouch){
     val idx=e.findPointerIndex(activePointerId)
     if(idx>=0){
      val x=e.getX(idx);val y=e.getY(idx);val dx=x-lx;val dy=y-ly
      if(!moved&&hypot(dx.toDouble(),dy.toDouble())>=touchSlop){moved=true;suppressTap=true;navigating=true}
      if(moved){ox+=dx;oy+=dy;postInvalidateOnAnimation()}
      lx=x;ly=y
     }
    }
   }
   MotionEvent.ACTION_POINTER_UP->{
    suppressTap=true;navigating=true
    if(e.getPointerId(e.actionIndex)==activePointerId){
     val newIndex=if(e.actionIndex==0)1 else 0
     if(newIndex<e.pointerCount){activePointerId=e.getPointerId(newIndex);lx=e.getX(newIndex);ly=e.getY(newIndex)}
    }
   }
   MotionEvent.ACTION_UP->{
    if(!moved&&!suppressTap)gesture.onTouchEvent(e)
    activePointerId=MotionEvent.INVALID_POINTER_ID;multiTouch=false;moved=false;suppressTap=false;navigating=false;postInvalidateOnAnimation()
   }
   MotionEvent.ACTION_CANCEL->{
    activePointerId=MotionEvent.INVALID_POINTER_ID;multiTouch=false;moved=false;suppressTap=false;navigating=false;snapHit=null;postInvalidateOnAnimation()
   }
  }
  return true
 }
}
