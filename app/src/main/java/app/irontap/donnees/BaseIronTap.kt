package app.irontap.donnees

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Entrainement::class, Exercice::class, Serie::class, EtatChrono::class],
    version = 1,
)
@TypeConverters(Convertisseurs::class)
abstract class BaseIronTap : RoomDatabase() {

    abstract fun entrainements(): EntrainementDao

    companion object {
        fun creer(context: Context): BaseIronTap =
            Room.databaseBuilder(context, BaseIronTap::class.java, "irontap.db").build()
    }
}
