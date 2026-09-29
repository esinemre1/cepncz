package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class NczPoint(val x:Double,val y:Double,val z:Double=0.0)
data class NczEntity(val kind:String,val layer:Int,val points:List<NczPoint>,val text:String="",val radius:Double=0.0,val startAngle:Double=0.0,val endAngle:Double=0.0,val textHeight:Double=0.0,val rotation:Double=0.0,val symbolCode:Int=-1,val scale:Double=1.0)
data class NczReport(val size:Int,val version:String?,val layers:List<String>,val entities:List<NczEntity>)

object NczScanner{
 private fun u32(b:ByteArray,o:Int):Long{if(o<0||o+4>b.size)return -1;return ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL}
 private fun d(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,8).order(ByteOrder.LITTLE_ENDIAN).double
 private fun f(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
 private fun valid(x:Double,y:Double)=x.isFinite()&&y.isFinite()&&abs(x)<=100_000_000&&abs(y)<=100_000_000
 private fun point(rawX:Double,rawY:Double,z:Double=0.0)=NczPoint(rawY,rawX,z)
 private fun normDeg(v:Double)=if(v.isFinite())((v%360.0)+360.0)%360.0 else 0.0
 private fun radDeg(v:Double)=normDeg(v*180.0/Math.PI)
 private fun legacy(b:ByteArray,o:Int,n:Int):String{
  if(n<=0||o<0||o+n>b.size)return ""
  val s=StringBuilder(n)
  for(i in 0 until n){val v=b[o+i].toInt() and 255;if(v==0)break;s.append(when(v){221->'İ';222->'Ş';208->'Ğ';240->'ğ';253->'ı';254->'ş';else->v.toChar()})}
  return s.toString().trim()
 }
 private fun lp(b:ByteArray,lenOff:Int,textOff:Int,max:Int=240):String{
  if(lenOff !in b.indices||textOff !in b.indices)return ""
  val n=b[lenOff].toInt() and 255
  return if(n in 1..max&&textOff+n<=b.size)legacy(b,textOff,n) else ""
 }
 private fun textPayload(b:ByteArray,o:Int,g:Int):String{
  for((lo,to) in arrayOf(o+g+97 to o+g+98,o+g+86 to o+g+87,o+97 to o+98,o+86 to o+87)){val s=lp(b,lo,to);if(s.isNotBlank())return s}
  return ""
 }
 fun scan(b:ByteArray):NczReport{
  val entities=ArrayList<NczEntity>();val layers=ArrayList<String>();var version:String?=null;var p=0
  while(p+6<b.size){
   val block=u32(b,p+1)+4;val total=block+1
   if(block<4||total>Int.MAX_VALUE||p.toLong()+total>b.size){p++;continue}
   val type=b[p].toInt() and 255
   if(type==25&&version==null){val n=b[p+5].toInt() and 255;version=legacy(b,p+6,n)}
   if(type==6&&p+18<=b.size){val count=(b[p+16].toInt() and 255)+((b[p+17].toInt() and 255)*256);for(i in 0 until count){val q=p+18+i*29;if(q+29>p+total)break;val s=lp(b,q+4,q+5);if(s.isNotBlank())layers.add(s)}}
   if(type==21||type==22)parseGeometry(b,p,block.toInt(),if(type==22)28 else 0,entities)
   else if(type in setOf(0,5,14,48,108,111,132,150,180))parseEmbedded(b,p+5,(p+total.toInt()).coerceAtMost(b.size),entities)
   p+=total.toInt()
  }
  if(entities.any{it.text=="SMART"})entities.removeAll{it.kind=="Symbol"&&it.layer==0&&it.symbolCode==0}
  return NczReport(b.size,version,layers,entities)
 }
 private fun parseEmbedded(b:ByteArray,start:Int,end:Int,out:MutableList<NczEntity>){
  var q=start
  while(q+8<end){
   val t=b[q].toInt() and 255
   if(t==21||t==22){val bl=u32(b,q+1)+4;if(bl>=4&&bl<Int.MAX_VALUE&&q+bl+1<=end){parseGeometry(b,q,bl.toInt(),if(t==22)28 else 0,out);q+=bl.toInt()+1;continue}}
   q++
  }
 }
 private fun parseGeometry(b:ByteArray,o:Int,block:Int,g:Int,out:MutableList<NczEntity>){
  if(o+38>b.size)return
  val gt=b[o+6].toInt() and 255;val layer=b[o+7].toInt() and 255;val end=(o+block+1).coerceAtMost(b.size)
  try{when(gt){
   1->{val x=d(b,o+8);val y=d(b,o+16);if(valid(x,y))out.add(NczEntity("Point",layer,listOf(point(x,y,f(b,o+24))),text=lp(b,o+g+86,o+g+87,120)))}
   2->{val x1=d(b,o+8);val y1=d(b,o+16);val x2=d(b,o+block-19);val y2=d(b,o+block-11);if(valid(x1,y1)&&valid(x2,y2))out.add(NczEntity("Line",layer,listOf(point(x1,y1,f(b,o+24)),point(x2,y2,f(b,o+block-3)))))}
   3->{val x=d(b,o+8);val y=d(b,o+16);if(valid(x,y))out.add(NczEntity("Circle",layer,listOf(point(x,y,f(b,o+24))),radius=abs(d(b,o+50)-d(b,o+66))/2.0))}
   4->{val x=d(b,o+8);val y=d(b,o+16);val q=o+g;if(valid(x,y)&&q+120<=end)out.add(NczEntity("Arc",layer,listOf(point(x,y,f(b,o+24))),radius=d(b,q+86),startAngle=d(b,q+104),endAngle=d(b,q+112)))}
   5->{
    val x=d(b,o+8);val y=d(b,o+16);val q=o+g;val txt=textPayload(b,o,g)
    val h=if(q+90<=end)f(b,q+86) else 0.0
    val rot=if(q+94<=end)radDeg(f(b,q+90)) else 0.0
    if(valid(x,y)&&txt.isNotBlank())out.add(NczEntity("Text",layer,listOf(point(x,y,f(b,o+24))),text=txt.take(160),textHeight=h,rotation=rot))
   }
   6->{
    val x=d(b,o+8);val y=d(b,o+16);val q=o+g;val so=if(q+94<end)q+94 else o+94
    val code=if(so in 0 until end)b[so].toInt() and 255 else 0
    val size=if(q+90<=end)f(b,q+86).takeIf{it.isFinite()&&it>0&&it<100000}?:5.0 else 5.0
    val rot=if(q+94<=end)radDeg(f(b,q+90)) else 0.0
    if(valid(x,y))out.add(NczEntity("Symbol",layer,listOf(point(x,y,f(b,o+24))),text="S$code",textHeight=size,rotation=rot,symbolCode=code))
   }
   7->{
    val count=(block+1-113-g)/24
    if(count>=2){val pts=ArrayList<NczPoint>();for(i in 0 until count){val q=o+g+113+i*24;if(q+24>end)break;val x=d(b,q);val y=d(b,q+8);if(valid(x,y))pts.add(point(x,y,d(b,q+16)))};if(pts.size>=2){val closed=hypot(pts.first().x-pts.last().x,pts.first().y-pts.last().y)<0.01;out.add(NczEntity(if(closed)"Polygon" else "Polyline",layer,pts,text=lp(b,o+g+86,o+g+87,120)))}}}
   9->{
    val ox=d(b,o+8);val oy=d(b,o+16);if(valid(ox,oy)){val pts=ArrayList<NczPoint>();pts.add(point(ox,oy,f(b,o+24)));var q=o+g+122;while(q+8<=end){val dx=f(b,q);val dy=f(b,q+4);if(!dx.isFinite()||!dy.isFinite())break;val rx=ox+dx;val ry=oy+dy;if(valid(rx,ry))pts.add(point(rx,ry));q+=18};if(pts.size>=2)out.add(NczEntity("Polyline",layer,pts))}}
   10->{val ax=d(b,o+8);val ay=d(b,o+16);val q=o+g;if(q+124<=end){val bx=d(b,q+104);val by=d(b,q+112);if(valid(ax,ay)&&valid(bx,by)){val a=point(ax,ay);val z=point(bx,by);out.add(NczEntity("Polygon",layer,listOf(a,NczPoint(z.x,a.y),z,NczPoint(a.x,z.y)),rotation=radDeg(f(b,q+120))))}}}
   11->{if(o+82<=end){val x1=d(b,o+50);val y1=d(b,o+58);val x2=d(b,o+66);val y2=d(b,o+74);if(valid(x1,y1)&&valid(x2,y2)){val a=point(x1,y1);val z=point(x2,y2);out.add(NczEntity("Polygon",layer,listOf(a,NczPoint(z.x,a.y),z,NczPoint(a.x,z.y)),text="PAFTA"))}}}
   12->{if(o+122<=end){val ax=d(b,o+8);val ay=d(b,o+16);val bx=d(b,o+86);val by=d(b,o+94);val cx=d(b,o+106);val cy=d(b,o+114);if(valid(ax,ay)&&valid(bx,by)&&valid(cx,cy))out.add(NczEntity("Polygon",layer,listOf(point(ax,ay),point(bx,by),point(cx,cy))))}}
   13->{val x=d(b,o+8);val y=d(b,o+16);val q=o+g;if(valid(x,y))out.add(NczEntity("Block",layer,listOf(point(x,y,f(b,o+24))),text=lp(b,q+86,q+87,120),rotation=if(q+122<=end)radDeg(f(b,q+118)) else 0.0))}
   15->{val x=d(b,o+8);val y=d(b,o+16);if(valid(x,y)){val grad=if(o+86<=end)f(b,o+82) else 0.0;val sc=if(o+90<=end)f(b,o+86) else 1.0;out.add(NczEntity("Block",layer,listOf(point(x,y,f(b,o+24))),text="SMART",rotation=normDeg(grad*.9),scale=sc))}}
  }}catch(_:Exception){}
 }
}
