# Relais 4x50 BAC — Import de liste

Écran d'import (1er onglet de l'appli) permettant de charger directement :
- le fichier **Relais-BAC_4X50.xlsx** (onglet « Liste » : Classe, Eq, Nom, Prénom,
  ORDRE, Sexe, Perf 50m), lu sans dépendance externe (pas de POI, juste
  `java.util.zip` + parseur XML standard Android) ;
- ou un **CSV** de secours (`classe;nom;prenom;sexe;perf50`).

La perf 50m est stockée en centièmes de seconde, comme dans le fichier Excel
(660 = 6''60 = 6,60 s), et ré-affichée au même format.

Un aperçu des élèves détectés s'affiche avant confirmation ; les lignes
illisibles sont listées en avertissement plutôt que de faire planter l'import.
On peut ensuite ajouter des élèves à la main ou en supprimer.

## Compiler

1. **Android Studio** (le plus simple) : `Ouvrir` ce dossier → laisser Gradle
   synchroniser → `Run`.
2. **Ligne de commande** (Android Studio installé, `ANDROID_HOME` défini) :
   ```
   ./gradlew assembleDebug
   ```
   → APK dans `app/build/outputs/apk/debug/`.

## Suite

Ce module correspond au tout premier écran de l'appli « Relais 4x50 BAC »
(architecture Kotlin/Compose/Room identique). La table `students` créée ici
est prête à être reliée à la composition d'équipes et au moteur de notation
déjà validés (barème 1+5+6+8 pts /20).
