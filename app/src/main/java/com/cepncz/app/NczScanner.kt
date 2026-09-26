package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NczPoint(val x:Double,val y:Double)
data class NczObject(val type:String,val anchor:NczPoint,val minX:Double,val minY:Double,val maxX:Double,val maxY:Double)
data class NczReport(val size:Int,val version:String?,val labels:List<String>,val objects:List<NczObject>)

object NczScanner {
 private fun d(b:ByteArray,o:Int)=ByteBuffer.wrap(b,o,8).order(ByteOrder.LITTLE_ENDIAN).double
 fun scan(bytes:ByteArray):NczReport {
  val text=bytes.toString(Charsets.ISO_8859_1)
  val version=Regex("""\d+\.\d+\.\d+\.\d+""").find(text)?.value
  val words=Regex("""[A-Za-z_][A-Za-z_0-9]{2,31}""").findAll(text).map{it.value}.distinct().take(120).toList()
  val out=ArrayList<NczObject>(); var i=0
  while(i+106<=bytes.size){
   if(bytes[i].toInt()==0x15 && bytes[i+5].toInt() in 1..8 && bytes[i+6].toInt() in 1..8){
    val recordSize=(bytes[i+1].toInt() and 255)+5
    if(recordSize>=90 && i+recordSize<=bytes.size){
     try{
      val x=d(bytes,i+8);val y=d(bytes,i+16)
      val minX=d(bytes,i+50);val minY=d(bytes,i+58);val maxX=d(bytes,i+66);val maxY=d(bytes,i+74)
      if(listOf(x,y,minX,minY,maxX,maxY).all{it.isFinite()&&it in 1000.0..10000000.0} && minX<=maxX && minY<=maxY){
       val type=(bytes[i+5].toInt() and 255).toString()+"/"+(bytes[i+6].toInt() and 255)+"/"+(bytes[i+7].toInt() and 255)
       out.add(NczObject(type,NczPoint(x,y),minX,minY,maxX,maxY));i+=recordSize;continue
      }
     }catch(_:Exception){}
    }
   };i++
  }
  return NczReport(bytes.size,version,words,out)
 }
}
