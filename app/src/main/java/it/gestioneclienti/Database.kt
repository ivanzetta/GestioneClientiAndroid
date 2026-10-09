package it.gestioneclienti

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

val fields = listOf("codice", "nome", "indirizzo", "citta", "provincia", "zona", "settore", "contatto_principale", "telefono_principale", "contatto_secondario", "telefono_secondario", "note")
val captions = listOf("Codice cliente", "Nome", "Indirizzo", "Città", "Provincia", "Zona", "Settore", "Contatto principale", "Telefono principale", "Contatto secondario", "Telefono secondario", "Note")
class Database(context: Context): SQLiteOpenHelper(context, "clienti.db", null, 1) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE clienti (codice TEXT PRIMARY KEY, nome TEXT NOT NULL DEFAULT '', indirizzo TEXT NOT NULL DEFAULT '', citta TEXT NOT NULL DEFAULT '', provincia TEXT NOT NULL DEFAULT '', zona TEXT NOT NULL DEFAULT '', settore TEXT NOT NULL DEFAULT '', contatto_principale TEXT NOT NULL DEFAULT '', telefono_principale TEXT NOT NULL DEFAULT '', contatto_secondario TEXT NOT NULL DEFAULT '', telefono_secondario TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE visite (id INTEGER PRIMARY KEY AUTOINCREMENT, codice TEXT NOT NULL REFERENCES clienti(codice) ON DELETE CASCADE, data TEXT NOT NULL)")
  db.execSQL("CREATE INDEX idx_visite_cliente ON visite(codice, data)")
 }
 override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
 fun list(q: String): List<Pair<String,String>> {
  val result= mutableListOf<Pair<String,String>>()
  val pattern="%$q%"
  readableDatabase.rawQuery("SELECT codice,nome FROM clienti WHERE codice LIKE ? OR nome LIKE ? OR citta LIKE ? OR settore LIKE ? OR zona LIKE ? ORDER BY nome COLLATE NOCASE", Array(5){pattern}).use { c -> while(c.moveToNext()) result.add(c.getString(0) to c.getString(1)) }
  return result
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
   val cv=android.content.ContentValues(); fields.forEach{cv.put(it, values[it] ?: "")}
   if (oldCode != null && oldCode != code) {
    // Create the new customer before moving visits to satisfy the foreign key.
    require(db.rawQuery("SELECT 1 FROM clienti WHERE codice=?", arrayOf(oldCode)).use { it.moveToFirst() }) { "Cliente non trovato" }
    require(!db.rawQuery("SELECT 1 FROM clienti WHERE codice=?", arrayOf(code)).use { it.moveToFirst() }) { "Codice già esistente" }
    db.insertOrThrow("clienti", null, cv)
    db.execSQL("UPDATE visite SET codice=? WHERE codice=?", arrayOf(code, oldCode))
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
    fields.forEach { cv.put(it, values[it].orEmpty()) }
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
    val cv=android.content.ContentValues();fields.forEach { cv.put(it,values[it].orEmpty()) }
    sql.insertOrThrow("clienti",null,cv)
   }
   visits.forEach { (code,date) -> sql.execSQL("INSERT INTO visite(codice,data) VALUES(?,?)",arrayOf(code,date)) }
   sql.setTransactionSuccessful()
  } finally { sql.endTransaction() }
 }
 fun history(code:String):String {
  val years= mutableListOf<String>()
  readableDatabase.rawQuery("SELECT substr(data,1,4),COUNT(*) FROM visite WHERE codice=? GROUP BY substr(data,1,4) ORDER BY 1 DESC",arrayOf(code)).use{c->while(c.moveToNext())years.add("${c.getString(0)}: ${c.getInt(1)} visite")}
  return years.joinToString("\n").ifBlank { "Nessuna visita registrata" }
 }
}
