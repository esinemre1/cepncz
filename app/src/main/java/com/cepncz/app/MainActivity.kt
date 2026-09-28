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
import java.util.concurrent.Executors

class MainActivity:AppCompatActivity(){
 private lateinit var info:TextView
 private lateinit var cad:CadView
 private lateinit var panel:LinearLayout
 private lateinit var layerList:LinearLayout
 private lateinit var selectionInfo:TextView
 private var layerQuery=""
 private val ioExecutor=Executors.newSingleThreadExecutor()
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->if(u!=null)load(u)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(22,25,29))}
  fun menuButton(label:String)=Button(this).apply{
   text=label;textSize=12f;minWidth=0;minimumWidth=0;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(42,47,53));setPadding(dp(8),0,dp(8),0)
  }
  selectionInfo=TextView(this).apply{setTextColor(Color.WHITE);textSize=11f;text="Hazır";setPadding(dp(10),0,dp(8),0);gravity=Gravity.CENTER_VERTICAL;maxLines=1;setBackgroundColor(Color.argb(190,28,32,36))}
  fun popup(anchor:View,items:List<Pair<String,()->Unit>>){
   val p=PopupMenu(this,anchor);items.forEachIndexed{i,it->p.menu.add(0,i,i,it.first)}
   p.setOnMenuItemClickListener{m->items[m.itemId].second();true};p.show()
  }
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(5),dp(8),dp(5));setBackgroundColor(Color.rgb(27,31,36))}
  val brand=TextView(this).apply{text="CepNCZ";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL}
  info=TextView(this).apply{setTextColor(Color.LTGRAY);text="Dosya bekleniyor";textSize=10f;gravity=Gravity.CENTER_VERTICAL or Gravity.END;maxLines=1}
  header.addView(brand,LinearLayout.LayoutParams(dp(90),dp(42)));header.addView(info,LinearLayout.LayoutParams(0,dp(42),1f));root.addView(header)
  val menuScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;setBackgroundColor(Color.rgb(34,38,43))}
  val menuBar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(6),dp(4),dp(6),dp(4))}
  val fileBtn=menuButton("DOSYA");fileBtn.setOnClickListener{v->popup(v,listOf("NCZ AÇ" to {picker.launch(arrayOf("*/*"))},"TABAKALAR" to {panel.visibility=if(panel.visibility==View.VISIBLE)View.GONE else View.VISIBLE}))}
  val viewBtn=menuButton("GÖRÜNÜM");viewBtn.setOnClickListener{v->popup(v,listOf(
   "TÜMÜNÜ SIĞDIR" to {cad.fitToScreen()},"YAKINLAŞTIR +" to {cad.zoomIn()},"UZAKLAŞTIR −" to {cad.zoomOut()},"PENCERE ZOOM" to {cad.startZoomWindow()},
   "SEÇİME ZOOM" to {if(!cad.zoomToSelected())Toast.makeText(this,"Önce obje seç",Toast.LENGTH_SHORT).show()},"TARAMA / DOLGU" to {cad.cycleFillMode()},"ALAN YAZILARI" to {cad.setShowAreas(!cad.isShowAreas())}
  ))}
  val queryBtn=menuButton("SORGU");queryBtn.setOnClickListener{v->popup(v,listOf(
   "SEÇİM" to {cad.setQueryMode(CadView.QueryMode.SELECT)},"ALAN SORGU" to {cad.setQueryMode(CadView.QueryMode.AREA)},"UZUNLUK" to {cad.setQueryMode(CadView.QueryMode.LENGTH)},
   "XY SORGU" to {cad.setQueryMode(CadView.QueryMode.COORDINATE)}
  ))}
  val measureBtn=menuButton("ÖLÇÜM");measureBtn.setOnClickListener{v->popup(v,listOf(
   "2 NOKTA MESAFE" to {cad.setQueryMode(CadView.QueryMode.DISTANCE)},"KIRIK HAT" to {cad.setQueryMode(CadView.QueryMode.POLYLINE)},
   "UZUNLUK YAZDIR" to {cad.startLengthLabelMode()},"UZUNLUK YAZILARINI SİL" to {cad.clearLengthLabels()},
   "GERİ AL" to {cad.undoMeasure()},"TEMİZLE" to {cad.clearMeasure()}
  ))}
  val cadBtn=menuButton("CAD");cadBtn.setOnClickListener{v->popup(v,listOf(
   "SNAP AÇ / KAPAT" to {val on=cad.toggleSnap();Toast.makeText(this,if(on)"SNAP açık" else "SNAP kapalı",Toast.LENGTH_SHORT).show()},
   "TABAKALAR" to {panel.visibility=if(panel.visibility==View.VISIBLE)View.GONE else View.VISIBLE},"SEÇİM MODU" to {cad.setQueryMode(CadView.QueryMode.SELECT)}
  ))}
  val parcelBtn=menuButton("PARSEL");parcelBtn.setOnClickListener{v->popup(v,listOf(
   "PARSEL SEÇ" to {cad.setQueryMode(CadView.QueryMode.SELECT);Toast.makeText(this,"Parsele dokunarak seç",Toast.LENGTH_SHORT).show()},
   "PARSEL BİLGİSİ" to {val s=cad.selectedParcelSummary();if(s!=null)AlertDialog.Builder(this).setTitle("PARSEL BİLGİSİ").setMessage(s).setPositiveButton("TAMAM",null).show() else Toast.makeText(this,"Önce bir parsel seç",Toast.LENGTH_SHORT).show()},
   "TÜM KENARLARI YAZDIR" to {val n=cad.labelSelectedParcelEdges();Toast.makeText(this,if(n>0)"$n kenar uzunluğu yazdırıldı" else "Önce bir parsel seç",Toast.LENGTH_SHORT).show()},
   "KÖŞELERİ NOKTA YAP" to {val n=cad.saveSelectedParcelCorners();Toast.makeText(this,if(n>0)"$n köşe Nokta Listesine eklendi" else "Önce bir parsel seç",Toast.LENGTH_SHORT).show()}
  ))}
  val pointBtn=menuButton("NOKTA");pointBtn.setOnClickListener{v->popup(v,listOf(
   "NOKTA YAKALA" to {cad.startPointCapture()},
   "SNAP AYARLARI" to {showSnapSettings()},
   "SON KONUMU KAYDET" to {if(cad.saveCurrentPoint()==null)Toast.makeText(this,"Önce haritada bir konuma dokun",Toast.LENGTH_SHORT).show()},
   "Y - X ELLE GİR" to {showManualPointDialog()},
   "NOKTA LİSTESİ" to {showPointList()},
   "SON NOKTAYI SİL" to {val n=cad.savedPointCount();if(n>0){cad.removeSavedPoint(n-1);Toast.makeText(this,"N$n silindi",Toast.LENGTH_SHORT).show()}else Toast.makeText(this,"Kayıtlı nokta yok",Toast.LENGTH_SHORT).show()},
   "TÜM NOKTALARI TEMİZLE" to {confirmClearPoints()}
  ))}
  listOf(fileBtn,viewBtn,queryBtn,measureBtn,parcelBtn,pointBtn,cadBtn).forEach{menuBar.addView(it,LinearLayout.LayoutParams(dp(92),dp(42)).apply{setMargins(dp(2),0,dp(2),0)})}
  menuScroll.addView(menuBar);root.addView(menuScroll,LinearLayout.LayoutParams(-1,dp(50)))
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
  workspace.addView(selectionInfo,FrameLayout.LayoutParams(-1,dp(34),Gravity.BOTTOM).apply{setMargins(dp(8),0,dp(8),dp(44))})
  val navHud=TextView(this).apply{
   setTextColor(Color.WHITE);setBackgroundColor(Color.argb(175,20,23,27));textSize=11f
   text="ZOOM 1.00x";setPadding(dp(10),dp(5),dp(10),dp(5));gravity=Gravity.CENTER
  }
  cad.onNavigationInfo={msg->navHud.text=msg}
  workspace.addView(navHud,FrameLayout.LayoutParams(-2,dp(34),Gravity.BOTTOM or Gravity.START).apply{setMargins(dp(8),0,0,dp(8))})
  val quickPad=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
  fun quickButton(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=if(label=="+"||label=="−")24f else 10f;minWidth=0;minimumWidth=0;setPadding(0,0,0,0);setOnClickListener{action()}}
  quickPad.addView(quickButton("+"){cad.zoomIn()},LinearLayout.LayoutParams(dp(52),dp(52)))
  quickPad.addView(quickButton("−"){cad.zoomOut()},LinearLayout.LayoutParams(dp(52),dp(52)))
  quickPad.addView(quickButton("SIĞDIR"){cad.fitToScreen()},LinearLayout.LayoutParams(dp(52),dp(46)))
  workspace.addView(quickPad,FrameLayout.LayoutParams(dp(56),dp(154),Gravity.END or Gravity.CENTER_VERTICAL).apply{setMargins(0,0,dp(10),0)})
  val north=TextView(this).apply{
   text="N ↑";setTextColor(Color.WHITE);textSize=15f;gravity=Gravity.CENTER
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
 private fun showSnapSettings(){
  val kinds=arrayOf("KÖŞE","ORTA","KESİŞİM","DİK","YAKIN")
  val checked=BooleanArray(kinds.size){cad.isSnapKindEnabled(kinds[it])}
  AlertDialog.Builder(this).setTitle("SNAP AYARLARI")
   .setMultiChoiceItems(kinds,checked){_,which,on->cad.setSnapKindEnabled(kinds[which],on)}
   .setPositiveButton("TAMAM",null).show()
 }
 private fun showManualPointDialog(){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),0)}
  val y=EditText(this).apply{hint="Y (Kuzey / Northing)";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED}
  val x=EditText(this).apply{hint="X (Doğu / Easting)";inputType=y.inputType}
  box.addView(y);box.addView(x)
  val d=AlertDialog.Builder(this).setTitle("KOORDİNATTAN NOKTA").setView(box).setNegativeButton("İPTAL",null).setPositiveButton("EKLE",null).create()
  d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   val yy=y.text.toString().replace(',','.').toDoubleOrNull();val xx=x.text.toString().replace(',','.').toDoubleOrNull()
   if(yy==null||xx==null){Toast.makeText(this,"Geçerli Y ve X gir",Toast.LENGTH_SHORT).show()}else{val n=cad.addSavedPoint(yy,xx);d.dismiss();cad.zoomToSavedPoint(n-1);selectionInfo.text="N$n • Y %.3f • X %.3f".format(yy,xx)}
  }}
  d.show()
 }
 private fun confirmClearPoints(){
  val n=cad.savedPointCount();if(n==0){Toast.makeText(this,"Kayıtlı nokta yok",Toast.LENGTH_SHORT).show();return}
  AlertDialog.Builder(this).setTitle("Noktaları temizle").setMessage("$n kayıtlı noktanın tamamı silinsin mi?")
   .setNegativeButton("VAZGEÇ",null).setPositiveButton("SİL"){_,_->cad.clearSavedPoints();Toast.makeText(this,"Nokta listesi temizlendi",Toast.LENGTH_SHORT).show()}.show()
 }
 private fun showPointList(){
  val pts=cad.savedPointList()
  if(pts.isEmpty()){Toast.makeText(this,"Kayıtlı nokta yok",Toast.LENGTH_SHORT).show();return}
  val labels=pts.mapIndexed{i,p->"N%-4d  Y %12.3f   X %12.3f".format(i+1,p.y,p.x)}.toTypedArray()
  AlertDialog.Builder(this).setTitle("NOKTA LİSTESİ • "+pts.size+" NOKTA")
   .setItems(labels){_,which->cad.zoomToSavedPoint(which);selectionInfo.text=labels[which]}
   .setNeutralButton("SON NOKTAYI SİL"){_,_->cad.removeSavedPoint(pts.lastIndex);Toast.makeText(this,"N"+pts.size+" silindi",Toast.LENGTH_SHORT).show()}
   .setNegativeButton("KAPAT",null).show()
 }
 private fun load(u:Uri){
  info.text="NCZ okunuyor…"
  selectionInfo.text="Dosya hazırlanıyor…"
  ioExecutor.execute{
   try{
    val bytes=contentResolver.openInputStream(u)?.use{it.readBytes()}?:throw IllegalStateException("Dosya açılamadı")
    val r=NczScanner.scan(bytes);val v=r.version?:"?"
    val types=r.entities.groupingBy{it.kind}.eachCount().entries.sortedByDescending{it.value}
    runOnUiThread{
     cad.setEntities(r.entities,r.layers);refreshLayers()
     info.text="  "+(r.size/1024)+" KB • Netcad "+v+" • "+r.entities.size+" geometri"
     selectionInfo.text="Hazır • "+r.entities.size+" geometri"
     val summary=buildString{
      append("Boyut: ").append(r.size).append(" bayt").appendLine()
      append("Sürüm: ").append(v).appendLine()
      append("Geometri: ").append(r.entities.size).appendLine()
      append("Tabaka: ").append(r.layers.size).appendLine().appendLine()
      append("Tipler:").appendLine()
      append(types.take(12).joinToString(separator=System.lineSeparator()){it.key+" : "+it.value})
     }
     AlertDialog.Builder(this).setTitle("NCZ okundu").setMessage(summary).setPositiveButton("TAMAM",null).show()
    }
   }catch(e:Exception){runOnUiThread{info.text="Dosya açılamadı";selectionInfo.text="Hazır";Toast.makeText(this,"Dosya okunamadı: "+e.message,Toast.LENGTH_LONG).show()}}
  }
 }
 override fun onDestroy(){ioExecutor.shutdownNow();super.onDestroy()}
}
