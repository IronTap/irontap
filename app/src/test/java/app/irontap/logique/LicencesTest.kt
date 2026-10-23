package app.irontap.logique

import org.junit.Assert.assertEquals
import org.junit.Test

class LicencesTest {

    @Test
    fun `paragraphes reunis, titres en majuscules, filets ignores`() {
        val texte = """
            Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)

            This Font Software is licensed under the SIL Open Font License, Version 1.1.
            This license is copied below.

            -----------------------------------------------------------
            SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
            -----------------------------------------------------------

            PREAMBLE
            The goals of the Open Font License (OFL) are to stimulate
            worldwide development.

              1. Definitions.
        """.trimIndent()
        assertEquals(
            listOf(
                BlocLicence.Copyright("Copyright © 2016 The Inter Project Authors (https://github.com/rsms/inter)"),
                BlocLicence.Paragraphe("This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is copied below."),
                BlocLicence.Paragraphe("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"),
                BlocLicence.Titre("PREAMBLE"),
                BlocLicence.Paragraphe("The goals of the Open Font License (OFL) are to stimulate worldwide development."),
                BlocLicence.Titre("1. Definitions."),
            ),
            extraireCopyright(decouperLicence(texte)),
        )
        assertEquals(listOf(BlocLicence.Titre("PREAMBLE"), BlocLicence.Titre("END OF TERMS AND CONDITIONS")), decouperLicence("PREAMBLE\n\n  END OF TERMS AND CONDITIONS\n"))
        // Une longue ligne en majuscules commence un paragraphe (MIT).
        assertEquals(
            listOf(BlocLicence.Paragraphe("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.")),
            decouperLicence("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\nIMPLIED."),
        )
    }

    @Test
    fun `titres de la GPL, centres ou numerotes`() {
        val gpl = "                            Preamble\n\n  The GNU General Public License is a free,\ncopyleft license.\n\n" +
            "  0. Definitions.\n\n  \"This License\" refers to version 3.\n\n  15. Disclaimer of Warranty.\n\n" +
            "            How to Apply These Terms to Your New Programs\n"
        assertEquals(
            listOf(
                BlocLicence.Titre("Preamble"),
                BlocLicence.Paragraphe("The GNU General Public License is a free, copyleft license."),
                BlocLicence.Titre("0. Definitions."),
                BlocLicence.Paragraphe("\"This License\" refers to version 3."),
                BlocLicence.Titre("15. Disclaimer of Warranty."),
                BlocLicence.Titre("How to Apply These Terms to Your New Programs"),
            ),
            decouperLicence(gpl),
        )
    }

    @Test
    fun `copyright cherche dans les premiers paragraphes seulement`() {
        val mit = decouperLicence("MIT License\n\nCopyright (c) 2023 Phosphor Icons\n\nPermission is hereby granted.")
        assertEquals(
            listOf(
                BlocLicence.Copyright("Copyright © 2023 Phosphor Icons"),
                BlocLicence.Paragraphe("MIT License"),
                BlocLicence.Paragraphe("Permission is hereby granted."),
            ),
            extraireCopyright(mit),
        )
        val sans = decouperLicence("A\n\nB\n\nC\n\nCopyright (c) tard")
        assertEquals(sans, extraireCopyright(sans))
    }

    @Test
    fun `bibliotheques par titulaire puis le texte Apache une seule fois`() {
        val liste = lireBibliotheques(
            "BSD-3-Clause\tGoogle Inc.\tandroidx.datastore:datastore-preferences-external-protobuf\t1.2.1\n" +
                "Apache-2.0\tSquare, Inc.\tcom.squareup.okio:okio\t3.9.1\n" +
                "Apache-2.0\tThe Android Open Source Project\tandroidx.room:room-runtime\t2.8.5\n" +
                "Apache-2.0\tThe Android Open Source Project\tandroidx.core:core\t1.17.0\n" +
                "ligne mal formée\n",
        )
        assertEquals(4, liste.size)
        assertEquals(
            listOf(
                BlocLicence.Copyright("Copyright © Square, Inc."),
                BlocLicence.Paragraphe("com.squareup.okio:okio 3.9.1"),
                BlocLicence.Copyright("Copyright © The Android Open Source Project"),
                BlocLicence.Paragraphe("androidx.room:room-runtime 2.8.5\nandroidx.core:core 1.17.0"),
                BlocLicence.Paragraphe("Ces bibliothèques sont distribuées sous la licence Apache 2.0, dont le texte suit."),
                BlocLicence.Titre("Apache License"),
                BlocLicence.Paragraphe("Version 2.0, January 2004"),
                BlocLicence.Copyright("Copyright © Google Inc."),
                BlocLicence.Paragraphe("androidx.datastore:datastore-preferences-external-protobuf 1.2.1"),
                BlocLicence.Paragraphe("Cette bibliothèque est distribuée sous la licence BSD 3 clauses, dont le texte suit."),
                BlocLicence.Paragraphe("Copyright 2008 Google Inc. All rights reserved."),
                BlocLicence.Paragraphe("Redistribution and use…"),
            ),
            blocsBibliotheques(
                liste,
                mapOf(
                    "Apache-2.0" to "                                 Apache License\n                           Version 2.0, January 2004\n",
                    "BSD-3-Clause" to "Copyright 2008 Google Inc. All rights reserved.\n\nRedistribution and use…",
                ),
            ),
        )
    }
}
