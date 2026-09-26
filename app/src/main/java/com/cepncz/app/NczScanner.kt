package com.cepncz.app

data class NczReport(val size:Int,val version:String?,val labels:List<String>)
object NczScanner {
 fun scan(bytes:ByteArray):NczReport {
  val text=bytes.toString(Charsets.ISO_8859_1)
  val version=Regex("""\\d+\\.\\d+\\.\\d+\\.\\d+""").find(text)?.value
  val words=Regex("""[A-Za-z_][A-Za-z_0-9]{2,31}""").findAll(text).map{it.value}.distinct().take(80).toList()
  return NczReport(bytes.size,version,words)
 }
}
