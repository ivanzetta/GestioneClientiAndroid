package it.gestioneclienti

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encrypted, password-protected portable backup. Does not encrypt the live SQLite database. */
object SecureBackup {
 private val rng=SecureRandom()
 private const val iterations=210000
 private val magic="GCBK1".toByteArray(Charsets.US_ASCII)
 private fun key(password:CharArray,salt:ByteArray):SecretKeySpec {
  val spec=PBEKeySpec(password,salt,iterations,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
  finally { spec.clearPassword() }
 }
 fun save(context:Context,uri:Uri,db:Database,password:CharArray) {
  require(password.size>=12) { "La password deve contenere almeno 12 caratteri" }
  val root=JSONObject().put("version",1)
  val customers=JSONArray()
  db.allCustomers().forEach { item -> val obj=JSONObject();fields.forEach { obj.put(it,item[it].orEmpty()) };customers.put(obj) }
  val visits=JSONArray()
  db.allVisits().forEach { (code,date) -> visits.put(JSONObject().put("codice",code).put("data",date)) }
  root.put("clienti",customers).put("visite",visits)
  val salt=ByteArray(16).also(rng::nextBytes)
  val nonce=ByteArray(12).also(rng::nextBytes)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,nonce))
  cipher.updateAAD(magic)
  val ciphertext=cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
  context.contentResolver.openOutputStream(uri,"w")!!.use { out -> out.write(magic);out.write(salt);out.write(nonce);out.write(ciphertext) }
 }
 fun restore(context:Context,uri:Uri,db:Database,password:CharArray):Pair<Int,Int> {
  val bytes=context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
  require(bytes.size in 50..50_000_000 && bytes.copyOfRange(0,5).contentEquals(magic)) { "Formato backup non valido" }
  val salt=bytes.copyOfRange(5,21);val nonce=bytes.copyOfRange(21,33)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,key(password,salt),GCMParameterSpec(128,nonce))
  cipher.updateAAD(magic)
  val plaintext=cipher.doFinal(bytes.copyOfRange(33,bytes.size))
  val root=JSONObject(String(plaintext,Charsets.UTF_8))
  require(root.getInt("version")==1) { "Versione backup non supportata" }
  val c=root.getJSONArray("clienti");val v=root.getJSONArray("visite")
  val customers=(0 until c.length()).map { i -> val item=c.getJSONObject(i);fields.associateWith { item.optString(it, "") } }
  val visits=(0 until v.length()).map { i -> val item=v.getJSONObject(i);item.getString("codice") to item.getString("data") }
  db.restoreSnapshot(customers,visits)
  return customers.size to visits.size
 }
}
