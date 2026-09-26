package com.cepncz.app
import android.app.*
import android.os.*
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
class MainActivity:androidx.appcompat.app.AppCompatActivity(){
 private lateinit var info:TextView
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->if(u!=null)load(u)}
 override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(22,25,29))};val bar=LinearLayout(this).apply{setPadding(12,10,12,10)};val open=Button(this).apply{text="NCZ AÇ";setOnClickListener{picker.launch(arrayOf("*/*"))}};info=TextView(this).apply{setTextColor(Color.WHITE);text="  Dosya bekleniyor";gravity=Gravity.CENTER_VERTICAL};bar.addView(open);bar.addView(info,LinearLayout.LayoutParams(0,-1,1f));root.addView(bar);root.addView(CadView(this),LinearLayout.LayoutParams(-1,0,1f));setContentView(root)}
 private fun load(u:Uri){try{contentResolver.openInputStream(u)?.use{val bytes=it.readBytes();val r=NczScanner.scan(bytes);val v=r.version ?: "?";info.text="  "+(r.size/1024)+" KB • Netcad "+v+" • "+r.labels.size+" kayıt";AlertDialog.Builder(this).setTitle("NCZ okundu").setMessage("Boyut: "+r.size+" bayt\nSürüm izi: "+v+"\n\nBulunan metinler:\n"+r.labels.take(25).joinToString(", ")).setPositiveButton("TAMAM",null).show()}}catch(e:Exception){Toast.makeText(this,"Dosya okunamadı: "+e.message,Toast.LENGTH_LONG).show()}}
}
