package app.irontap.donnees

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (séries dégressives, spec §6 v1.4) : le poids et les répétitions de chaque série deviennent son
 * palier 1 ; la table `serie` est recréée sans ces colonnes (SQLite d'Android 8 ne sait pas en supprimer) ;
 * table `palier_en_cours` pour la dégressive en cours de saisie.
 *
 * Ordre imposé par les clés étrangères, actives pendant la migration : supprimer l'ancienne table `serie`
 * supprimerait en cascade des paliers qui la référenceraient déjà. Les valeurs passent donc par une table
 * de transit, et `palier` n'est créée qu'une fois la nouvelle table `serie` en place.
 */
internal object Migration1Vers2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `transit_palier` (`serie_id` INTEGER NOT NULL, `poids_kg` REAL, `repetitions` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `transit_palier` SELECT `id`, `poids_kg`, `repetitions` FROM `serie`")

        db.execSQL(
            "CREATE TABLE `serie_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exercice_id` INTEGER NOT NULL, " +
                "`ordre` INTEGER NOT NULL, `cree_le` INTEGER NOT NULL, `modifie_le` INTEGER, " +
                "FOREIGN KEY(`exercice_id`) REFERENCES `exercice`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO `serie_v2` SELECT `id`, `exercice_id`, `ordre`, `cree_le`, `modifie_le` FROM `serie`")
        db.execSQL("DROP TABLE `serie`")
        db.execSQL("ALTER TABLE `serie_v2` RENAME TO `serie`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_serie_exercice_id` ON `serie` (`exercice_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palier` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serie_id` INTEGER NOT NULL, " +
                "`ordre` INTEGER NOT NULL, `poids_kg` REAL, `repetitions` INTEGER NOT NULL, " +
                "FOREIGN KEY(`serie_id`) REFERENCES `serie`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palier_serie_id` ON `palier` (`serie_id`)")
        db.execSQL("INSERT INTO `palier` (`serie_id`, `ordre`, `poids_kg`, `repetitions`) SELECT `serie_id`, 1, `poids_kg`, `repetitions` FROM `transit_palier`")
        db.execSQL("DROP TABLE `transit_palier`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palier_en_cours` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entrainement_id` INTEGER NOT NULL, `ordre` INTEGER NOT NULL, `poids_kg` REAL, `repetitions` INTEGER NOT NULL, " +
                "FOREIGN KEY(`entrainement_id`) REFERENCES `entrainement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palier_en_cours_entrainement_id` ON `palier_en_cours` (`entrainement_id`)")
    }
}

/**
 * v2 → v3 (supersets, spec §6 v1.6) : rôle de superset des exercices, onglet des paliers gardés, et superset
 * en cours de saisie. Les colonnes ajoutées sont vides pour l'existant : aucun exercice n'est en superset.
 */
