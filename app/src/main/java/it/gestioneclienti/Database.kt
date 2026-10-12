package it.gestioneclienti

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

val fields = listOf("codice", "nome", "indirizzo", "cap", "citta", "provincia", "zona", "settore", "contatto_principale", "telefono_principale", "email_principale", "contatto_secondario", "telefono_secondario", "email_secondaria", "info1", "info2", "info3", "info4", "info5", "note")
val captions = listOf("Codice cliente", "Nome", "Indirizzo", "CAP", "Città", "Provincia", "Zona", "Settore", "Contatto principale", "Telefono principale", "Email principale", "Contatto secondario", "Telefono secondario", "Email secondaria", "Info1", "Info2", "Info3", "Info4", "Info5", "Note")
class Database(context: Context): SQLiteOpenHelper(context, "clienti.db", null, 4) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE clienti (codice TEXT PRIMARY KEY, nome TEXT NOT NULL DEFAULT '', indirizzo TEXT NOT NULL DEFAULT '', cap TEXT NOT NULL DEFAULT '', citta TEXT NOT NULL DEFAULT '', provincia TEXT NOT NULL DEFAULT '', zona TEXT NOT NULL DEFAULT '', settore TEXT NOT NULL DEFAULT '', contatto_principale TEXT NOT NULL DEFAULT '', telefono_principale TEXT NOT NULL DEFAULT '', email_principale TEXT NOT NULL DEFAULT '', contatto_secondario TEXT NOT NULL DEFAULT '', telefono_secondario TEXT NOT NULL DEFAULT '', email_secondaria TEXT NOT NULL DEFAULT '', info1 TEXT NOT NULL DEFAULT 'false', info2 TEXT NOT NULL DEFAULT 'false', info3 TEXT NOT NULL DEFAULT 'false', info4 TEXT NOT NULL DEFAULT 'false', info5 TEXT NOT NULL DEFAULT 'false', note TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE visite (id INTEGER PRIMARY KEY AUTOINCREMENT, codice TEXT NOT NULL REFERENCES clienti(codice) ON DELETE CASCADE, data TEXT NOT NULL)")
  db.execSQL("CREATE INDEX idx_visite_cliente ON visite(codice, data)")
  createPlans(db)
 }
 private fun createPlans(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS attivita (id INTEGER PRIMARY KEY AUTOINCREMENT, codice TEXT NOT NULL REFERENCES clienti(codice) ON UPDATE CASCADE ON DELETE CASCADE, tipo TEXT NOT NULL CHECK(tipo IN ('visita','contatto','giro')), data TEXT, note TEXT NOT NULL DEFAULT '', posizione INTEGER NOT NULL DEFAULT 0, completata INTEGER NOT NULL DEFAULT 0)")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_attivita_aperte ON attivita(codice,tipo) WHERE tipo IN ('visita','contatto') AND completata=0")
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_attivita_data ON attivita(data,posizione)")
 }
 override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  if(oldVersion < 2) {
   db.execSQL("ALTER TABLE clienti ADD COLUMN email_principale TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE clienti ADD COLUMN email_secondaria TEXT NOT NULL DEFAULT ''")
  }
  if(oldVersion < 3) {
   db.execSQL("ALTER TABLE clienti ADD COLUMN cap TEXT NOT NULL DEFAULT ''")
   for(i in 1..5) db.execSQL("ALTER TABLE clienti ADD COLUMN info$i TEXT NOT NULL DEFAULT 'false'")
  }
  if(oldVersion < 4) createPlans(db)
 }
 fun setCustomerInfo(code:String,key:String,checked:Boolean) {
  require(key in (1..5).map { "info$it" }) { "Campo Info non valido" }
  val values=android.content.ContentValues().apply { put(key,if(checked) "true" else "false") }
  require(writableDatabase.update("clienti",values,"codice=?",arrayOf(code))==1) { "Cliente non trovato" }
 }
 fun setInfoFalseForAll(key:String) {
  require(key in (1..5).map { "info$it" })
  val values=android.content.ContentValues().apply { put(key,"false") }
  writableDatabase.update("clienti",values,null,null)
 }
 fun list(q: String): List<Pair<String,String>> {
  val result= mutableListOf<Pair<String,String>>()
  val pattern="%$q%"
  readableDatabase.rawQuery("SELECT codice,nome FROM clienti WHERE codice LIKE ? OR nome LIKE ? OR citta LIKE ? OR settore LIKE ? OR zona LIKE ? ORDER BY nome COLLATE NOCASE", Array(5){pattern}).use { c -> while(c.moveToNext()) result.add(c.getString(0) to c.getString(1)) }
  return result
 }
 fun sortedList(q:String, sort:String, descending:Boolean):List<Pair<String,String>> {
  val allowed=mapOf("name" to "nome COLLATE NOCASE", "code" to "codice COLLATE NOCASE", "city" to "citta COLLATE NOCASE", "zone" to "zona COLLATE NOCASE", "sector" to "settore COLLATE NOCASE", "visit" to "(SELECT MAX(data) FROM visite WHERE visite.codice=clienti.codice)", "count" to "(SELECT COUNT(*) FROM visite WHERE visite.codice=clienti.codice AND substr(data,1,4)=strftime('%Y','now','localtime'))")
  val column=allowed[sort] ?: allowed.getValue("name")
  val order=if(descending) "DESC" else "ASC"
  val result=mutableListOf<Pair<String,String>>()
  val pattern="%$q%"
  readableDatabase.rawQuery("SELECT codice,nome FROM clienti WHERE codice LIKE ? OR nome LIKE ? OR citta LIKE ? OR settore LIKE ? OR zona LIKE ? ORDER BY $column $order, nome COLLATE NOCASE ASC, codice COLLATE NOCASE ASC",Array(5){pattern}).use { cursor ->
   while(cursor.moveToNext()) result.add(cursor.getString(0) to cursor.getString(1))
  }
  return result
 }
 fun zones():List<String> {
  val zones=mutableListOf<String>()
  readableDatabase.rawQuery("SELECT DISTINCT TRIM(zona) FROM clienti WHERE TRIM(zona) <> '' ORDER BY TRIM(zona) COLLATE NOCASE",null).use { c -> while(c.moveToNext()) zones.add(c.getString(0)) }
  return zones
 }
 fun visitEntries(code:String):List<Pair<Long,LocalDate>> {
  val entries=mutableListOf<Pair<Long,LocalDate>>()
  readableDatabase.rawQuery("SELECT id,data FROM visite WHERE codice=? ORDER BY data DESC,id DESC",arrayOf(code)).use { c ->
   while(c.moveToNext()) entries.add(c.getLong(0) to LocalDate.parse(c.getString(1)))
  }
  return entries
 }
 fun changeVisitDate(code:String,id:Long,date:LocalDate) {
  require(!date.isAfter(LocalDate.now())) { "Non puoi registrare una visita futura" }
  val values=android.content.ContentValues().apply { put("data",date.toString()) }
  require(writableDatabase.update("visite",values,"id=? AND codice=?",arrayOf(id.toString(),code))==1) { "Visita non trovata" }
 }
 fun get(code: String): Map<String,String>? {
  readableDatabase.query("clienti", fields.toTypedArray(), "codice=?", arrayOf(code),null,null,null).use { c ->
   if(!c.moveToFirst()) return null
   return fields.indices.associate { fields[it] to c.getString(it) }
  }
 }
 fun save(oldCode: String?, values: Map<String,String>) {
  val code=values.getValue("codice").trim(); require(code.isNotEmpty()) { "Codice cliente obbligatorio" }
  val db=writableDatabase
  db.beginTransaction()
  try {
   val cv=android.content.ContentValues(); fields.forEach{cv.put(it, values[it] ?: if(it.matches(Regex("info[1-5]"))) "false" else "")}
   if (oldCode != null && oldCode != code) {
    // Create the new customer before moving visits to satisfy the foreign key.
    require(db.rawQuery("SELECT 1 FROM clienti WHERE codice=?", arrayOf(oldCode)).use { it.moveToFirst() }) { "Cliente non trovato" }
    require(!db.rawQuery("SELECT 1 FROM clienti WHERE codice=?", arrayOf(code)).use { it.moveToFirst() }) { "Codice già esistente" }
    db.insertOrThrow("clienti", null, cv)
    db.execSQL("UPDATE visite SET codice=? WHERE codice=?", arrayOf(code, oldCode))
    db.execSQL("UPDATE attivita SET codice=? WHERE codice=?", arrayOf(code, oldCode))
    require(db.delete("clienti", "codice=?", arrayOf(oldCode)) == 1)
   } else if (oldCode != null) {
    require(db.update("clienti", cv, "codice=?", arrayOf(code)) == 1) { "Cliente non trovato" }
   } else {
    db.insertOrThrow("clienti", null, cv)
   }
   db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 fun allCustomers():List<Map<String,String>> {
  val result=mutableListOf<Map<String,String>>()
  readableDatabase.query("clienti",fields.toTypedArray(),null,null,null,null,"nome COLLATE NOCASE").use { c ->
   while(c.moveToNext()) result.add(fields.indices.associate { fields[it] to c.getString(it) })
  }
  return result
 }
 fun importCustomers(rows:List<Map<String,String>>) {
  val database=writableDatabase
  database.beginTransaction()
  try {
   rows.forEach { values ->
    val cv=android.content.ContentValues()
    fields.filter { it in values }.forEach { cv.put(it, values[it].orEmpty()) }
    val updated = database.update("clienti",cv,"codice=?",arrayOf(values.getValue("codice")))
    if(updated == 0) database.insertOrThrow("clienti",null,cv)
   }
   database.setTransactionSuccessful()
  } finally { database.endTransaction() }
 }
 fun delete(code:String) { writableDatabase.delete("clienti","codice=?",arrayOf(code)) }
 fun visit(code:String, date:LocalDate = LocalDate.now()) { require(!date.isAfter(LocalDate.now())) { "Non puoi registrare una visita futura" }; writableDatabase.execSQL("INSERT INTO visite(codice,data) VALUES(?,?)",arrayOf(code,date.toString())) }
 fun undoLastVisit(code:String) { writableDatabase.execSQL("DELETE FROM visite WHERE id=(SELECT id FROM visite WHERE codice=? ORDER BY id DESC LIMIT 1)",arrayOf(code)) }
 fun stats(code:String):Pair<Int,String> {
  val year=LocalDate.now().year.toString()
  val count=readableDatabase.rawQuery("SELECT COUNT(*) FROM visite WHERE codice=? AND substr(data,1,4)=?",arrayOf(code,year)).use{it.moveToFirst();it.getInt(0)}
  val last=readableDatabase.rawQuery("SELECT MAX(data) FROM visite WHERE codice=?",arrayOf(code)).use{it.moveToFirst();if(it.isNull(0)) "Nessuna" else it.getString(0)}
  return count to last
 }
 fun allVisits():List<Pair<String,String>> {
  val visits=mutableListOf<Pair<String,String>>()
  readableDatabase.rawQuery("SELECT codice,data FROM visite ORDER BY codice,data",null).use { c ->
   while(c.moveToNext()) visits.add(c.getString(0) to c.getString(1))
  }
  return visits
 }
 fun restoreSnapshot(customers:List<Map<String,String>>, visits:List<Pair<String,String>>) {
  require(customers.size <= 100000 && visits.size <= 1000000) { "Backup troppo grande" }
  val codes=customers.map { it["codice"].orEmpty() }
  require(codes.all { it.isNotBlank() } && codes.distinct().size==codes.size) { "Codici cliente non validi o duplicati" }
  require(visits.all { it.first in codes && runCatching { LocalDate.parse(it.second) }.isSuccess }) { "Visite non valide" }
  val sql=writableDatabase
  sql.beginTransaction()
  try {
   sql.delete("visite",null,null);sql.delete("clienti",null,null)
   customers.forEach { values ->
    val cv=android.content.ContentValues();fields.forEach { cv.put(it,values[it] ?: if(it.matches(Regex("info[1-5]"))) "false" else "") }
    sql.insertOrThrow("clienti",null,cv)
   }
   visits.forEach { (code,date) -> sql.execSQL("INSERT INTO visite(codice,data) VALUES(?,?)",arrayOf(code,date)) }
   sql.setTransactionSuccessful()
  } finally { sql.endTransaction() }
 }

 data class Task(val id:Long,val code:String,val name:String,val type:String,val date:String?,val notes:String,val position:Int,val done:Boolean)
 fun tasks(type:String?=null,date:String?=null,openOnly:Boolean=true):List<Task> {
  val where=mutableListOf<String>();val args=mutableListOf<String>()
  if(type!=null){where.add("a.tipo=?");args.add(type)}
  if(date!=null){where.add("a.data=?");args.add(date)}
  if(openOnly)where.add("a.completata=0")
  val filter=if(where.isEmpty()) "" else "WHERE "+where.joinToString(" AND ")
  val out=mutableListOf<Task>()
  readableDatabase.rawQuery("SELECT a.id,a.codice,c.nome,a.tipo,a.data,a.note,a.posizione,a.completata FROM attivita a JOIN clienti c ON c.codice=a.codice $filter ORDER BY CASE WHEN a.data IS NULL THEN 1 ELSE 0 END,a.data,a.posizione,a.id",args.toTypedArray()).use { c ->
   while(c.moveToNext())out.add(Task(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),if(c.isNull(4))null else c.getString(4),c.getString(5),c.getInt(6),c.getInt(7)!=0))
  }
  return out
 }
 fun saveTask(code:String,type:String,date:String?,notes:String):Boolean {
  require(type=="visita" || type=="contatto")
  val cv=android.content.ContentValues().apply {put("codice",code);put("tipo",type);put("data",date);put("note",notes);put("completata",0)}
  val existing=readableDatabase.rawQuery("SELECT id FROM attivita WHERE codice=? AND tipo=? AND completata=0",arrayOf(code,type)).use { if(it.moveToFirst()) it.getLong(0) else null }
  if(existing!=null) writableDatabase.update("attivita",cv,"id=?",arrayOf(existing.toString())) else writableDatabase.insertOrThrow("attivita",null,cv)
  return existing!=null
 }
 fun addDayClient(code:String,date:String,notes:String="") {
  val already=readableDatabase.rawQuery("SELECT 1 FROM attivita WHERE codice=? AND tipo='giro' AND data=? AND completata=0",arrayOf(code,date)).use {it.moveToFirst()}
  require(!already){"Cliente già presente in questo giro"}
  val next=readableDatabase.rawQuery("SELECT COALESCE(MAX(posizione),0)+1 FROM attivita WHERE tipo='giro' AND data=?",arrayOf(date)).use{it.moveToFirst();it.getInt(0)}
  val cv=android.content.ContentValues().apply{put("codice",code);put("tipo","giro");put("data",date);put("note",notes);put("posizione",next)}
  writableDatabase.insertOrThrow("attivita",null,cv)
 }
 fun completeTask(id:Long){
  val sql=writableDatabase
  sql.beginTransaction()
  try {
   sql.rawQuery("SELECT codice,tipo FROM attivita WHERE id=? AND completata=0",arrayOf(id.toString())).use { c ->
    if(c.moveToFirst()) {
     val code=c.getString(0);val type=c.getString(1)
     val values=android.content.ContentValues().apply {put("completata",1)}
     if(sql.update("attivita",values,"id=? AND completata=0",arrayOf(id.toString()))==1 && (type=="visita" || type=="giro")) {
      sql.execSQL("INSERT INTO visite(codice,data) VALUES(?,?)",arrayOf(code,LocalDate.now().toString()))
     }
    }
   }
   sql.setTransactionSuccessful()
  } finally {sql.endTransaction()}
 }
 fun removeTask(id:Long){writableDatabase.delete("attivita","id=?",arrayOf(id.toString()))}
 fun changeTaskNote(id:Long,note:String){val cv=android.content.ContentValues().apply{put("note",note)};writableDatabase.update("attivita",cv,"id=?",arrayOf(id.toString()))}
 fun swapDayTasks(a:Task,b:Task){val db=writableDatabase;db.beginTransaction();try{db.execSQL("UPDATE attivita SET posizione=? WHERE id=?",arrayOf(b.position,a.id));db.execSQL("UPDATE attivita SET posizione=? WHERE id=?",arrayOf(a.position,b.id));db.setTransactionSuccessful()}finally{db.endTransaction()}}

 fun history(code:String):String {
  val years= mutableListOf<String>()
  readableDatabase.rawQuery("SELECT substr(data,1,4),COUNT(*) FROM visite WHERE codice=? GROUP BY substr(data,1,4) ORDER BY 1 DESC",arrayOf(code)).use{c->while(c.moveToNext())years.add("${c.getString(0)}: ${c.getInt(1)} visite")}
  return years.joinToString("\n").ifBlank { "Nessuna visita registrata" }
 }
}
