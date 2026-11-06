# Historique du projet HybridBridge

Ce journal distingue les modifications realisees, les preuves de validation et
les travaux restants. Une compilation reussie ne valide pas un test materiel.

## Regle de suivi

Pour chaque evolution, ajouter une entree datee avec : objectif, fichiers
modifies, raison du changement, verification et resultat, reference GitHub
(commit ou execution CI si disponible), limitations et prochaine action.
Ne jamais inclure de secrets, de PSK, de messages prives ou d'adresses Bluetooth.
Les entrees ci-dessous retracent uniquement les faits observes dans cette session.

## 2026-11-06 - Mise en place du suivi des evolutions

- Fichiers locaux : creation de `CHANGELOG.md` et ajout d'un lien dans `README.md`.
- Chaque nouvelle evolution doit documenter ses fichiers, sa justification,
  ses validations, sa reference GitHub et ses limites.
- Le journal et son lien README accompagnent la correction dans le depot.
- Verification : journal sans diagnostics Markdown ; le README conserve des
  avertissements preexistants d'espacement autour de ses autres titres/listes.

## 2026-11-06 - Correction de la preparation Android

- Objectif : permettre la compilation du premier APK de test.
- Fichier : `.github/workflows/android-apk.yml`.
- Diagnostic confirme dans les logs de l'execution
  [42283341188](https://github.com/azatote/HybridBridge/actions/runs/42283341188/job/128117729923) :
  `sdkmanager tools` echoue avec `Failed to find package 'tools'`, code 1.
- Cause : `android-actions/setup-android@v3` utilise par defaut
  `packages: 'tools platform-tools'`, verifie dans son fichier `action.yml` officiel.
- Correction locale : definir `packages: 'platform-tools'` explicitement.
  La commande existante conserve Android SDK 35 et Build Tools 35.0.0.
- Verification locale : aucun diagnostic signale sur le workflow dans VS Code.
- Correction publiee sur GitHub : commit
  [2bd8ef7](https://github.com/azatote/HybridBridge/commit/2bd8ef7e42561dfa8d8fbf3189e49a97c280bfe3).
- Nouvelle execution : [42285264911](https://github.com/azatote/HybridBridge/actions/runs/42285264911).
  Preparation Android, installation SDK 35 et preparation Gradle reussies.
  Etape `gradle --no-daemon :app:assembleDebug` reussie.
  Artefact `HybridBridge-0.1-debug-apk` present, 812200 octets, non expire.
  Resultat global confirme : `completed / success` (CI verte).
- Limites : aucun APK produit par la premiere execution ; aucun test sur telephone.
  La nouvelle execution ne lance pas de tests unitaires ; compiler n'est pas tester le BLE.
- Avertissements CI non corriges dans cette intervention ciblee : actions basees
  sur Node 20, deprecation de setup-java v4 et migration de ubuntu-latest.

## 2026-11-06 - Procedure du premier test Android

- Perimetre actuel : installation, ouverture, permissions, decouverte BLE
  Meshtastic, affichage du nom/adresse/RSSI, arret et reprise du scan.
- Prerequis : compilation CI reussie, APK debug, telephone Android 8 ou plus,
  Heltec avec firmware Meshtastic existant et Bluetooth active.
- Validation materielle : non realisee. Aucun flashage requis ni effectue.
- Connexion GATT, messages, Bitchat et passerelle : non implementes dans la base observee.
- Le cahier des charges `HYBRIDBRIDGE_SPECIFICATION.md` a ete lu integralement.
  Il decrit une cible de conception et ne constitue pas une preuve de livraison.

## 2026-11-06 - Deplacement du workflow sur GitHub

- Ancien chemin distant : `android-apk.yml` a la racine, non reconnu par Actions.
- Nouveau chemin : `.github/workflows/android-apk.yml`, deja correct en local.
- Commit : [3302228](https://github.com/azatote/HybridBridge/commit/33022282214a7eb34996dc20379dbcc9b681ec95).
- Verification API GitHub : nouveau chemin present, ancien absent, empreinte
  du contenu inchangee, workflow `Build HybridBridge APK` actif.
- Resultat : premiere execution declenchee ; echec de preparation Android
  documente plus haut, avant compilation Kotlin ou publication de l'APK.

## 2026-11-06 - Comparaison initiale local / GitHub

- Depot consulte : <https://github.com/azatote/HybridBridge>, branche `main`.
- Les sept fichiers communs compares avaient des empreintes Git identiques :
  README, configurations Gradle, manifeste Android et MainActivity Kotlin.
- Seule difference observee dans cette comparaison : emplacement du workflow.
- Le dossier local n'etait pas un depot Git ; aucune initialisation ni liaison
  Git locale n'a ete effectuee pendant cette session.
- Base observee : HybridBridge 0.1, Kotlin 2.0.21, AGP 8.7.3,
  Gradle CI 8.10.2, JDK 17, compileSdk/targetSdk 35, minSdk 26.

## Travaux restants

- Telecharger l'artefact `HybridBridge-0.1-debug-apk` et installer `app-debug.apk`.
- Realiser et consigner le premier test de decouverte BLE sur le Samsung.
- Poursuivre ensuite les phases du cahier des charges, sans annoncer une
  connexion ou une passerelle fonctionnelle avant validation sur appareils reels.
