package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

data class NczPoint(val x:Double,val y:Double,val z:Double=0.0)
data class NczEntity(val kind:String,val layer:Int,val points:List<NczPoint>)
data class NczReport(val size:Int,val version:String?,val layers:List<String>,val entities:List<NczEntity>)

object NczScanner {
 private fun u32(b:ByteArray,o:Int):Long { if(o<0||o+4>b.size)return -1; return ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL }
 private fun d(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,8).order(ByteOrder.LITTLE_ENDIAN).double
 private fun f(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
 private fun valid(x:Double,y:Double)=x.isFinite()&&y.isFinite()&&kotlin.math.abs(x)<1e9&&kotlin.math.abs(y)<1e9
 private fun legacy(b:ByteArray,o:Int,n:Int):String { if(n<=0||o<0||o+n>b.size)return ""; return b.copyOfRange(o,o+n).toString(Charsets.ISO_8859_1).trimEnd('\u0000') }
 fun scan(b:ByteArray):NczReport {
  val entities=ArrayList<NczEntity>(); val layers=ArrayList<String>(); var version:String?=null; var p=0
  while(p+6<b.size){
   val block=u32(b,p+1)+4; val total=block+1
   if(block<4||total>Int.MAX_VALUE||p+total>b.size){p++;continue}
   val type=b[p].toInt() and 255
   if(type==25 && version==null){val n=b[p+5].toInt() and 255;version=legacy(b,p+6,n)}
   if(type==6 && p+18<=b.size){val count=(b[p+16].toInt() and 255)+((b[p+17].toInt() and 255)*256);for(i in 0 until count){val q=p+18+i*29;if(q+29>p+total)break;val n=b[q+4].toInt() and 255;val s=legacy(b,q+5,n);if(s.isNotBlank())layers.add(s)}}
   if(type==21||type==22) parseGeometry(b,p,block.toInt(),if(type==22)28 else 0,entities)
   p+=total.toInt()
  }
  return NczReport(b.size,version,layers,entities)
 }
 private fun parseGeometry(b:ByteArray,o:Int,block:Int,ext:Int,out:MutableList<NczEntity>){
  if(o+38>b.size)return;val gt=b[o+6].toInt() and 255;val layer=b[o+7].toInt() and 255
  try{
   when(gt){
    1->{val x=d(b,o+8);val y=d(b,o+16);if(valid(x,y))out.add(NczEntity("Point",layer,listOf(NczPoint(x,y,f(b,o+24)))))}
    2->{val x1=d(b,o+8);val y1=d(b,o+16);val x2=d(b,o+block-19);val y2=d(b,o+block-11);if(valid(x1,y1)&&valid(x2,y2))out.add(NczEntity("Line",layer,listOf(NczPoint(x1,y1,f(b,o+24)),NczPoint(x2,y2,f(b,o+block-3)))))}
    7->{val count=(block+1-113-ext)/24;if(count>=2){val pts=ArrayList<NczPoint>();for(i in 0 until count){val q=o+ext+113+i*24;if(q+24>o+block+1||q+24>b.size)break;val x=d(b,q);val y=d(b,q+8);val z=d(b,q+16);if(valid(x,y))pts.add(NczPoint(x,y,z))};if(pts.size>=2){val closed=hypot(pts.first().x-pts.last().x,pts.first().y-pts.last().y)<0.01;out.add(NczEntity(if(closed)"Polygon" else "Polyline",layer,pts))}}}
   }
  }catch(_:Exception){}
 }
}