internal object Migration2Vers3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercice` ADD COLUMN `superset_id` TEXT")
        db.execSQL("ALTER TABLE `exercice` ADD COLUMN `superset_role` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercice_superset_id` ON `exercice` (`superset_id`)")
        db.execSQL("ALTER TABLE `palier_en_cours` ADD COLUMN `role` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `superset_en_cours` (`entrainement_id` INTEGER NOT NULL, `superset_id` TEXT NOT NULL, " +
                "`nom_a` TEXT NOT NULL, `nom_b` TEXT NOT NULL, `actif` TEXT NOT NULL, `poids_a` TEXT NOT NULL, " +
                "`repetitions_a` TEXT NOT NULL, `poids_b` TEXT NOT NULL, `repetitions_b` TEXT NOT NULL, " +
                "PRIMARY KEY(`entrainement_id`), " +
                "FOREIGN KEY(`entrainement_id`) REFERENCES `entrainement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/**
 * v3 → v4 (marqueur « à l'échec », DECISIONS.md) : `serie.echec`, et `echec_a` / `echec_b` du superset en cours de
 * saisie. Faux pour l'existant : aucune série n'était marquée.
 */
internal object Migration3Vers4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `serie` ADD COLUMN `echec` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `superset_en_cours` ADD COLUMN `echec_a` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `superset_en_cours` ADD COLUMN `echec_b` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5 (RIR par palier, DECISIONS.md) : `palier.rir` et `palier_en_cours.rir` (vides : pas noté), `rir_a` / `rir_b`
 * du superset en cours de saisie, à la place du marqueur « à l'échec » (`serie.echec`, `superset_en_cours.echec_a` /
 * `echec_b`). Une série à l'échec passe à RIR 0 sur chacun de ses paliers, le marqueur valant pour toute une dégressive ;
 * de même pour un onglet de superset en cours dont la case était cochée (paliers gardés et valeur en saisie).
 *
 * `serie` et `superset_en_cours` sont recréées sans leurs anciennes colonnes (SQLite d'Android 8 ne sait pas en
 * supprimer). Comme pour [Migration1Vers2], les clés étrangères restent actives : supprimer l'ancienne `serie`
 * effacerait ses paliers en cascade. Ils passent donc par une table de transit, et `palier` est recréée après `serie`.
 * Le compteur d'identifiants de `serie` est conservé : un identifiant de série n'est jamais réutilisé.
 */
internal object Migration4Vers5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `transit_palier` (`id` INTEGER NOT NULL, `serie_id` INTEGER NOT NULL, `ordre` INTEGER NOT NULL, " +
                "`poids_kg` REAL, `repetitions` INTEGER NOT NULL, `rir` INTEGER)",
        )
        db.execSQL(
            "INSERT INTO `transit_palier` SELECT p.`id`, p.`serie_id`, p.`ordre`, p.`poids_kg`, p.`repetitions`, " +
                "CASE WHEN s.`echec` != 0 THEN 0 END FROM `palier` p JOIN `serie` s ON s.`id` = p.`serie_id`",
        )
        db.execSQL("DROP TABLE `palier`")

        val compteurSeries = db.query("SELECT `seq` FROM `sqlite_sequence` WHERE `name` = 'serie'").use { if (it.moveToFirst()) it.getLong(0) else null }
        db.execSQL(
            "CREATE TABLE `serie_v5` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exercice_id` INTEGER NOT NULL, " +
                "`ordre` INTEGER NOT NULL, `cree_le` INTEGER NOT NULL, `modifie_le` INTEGER, " +
                "FOREIGN KEY(`exercice_id`) REFERENCES `exercice`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO `serie_v5` SELECT `id`, `exercice_id`, `ordre`, `cree_le`, `modifie_le` FROM `serie`")
        db.execSQL("DROP TABLE `serie`")
        db.execSQL("ALTER TABLE `serie_v5` RENAME TO `serie`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_serie_exercice_id` ON `serie` (`exercice_id`)")
        if (compteurSeries != null) {
            db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = 'serie'")
            db.execSQL("INSERT INTO `sqlite_sequence` (`name`, `seq`) VALUES ('serie', ?)", arrayOf(compteurSeries))
        }

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palier` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serie_id` INTEGER NOT NULL, " +
                "`ordre` INTEGER NOT NULL, `poids_kg` REAL, `repetitions` INTEGER NOT NULL, `rir` INTEGER, " +
                "FOREIGN KEY(`serie_id`) REFERENCES `serie`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palier_serie_id` ON `palier` (`serie_id`)")
        db.execSQL("INSERT INTO `palier` SELECT `id`, `serie_id`, `ordre`, `poids_kg`, `repetitions`, `rir` FROM `transit_palier`")
        db.execSQL("DROP TABLE `transit_palier`")

        db.execSQL("ALTER TABLE `palier_en_cours` ADD COLUMN `rir` INTEGER")
        db.execSQL(
            "UPDATE `palier_en_cours` SET `rir` = 0 WHERE EXISTS (SELECT 1 FROM `superset_en_cours` s " +
                "WHERE s.`entrainement_id` = `palier_en_cours`.`entrainement_id` " +
                "AND ((`palier_en_cours`.`role` = 'A' AND s.`echec_a` != 0) OR (`palier_en_cours`.`role` = 'B' AND s.`echec_b` != 0)))",
        )

        db.execSQL(
            "CREATE TABLE `superset_en_cours_v5` (`entrainement_id` INTEGER NOT NULL, `superset_id` TEXT NOT NULL, " +
                "`nom_a` TEXT NOT NULL, `nom_b` TEXT NOT NULL, `actif` TEXT NOT NULL, `poids_a` TEXT NOT NULL, " +
                "`repetitions_a` TEXT NOT NULL, `poids_b` TEXT NOT NULL, `repetitions_b` TEXT NOT NULL, `rir_a` INTEGER, `rir_b` INTEGER, " +
                "PRIMARY KEY(`entrainement_id`), " +
                "FOREIGN KEY(`entrainement_id`) REFERENCES `entrainement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `superset_en_cours_v5` SELECT `entrainement_id`, `superset_id`, `nom_a`, `nom_b`, `actif`, `poids_a`, " +
                "`repetitions_a`, `poids_b`, `repetitions_b`, CASE WHEN `echec_a` != 0 THEN 0 END, CASE WHEN `echec_b` != 0 THEN 0 END " +
                "FROM `superset_en_cours`",
        )
        db.execSQL("DROP TABLE `superset_en_cours`")
        db.execSQL("ALTER TABLE `superset_en_cours_v5` RENAME TO `superset_en_cours`")
    }
}
