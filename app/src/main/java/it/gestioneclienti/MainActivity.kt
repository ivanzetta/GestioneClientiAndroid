package it.gestioneclienti

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.app.DatePickerDialog
import android.app.Activity
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.app.KeyguardManager
import android.content.Context
import android.app.AlertDialog
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import java.security.MessageDigest

class MainActivity: Activity() {
 private val prefs by lazy { getSharedPreferences("gestione_clienti_options", Context.MODE_PRIVATE) }
 private fun tr(it:String):String = if(prefs.getString("language", "it") == "de") translations[it] ?: it else it
 private fun isGerman() = prefs.getString("language", "it") == "de"
 private var unlocked=false
 private var lockedSinceAuth=false
 private lateinit var keyguardManager:KeyguardManager
 private val infoKeys=(1..5).map { "info$it" }
 private fun infoLabel(key:String)=prefs.getString("label_$key","Info"+key.removePrefix("info")) ?: key
 private fun customInfo(key:String)=prefs.getString("label_$key",null)?.trim()?.takeIf { it.isNotBlank() && it!="Info"+key.removePrefix("info") }
 private var prompting=false
 private var hasStarted=false
 private var activationDialogShowing=false
 private val activationHash="676dd0aa983bd7d0d99e4cb2fc0c927a9680ce6bd2c25b247635afe8b6286638"
 private fun isActivated()=prefs.getBoolean("yellowkunde_activated",false)
 private fun checkActivation() {
  if (isActivated()) { authenticate(); return }
  if (activationDialogShowing || isFinishing) return
  activationDialogShowing=true
  val field=EditText(this).apply { hint="Codice di attivazione";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setPadding(dp(16),dp(12),dp(16),dp(12)) }
  val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),0);addView(field) }
  val dialog=AlertDialog.Builder(this).setTitle("Attiva YellowKunde").setMessage("Inserisci il codice condiviso per attivare l’app offline.").setView(box).setPositiveButton("Attiva",null).setNegativeButton("Esci"){_,_->finish()}.setCancelable(false).create()
  dialog.setOnShowListener {
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
    val digest=MessageDigest.getInstance("SHA-256").digest(field.text.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    if (MessageDigest.isEqual(digest.toByteArray(),activationHash.toByteArray())) {
     prefs.edit().putBoolean("yellowkunde_activated",true).apply();activationDialogShowing=false;dialog.dismiss();authenticate()
    } else field.error="Codice non corretto"
   }
  }
  dialog.show()
 }
 private var currentPage="list"
 private var selectedDay=LocalDate.now()
 private var editingCustomerCode:String?=null
 private var editingFields:Map<String,EditText> = emptyMap()
 private var originalValues:Map<String,String> = emptyMap()
 private var listFiltersVisible=false
 private var listInfoVisible=false
 private var listSearch=""
 private var listZone=""
 private var listScrollY=0
 private val displayDate:DateTimeFormatter=DateTimeFormatter.ofPattern("dd-MM-uuuu",Locale.ITALIAN)
 private fun formatDate(value:String):String = runCatching { LocalDate.parse(value).format(displayDate) }.getOrDefault(value)
 private fun pickDate(initial:LocalDate=LocalDate.now(), onPicked:(LocalDate)->Unit) {
  DatePickerDialog(this,{ _,year,month,day -> onPicked(LocalDate.of(year,month+1,day)) },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
   // Dates in the planning screens may be in the future.
  }.show()
 }

 private lateinit var db:Database
 private val importRequest=101
 private val exportRequest=102
 private val backupRequest=103
 private val restoreRequest=104
 private var backupPassword:CharArray?=null
 private lateinit var root:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=Database(this); keyguardManager=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and (android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE).inv()
  if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
  registerReceiver(object:android.content.BroadcastReceiver(){ override fun onReceive(context:Context?, intent:Intent?) { lockedSinceAuth=true;unlocked=false } },android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
  if (android.os.Build.VERSION.SDK_INT >= 33) {
   onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, OnBackInvokedCallback { handleAppBack() })
  }
  authenticate() }
 private fun authenticate() {
  if (unlocked || prompting) return
  val km=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  if (!km.isDeviceSecure) {
   AlertDialog.Builder(this).setTitle(tr("Protezione richiesta"))
    .setMessage(tr("Configura un PIN e, se desideri, l’impronta digitale nelle impostazioni di Android prima di usare l’app."))
    .setPositiveButton(tr("Chiudi")) { _,_ -> finish() }.setCancelable(false).show()
   return
  }
  if (android.os.Build.VERSION.SDK_INT < 30) {
   AlertDialog.Builder(this).setMessage(tr("Questa versione richiede Android 11 o successivo per l’accesso protetto."))
    .setPositiveButton(tr("Chiudi")) { _,_ -> finish() }.setCancelable(false).show()
   return
  }
  prompting=true
  val prompt=BiometricPrompt.Builder(this)
   .setTitle(tr("Sblocca YellowKunde"))
   .setSubtitle(tr("Usa impronta digitale o PIN del telefono"))
   .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
   .build()
  prompt.authenticate(CancellationSignal(), mainExecutor, object:BiometricPrompt.AuthenticationCallback() {
   override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {
    prompting=false;unlocked=true;lockedSinceAuth=false;showHome()
   }
   override fun onAuthenticationError(errorCode:Int, errString:CharSequence) {
    prompting=false; if(!unlocked) finish()
   }
  })
 }
 override fun onStop() { super.onStop() }
 override fun onStart() { super.onStart(); if (hasStarted && ::db.isInitialized && (!unlocked || lockedSinceAuth || keyguardManager.isKeyguardLocked)) { unlocked=false;authenticate() }; hasStarted=true }
 private val brandYellow=Color.rgb(250,179,0)
 private val ink=Color.rgb(34,34,34)
 private val warmBackground=Color.rgb(249,249,247)
 private val softBorder=Color.rgb(223,199,137)
 private val paleYellow=Color.rgb(255,246,218)
 private fun dp(n:Int):Int=(n*resources.displayMetrics.density).toInt()
 private fun shape(color:Int,stroke:Int?=null):GradientDrawable=GradientDrawable().apply {
  setColor(color);cornerRadius=dp(14).toFloat();if(stroke!=null)setStroke(dp(1),stroke)
 }
 private fun button(label:String, action:()->Unit):Button = Button(this).apply {
  text=label;isAllCaps=false;textSize=16f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD
  background=shape(Color.WHITE,Color.rgb(233,230,219));minHeight=dp(46);setPadding(dp(12),dp(8),dp(12),dp(8))
  val params=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
  params.setMargins(0,dp(5),0,dp(5));layoutParams=params
  setOnClickListener{action()}
 }
 private fun iconButton(symbol:String, description:String, action:()->Unit):ImageButton = ImageButton(this).apply {
  val icon = when(symbol) {
   "☎" -> R.drawable.ic_action_phone
   "✉" -> R.drawable.ic_action_mail
   "➤" -> R.drawable.ic_action_navigation
   "↗" -> R.drawable.ic_action_share
   "▦" -> R.drawable.ic_action_calendar
   else -> R.drawable.ic_action_share
  }
  setImageResource(icon)
  scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
  contentDescription=tr(description)
  background=shape(brandYellow)
  setPadding(dp(11),dp(11),dp(11),dp(11))
  layoutParams=LinearLayout.LayoutParams(dp(44),dp(44)).apply { marginEnd=dp(7) }
  setOnClickListener { action() }
 }
 private fun compactAction(icon:Int,description:String,action:()->Unit):ImageButton = ImageButton(this).apply {
  setImageResource(icon);contentDescription=tr(description)
  scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
  background=shape(brandYellow);setPadding(dp(12),dp(12),dp(12),dp(12))
  layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(4) }
  setOnClickListener { action() }
 }
 private fun homeIconAction(icon:Int,description:String,action:()->Unit):ImageButton = ImageButton(this).apply {
  setImageResource(icon)
  imageTintList=android.content.res.ColorStateList.valueOf(Color.BLACK)
  contentDescription=tr(description)
  scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
  background=shape(brandYellow)
  setPadding(dp(7),dp(7),dp(7),dp(7))
  minimumWidth=0;minimumHeight=0
  layoutParams=LinearLayout.LayoutParams(dp(36),dp(36)).apply { marginStart=dp(3) }
  setOnClickListener { action() }
 }
 private fun compactTextAction(symbol:String,description:String,action:()->Unit):TextView = TextView(this).apply {
  text=symbol;textSize=19f;gravity=android.view.Gravity.CENTER;setTextColor(ink)
  contentDescription=tr(description);background=shape(brandYellow)
  layoutParams=LinearLayout.LayoutParams(dp(36),dp(36)).apply { marginStart=dp(3) }
  setOnClickListener { action() }
 }
 private fun contactRow(label:String,value:String,symbol:String,action:()->Unit) {
  if(value.isBlank()) return
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,dp(4)) }
  row.addView(TextView(this).apply {
   val prefix=tr(label)+": "
   text=android.text.SpannableString(prefix+value).apply {
    setSpan(android.text.style.StyleSpan(Typeface.BOLD),prefix.length,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
   }
   textSize=16f;setTextColor(ink);layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
  })
  row.addView(iconButton(symbol,label,action));root.addView(row)
 }
 private fun primaryButton(label:String, action:()->Unit):Button=button(label,action).apply { background=shape(Color.rgb(255,216,91)) }
 private fun secondaryButton(label:String, action:()->Unit):Button=button(label,action).apply {
  background=shape(Color.WHITE,softBorder)
 }
 private fun homeText(label:String,size:Float=16f,bold:Boolean=false,color:Int=ink):TextView =
  TextView(this).apply { text=label;textSize=size;setTextColor(color)
   if(bold)typeface=Typeface.DEFAULT_BOLD;gravity=android.view.Gravity.CENTER_VERTICAL }
 private fun homeCard(color:Int):LinearLayout=LinearLayout(this).apply {
  orientation=LinearLayout.VERTICAL;background=shape(color)
  setPadding(dp(15),dp(16),dp(15),dp(16))
 }
 private fun bottomNavigation(active:String):LinearLayout {
  val nav=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER
   setPadding(dp(8),dp(6),dp(8),dp(6));setBackgroundColor(Color.WHITE)
  }
  listOf(Triple(R.drawable.ic_nav_home,"Home","home"),Triple(R.drawable.ic_nav_clients,"Clienti","clients"),Triple(R.drawable.ic_action_calendar,"Pianifica","day"),Triple(R.drawable.ic_nav_settings,"Opzioni","options")).forEach { (symbol,label,key) ->
   val item=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER
    setPadding(dp(5),dp(8),dp(5),dp(8))
    background=shape(if(active==key)Color.rgb(255,243,204) else Color.WHITE)
    contentDescription=label
   }
   item.addView(android.widget.ImageView(this).apply {setImageResource(symbol);contentDescription=label;scaleType=android.widget.ImageView.ScaleType.FIT_CENTER;setColorFilter(if(active==key)Color.rgb(190,131,0) else ink)},LinearLayout.LayoutParams(dp(25),dp(25)).apply {gravity=android.view.Gravity.CENTER_HORIZONTAL})
   item.addView(homeText(label,13f,active==key).apply {gravity=android.view.Gravity.CENTER})
   item.setOnClickListener { when(key) { "home"->showHome(); "clients"->showList(""); "day"->showDay(); else->showOptions() } }
   nav.addView(item,LinearLayout.LayoutParams(0,dp(66),1f))
  }
  return nav
 }
 private fun showHome() {
  screen("Home")
  root.setPadding(dp(14),dp(12),dp(14),dp(24))
  val header=homeCard(Color.rgb(255,212,63))
  header.addView(homeText("YellowKunde",27f,true,Color.BLACK))
  header.addView(homeText("Gestione clienti",15f,false,Color.rgb(85,70,30)))
  root.addView(header,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(15)})
  root.addView(homeText("Riepilogo attività",18f,true))
  val now=LocalDate.now()
  val tasks=db.tasks().filter { it.type=="visita" || it.type=="contatto" || it.type=="giro" }
  val rows=listOf(listOf("▣","Visite oggi","visita","today"),listOf("▣","Visite future","visita","future"),listOf("🔔","Promemoria oggi","contatto","today"),listOf("🔔","Promemoria futuri","contatto","future"))
  rows.chunked(2).forEach { pair ->
   val tiles=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
   pair.forEachIndexed { i,info ->
    val type=info[2];val period=info[3]
    val matching=tasks.filter { (it.type==type || (type=="visita" && it.type=="giro")) && it.date!=null && (if(period=="today") it.date<=now.toString() else it.date>now.toString()) }.distinctBy { it.code to it.date }
    val overdue=matching.count { it.date!!<now.toString() }
    val card=homeCard(if(type=="visita")Color.rgb(255,246,218) else Color.rgb(225,241,255)).apply {
     gravity=android.view.Gravity.CENTER
     setPadding(dp(8),dp(5),dp(8),dp(5))
     if(type=="visita") {
      addView(android.widget.ImageView(this@MainActivity).apply {
       setImageResource(R.drawable.ic_action_calendar)
       contentDescription="Visite"
       scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
      },LinearLayout.LayoutParams(dp(25),dp(25)).apply {gravity=android.view.Gravity.CENTER_HORIZONTAL})
     } else {
      addView(android.widget.ImageView(this@MainActivity).apply {setImageResource(R.drawable.ic_home_reminder);contentDescription="Promemoria";scaleType=android.widget.ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(25),dp(25)).apply {gravity=android.view.Gravity.CENTER_HORIZONTAL})
     }
     addView(homeText("${matching.size}",27f,true).apply{gravity=android.view.Gravity.CENTER})
     addView(homeText(info[1],14f,true).apply{gravity=android.view.Gravity.CENTER})
     if(overdue>0)addView(homeText("⚠ $overdue scadute",12f,true,Color.rgb(180,40,30)).apply{gravity=android.view.Gravity.CENTER})
     setOnClickListener { showTasks(type,period) }
    }
    tiles.addView(card,LinearLayout.LayoutParams(0,dp(108),1f).apply { marginStart=if(i==0)0 else dp(5);marginEnd=if(i==0)dp(5) else 0 })
   }
   root.addView(tiles,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(9);bottomMargin=dp(5)})
  }
  val day=homeCard(Color.rgb(230,246,237))
  day.addView(homeText("⌁   Pianifica giornata     ›",19f,true,Color.rgb(0,93,67)))
  day.addView(homeText("      Organizza il giro clienti senza orari",13f,false,Color.DKGRAY))
  day.setOnClickListener{showDay()}
  root.addView(day,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(18)})
  root.addView(homeText("Clienti",22f,true))
  val clients=homeCard(Color.WHITE).apply {background=shape(Color.WHITE,Color.rgb(225,225,225))}
  clients.addView(homeText("♟   Elenco clienti                 ›",18f,true))
  clients.addView(homeText("      Cerca, aggiungi e gestisci i tuoi clienti",13f,false,Color.DKGRAY))
  clients.setOnClickListener{showList("")}
  root.addView(clients,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
 }
 private fun screen(title:String):LinearLayout {
  currentPage=when(title) { "YellowKunde" -> "list"; "Home" -> "home"; "Pianifica giornata" -> "day"; "Attività" -> "tasks"; tr("Opzioni") -> "options"; tr("Sicurezza"),tr("Informazioni sull’app") -> "suboptions"; tr("Nuovo cliente"),tr("Modifica cliente") -> "edit"; else -> "detail" }
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(16),dp(14),dp(24));setBackgroundColor(warmBackground)}
  val scroll=ScrollView(this)
  // Respect the real system-bar insets on Pixel and Samsung, including display cutouts.
  scroll.setOnApplyWindowInsetsListener { view, insets ->
   val top:Int; val bottom:Int
   if (android.os.Build.VERSION.SDK_INT >= 30) {
    val bars=insets.getInsets(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.navigationBars())
    top=bars.top;bottom=bars.bottom
   } else {
    top=insets.systemWindowInsetTop;bottom=insets.systemWindowInsetBottom
   }
   view.setPadding(0,top,0,bottom)
   insets
  }
  scroll.clipToPadding=false
  scroll.addView(root)
  val navPage=when(title){"Home"->"home";"YellowKunde"->"clients";"Pianifica giornata"->"day";tr("Opzioni")->"options";else->null}
  if(navPage!=null) {
   val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
   shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
   val bottomNav = bottomNavigation(navPage)
    bottomNav.setOnApplyWindowInsetsListener { view, insets ->
    val bottom = if (android.os.Build.VERSION.SDK_INT >= 30) {
        insets.getInsets(
            android.view.WindowInsets.Type.navigationBars()
        ).bottom
    } else {
        insets.systemWindowInsetBottom
    }
    view.setPadding(dp(8), dp(6), dp(8), dp(6) + bottom)
    insets
}
shell.addView(bottomNav)
   setContentView(shell)
  } else setContentView(scroll)
  if(title!="YellowKunde" && title!="Home") root.addView(homeCard(Color.rgb(255,212,63)).apply { addView(homeText(title,24f,true,Color.BLACK)) })
  return root
 }
 private val selectedZones=mutableSetOf<String>()
 private var selectedInfoField:String?=null
 private var selectedInfoValue:Boolean=true
 private val sortLabels=listOf("Nome cliente","Codice cliente","Città","Zona","Settore","Data ultima visita","Numero visite annuali")
 private val sortKeys=listOf("name","code","city","zone","sector","visit","count")
 private fun showList(q:String=listSearch) {
  listSearch=q
  screen("YellowKunde")
  root.setPadding(dp(14),dp(12),dp(14),dp(24))
  val scroll=root.parent as ScrollView
  lateinit var searchBar:LinearLayout
  lateinit var sortAction:()->Unit
  val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
  header.addView(TextView(this).apply { text="YellowKunde";textSize=23f;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD;layoutParams=LinearLayout.LayoutParams(0,dp(44),1f);gravity=android.view.Gravity.CENTER_VERTICAL })
  header.addView(homeIconAction(R.drawable.ic_home_add,"Nuovo cliente") { saveListPosition(scroll);edit(null) })
  root.addView(header)
  heading("Elenco clienti")
  searchBar=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val searchRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
  val search=EditText(this).apply{hint=tr("Cerca codice, nome, città, settore, zona");setSingleLine(true);setText(q);textSize=14f;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_home_search,0,0,0);compoundDrawablePadding=dp(7)}
  searchRow.addView(search,LinearLayout.LayoutParams(0,dp(48),1f))
  val zoneOptions=db.zones().filter { it.isNotBlank() }
  val noZoneFilter="__YELLOWKUNDE_NO_ZONE__"
  val zoneChoices=zoneOptions+noZoneFilter
  val zoneLabels=zoneOptions+listOf("Senza zona")
  val zoneAction=homeIconAction(R.drawable.ic_home_zone,"Zona") {}
  fun updateZoneLabel() { zoneAction.contentDescription=tr("Zona")+": "+(if(selectedZones.isEmpty()) tr("Tutte le zone") else selectedZones.joinToString(", ") { if(it==noZoneFilter) "Senza zona" else it }) }
  updateZoneLabel()
  searchBar.addView(searchRow)
  val filterButtons=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val infoActions=(1..5).filter { customInfo("info$it")!=null }.map { number ->
   val field="info$number"
   Pair(infoLabel(field),{
    AlertDialog.Builder(this).setTitle(infoLabel(field))
     .setItems(arrayOf("SÌ","NO")){_,which->
      selectedInfoField=field;selectedInfoValue=which==0;listScrollY=0;showList()
     }
     .setNeutralButton("Rimuovi filtro"){_,_->selectedInfoField=null;listScrollY=0;showList()}
     .setNegativeButton("Annulla",null).show()
   })
  }
  val infoContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val toggleFilters:()->Unit={
   AlertDialog.Builder(this).setTitle("Filtri clienti")
    .setItems(arrayOf("Filtra per zona","Filtra per Info personalizzate","Azzera tutti i filtri")){_,which->
     when(which) {
      0 -> zoneAction.performClick()
      1 -> {listInfoVisible=true;infoContainer.visibility=android.view.View.VISIBLE}
      2 -> {listSearch="";selectedZones.clear();selectedInfoField=null;listScrollY=0;showList("")}
     }
    }.setNegativeButton("Annulla",null).show()
  }
  filterButtons.removeAllViews()
  val firstRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  fun addSquare(row:LinearLayout,label:String,action:()->Unit) {
   row.addView(secondaryButton(label,action).apply {
    textSize=23f;minWidth=0;minimumWidth=0;isAllCaps=false
    contentDescription=when(label) {"☷"->"Filtri";"↕"->"Ordina";"◎"->"Zona";"ⓘ"->"Info";else->label}
    background=shape(Color.rgb(255,243,204))
    setPadding(dp(2),0,dp(2),0)
   },LinearLayout.LayoutParams(0,dp(46),1f).apply {marginEnd=dp(4);topMargin=dp(5)})
  }
  fun addListIcon(icon:Int,label:String,action:()->Unit) {
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL
    gravity=android.view.Gravity.CENTER
    background=shape(Color.rgb(255,243,204))
    contentDescription=label
    isClickable=true
    isFocusable=true
    setOnClickListener { action() }
   }
   tile.addView(android.widget.ImageView(this).apply {
    setImageResource(icon)
    contentDescription=label
    scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
   },LinearLayout.LayoutParams(dp(25),dp(25)))
   tile.addView(homeText(label,12f,true).apply {gravity=android.view.Gravity.CENTER})
   firstRow.addView(tile,LinearLayout.LayoutParams(0,dp(54),1f).apply {marginEnd=dp(4);topMargin=dp(5)})
  }
  addListIcon(R.drawable.ic_list_filter,"Filtri",toggleFilters)
  addListIcon(R.drawable.ic_list_sort,"Ordina",{sortAction()})
  addListIcon(R.drawable.ic_list_zone,"Zona",{zoneAction.performClick()})
  addListIcon(R.drawable.ic_list_info,"Info",{listInfoVisible=!listInfoVisible;infoContainer.visibility=if(listInfoVisible) android.view.View.VISIBLE else android.view.View.GONE})
  filterButtons.addView(firstRow)
  infoActions.chunked(4).forEach { chunk ->
   val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
   chunk.forEach { (label,action) -> addSquare(row,label) { action() } }
   infoContainer.addView(row)
  }
  infoContainer.visibility=if(listInfoVisible) android.view.View.VISIBLE else android.view.View.GONE
  filterButtons.addView(infoContainer)
  searchBar.addView(filterButtons)
  root.addView(searchBar)
  val resetAction=compactTextAction("×","Azzera filtri") {
   listSearch="";listZone="";selectedZones.clear();selectedInfoField=null;listScrollY=0
   prefs.edit().putString("sort_key","name").putBoolean("sort_desc",false).apply();showList("")
  }
  // Reset remains accessible through the zone selector without taking a full row.
  zoneAction.setOnClickListener {
   val checked=zoneChoices.map { it in selectedZones }.toBooleanArray()
   AlertDialog.Builder(this).setTitle(tr("Zona"))
    .setMultiChoiceItems(zoneLabels.toTypedArray(),checked){_,which,value->checked[which]=value}
    .setNeutralButton(tr("Tutte le zone")){_,_->selectedZones.clear();listScrollY=0;showList()}
    .setNegativeButton(tr("Annulla"),null)
    .setPositiveButton(tr("Applica")){_,_->selectedZones.clear();zoneChoices.forEachIndexed{ i,z->if(checked[i]) selectedZones.add(z) };listScrollY=0;showList()}.show()
  }
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  fun update(){
   list.removeAllViews()
   val sort=prefs.getString("sort_key","name") ?: "name"
   val descending=prefs.getBoolean("sort_desc",false)
   val pending=db.tasks().filter { it.type=="visita" || it.type=="contatto" }.groupBy { it.code }
   val today=LocalDate.now().toString()
   db.sortedList(listSearch,sort,descending).forEach{(code,name)->
    val customer=db.get(code)
    val customerZone=customer?.get("zona")?.trim().orEmpty()
    val zoneMatches=selectedZones.isEmpty() || customerZone in selectedZones || (customerZone.isEmpty() && noZoneFilter in selectedZones)
    val infoMatches=selectedInfoField?.let { field ->
     val value=customer?.get(field)?.trim().orEmpty()
     val truthy=value.equals("true",true) || value=="1"
     if(selectedInfoValue) truthy else !truthy
    } ?: true
    if(zoneMatches && infoMatches) {
     val flags=pending[code].orEmpty().map { task ->
      val label=if(task.type=="visita") "Visita" else "Chiamata"
      when {task.date==null -> "$label da programmare";task.date<today -> "⚠ $label scaduta (${formatDate(task.date)})";task.date==today -> "$label oggi";else -> "$label ${formatDate(task.date)}"}
     }
     val text=if(flags.isEmpty()) "$name  ·  $code" else "$name  ·  $code\n"+flags.joinToString("   •   ")
     val item=secondaryButton(text){saveListPosition(scroll);detail(code)}
     if(flags.any { it.contains("scaduta") })item.setTextColor(Color.rgb(180,40,30))
     else if(flags.isNotEmpty())item.setTextColor(Color.rgb(25,99,160))
     list.addView(item)
    }
   }
  }
  sortAction={
   val current=prefs.getString("sort_key","name") ?: "name"
   val selected=sortKeys.indexOf(current).coerceAtLeast(0)
   val choices=sortLabels.map{tr(it)}.toTypedArray()
   AlertDialog.Builder(this).setTitle(tr("Ordina per"))
    .setSingleChoiceItems(choices,selected){dialog,which->
     dialog.dismiss()
     val directions=arrayOf(tr("Crescente"),tr("Decrescente"))
     AlertDialog.Builder(this).setTitle(tr("Direzione"))
      .setSingleChoiceItems(directions,if(prefs.getBoolean("sort_desc",false))1 else 0){d,choice->
       prefs.edit().putString("sort_key",sortKeys[which]).putBoolean("sort_desc",choice==1).apply()
       d.dismiss();saveListPosition(scroll);showList()
      }.setNegativeButton(tr("Annulla"),null).show()
    }.setNegativeButton(tr("Annulla"),null).show()
  }


  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){listSearch=s.toString();listScrollY=0;update()}
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  update()
  scroll.post { scroll.scrollTo(0,listScrollY) }
 }
 private fun saveListPosition(scroll:ScrollView) { listScrollY=scroll.scrollY }

 private fun heading(label:String) {
  root.addView(TextView(this).apply { text=label; textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ink);setPadding(0,dp(16),0,dp(10)) })
 }
 private fun editTask(code:String,type:String) {
  val current=db.tasks(type).firstOrNull{it.code==code}
  val note=EditText(this).apply{hint="Note";setText(current?.notes.orEmpty());minLines=3}
  var date=current?.date
  val wrapper=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),0)}
  val dateButton=Button(this).apply{text="Data: "+(date?.let{formatDate(it)}?:"da definire")}
  dateButton.setOnClickListener {pickDate(date?.let{LocalDate.parse(it)}?:LocalDate.now()){chosen->date=chosen.toString();dateButton.text="Data: "+formatDate(date!!)}}
  wrapper.addView(dateButton)
  wrapper.addView(secondaryButton("Rimuovi data"){date=null;dateButton.text="Data: da definire"})
  wrapper.addView(note)
  AlertDialog.Builder(this).setTitle(if(type=="visita")"Pianifica visita" else "Da contattare").setView(wrapper)
   .setNegativeButton("Annulla",null)
   .setNeutralButton("Calendario"){_,_->saveTaskAndCalendar(code,type,date,note.text.toString())}
   .setPositiveButton("Salva"){_,_->try { db.saveTask(code,type,date,note.text.toString());detail(code) } catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show();detail(code)} }.show()
 }
 private fun saveTaskAndCalendar(code:String,type:String,date:String?,note:String){
  try { db.saveTask(code,type,date,note) }
  catch(e:Exception) { Toast.makeText(this,e.message ?: "Attività già presente per questa data",Toast.LENGTH_LONG).show();return }
  val customer=db.get(code)
  if(date==null){Toast.makeText(this,"Seleziona una data per il calendario",Toast.LENGTH_LONG).show();detail(code);return}
  val day=LocalDate.parse(date)
  val start=day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  val intent=Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
   .putExtra(CalendarContract.Events.TITLE,(if(type=="visita")"Visita" else "Contattare")+" - "+customer?.get("nome"))
   .putExtra(CalendarContract.Events.DESCRIPTION,note)
   .putExtra(CalendarContract.Events.ALL_DAY,true)
   .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start)
   .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
  openExternal(intent);detail(code)
 }
 private fun confirmCompletion(task:Database.Task,after:()->Unit) {
  val isVisit=task.type=="visita" || task.type=="giro"
  AlertDialog.Builder(this)
   .setTitle("Conferma completamento")
   .setMessage(if(isVisit)"Confermi che la visita a ${task.name} è stata effettuata? Verrà registrata nello storico visite." else "Confermi che il contatto con ${task.name} è stato effettuato?")
   .setNegativeButton("Annulla",null)
   .setPositiveButton("Conferma"){_,_->db.completeTask(task.id);after()}
   .show()
 }
 private fun showTasks(type:String,period:String?=null){
  screen("Attività")
  root.addView(secondaryButton("← Home"){showHome()})
  heading(if(type=="visita")"Visite" else "Da contattare")
  val today=LocalDate.now().toString()
  val activityList=if(type=="visita") db.tasks().filter { it.type=="visita" || it.type=="giro" } else db.tasks(type)
  activityList.filter {task-> when(period){"today"->task.date!=null && task.date<=today;"future"->task.date!=null && task.date>today;else->true} }.distinctBy { it.code to it.date }.forEach {task->
   val overdue=task.date!=null && task.date<today
   val prefix=if(overdue)"⚠ SCADUTA · " else ""
   root.addView(secondaryButton(prefix+task.name+" · "+(task.date?.let{formatDate(it)}?:"Senza data")+"\n"+task.notes){detail(task.code)})
   val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
   fun action(label:String,primary:Boolean,run:()->Unit) {
    val b=if(primary) primaryButton(label,run) else secondaryButton(label,run)
    actions.addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(5) })
   }
   action(if(type=="visita")"✓ Conferma visita" else "✓ Completato",true) {
    if(type=="visita") AlertDialog.Builder(this).setTitle("Conferma visita")
     .setMessage("Confermi che la visita a ${task.name} è stata effettuata?")
     .setNegativeButton("Torna indietro",null)
     .setPositiveButton("Conferma"){_,_->db.completeDayVisit(task.id,task.date ?: today);showTasks(type,period)}.show()
    else confirmCompletion(task){showTasks(type,period)}
   }
   if(type=="visita") action("× Annulla visita",false) {
    AlertDialog.Builder(this).setTitle("Annullare questa visita?")
     .setMessage("La visita programmata verrà eliminata definitivamente.")
     .setNegativeButton("Torna indietro",null)
     .setPositiveButton("Elimina visita"){_,_->db.cancelDayVisit(task.id,task.date ?: today);showTasks(type,period)}.show()
   }
   root.addView(actions)
  }
 }
 private fun showDay(day:LocalDate=selectedDay){
  selectedDay=day;screen("Pianifica giornata")
  root.addView(button("← Home"){showHome()})
  root.addView(secondaryButton("Data: "+formatDate(day.toString())){pickDate(day){showDay(it)}})
  root.addView(primaryButton("+ Aggiungi cliente"){
   val allClients=db.allCustomers()
   val zones=db.zones().filter { it.isNotBlank() }
   val zoneLabels=(zones+listOf("Senza zona")).toTypedArray()
   AlertDialog.Builder(this).setTitle("Seleziona zona")
    .setItems(zoneLabels){_,zoneIndex->
     val zone=if(zoneIndex==zones.size) "" else zones[zoneIndex]
     val clients=allClients.filter { it["zona"].orEmpty().trim()==zone }
     if(clients.isEmpty()) {
      Toast.makeText(this,"Nessun cliente in questa zona",Toast.LENGTH_SHORT).show()
     } else {
      val selected=BooleanArray(clients.size)
      val names=clients.map { it["nome"].orEmpty()+" · "+it["codice"].orEmpty() }.toTypedArray()
      AlertDialog.Builder(this).setTitle("Clienti · "+zoneLabels[zoneIndex])
       .setMultiChoiceItems(names,selected){_,index,checked->selected[index]=checked}
       .setNegativeButton("Annulla",null)
       .setPositiveButton("Aggiungi selezionati"){_,_->
        var added=0
        val skipped=mutableListOf<String>()
        clients.forEachIndexed { index,customer ->
         if(selected[index]) {
          try {
           db.addDayClient(customer["codice"].orEmpty(),day.toString())
           added++
          } catch(e:Exception) {
           skipped.add(customer["nome"].orEmpty()+": "+(e.message ?: "già presente"))
          }
         }
        }
        showDay(day)
        if(skipped.isNotEmpty()) AlertDialog.Builder(this)
         .setTitle("Aggiunti: "+added+" · Non aggiunti: "+skipped.size)
         .setMessage(skipped.joinToString("\n"))
         .setPositiveButton("OK",null).show()
        else if(added>0) Toast.makeText(this,"Aggiunti "+added+" clienti",Toast.LENGTH_SHORT).show()
       }.show()
     }
    }.setNegativeButton("Annulla",null).show()
  })
  val manual=db.tasks("giro",day.toString())
  val scheduled=db.tasks("visita").filter { it.date!=null && it.date<=day.toString() }
  // A customer present in both lists must only appear once.
  val scheduledCodes=scheduled.map { it.code }.toSet()
  val items=(scheduled+manual.filter { it.code !in scheduledCodes }).filter { !db.hasVisit(it.code,day.toString()) }.sortedWith(compareBy<Database.Task> { if(it.position>0)it.position else Int.MAX_VALUE }.thenBy { it.date })
  heading("Visite da effettuare · "+items.size)
  if(items.isEmpty())root.addView(homeCard(paleYellow).apply { addView(homeText("Nessuna visita da effettuare per questa data")) })
  items.forEachIndexed{index,task->
   val overdue=task.date!=null && task.date<day.toString() && task.type=="visita"
   val label=if(task.type=="visita") (if(overdue)"⚠ Visita scaduta · " else "Visita programmata · ") +formatDate(task.date.orEmpty()) else "Aggiunta al giro"
   root.addView(secondaryButton("${index+1}. ${task.name}\n${label}\n${task.notes}"){detail(task.code)})
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   fun small(symbol:String,description:String,bg:Int,fg:Int,enabled:Boolean=true,action:()->Unit) {
    row.addView(TextView(this).apply {
     text=symbol;textSize=23f;gravity=android.view.Gravity.CENTER
     setTextColor(fg);contentDescription=description;background=shape(bg)
     alpha=if(enabled)1f else .35f;isEnabled=enabled;setOnClickListener { action() }
    },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(4) })
   }
   small("✓","Conferma visita",Color.rgb(229,244,233),Color.rgb(38,115,69)) {
    AlertDialog.Builder(this).setTitle("Conferma visita").setMessage("Confermi che la visita a ${task.name} è stata effettuata?")
     .setNegativeButton("Indietro",null).setPositiveButton("Conferma"){_,_->db.completeDayVisit(task.id,day.toString());showDay(day)}.show()
   }
   small("×","Annulla visita",Color.rgb(251,232,232),Color.rgb(180,60,60)) {
    AlertDialog.Builder(this).setTitle("Annulla visita").setMessage("La visita programmata verrà eliminata definitivamente, senza registrarla nello storico.")
     .setNegativeButton("Indietro",null).setPositiveButton("Elimina visita"){_,_->db.cancelDayVisit(task.id,day.toString());showDay(day)}.show()
   }
   small("↑","Sposta su",Color.rgb(255,241,184),Color.rgb(115,88,0),index>0) {
    val order=items.map { it.code }.toMutableList();java.util.Collections.swap(order,index,index-1);db.reorderDayClients(day.toString(),order);showDay(day)
   }
   small("↓","Sposta giù",Color.rgb(255,241,184),Color.rgb(115,88,0),index<items.lastIndex) {
    val order=items.map { it.code }.toMutableList();java.util.Collections.swap(order,index,index+1);db.reorderDayClients(day.toString(),order);showDay(day)
   }
   small("✎","Note",Color.rgb(233,239,251),Color.rgb(49,92,154)) {
    val input=EditText(this).apply{setText(task.notes)}
    AlertDialog.Builder(this).setTitle("Note visita").setView(input).setNegativeButton("Annulla",null)
     .setPositiveButton("Salva"){_,_->db.changeTaskNote(task.id,input.text.toString());showDay(day)}.show()
   }
   root.addView(row)
  }
  root.addView(secondaryButton("Aggiungi giro al calendario"){
   val start=day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
   val description=items.mapIndexed{index,task->"${index+1}. ${task.name}: ${task.notes}"}.joinToString("\n")
   openExternal(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
    .putExtra(CalendarContract.Events.TITLE,"Giro clienti YellowKunde")
    .putExtra(CalendarContract.Events.DESCRIPTION,description)
    .putExtra(CalendarContract.Events.ALL_DAY,true)
    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start)
    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
  })
 }

 private fun showOptions() {
  screen(tr("Opzioni"))
  root.addView(secondaryButton("← " + tr("Indietro")){showHome()})
  heading(tr("Importazione ed esportazione Excel"))
  root.addView(secondaryButton(tr("Importa Excel .xlsx")) {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";addCategory(Intent.CATEGORY_OPENABLE) },importRequest)
  })
  root.addView(secondaryButton(tr("Esporta Excel .xlsx")) {
   AlertDialog.Builder(this).setTitle(tr("Attenzione ai dati personali"))
    .setMessage(tr("Il file Excel non è cifrato e contiene dati dei clienti. Conservalo in una posizione protetta e non condividerlo senza autorizzazione."))
    .setNegativeButton(tr("Annulla"), null)
    .setPositiveButton(tr("Continua")) { _, _ ->
     startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_TITLE,"clienti.xlsx");addCategory(Intent.CATEGORY_OPENABLE) },exportRequest)
    }.show()
  })
  heading(tr("Backup e ripristino"))
  root.addView(secondaryButton(tr("Crea backup cifrato")) {
   askBackupPassword(tr("Password per il backup (minimo 12 caratteri)")) { password ->
    backupPassword=password
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"clienti.gcbk");addCategory(Intent.CATEGORY_OPENABLE) },backupRequest)
   }
  })
  root.addView(secondaryButton(tr("Ripristina backup cifrato")) {
   AlertDialog.Builder(this).setTitle(tr("Ripristino completo"))
    .setMessage(tr("Il ripristino SOSTITUIRÀ tutti i clienti e tutte le visite presenti. Assicurati di avere una copia di sicurezza."))
    .setNegativeButton(tr("Annulla"),null).setPositiveButton(tr("Continua")) { _,_ ->
     askBackupPassword(tr("Password del backup")) { password ->
      backupPassword=password
      startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE) },restoreRequest)
     }
    }.show()
  })

  heading("Personalizza campi Info")
  root.addView(secondaryButton("Modifica etichette Info1–Info5"){showInfoSettings()})
  heading(tr("Sicurezza"))
  root.addView(secondaryButton(tr("Sicurezza")){showSecurity()})
  heading(tr("Lingua"))
  root.addView(secondaryButton(tr("Lingua") + " / Sprache"){chooseLanguage()})
  heading(tr("Informazioni sull’app"))
  root.addView(secondaryButton(tr("Informazioni sull’app")){showAbout()})
 }
 private fun showInfoSettings() {
  screen("Personalizza campi Info")
  root.addView(button("← " + tr("Indietro")){showOptions()})
  infoKeys.forEach { key ->
   root.addView(button("${key.uppercase()}: ${infoLabel(key)}") {
    val input=EditText(this).apply { setSingleLine(true);setText(infoLabel(key)) }
    AlertDialog.Builder(this).setTitle("Nuova etichetta per ${key.uppercase()}").setView(input)
     .setNegativeButton(tr("Annulla"),null)
     .setPositiveButton(tr("Salva")){_,_->
      val label=input.text.toString().trim()
      if(label.isBlank()) { Toast.makeText(this,"L'etichetta non può essere vuota",Toast.LENGTH_LONG).show();return@setPositiveButton }
      if(label==infoLabel(key)) {showInfoSettings();return@setPositiveButton}
      AlertDialog.Builder(this).setTitle("Etichetta modificata")
       .setMessage("Vuoi impostare il campo '$label' su Falso per tutti i clienti?")
       .setNegativeButton("Mantieni valori"){_,_->prefs.edit().putString("label_$key",label).apply();showInfoSettings()}
       .setPositiveButton("Imposta tutti su Falso"){_,_->
        AlertDialog.Builder(this).setTitle("Confermi l'azzeramento?")
         .setMessage("Il campo '$label' sarà Falso per tutti i clienti. L'operazione non è annullabile automaticamente.")
         .setNegativeButton(tr("Annulla"),null)
         .setPositiveButton("Conferma"){_,_->db.setInfoFalseForAll(key);prefs.edit().putString("label_$key",label).apply();showInfoSettings()}.show()
       }.show()
     }.show()
   })
  }
  root.addView(secondaryButton("Ripristina nomi predefiniti") {
   AlertDialog.Builder(this).setMessage("Ripristinare i nomi Info1–Info5 senza modificare i valori dei clienti?")
    .setNegativeButton(tr("Annulla"),null).setPositiveButton("Ripristina"){_,_->val editor=prefs.edit();infoKeys.forEach{editor.remove("label_$it")};editor.apply();showInfoSettings()}.show()
  })
 }
 private fun showSecurity() {
  screen(tr("Sicurezza"))
  root.addView(button("← " + tr("Indietro")){showOptions()})
  root.addView(TextView(this).apply {
   text=tr("Accesso protetto con impronta digitale o PIN del dispositivo.") + "\n\n" + tr("La protezione è obbligatoria e non può essere disattivata da questa schermata.")
   textSize=17f
  })
 }
 private fun chooseLanguage() {
  val choices=arrayOf("Italiano", "Deutsch")
  AlertDialog.Builder(this).setTitle(tr("Scegli la lingua dell’app"))
   .setSingleChoiceItems(choices,if(isGerman()) 1 else 0){ dialog,which ->
    prefs.edit().putString("language",if(which==1) "de" else "it").apply()
    dialog.dismiss()
    showOptions()
    Toast.makeText(this,tr("Lingua aggiornata"),Toast.LENGTH_SHORT).show()
   }.setNegativeButton(tr("Annulla"),null).show()
 }
 private fun showAbout() {
  screen(tr("Informazioni sull’app"))
  root.addView(button("← " + tr("Indietro")){showOptions()})
  val packageInfo=packageManager.getPackageInfo(packageName,0)
  root.addView(TextView(this).apply {
   text="YellowKunde\n${tr("Versione")}: ${packageInfo.versionName}\nSviluppato da: Ivan V."
   textSize=18f
  })
 }
 private fun edit(code:String?) {
  val existing=code?.let{db.get(it)} ?: emptyMap()
  screen(if(code==null)tr("Nuovo cliente") else tr("Modifica cliente"))
  editingCustomerCode=code;originalValues=existing
  val inputs=mutableMapOf<String,EditText>()
  val checks=mutableMapOf<String,android.widget.CheckBox>()
  fields.forEachIndexed{index,key->
   if(key=="info1") root.addView(TextView(this).apply{text="Informazioni aggiuntive";textSize=19f;typeface=Typeface.DEFAULT_BOLD})
   if(key in infoKeys) { val check=android.widget.CheckBox(this).apply { text=infoLabel(key);isChecked=existing[key].equals("true",true) || existing[key]=="1" };checks[key]=check;root.addView(check);return@forEachIndexed }
   root.addView(TextView(this).apply{text=tr(captions[index])})
   val field=EditText(this).apply{setText(existing[key] ?: "");setSingleLine(key!="note");if(key=="note")minLines=3; if(key.startsWith("telefono"))inputType=InputType.TYPE_CLASS_PHONE; if(key.startsWith("email"))inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS}
   inputs[key]=field;root.addView(field)
  }
  editingFields=inputs
  root.addView(button(tr("Salva")){
   try {db.save(code,inputs.mapValues{it.value.text.toString()} + checks.mapValues { if(it.value.isChecked) "true" else "false" });detail(inputs.getValue("codice").text.toString().trim())}
   catch(e:Exception){Toast.makeText(this,e.message ?: tr("Errore"),Toast.LENGTH_LONG).show()}
  })
  root.addView(button(tr("Annulla")){if(code==null)showList() else detail(code)})
 }
 private fun detail(code:String) {
  val customer=db.get(code) ?: run{showList();return}
  screen(customer["nome"].orEmpty())
  val detailToolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
  detailToolbar.addView(button(tr("← Elenco clienti")){showList()}.apply { minHeight=dp(40);minimumHeight=dp(40);setPadding(dp(8),0,dp(8),0);textSize=14f },LinearLayout.LayoutParams(0,dp(40),1f).apply {marginEnd=dp(6)})
  fun detailAction(icon:Int,description:String,action:()->Unit) {
   detailToolbar.addView(homeIconAction(icon,description,action),LinearLayout.LayoutParams(dp(40),dp(40)).apply {marginStart=dp(5)})
  }
  detailAction(android.R.drawable.ic_menu_edit,"Modifica cliente"){edit(code)}
  detailAction(android.R.drawable.ic_menu_delete,"Elimina cliente"){
   AlertDialog.Builder(this).setTitle(tr("Elimina cliente"))
    .setMessage(tr("Eliminare definitivamente questo cliente e tutte le visite?"))
    .setNegativeButton(tr("Annulla"),null)
    .setPositiveButton(tr("Elimina")){_,_->db.delete(code);showList()}.show()
  }
  root.addView(detailToolbar)
  fun section(title:String,keys:List<String>) {
   val visible=keys.filter { customer[it].orEmpty().isNotBlank() }
   if(visible.isEmpty())return
   root.addView(homeCard(paleYellow).apply { addView(homeText(title,18f,true)) })
   visible.forEach { key ->
    val value=customer[key].orEmpty().trim()
    val index=fields.indexOf(key)
    if(key.startsWith("telefono")) contactRow(captions[index],value,"☎") {
     openExternal(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(value))))
    } else if(key.startsWith("email")) contactRow(captions[index],value,"✉") {
     openExternal(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(value))))
    } else root.addView(TextView(this).apply {
     val label=tr(captions[index])+": "
     text=android.text.SpannableString(label+value).apply {
      setSpan(android.text.style.StyleSpan(Typeface.BOLD),label.length,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
     }
     textSize=16f;setTextColor(ink);setPadding(0,dp(5),0,dp(5))
    })
   }
  }
  section("▣  Dati cliente",listOf("codice","nome","settore"))
  section("⌖  Indirizzo e zona",listOf("indirizzo","cap","citta","provincia","zona"))
  section("☎  Contatti",listOf("contatto_principale","telefono_principale","email_principale","contatto_secondario","telefono_secondario","email_secondaria"))
  section("✎  Note",listOf("note"))
  val customFields=infoKeys.filter { customInfo(it)!=null && customer[it].orEmpty().isNotBlank() }
  if(customFields.isNotEmpty()) {
   root.addView(homeCard(paleYellow).apply { addView(homeText("ⓘ  Info personalizzate",18f,true)) })
   customFields.forEach { key ->
    root.addView(android.widget.CheckBox(this).apply {
     text=infoLabel(key);textSize=16f
     isChecked=customer[key].equals("true",true) || customer[key]=="1"
     setOnCheckedChangeListener { check,checked ->
      check.isEnabled=false
      try { db.setCustomerInfo(code,key,checked);check.isEnabled=true }
      catch(e:Exception) { Toast.makeText(this@MainActivity,e.message,Toast.LENGTH_LONG).show();detail(code) }
     }
    })
   }
  }
  val destination=listOf("indirizzo","cap","citta","provincia").map { customer[it].orEmpty().trim() }.filter { it.isNotBlank() }.joinToString(", ")
  val actionRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(8),0,dp(8)) }
  if(destination.isNotBlank()) actionRow.addView(iconButton("➤","Vai a") {
   val maps=Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(destination))).setPackage("com.google.android.apps.maps")
   if(maps.resolveActivity(packageManager)!=null) openExternal(maps)
   else openExternal(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(destination))))
  })
  root.addView(actionRow)
  actionRow.addView(iconButton("↗","Condividi cliente") {
   val parts=mutableListOf(customer["nome"].orEmpty(), destination)
   listOf("contatto_principale","telefono_principale","email_principale","contatto_secondario","telefono_secondario","email_secondaria").forEach { key ->
    customer[key]?.takeIf { it.isNotBlank() }?.let { parts.add(tr(captions[fields.indexOf(key)])+": "+it) }
   }
   if(destination.isNotBlank()) parts.add("Google Maps: https://www.google.com/maps/search/?api=1&query="+Uri.encode(destination))
   val share=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,parts.filter{it.isNotBlank()}.joinToString("\n")) }
   startActivity(Intent.createChooser(share,tr("Condividi cliente")))
  })
  actionRow.addView(iconButton("▦","Appuntamento") {
   val subject="Appuntamento – "+customer["nome"].orEmpty()
   val description="Cliente: "+customer["nome"].orEmpty()+"\nTelefono: "+customer["telefono_principale"].orEmpty()
   val intent=Intent(Intent.ACTION_INSERT).apply {
    data=CalendarContract.Events.CONTENT_URI
    putExtra(CalendarContract.Events.TITLE,subject)
    putExtra(CalendarContract.Events.EVENT_LOCATION,destination)
    putExtra(CalendarContract.Events.DESCRIPTION,description)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,System.currentTimeMillis())
    putExtra(CalendarContract.EXTRA_EVENT_END_TIME,System.currentTimeMillis()+60*60*1000)
   }
   openExternal(intent)
  })
  val (count,last)=db.stats(code)
  val visits=db.visitEntries(code)
  val visitHistory=visits.joinToString("\n") { "• ${it.second.format(displayDate)}" }
  root.addView(TextView(this).apply {
   text=if(isGerman()) "Besuche ${LocalDate.now().year}: $count\nLetzter Besuch: ${formatDate(last)}\n\nVerlauf:\n$visitHistory" else "Visite ${LocalDate.now().year}: $count\nUltima visita: ${formatDate(last)}\n\nStorico:\n$visitHistory"
   textSize=17f;setPadding(0,15,0,15)
  })
  val completed=db.tasks(openOnly=false).filter { it.code==code && it.done && it.notes.isNotBlank() }
  if(completed.isNotEmpty())root.addView(TextView(this).apply {
   text="Note attività completate:\n"+completed.joinToString("\n") { "• "+(if(it.type=="contatto")"Telefonata" else "Visita")+": "+it.notes }
   textSize=15f;setPadding(0,dp(8),0,dp(12))
  })
  root.addView(button(tr("Registra visita (oggi)")){try { db.visit(code);detail(code) } catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()} })
  root.addView(button(tr("Registra visita dal calendario")) {
   pickDate { date ->
    try { db.visit(code,date);detail(code) }
    catch(e:Exception) { Toast.makeText(this,e.message ?: tr("Errore"),Toast.LENGTH_LONG).show() }
   }
  })
  root.addView(button(tr("Modifica data visita")) {
   val entries=db.visitEntries(code)
   if(entries.isEmpty()) Toast.makeText(this,tr("Nessuna visita registrata"),Toast.LENGTH_SHORT).show()
   else AlertDialog.Builder(this).setTitle(tr("Seleziona visita"))
    .setItems(entries.map { it.second.format(displayDate) }.toTypedArray()) { _,index ->
     val (id,date)=entries[index]
     pickDate(date) { newDate ->
      try { db.changeVisitDate(code,id,newDate);detail(code) }
      catch(e:Exception) { Toast.makeText(this,e.message ?: tr("Errore"),Toast.LENGTH_LONG).show() }
     }
    }.setNegativeButton(tr("Annulla"),null).show()
  })
  root.addView(button(tr("Annulla ultima visita")) {AlertDialog.Builder(this).setMessage(tr("Eliminare l'ultima visita registrata?")).setPositiveButton(tr("Sì")){_,_->db.undoLastVisit(code);detail(code)}.setNegativeButton(tr("No"),null).show()})
  heading("Promemoria cliente")
  root.addView(secondaryButton("Pianifica visita (con note)"){editTask(code,"visita")})
  root.addView(secondaryButton("Da contattare (con note)"){editTask(code,"contatto")})

 }
 @Deprecated("Android back navigation")
 override fun onBackPressed() { handleAppBack() }
 private fun handleAppBack() {
  when(currentPage) {
   "options" -> showHome()
   "list" -> showHome()
   "detail" -> showList()
    "tasks", "day" -> showHome()
   "suboptions" -> showOptions()
   "edit" -> {
    val changed=editingFields.any { (key,field) -> field.text.toString() != (originalValues[key] ?: "") }
    val goBack:()->Unit={ if(editingCustomerCode==null) showList() else detail(editingCustomerCode!!) }
    if(changed) AlertDialog.Builder(this).setTitle(tr("Modifiche non salvate"))
     .setMessage(tr("Vuoi uscire senza salvare le modifiche?"))
     .setNegativeButton(tr("Annulla"),null)
     .setPositiveButton(tr("Esci senza salvare")){_,_->goBack()}.show()
    else goBack()
   }
   "home" -> Toast.makeText(this,"Sei nella Home",Toast.LENGTH_SHORT).show()
    else -> showHome()
  }
 }
 private fun openExternal(intent:Intent) {
  try { startActivity(intent) }
  catch(e:Exception) { Toast.makeText(this,tr("Nessuna app compatibile"),Toast.LENGTH_LONG).show() }
 }
 private fun askBackupPassword(title:String, action:(CharArray)->Unit) {
  val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
  AlertDialog.Builder(this).setTitle(title).setView(input)
   .setNegativeButton(tr("Annulla"),null).setPositiveButton(tr("Continua")) { _,_ ->
    val password=input.text.toString().toCharArray()
    if(password.isEmpty()) Toast.makeText(this,tr("Inserisci una password"),Toast.LENGTH_LONG).show()
    else action(password)
   }.show()
 }
 @Deprecated("Legacy activity result for compatibility")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK) { backupPassword?.fill('\u0000');backupPassword=null;return }
  val uri:Uri=data?.data ?: return
  try {
   when(requestCode) {
    importRequest -> {
     val preview=Excel.read(this,uri,db)
     AlertDialog.Builder(this).setTitle(tr("Anteprima importazione"))
      .setMessage(if(isGerman()) "Neue Kunden: ${preview.newCount}\nZu aktualisieren: ${preview.updateCount}\nGesamt: ${preview.rows.size}\n\nBereits erfasste Besuche bleiben erhalten." else "Nuovi clienti: ${preview.newCount}\nDa aggiornare: ${preview.updateCount}\nTotale: ${preview.rows.size}\n\nLe visite già registrate saranno conservate.")
      .setNegativeButton(tr("Annulla"),null)
      .setPositiveButton(tr("Conferma")) { _,_ ->
       try { db.importCustomers(preview.rows);showList();Toast.makeText(this,tr("Importazione completata"),Toast.LENGTH_LONG).show() }
       catch(e:Exception) { Toast.makeText(this,e.message ?: tr("Errore importazione"),Toast.LENGTH_LONG).show() }
      }.show()
    }
    exportRequest -> { Excel.write(this,uri,db);Toast.makeText(this,tr("Esportazione completata"),Toast.LENGTH_LONG).show() }
    backupRequest -> { SecureBackup.save(this,uri,db,backupPassword ?: error("Password mancante"));Toast.makeText(this,tr("Backup cifrato creato"),Toast.LENGTH_LONG).show() }
    restoreRequest -> { val (customers,visits)=SecureBackup.restore(this,uri,db,backupPassword ?: error("Password mancante"));showList();Toast.makeText(this,if(isGerman()) "$customers Kunden und $visits Besuche wiederhergestellt" else "Ripristinati $customers clienti e $visits visite",Toast.LENGTH_LONG).show() }
   }
  } catch(e:Exception) { AlertDialog.Builder(this).setTitle(tr("Operazione non riuscita")).setMessage(e.message ?: tr("Password errata o file non valido")).setPositiveButton("OK",null).show() }
  finally { backupPassword?.fill('\u0000');backupPassword=null }
 }

}
