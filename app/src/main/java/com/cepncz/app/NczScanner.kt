package com.cepncz.app
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NczPoint(val x:Double,val y:Double)
data class NczReport(val size:Int,val version:String?,val labels:List<String>,val points:List<NczPoint>)

object NczScanner {
 private val pointSignature=byteArrayOf(0x15,0x65,0,0,0,0x02,0x02,0x01)
 fun scan(bytes:ByteArray):NczReport {
  val text=bytes.toString(Charsets.ISO_8859_1)
  val version=Regex("""\d+\.\d+\.\d+\.\d+""").find(text)?.value
  val words=Regex("""[A-Za-z_][A-Za-z_0-9]{2,31}""").findAll(text).map{it.value}.distinct().take(80).toList()
  val points=ArrayList<NczPoint>()
  var i=0
  while(i<=bytes.size-pointSignature.size-16){
   var ok=true
   for(k in pointSignature.indices) if(bytes[i+k]!=pointSignature[k]){ok=false;break}
   if(ok){
    val bb=ByteBuffer.wrap(bytes,i+8,16).order(ByteOrder.LITTLE_ENDIAN)
    val x=bb.double; val y=bb.double
    if(x.isFinite()&&y.isFinite()&&x in 1000.0..10000000.0&&y in 1000.0..10000000.0) points.add(NczPoint(x,y))
    i+=24
   } else i++
  }
  return NczReport(bytes.size,version,words,points)
 }
}
