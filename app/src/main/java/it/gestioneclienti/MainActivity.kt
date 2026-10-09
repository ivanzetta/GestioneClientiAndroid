package it.gestioneclienti

import android.content.Intent
import android.net.Uri
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

class MainActivity: Activity() {
 private var unlocked=false
 private var prompting=false
 private var hasStarted=false
 private lateinit var db:Database
 private val importRequest=101
 private val exportRequest=102
 private val backupRequest=103
 private val restoreRequest=104
 private var backupPassword:CharArray?=null
 private lateinit var root:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=Database(this); authenticate() }
 private fun authenticate() {
  if (unlocked || prompting) return
  val km=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
  if (!km.isDeviceSecure) {
   AlertDialog.Builder(this).setTitle("Protezione richiesta")
    .setMessage("Configura un PIN e, se desideri, l’impronta digitale nelle impostazioni di Android prima di usare l’app.")
    .setPositiveButton("Chiudi") { _,_ -> finish() }.setCancelable(false).show()
   return
  }
  if (android.os.Build.VERSION.SDK_INT < 30) {
   AlertDialog.Builder(this).setMessage("Questa versione richiede Android 11 o successivo per l’accesso protetto.")
    .setPositiveButton("Chiudi") { _,_ -> finish() }.setCancelable(false).show()
   return
  }
  prompting=true
  val prompt=BiometricPrompt.Builder(this)
   .setTitle("Sblocca Gestione Clienti")
   .setSubtitle("Usa impronta digitale o PIN del telefono")
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
 override fun onStart() { super.onStart(); if (hasStarted && ::db.isInitialized && !unlocked) authenticate(); hasStarted=true }
 private fun button(label:String, action:()->Unit):Button = Button(this).apply{text=label;setOnClickListener{action()}}
 private fun screen(title:String):LinearLayout {
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,25,20,15)}
  val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  root.addView(TextView(this).apply{text=title;textSize=25f;setPadding(0,0,0,20)})
  return root
 }
 private fun showList(q:String="") {
  screen("Gestione Clienti")
  root.addView(button("+ Nuovo cliente"){edit(null)})
  root.addView(button("Importa Excel .xlsx") {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";addCategory(Intent.CATEGORY_OPENABLE) },importRequest)
  })
  root.addView(button("Esporta Excel .xlsx") {
   AlertDialog.Builder(this).setTitle("Attenzione ai dati personali")
    .setMessage("Il file Excel non è cifrato e contiene dati dei clienti. Conservalo in una posizione protetta e non condividerlo senza autorizzazione.")
    .setNegativeButton("Annulla", null)
    .setPositiveButton("Continua") { _, _ ->
     startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_TITLE,"clienti.xlsx");addCategory(Intent.CATEGORY_OPENABLE) },exportRequest)
    }.show()
  })
  root.addView(button("Crea backup cifrato") {
   askBackupPassword("Password per il backup (minimo 12 caratteri)") { password ->
    backupPassword=password
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"clienti.gcbk");addCategory(Intent.CATEGORY_OPENABLE) },backupRequest)
   }
  })
  root.addView(button("Ripristina backup cifrato") {
   AlertDialog.Builder(this).setTitle("Ripristino completo")
    .setMessage("Il ripristino SOSTITUIRÀ tutti i clienti e tutte le visite presenti. Assicurati di avere una copia di sicurezza.")
    .setNegativeButton("Annulla",null).setPositiveButton("Continua") { _,_ ->
     askBackupPassword("Password del backup") { password ->
      backupPassword=password
      startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE) },restoreRequest)
     }
    }.show()
  })
  val search=EditText(this).apply{hint="Cerca codice, nome, città, settore, zona";setSingleLine(true);setText(q)}
  root.addView(search)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  fun update(text:String){list.removeAllViews();db.list(text).forEach{(code,name)->list.addView(button("$name  ·  $code"){detail(code)})}}
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){update(s.toString())};override fun afterTextChanged(s:android.text.Editable?) {}})
  update(q)
 }
 private fun edit(code:String?) {
  val existing=code?.let{db.get(it)} ?: emptyMap()
  screen(if(code==null)"Nuovo cliente" else "Modifica cliente")
  val inputs=mutableMapOf<String,EditText>()
  fields.forEachIndexed{index,key->
   root.addView(TextView(this).apply{text=captions[index]})
   val field=EditText(this).apply{setText(existing[key] ?: "");setSingleLine(key!="note");if(key=="note")minLines=3; if(key.startsWith("telefono"))inputType=InputType.TYPE_CLASS_PHONE}
   inputs[key]=field;root.addView(field)
  }
  root.addView(button("Salva"){
   try {db.save(code,inputs.mapValues{it.value.text.toString()});detail(inputs.getValue("codice").text.toString().trim())}
   catch(e:Exception){Toast.makeText(this,e.message ?: "Errore",Toast.LENGTH_LONG).show()}
  })
  root.addView(button("Annulla"){if(code==null)showList() else detail(code)})
 }
 private fun detail(code:String) {
  val customer=db.get(code) ?: run{showList();return}
  screen(customer["nome"].orEmpty())
  root.addView(button("← Elenco clienti"){showList()})
  fields.forEachIndexed{index,key->root.addView(TextView(this).apply{text="${captions[index]}: ${customer[key].orEmpty()}";textSize=16f;setPadding(0,5,0,5)})}
  val (count,last)=db.stats(code)
  root.addView(TextView(this).apply{text="Visite ${java.time.LocalDate.now().year}: $count\nUltima visita: $last\n\nStorico:\n${db.history(code)}";textSize=17f;setPadding(0,15,0,15)})
  root.addView(button("Registra visita (oggi)"){db.visit(code);detail(code)})
  root.addView(button("Registra visita con altra data") {
   val input=EditText(this).apply { hint="AAAA-MM-GG";setText(java.time.LocalDate.now().toString());setSingleLine(true) }
   AlertDialog.Builder(this).setTitle("Data della visita").setView(input)
    .setNegativeButton("Annulla",null).setPositiveButton("Registra") { _,_ ->
     try { db.visit(code,java.time.LocalDate.parse(input.text.toString().trim()));detail(code) }
     catch(e:Exception) { Toast.makeText(this,"Data non valida. Usa AAAA-MM-GG",Toast.LENGTH_LONG).show() }
    }.show()
  })
  root.addView(button("Annulla ultima visita") {AlertDialog.Builder(this).setMessage("Eliminare l'ultima visita registrata?").setPositiveButton("Sì"){_,_->db.undoLastVisit(code);detail(code)}.setNegativeButton("No",null).show()})
  root.addView(button("Modifica cliente"){edit(code)})
  root.addView(button("Elimina cliente") {AlertDialog.Builder(this).setMessage("Eliminare definitivamente questo cliente e tutte le visite?").setPositiveButton("Elimina"){_,_->db.delete(code);showList()}.setNegativeButton("Annulla",null).show()})
 }
 private fun askBackupPassword(title:String, action:(CharArray)->Unit) {
  val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
  AlertDialog.Builder(this).setTitle(title).setView(input)
   .setNegativeButton("Annulla",null).setPositiveButton("Continua") { _,_ ->
    val password=input.text.toString().toCharArray()
    if(password.isEmpty()) Toast.makeText(this,"Inserisci una password",Toast.LENGTH_LONG).show()
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
     AlertDialog.Builder(this).setTitle("Anteprima importazione")
      .setMessage("Nuovi clienti: ${preview.newCount}\nDa aggiornare: ${preview.updateCount}\nTotale: ${preview.rows.size}\n\nLe visite già registrate saranno conservate.")
      .setNegativeButton("Annulla",null)
      .setPositiveButton("Conferma") { _,_ ->
       try { db.importCustomers(preview.rows);showList();Toast.makeText(this,"Importazione completata",Toast.LENGTH_LONG).show() }
       catch(e:Exception) { Toast.makeText(this,e.message ?: "Errore importazione",Toast.LENGTH_LONG).show() }
      }.show()
    }
    exportRequest -> { Excel.write(this,uri,db);Toast.makeText(this,"Esportazione completata",Toast.LENGTH_LONG).show() }
    backupRequest -> { SecureBackup.save(this,uri,db,backupPassword ?: error("Password mancante"));Toast.makeText(this,"Backup cifrato creato",Toast.LENGTH_LONG).show() }
    restoreRequest -> { val (customers,visits)=SecureBackup.restore(this,uri,db,backupPassword ?: error("Password mancante"));showList();Toast.makeText(this,"Ripristinati $customers clienti e $visits visite",Toast.LENGTH_LONG).show() }
   }
  } catch(e:Exception) { AlertDialog.Builder(this).setTitle("Operazione non riuscita").setMessage(e.message ?: "Password errata o file non valido").setPositiveButton("OK",null).show() }
  finally { backupPassword?.fill('\u0000');backupPassword=null }
 }

}
