package it.gestioneclienti

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class ImportPreview(val rows: List<Map<String,String>>, val newCount:Int, val updateCount:Int)

object Excel {
 fun read(context:Context, uri:Uri, db:Database):ImportPreview {
  val rows=mutableListOf<Map<String,String>>()
  context.contentResolver.openInputStream(uri)!!.use { stream ->
   XSSFWorkbook(stream).use { workbook ->
    val sheet=workbook.getSheetAt(0)
    val formatter=DataFormatter()
    val iterator=sheet.iterator()
    require(iterator.hasNext()) { "File Excel vuoto" }
    val header=iterator.next().map { formatter.formatCellValue(it).trim().lowercase() }
    require(fields.all { it in header }) { "Colonne mancanti: ${fields.filter { it !in header }.joinToString()}" }
    val seen=mutableSetOf<String>()
    while(iterator.hasNext()) {
     val row=iterator.next()
     val values=fields.associateWith { key -> formatter.formatCellValue(row.getCell(header.indexOf(key))).trim() }
     val code=values.getValue("codice")
     if(code.isBlank() && values.values.all { it.isBlank() }) continue
     require(code.isNotBlank()) { "Codice cliente mancante alla riga ${row.rowNum+1}" }
     require(seen.add(code)) { "Codice duplicato nel file: $code" }
     rows.add(values)
    }
   }
  }
  val updates=rows.count { db.get(it.getValue("codice")) != null }
  return ImportPreview(rows, rows.size-updates, updates)
 }
 fun write(context:Context, uri:Uri, db:Database) {
  XSSFWorkbook().use { workbook ->
   val sheet=workbook.createSheet("Clienti")
   val header=sheet.createRow(0)
   fields.forEachIndexed { i,key -> header.createCell(i).setCellValue(key) }
   db.allCustomers().forEachIndexed { index, values ->
    val row=sheet.createRow(index+1)
    fields.forEachIndexed { i,key -> row.createCell(i).setCellValue(values[key].orEmpty()) }
   }
   val visits=workbook.createSheet("Visite")
   val visitHeader=visits.createRow(0)
   visitHeader.createCell(0).setCellValue("codice")
   visitHeader.createCell(1).setCellValue("data")
   db.allVisits().forEachIndexed { index, entry ->
    val row=visits.createRow(index+1)
    row.createCell(0).setCellValue(entry.first)
    row.createCell(1).setCellValue(entry.second)
   }
   context.contentResolver.openOutputStream(uri)!!.use { workbook.write(it) }
  }
 }
}
