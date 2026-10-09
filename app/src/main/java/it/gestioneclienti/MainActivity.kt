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
 private var listSearch=""
 private var listZone=""
 private var listScrollY=0
 private val displayDate:DateTimeFormatter=DateTimeFormatter.ofPattern("dd-MM-uuuu",Locale.ITALIAN)
 private fun formatDate(value:String):String = runCatching { LocalDate.parse(value).format(displayDate) }.getOrDefault(value)
 private fun pickDate(initial:LocalDate=LocalDate.now(), onPicked:(LocalDate)->Unit) {
  DatePickerDialog(this,{ _,year,month,day -> onPicked(LocalDate.of(year,month+1,day)) },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
   datePicker.maxDate=System.currentTimeMillis()
  }.show()
 }

 private lateinit var db:Database
 private val importRequest=101
 private val exportRequest=102
 private val backupRequest=103
 private val restoreRequest=104
 private var backupPassword:CharArray?=null
 private lateinit var root:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=Database(this); checkActivation() }
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
    prompting=false;unlocked=true;showList()
   }
   override fun onAuthenticationError(errorCode:Int, errString:CharSequence) {
    prompting=false; if(!unlocked) finish()
   }
  })
 }
 override fun onStop() { super.onStop(); unlocked=false; prompting=false }
 override fun onStart() { super.onStart(); if (hasStarted && ::db.isInitialized && !unlocked) checkActivation(); hasStarted=true }
 private val brandYellow=Color.rgb(250,179,0)
 private val ink=Color.rgb(34,34,34)
 private fun dp(n:Int):Int=(n*resources.displayMetrics.density).toInt()
 private fun shape(color:Int,stroke:Int?=null):GradientDrawable=GradientDrawable().apply {
  setColor(color);cornerRadius=dp(14).toFloat();if(stroke!=null)setStroke(dp(1),stroke)
 }
 private fun button(label:String, action:()->Unit):Button = Button(this).apply {
  text=label;isAllCaps=false;textSize=16f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD
  background=shape(brandYellow);minHeight=dp(50);setPadding(dp(14),dp(9),dp(14),dp(9))
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
 private fun compactTextAction(symbol:String,description:String,action:()->Unit):TextView = TextView(this).apply {
  text=symbol;textSize=19f;gravity=android.view.Gravity.CENTER;setTextColor(ink)
  contentDescription=tr(description);background=shape(brandYellow)
  layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(4) }
  setOnClickListener { action() }
 }
 private fun contactRow(label:String,value:String,symbol:String,action:()->Unit) {
  if(value.isBlank()) return
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,dp(4)) }
  row.addView(TextView(this).apply { text="${tr(label)}: $value";textSize=16f;setTextColor(ink);layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f) })
  row.addView(iconButton(symbol,label,action));root.addView(row)
 }
 private fun secondaryButton(label:String, action:()->Unit):Button=button(label,action).apply {
  background=shape(Color.WHITE,Color.rgb(223,199,137))
 }
 private fun screen(title:String):LinearLayout {
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(22),dp(18),dp(20));setBackgroundColor(Color.rgb(249,249,247))}
  val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  root.addView(TextView(this).apply{text=title;textSize=25f;setTextColor(ink);typeface=Typeface.DEFAULT_BOLD;setPadding(0,0,0,dp(18))})
  return root
 }
 private val sortLabels=listOf("Nome cliente","Codice cliente","Città","Zona","Settore","Data ultima visita","Numero visite annuali")
 private val sortKeys=listOf("name","code","city","zone","sector","visit","count")
 private fun showList(q:String=listSearch) {
  listSearch=q
  screen("YellowKunde")
  val scroll=root.parent as ScrollView
  lateinit var searchBar:LinearLayout
  lateinit var sortAction:()->Unit
  val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
  toolbar.addView(compactTextAction("＋","Nuovo cliente") { saveListPosition(scroll);edit(null) })
  toolbar.addView(compactTextAction("☷","Filtri") { searchBar.visibility=if(searchBar.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE })
  toolbar.addView(compactTextAction("↕","Ordina clienti") { sortAction() })
  toolbar.addView(compactTextAction("⚙","Opzioni") { saveListPosition(scroll);showOptions() })
  root.addView(toolbar)
  searchBar=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val search=EditText(this).apply{hint=tr("Cerca codice, nome, città, settore, zona");setSingleLine(true);setText(q);textSize=14f}
  searchBar.addView(search)
  val zoneOptions=listOf("") + db.zones()
  val zoneSpinner=Spinner(this).apply { contentDescription=tr("Filtra per zona") }
  zoneSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,zoneOptions.map { if(it.isBlank()) tr("Tutte le zone") else it })
  val filterRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL }
  filterRow.addView(TextView(this).apply { text=tr("Zona");textSize=13f;setTextColor(ink) })
  filterRow.addView(zoneSpinner,LinearLayout.LayoutParams(0,dp(48),1f))
  filterRow.addView(compactTextAction("×","Azzera filtri") {
   listSearch="";listZone="";listScrollY=0
   prefs.edit().putString("sort_key","name").putBoolean("sort_desc",false).apply();showList("")
  })
  searchBar.addView(filterRow)
  root.addView(searchBar)
  val zoneIndex=zoneOptions.indexOf(listZone).coerceAtLeast(0)
  if(zoneIndex==0) listZone=""
  zoneSpinner.setSelection(zoneIndex)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  fun update(){
   list.removeAllViews()
   val sort=prefs.getString("sort_key","name") ?: "name"
   val descending=prefs.getBoolean("sort_desc",false)
   db.sortedList(listSearch,sort,descending).forEach{(code,name)->
    if(listZone.isBlank() || db.get(code)?.get("zona")?.trim()==listZone) {
     list.addView(secondaryButton("$name  ·  $code"){saveListPosition(scroll);detail(code)})
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
  zoneSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
    listZone=zoneOptions[position];update()
   }
  }
  update()
  scroll.post { scroll.scrollTo(0,listScrollY) }
 }
 private fun saveListPosition(scroll:ScrollView) { listScrollY=scroll.scrollY }

 private fun heading(label:String) {
  root.addView(TextView(this).apply { text=label; textSize=19f; setPadding(0,22,0,8) })
 }
 private fun showOptions() {
  screen(tr("Opzioni"))
  root.addView(button("← " + tr("Indietro")){showList()})
  heading(tr("Importazione ed esportazione Excel"))
  root.addView(button(tr("Importa Excel .xlsx")) {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";addCategory(Intent.CATEGORY_OPENABLE) },importRequest)
  })
  root.addView(button(tr("Esporta Excel .xlsx")) {
   AlertDialog.Builder(this).setTitle(tr("Attenzione ai dati personali"))
    .setMessage(tr("Il file Excel non è cifrato e contiene dati dei clienti. Conservalo in una posizione protetta e non condividerlo senza autorizzazione."))
    .setNegativeButton(tr("Annulla"), null)
    .setPositiveButton(tr("Continua")) { _, _ ->
     startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_TITLE,"clienti.xlsx");addCategory(Intent.CATEGORY_OPENABLE) },exportRequest)
    }.show()
  })
  heading(tr("Backup e ripristino"))
  root.addView(button(tr("Crea backup cifrato")) {
   askBackupPassword(tr("Password per il backup (minimo 12 caratteri)")) { password ->
    backupPassword=password
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"clienti.gcbk");addCategory(Intent.CATEGORY_OPENABLE) },backupRequest)
   }
  })
  root.addView(button(tr("Ripristina backup cifrato")) {
   AlertDialog.Builder(this).setTitle(tr("Ripristino completo"))
    .setMessage(tr("Il ripristino SOSTITUIRÀ tutti i clienti e tutte le visite presenti. Assicurati di avere una copia di sicurezza."))
    .setNegativeButton(tr("Annulla"),null).setPositiveButton(tr("Continua")) { _,_ ->
     askBackupPassword(tr("Password del backup")) { password ->
      backupPassword=password
      startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE) },restoreRequest)
     }
    }.show()
  })

  heading(tr("Sicurezza"))
  root.addView(button(tr("Sicurezza")){showSecurity()})
  heading(tr("Lingua"))
  root.addView(button(tr("Lingua") + " / Sprache"){chooseLanguage()})
  heading(tr("Informazioni sull’app"))
  root.addView(button(tr("Informazioni sull’app")){showAbout()})
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
   text="YellowKunde\n${tr("Versione")}: ${packageInfo.versionName}"
   textSize=18f
  })
 }
 private fun edit(code:String?) {
  val existing=code?.let{db.get(it)} ?: emptyMap()
  screen(if(code==null)tr("Nuovo cliente") else tr("Modifica cliente"))
  val inputs=mutableMapOf<String,EditText>()
  fields.forEachIndexed{index,key->
   root.addView(TextView(this).apply{text=tr(captions[index])})
   val field=EditText(this).apply{setText(existing[key] ?: "");setSingleLine(key!="note");if(key=="note")minLines=3; if(key.startsWith("telefono"))inputType=InputType.TYPE_CLASS_PHONE; if(key.startsWith("email"))inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS}
   inputs[key]=field;root.addView(field)
  }
  root.addView(button(tr("Salva")){
   try {db.save(code,inputs.mapValues{it.value.text.toString()});detail(inputs.getValue("codice").text.toString().trim())}
   catch(e:Exception){Toast.makeText(this,e.message ?: tr("Errore"),Toast.LENGTH_LONG).show()}
  })
  root.addView(button(tr("Annulla")){if(code==null)showList() else detail(code)})
 }
 private fun detail(code:String) {
  val customer=db.get(code) ?: run{showList();return}
  screen(customer["nome"].orEmpty())
  root.addView(button(tr("← Elenco clienti")){showList()})
  val actionFields=setOf("telefono_principale","telefono_secondario","email_principale","email_secondaria")
  fields.forEachIndexed { index,key ->
   if(key !in actionFields) root.addView(TextView(this).apply { text="${tr(captions[index])}: ${customer[key].orEmpty()}";textSize=16f;setPadding(0,5,0,5) })
  }
  listOf("telefono_principale","telefono_secondario").forEach { key ->
   val number=customer[key].orEmpty().trim()
   contactRow(captions[fields.indexOf(key)],number,"☎") { openExternal(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number)))) }
  }
  listOf("email_principale","email_secondaria").forEach { key ->
   val email=customer[key].orEmpty().trim()
   contactRow(captions[fields.indexOf(key)],email,"✉") { openExternal(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(email)))) }
  }
  val destination=listOf("indirizzo","citta","provincia").map { customer[it].orEmpty().trim() }.filter { it.isNotBlank() }.joinToString(", ")
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
  actionRow.addView(iconButton("▦","Appuntamento Outlook") {
   // Outlook Android does not reliably implement CalendarContract event insertion.
   // Open the Outlook calendar compose page, allowing the user to choose the account type.
   val subject="Visita – "+customer["nome"].orEmpty()
   val description="Cliente: "+customer["nome"].orEmpty()+"\nTelefono: "+customer["telefono_principale"].orEmpty()
   val options=if(isGerman()) arrayOf("Geschäfts- oder Schulkonto", "Privates Outlook-Konto")
               else arrayOf("Account aziendale o scolastico", "Account Outlook personale")
   AlertDialog.Builder(this)
    .setTitle(tr("Appuntamento Outlook"))
    .setItems(options) { _,which ->
     val base=if(which==0) "https://outlook.office.com/calendar/deeplink/compose"
              else "https://outlook.live.com/calendar/0/deeplink/compose"
     val url=Uri.parse(base).buildUpon()
      .appendQueryParameter("subject",subject)
      .appendQueryParameter("location",destination)
      .appendQueryParameter("body",description)
      .build()
     openExternal(Intent(Intent.ACTION_VIEW,url))
    }
    .setNegativeButton(tr("Annulla"),null)
    .show()
  })
  val (count,last)=db.stats(code)
  val visits=db.visitEntries(code)
  val visitHistory=visits.joinToString("\n") { "• ${it.second.format(displayDate)}" }
  root.addView(TextView(this).apply {
   text=if(isGerman()) "Besuche ${LocalDate.now().year}: $count\nLetzter Besuch: ${formatDate(last)}\n\nVerlauf:\n$visitHistory" else "Visite ${LocalDate.now().year}: $count\nUltima visita: ${formatDate(last)}\n\nStorico:\n$visitHistory"
   textSize=17f;setPadding(0,15,0,15)
  })
  root.addView(button(tr("Registra visita (oggi)")){db.visit(code);detail(code)})
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
  root.addView(button(tr("Modifica cliente")){edit(code)})
  root.addView(button(tr("Elimina cliente")) {AlertDialog.Builder(this).setMessage(tr("Eliminare definitivamente questo cliente e tutte le visite?")).setPositiveButton(tr("Elimina")){_,_->db.delete(code);showList()}.setNegativeButton(tr("Annulla"),null).show()})
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
