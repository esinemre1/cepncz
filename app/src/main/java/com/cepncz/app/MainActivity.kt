package com.cepncz.app
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity(){
 private lateinit var info:TextView
 private lateinit var cad:CadView
 private lateinit var panel:LinearLayout
 private lateinit var layerList:LinearLayout
 private lateinit var selectionInfo:TextView
 private var layerQuery=""
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->if(u!=null)load(u)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(22,25,29))}
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(8),dp(6))}
  val open=Button(this).apply{text="NCZ AÇ";setOnClickListener{picker.launch(arrayOf("*/*"))}}
  val layers=Button(this).apply{text="TABAKA";setOnClickListener{panel.visibility=if(panel.visibility==View.VISIBLE)View.GONE else View.VISIBLE}}
  info=TextView(this).apply{setTextColor(Color.WHITE);text="  Dosya bekleniyor";gravity=Gravity.CENTER_VERTICAL;maxLines=1}
  bar.addView(open);bar.addView(layers);bar.addView(info,LinearLayout.LayoutParams(0,dp(48),1f));root.addView(bar)
  val tools=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),dp(4))}
  fun toolButton(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=11f;minWidth=0;minimumWidth=0;setPadding(dp(5),0,dp(5),0);setOnClickListener{action()}}
  tools.addView(toolButton("SIĞDIR"){cad.fitToScreen()},LinearLayout.LayoutParams(0,dp(42),1f))
  tools.addView(toolButton("−"){cad.zoomOut()},LinearLayout.LayoutParams(0,dp(42),1f))
  tools.addView(toolButton("+"){cad.zoomIn()},LinearLayout.LayoutParams(0,dp(42),1f))
  tools.addView(toolButton("TARAMA"){val m=cad.cycleFillMode();Toast.makeText(this,"Tarama: "+cad.fillModeName(),Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(0,dp(42),1f))
  tools.addView(toolButton("ALAN"){cad.setShowAreas(!cad.isShowAreas())},LinearLayout.LayoutParams(0,dp(42),1f))
  val tools2=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),dp(4))}
  tools2.addView(toolButton("ALAN SORGU"){cad.setQueryMode(CadView.QueryMode.AREA);selectionInfo.text="Kapalı alana dokun"},LinearLayout.LayoutParams(0,dp(40),1f))
  tools2.addView(toolButton("UZUNLUK"){cad.setQueryMode(CadView.QueryMode.LENGTH);selectionInfo.text="Çizgi veya kenara dokun"},LinearLayout.LayoutParams(0,dp(40),1f))
  tools2.addView(toolButton("SEÇ"){cad.setQueryMode(CadView.QueryMode.SELECT);selectionInfo.text="Normal seçim modu"},LinearLayout.LayoutParams(0,dp(40),.75f))
  selectionInfo=TextView(this).apply{setTextColor(Color.WHITE);textSize=12f;text="Objeye dokun: bilgi burada gösterilir";setPadding(dp(8),0,dp(6),0);gravity=Gravity.CENTER_VERTICAL;maxLines=2}
  tools2.addView(selectionInfo,LinearLayout.LayoutParams(0,dp(48),2.1f))
  root.addView(tools2)
  root.addView(tools)
  val workspace=FrameLayout(this)
  cad=CadView(this)
  cad.onMeasureInfo={msg->selectionInfo.text=msg}\n  cad.onSelectionChanged={s->selectionInfo.text=when{
   s==null->"Seçim yok"
   s.queryMode=="AREA"&&s.area>0.0->s.layerName+" • Alan %.2f m² • Çevre %.2f m".format(s.area,s.perimeter)
   s.queryMode=="LENGTH"->s.layerName+" • Uzunluk %.2f m".format(s.length)
   s.area>0.0->s.layerName+" • Alan %.2f m² • Çevre %.2f m".format(s.area,s.perimeter)
   else->s.layerName+" • "+s.kind+" • X %.3f Y %.3f".format(s.x,s.y)
  }}
  workspace.addView(cad,FrameLayout.LayoutParams(-1,-1))
  panel=buildLayerPanel();workspace.addView(panel,FrameLayout.LayoutParams(dp(310),-1,Gravity.END))
  root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
 }
 private fun buildLayerPanel():LinearLayout{
  val p=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(10),dp(10),dp(10));setBackgroundColor(Color.rgb(31,35,40));visibility=View.GONE;elevation=dp(12).toFloat()}
  val title=TextView(this).apply{text="TABAKALAR";textSize=18f;setTextColor(Color.WHITE);setPadding(dp(4),dp(4),0,dp(8))}
  val search=EditText(this).apply{hint="Tabaka ara...";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);setSingleLine(true);setBackgroundColor(Color.rgb(45,50,56));setPadding(dp(10),0,dp(10),0)}
  search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){layerQuery=s?.toString()?.trim()?:"";refreshLayers()};override fun afterTextChanged(e:Editable?){}})
  val actions=LinearLayout(this)
  val all=Button(this).apply{text="TÜMÜ AÇ";textSize=11f;setOnClickListener{cad.setAllLayersVisible(true);refreshLayers()}}
  val none=Button(this).apply{text="TÜMÜ KAPAT";textSize=11f;setOnClickListener{cad.setAllLayersVisible(false);refreshLayers()}}
  actions.addView(all,LinearLayout.LayoutParams(0,dp(46),1f));actions.addView(none,LinearLayout.LayoutParams(0,dp(46),1f))
  val scroll=ScrollView(this);layerList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(layerList)
  p.addView(title);p.addView(search,LinearLayout.LayoutParams(-1,dp(44)));p.addView(actions);p.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));return p
 }
 private fun refreshLayers(){
  if(!::layerList.isInitialized||!::cad.isInitialized)return
  layerList.removeAllViews()
  val counts=cad.layerCounts().entries.sortedBy{cad.layerName(it.key).lowercase()}
  for((code,count) in counts){
   val name=cad.layerName(code);if(layerQuery.isNotEmpty()&&!name.contains(layerQuery,true))continue
   val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(2),dp(2),dp(2))}
   val sw=Switch(this).apply{isChecked=cad.isLayerVisible(code);setOnCheckedChangeListener{_,on->cad.setLayerVisible(code,on)}}
   val label=TextView(this).apply{text=name;setTextColor(Color.WHITE);textSize=14f;maxLines=1}
   val badge=TextView(this).apply{text=count.toString();setTextColor(Color.LTGRAY);gravity=Gravity.CENTER}
   row.setOnClickListener{sw.isChecked=!sw.isChecked};row.addView(sw,LinearLayout.LayoutParams(dp(52),dp(44)));row.addView(label,LinearLayout.LayoutParams(0,dp(44),1f));row.addView(badge,LinearLayout.LayoutParams(dp(48),dp(44)));layerList.addView(row)
  }
 }
 private fun load(u:Uri){
  try{contentResolver.openInputStream(u)?.use{
   val r=NczScanner.scan(it.readBytes());val v=r.version?:"?"
   cad.setEntities(r.entities,r.layers);refreshLayers()
   val types=r.entities.groupingBy{it.kind}.eachCount().entries.sortedByDescending{it.value}
   info.text="  "+(r.size/1024)+" KB • Netcad "+v+" • "+r.entities.size+" geometri"
   AlertDialog.Builder(this).setTitle("NCZ okundu").setMessage("Boyut: "+r.size+" bayt\nSürüm: "+v+"\nGeometri: "+r.entities.size+"\nTabaka: "+r.layers.size+"\n\nTipler:\n"+types.take(12).joinToString("\n"){it.key+" : "+it.value}).setPositiveButton("TAMAM",null).show()
  }}catch(e:Exception){Toast.makeText(this,"Dosya okunamadı: "+e.message,Toast.LENGTH_LONG).show()}
 }
}
