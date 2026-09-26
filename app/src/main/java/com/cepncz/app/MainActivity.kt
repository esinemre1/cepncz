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
  fun toolButton(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=11f;minWidth=0;minimumWidth=0;setPadding(dp(5),0,dp(5),0);setOnClickListener{action()}}
  selectionInfo=TextView(this).apply{setTextColor(Color.LTGRAY);textSize=11f;text="Hazır";setPadding(dp(10),0,dp(8),0);gravity=Gravity.CENTER_VERTICAL;maxLines=1}
  val menuBar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(3),dp(4),dp(3));setBackgroundColor(Color.rgb(31,35,40))}
  fun popup(anchor:View,items:List<Pair<String,()->Unit>>){
   val p=PopupMenu(this,anchor);items.forEachIndexed{i,it->p.menu.add(0,i,i,it.first)}
   p.setOnMenuItemClickListener{m->items[m.itemId].second();true};p.show()
  }
  val fileBtn=toolButton("DOSYA"){v->}
  fileBtn.setOnClickListener{v->popup(v,listOf("NCZ AÇ" to {picker.launch(arrayOf("*/*"))},"TABAKALAR" to {panel.visibility=if(panel.visibility==View.VISIBLE)View.GONE else View.VISIBLE}))}
  val viewBtn=toolButton("GÖRÜNÜM"){v->}
  viewBtn.setOnClickListener{v->popup(v,listOf(
   "TÜMÜNÜ SIĞDIR" to {cad.fitToScreen()},"YAKINLAŞTIR +" to {cad.zoomIn()},"UZAKLAŞTIR −" to {cad.zoomOut()},
   "PENCERE ZOOM" to {cad.startZoomWindow()},"SEÇİME ZOOM" to {if(!cad.zoomToSelected())Toast.makeText(this,"Önce obje seç",Toast.LENGTH_SHORT).show()},
   "TARAMA / DOLGU" to {cad.cycleFillMode()},"ALAN YAZILARI" to {cad.setShowAreas(!cad.isShowAreas())}
  ))}
  val queryBtn=toolButton("SORGU"){v->}
  queryBtn.setOnClickListener{v->popup(v,listOf(
   "SEÇİM" to {cad.setQueryMode(CadView.QueryMode.SELECT)},"ALAN SORGU" to {cad.setQueryMode(CadView.QueryMode.AREA)},
   "UZUNLUK" to {cad.setQueryMode(CadView.QueryMode.LENGTH)},"2 NOKTA" to {cad.setQueryMode(CadView.QueryMode.DISTANCE)},
   "KIRIK HAT" to {cad.setQueryMode(CadView.QueryMode.POLYLINE)},"XY SORGU" to {cad.setQueryMode(CadView.QueryMode.COORDINATE)}
  ))}
  val cadBtn=toolButton("CAD"){v->}
  cadBtn.setOnClickListener{v->popup(v,listOf(
   "SNAP AÇ / KAPAT" to {cad.toggleSnap()},"ÖLÇÜM GERİ" to {cad.undoMeasure()},"ÖLÇÜM TEMİZLE" to {cad.clearMeasure()}
  ))}
  info=TextView(this).apply{setTextColor(Color.WHITE);text="Dosya bekleniyor";textSize=11f;gravity=Gravity.CENTER_VERTICAL;maxLines=1}
  menuBar.addView(fileBtn,LinearLayout.LayoutParams(dp(78),dp(42)));menuBar.addView(viewBtn,LinearLayout.LayoutParams(dp(92),dp(42)))
  menuBar.addView(queryBtn,LinearLayout.LayoutParams(dp(78),dp(42)));menuBar.addView(cadBtn,LinearLayout.LayoutParams(dp(66),dp(42)))
  menuBar.addView(info,LinearLayout.LayoutParams(0,dp(42),1f));root.addView(menuBar)
  val workspace=FrameLayout(this)
  cad=CadView(this)
  cad.onMeasureInfo={msg->selectionInfo.text=msg}
  cad.onSelectionChanged={s->selectionInfo.text=when{
   s==null->"Seçim yok"
   s.queryMode=="AREA"&&s.area>0.0->s.layerName+" • Alan %.2f m² • Çevre %.2f m".format(s.area,s.perimeter)
   s.queryMode=="LENGTH"->s.layerName+" • Uzunluk %.2f m".format(s.length)
   s.area>0.0->s.layerName+" • Alan %.2f m² • Çevre %.2f m".format(s.area,s.perimeter)
   else->s.layerName+" • "+s.kind+" • X %.3f Y %.3f".format(s.x,s.y)
  }}
  workspace.addView(cad,FrameLayout.LayoutParams(-1,-1))
  val navHud=TextView(this).apply{
   setTextColor(Color.WHITE);setBackgroundColor(Color.argb(175,20,23,27));textSize=11f
   text="ZOOM 1.00x";setPadding(dp(10),dp(5),dp(10),dp(5));gravity=Gravity.CENTER
  }
  cad.onNavigationInfo={msg->navHud.text=msg}
  workspace.addView(navHud,FrameLayout.LayoutParams(-2,dp(34),Gravity.BOTTOM or Gravity.START).apply{setMargins(dp(8),0,0,dp(8))})
  val north=TextView(this).apply{
   text="N\n↑";setTextColor(Color.WHITE);textSize=15f;gravity=Gravity.CENTER
   setBackgroundColor(Color.argb(175,20,23,27))
  }
  workspace.addView(north,FrameLayout.LayoutParams(dp(46),dp(58),Gravity.TOP or Gravity.END).apply{setMargins(0,dp(8),dp(8),0)})
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
   val summary=buildString {
    append("Boyut: ").append(r.size).append(" bayt").appendLine()
    append("Sürüm: ").append(v).appendLine()
    append("Geometri: ").append(r.entities.size).appendLine()
    append("Tabaka: ").append(r.layers.size).appendLine().appendLine()
    append("Tipler:").appendLine()
    append(types.take(12).joinToString(separator=System.lineSeparator()){it.key+" : "+it.value})
   }
   AlertDialog.Builder(this).setTitle("NCZ okundu").setMessage(summary).setPositiveButton("TAMAM",null).show()
  }}catch(e:Exception){Toast.makeText(this,"Dosya okunamadı: "+e.message,Toast.LENGTH_LONG).show()}
 }
}
