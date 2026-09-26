package com.cepncz.app
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity(){
 private lateinit var info:TextView
 private lateinit var cad:CadView
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->if(u!=null)load(u)}
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(22,25,29))}
  val bar=LinearLayout(this).apply{setPadding(12,10,12,10)}
  val open=Button(this).apply{text="NCZ AÇ";setOnClickListener{picker.launch(arrayOf("*/*"))}}
  info=TextView(this).apply{setTextColor(Color.WHITE);text="  Dosya bekleniyor";gravity=Gravity.CENTER_VERTICAL}
  bar.addView(open);bar.addView(info,LinearLayout.LayoutParams(0,-1,1f));root.addView(bar)
  cad=CadView(this);root.addView(cad,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
 }
 private fun load(u:Uri){
  try{contentResolver.openInputStream(u)?.use{
   val r=NczScanner.scan(it.readBytes());val v=r.version?:"?"
   cad.setNczObjects(r.objects)
   val types=r.objects.groupingBy{it.type}.eachCount().entries.sortedByDescending{it.value}
   info.text="  "+(r.size/1024)+" KB • Netcad "+v+" • "+r.objects.size+" obje"
   AlertDialog.Builder(this).setTitle("NCZ okundu").setMessage("Boyut: "+r.size+" bayt\nSürüm: "+v+"\nObje: "+r.objects.size+"\n\nTipler:\n"+types.take(12).joinToString("\n"){it.key+" : "+it.value}).setPositiveButton("TAMAM",null).show()
  }}catch(e:Exception){Toast.makeText(this,"Dosya okunamadı: "+e.message,Toast.LENGTH_LONG).show()}
 }
}
