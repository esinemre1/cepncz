package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

data class NczPoint(val x:Double,val y:Double,val z:Double=0.0)
data class NczEntity(val kind:String,val layer:Int,val points:List<NczPoint>,val text:String="",val radius:Double=0.0,val startAngle:Double=0.0,val endAngle:Double=0.0,val textHeight:Double=0.0,val rotation:Double=0.0,val symbolCode:Int=-1)
data class NczReport(val size:Int,val version:String?,val layers:List<String>,val entities:List<NczEntity>)

object NczScanner {
 private fun u32(b:ByteArray,o:Int):Long { if(o<0||o+4>b.size)return -1; return ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL }
 private fun d(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,8).order(ByteOrder.LITTLE_ENDIAN).double
 private fun f(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
 private fun valid(x:Double,y:Double)=x.isFinite()&&y.isFinite()&&kotlin.math.abs(x)<1e9&&kotlin.math.abs(y)<1e9
 private fun legacy(b:ByteArray,o:Int,n:Int):String{
  if(n<=0||o<0||o+n>b.size)return ""
  val s=StringBuilder(n)
  for(i in 0 until n){val v=b[o+i].toInt() and 255;if(v==0)break;s.append(when(v){221->'İ';222->'Ş';208->'Ğ';240->'ğ';253->'ı';254->'ş';else->v.toChar()})}
  return s.toString().trim()
 }
 private fun point(rawX:Double,rawY:Double,z:Double=0.0)=NczPoint(rawY,rawX,z)
 private fun textPayload(b:ByteArray,o:Int,ext:Int):String{
  val tries=arrayOf((o+ext+97) to (o+ext+98),(o+ext+86) to (o+ext+87),(o+97) to (o+98),(o+86) to (o+87))
  for((lo,to) in tries){if(lo in b.indices&&to in b.indices){val n=b[lo].toInt() and 255;if(n in 1..240&&to+n<=b.size){val s=legacy(b,to,n);if(s.isNotBlank())return s}}}
  return ""
 }
 fun scan(b:ByteArray):NczReport {
  val entities=ArrayList<NczEntity>(); val layers=ArrayList<String>(); var version:String?=null; var p=0
  while(p+6<b.size){
   val block=u32(b,p+1)+4; val total=block+1
   if(block < 4L || total > Int.MAX_VALUE.toLong() || p.toLong() + total > b.size.toLong()){p++;continue}
   val type=b[p].toInt() and 255
   if(type==25 && version==null){val n=b[p+5].toInt() and 255;version=legacy(b,p+6,n)}
   if(type==6 && p+18<=b.size){val count=(b[p+16].toInt() and 255)+((b[p+17].toInt() and 255)*256);for(i in 0 until count){val q=p+18+i*29;if(q.toLong()+29L > p.toLong()+total)break;val n=b[q+4].toInt() and 255;val s=legacy(b,q+5,n);if(s.isNotBlank())layers.add(s)}}
   if(type==21||type==22) parseGeometry(b,p,block.toInt(),if(type==22)28 else 0,entities)
   p+=total.toInt()
  }
  return NczReport(b.size,version,layers,entities)
 }
 private fun parseGeometry(b:ByteArray,o:Int,block:Int,ext:Int,out:MutableList<NczEntity>){
  if(o+38>b.size)return;val gt=b[o+6].toInt() and 255;val layer=b[o+7].toInt() and 255
  try{
   when(gt){
    1->{val x=d(b,o+8);val y=d(b,o+16);if(valid(x,y))out.add(NczEntity("Point",layer,listOf(point(x,y,f(b,o+24)))))}
    2->{val x1=d(b,o+8);val y1=d(b,o+16);val x2=d(b,o+block-19);val y2=d(b,o+block-11);if(valid(x1,y1)&&valid(x2,y2))out.add(NczEntity("Line",layer,listOf(point(x1,y1,f(b,o+24)),point(x2,y2,f(b,o+block-3)))))}
    3->{val x=d(b,o+8);val y=d(b,o+16);val x2=d(b,o+50);val x3=d(b,o+66);if(valid(x,y))out.add(NczEntity("Circle",layer,listOf(point(x,y,f(b,o+24))),radius=kotlin.math.abs(x2-x3)/2.0))}
    4->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;if(valid(x,y)&&q+120<=b.size)out.add(NczEntity("Arc",layer,listOf(point(x,y,f(b,o+24))),radius=d(b,q+86),startAngle=d(b,q+104),endAngle=d(b,q+112)))}
    5->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val h=if(q+90<=b.size)f(b,q+86) else 0.0;val rot=if(q+94<=b.size)f(b,q+90)*180.0/Math.PI else 0.0;val txt=textPayload(b,o,ext);if(valid(x,y)&&txt.isNotBlank())out.add(NczEntity("Text",layer,listOf(point(x,y,f(b,o+24))),text=txt.take(160),textHeight=h,rotation=rot))}
    6->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val end=(o+block+1).coerceAtMost(b.size);val so=if(q+94<end)q+94 else o+94;val code=if(so in 0 until end)b[so].toInt() and 255 else 0;val size=if(q+90<=end)f(b,q+86).takeIf{it.isFinite()&&it>0&&it<100000}?:5.0 else 5.0;val rot=if(q+94<=end)f(b,q+90)*180.0/Math.PI else 0.0;if(valid(x,y))out.add(NczEntity("Symbol",layer,listOf(point(x,y,f(b,o+24))),text="S$code",textHeight=size,rotation=((rot%360)+360)%360,symbolCode=code))}
    7->{val count=(block+1-113-ext)/24;if(count>=2){val pts=ArrayList<NczPoint>();for(i in 0 until count){val q=o+ext+113+i*24;if(q+24>o+block+1||q+24>b.size)break;val x=d(b,q);val y=d(b,q+8);val z=d(b,q+16);if(valid(x,y))pts.add(point(x,y,z))};if(pts.size>=2){val closed=hypot(pts.first().x-pts.last().x,pts.first().y-pts.last().y)<0.01;out.add(NczEntity(if(closed)"Polygon" else "Polyline",layer,pts))}}}
   }
  }catch(_:Exception){}
 }
}
