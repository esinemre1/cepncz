package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

data class NczPoint(val x:Double,val y:Double,val z:Double=0.0)
data class NczEntity(val kind:String,val layer:Int,val points:List<NczPoint>,val text:String="",val radius:Double=0.0,val startAngle:Double=0.0,val endAngle:Double=0.0,val textHeight:Double=0.0,val rotation:Double=0.0,val symbolCode:Int=-1,val scale:Double=1.0)
data class NczReport(val size:Int,val version:String?,val layers:List<String>,val entities:List<NczEntity>,val layerColors:List<Int>)

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
  val entities=ArrayList<NczEntity>(); val layers=ArrayList<String>();val layerColors=ArrayList<Int>(); var version:String?=null; var p=0
  while(p+6<b.size){
   val block=u32(b,p+1)+4; val total=block+1
   if(block < 4L || total > Int.MAX_VALUE.toLong() || p.toLong() + total > b.size.toLong()){p++;continue}
   val type=b[p].toInt() and 255
   if(type==25 && version==null){val n=b[p+5].toInt() and 255;version=legacy(b,p+6,n)}
   if(type==6 && p+18<=b.size){val count=(b[p+16].toInt() and 255)+((b[p+17].toInt() and 255)*256);for(i in 0 until count){val q=p+18+i*29;if(q.toLong()+29L > p.toLong()+total)break;val n=b[q+4].toInt() and 255;val s=legacy(b,q+5,n);if(s.isNotBlank())layers.add(s)}}
   if(type==28&&p+7<b.size){val n=b[p+5].toInt() and 255;val name=legacy(b,p+6,n);if(name=="LEX.ST2"&&p+21<b.size){val count=b[p+20].toInt() and 255;for(i in 0 until count){val q=p+23+i*256+56;if(q+2>=p+total||q+2>=b.size)break;val r=b[q].toInt() and 255;val g=b[q+1].toInt() and 255;val bl=b[q+2].toInt() and 255;layerColors.add((0xff shl 24) or (r shl 16) or (g shl 8) or bl)}}}
   if(type==21||type==22) parseGeometry(b,p,block.toInt(),if(type==22)28 else 0,entities)
   p+=total.toInt()
  }
  val hasSmart=entities.any{it.text=="SMART"}
  if(hasSmart)entities.removeAll{it.kind=="Symbol"&&it.layer==0&&it.symbolCode==0}
  return NczReport(b.size,version,layers,entities,layerColors)
 }
 private fun parseEmbedded(b:ByteArray,start:Int,end:Int,out:MutableList<NczEntity>){
  var q=start
  while(q+8<end){val t=b[q].toInt() and 255;if(t==21||t==22){val bl=u32(b,q+1)+4;if(bl>=4&&bl<Int.MAX_VALUE&&q+bl+1<=end){parseGeometry(b,q,bl.toInt(),if(t==22)28 else 0,out);q+=bl.toInt()+1;continue}};q++}
 }
 private fun parseGeometry(b:ByteArray,o:Int,block:Int,ext:Int,out:MutableList<NczEntity>){
  if(o+38>b.size)return;val gt=b[o+6].toInt() and 255;val layer=b[o+7].toInt() and 255
  try{
   when(gt){
    1->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val name=if(q+87<b.size){val n=(b[q+86].toInt() and 255);if(n in 1..120&&q+87+n<=b.size)legacy(b,q+87,n) else ""}else "";if(valid(x,y))out.add(NczEntity("Point",layer,listOf(point(x,y,f(b,o+24))),text=name))}
    2->{val x1=d(b,o+8);val y1=d(b,o+16);val x2=d(b,o+block-19);val y2=d(b,o+block-11);if(valid(x1,y1)&&valid(x2,y2))out.add(NczEntity("Line",layer,listOf(point(x1,y1,f(b,o+24)),point(x2,y2,f(b,o+block-3)))))}
    3->{val x=d(b,o+8);val y=d(b,o+16);val x2=d(b,o+50);val x3=d(b,o+66);if(valid(x,y))out.add(NczEntity("Circle",layer,listOf(point(x,y,f(b,o+24))),radius=kotlin.math.abs(x2-x3)/2.0))}
    4->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;if(valid(x,y)&&q+120<=b.size)out.add(NczEntity("Arc",layer,listOf(point(x,y,f(b,o+24))),radius=d(b,q+86),startAngle=d(b,q+104),endAngle=d(b,q+112)))}
    5->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val h=if(q+90<=b.size)f(b,q+86) else 0.0;val rot=if(q+94<=b.size)f(b,q+90)*180.0/Math.PI else 0.0;val txt=textPayload(b,o,ext);if(valid(x,y)&&txt.isNotBlank())out.add(NczEntity("Text",layer,listOf(point(x,y,f(b,o+24))),text=txt.take(160),textHeight=h,rotation=rot))}
    6->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val end=(o+block+1).coerceAtMost(b.size);val so=if(q+94<end)q+94 else o+94;val code=if(so in 0 until end)b[so].toInt() and 255 else 0;val size=if(q+90<=end)f(b,q+86).takeIf{it.isFinite()&&it>0&&it<100000}?:5.0 else 5.0;val rot=if(q+94<=end)f(b,q+90)*180.0/Math.PI else 0.0;if(valid(x,y))out.add(NczEntity("Symbol",layer,listOf(point(x,y,f(b,o+24))),text="S$code",textHeight=size,rotation=((rot%360)+360)%360,symbolCode=code))}
    9->{val ox=d(b,o+8);val oy=d(b,o+16);val pts=ArrayList<NczPoint>();if(valid(ox,oy)){pts.add(point(ox,oy,f(b,o+24)));var q=o+ext+122;val end=(o+block+1).coerceAtMost(b.size);while(q+8<=end){val dx=f(b,q);val dy=f(b,q+4);if(!dx.isFinite()||!dy.isFinite())break;val rx=ox+dx;val ry=oy+dy;if(valid(rx,ry)){pts.add(point(rx,ry))};q+=18};if(pts.size>=2)out.add(NczEntity("Polyline",layer,pts))}}
    10->{val ax=d(b,o+8);val ay=d(b,o+16);val q=o+ext;val bx=if(q+112<b.size)d(b,q+104) else ax;val by=if(q+120<b.size)d(b,q+112) else ay;val rot=if(q+124<=b.size)f(b,q+120) else 0.0;if(valid(ax,ay)&&valid(bx,by)){val a=point(ax,ay);val b2=point(bx,by);val w=b2.x-a.x;val h=b2.y-a.y;val ang=rot;val ca=kotlin.math.cos(ang);val sa=kotlin.math.sin(ang);val p2=NczPoint(a.x+w*ca,a.y+w*sa);val p3=NczPoint(a.x+w*ca-h*sa,a.y+w*sa+h*ca);val p4=NczPoint(a.x-h*sa,a.y+h*ca);out.add(NczEntity("Polygon",layer,listOf(a,p2,p3,p4),rotation=ang*180.0/Math.PI))}}
    11->{val x1=d(b,o+50);val y1=d(b,o+58);val x2=d(b,o+66);val y2=d(b,o+74);if(valid(x1,y1)&&valid(x2,y2)){val a=point(x1,y1);val z=point(x2,y2);out.add(NczEntity("Polygon",layer,listOf(NczPoint(a.x,a.y),NczPoint(z.x,a.y),NczPoint(z.x,z.y),NczPoint(a.x,z.y)),text="PAFTA"))}}
    12->{val ax=d(b,o+8);val ay=d(b,o+16);val bx=d(b,o+86);val by=d(b,o+94);val cx=d(b,o+106);val cy=d(b,o+114);if(valid(ax,ay)&&valid(bx,by)&&valid(cx,cy))out.add(NczEntity("Polygon",layer,listOf(point(ax,ay),point(bx,by),point(cx,cy))))}
    13->{val x=d(b,o+8);val y=d(b,o+16);val q=o+ext;val name=if(q+86<b.size){val n=b[q+86].toInt() and 255;if(n in 1..120&&q+87+n<=b.size)legacy(b,q+87,n) else ""}else "";val rot=if(q+122<=b.size)f(b,q+118)*180.0/Math.PI else 0.0;if(valid(x,y))out.add(NczEntity("Block",layer,listOf(point(x,y,f(b,o+24))),text=name,rotation=((rot%360)+360)%360))}
    15->{val x=d(b,o+8);val y=d(b,o+16);val w=if(o+177<=b.size)d(b,o+169) else 0.0;val h=if(o+185<=b.size)d(b,o+177) else 0.0;val grad=if(o+86<=b.size)f(b,o+82) else 0.0;val sc=if(o+90<=b.size)f(b,o+86) else 1.0;if(valid(x,y)){val a=point(x,y);if(w.isFinite()&&h.isFinite()&&kotlin.math.abs(w)>1e-9&&kotlin.math.abs(h)>1e-9){val ang=grad*Math.PI/200.0;val ca=kotlin.math.cos(ang);val sa=kotlin.math.sin(ang);val p2=NczPoint(a.x+w*ca,a.y+w*sa);val p3=NczPoint(a.x+w*ca-h*sa,a.y+w*sa+h*ca);val p4=NczPoint(a.x-h*sa,a.y+h*ca);out.add(NczEntity("Polygon",layer,listOf(a,p2,p3,p4),text="SMART",rotation=grad*.9,scale=sc))}else out.add(NczEntity("Block",layer,listOf(a),text="SMART",rotation=grad*.9,scale=sc))}}
    7->{val count=(block+1-113-ext)/24;if(count>=2){val pts=ArrayList<NczPoint>();for(i in 0 until count){val q=o+ext+113+i*24;if(q+24>o+block+1||q+24>b.size)break;val x=d(b,q);val y=d(b,q+8);val z=d(b,q+16);if(valid(x,y))pts.add(point(x,y,z))};if(pts.size>=2){val closed=hypot(pts.first().x-pts.last().x,pts.first().y-pts.last().y)<0.01;val q=o+ext;val label=if(q+87<b.size){val n=b[q+86].toInt() and 255;if(n in 1..120&&q+87+n<=b.size)legacy(b,q+87,n) else ""}else "";out.add(NczEntity(if(closed)"Polygon" else "Polyline",layer,pts,text=label))}}}
   }
  }catch(_:Exception){}
 }
}
