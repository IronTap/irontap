package app.irontap.donnees

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Dates en texte ISO (« 2026-10-12 », lisible et triable), horodatages en millisecondes epoch. */
internal class Convertisseurs {

    @TypeConverter
    fun versDate(texte: String): LocalDate = LocalDate.parse(texte)

    @TypeConverter
    fun depuisDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun versInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    @TypeConverter
    fun depuisInstant(instant: Instant): Long = instant.toEpochMilli()
}
