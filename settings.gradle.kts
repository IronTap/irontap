// Dépôts : androidx et com.android ne peuvent venir que du dépôt Google (contenu exclusif) ;
// les autres groupes Google (KSP, Guava…) sont aussi publiés sur Maven Central.
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "IronTap"
include(":app")
