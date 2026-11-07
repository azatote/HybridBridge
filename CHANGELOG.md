# Historique du projet HybridBridge

Ce journal distingue les modifications realisees, les preuves de validation et
les travaux restants. Une compilation reussie ne valide pas un test materiel.

## Regle de suivi

Pour chaque evolution, ajouter une entree datee avec : objectif, fichiers
modifies, raison du changement, verification et resultat, reference GitHub
(commit ou execution CI si disponible), limitations et prochaine action.
Ne jamais inclure de secrets, de PSK, de messages prives ou d'adresses Bluetooth.
Les entrees ci-dessous retracent uniquement les faits observes dans cette session.

## 2026-11-07 - Socle de protocole Bitchat officiel 2.2.0

- Test materiel Meshtastic aller-retour confirme par l'utilisateur ; debut de l'etape Bitchat.
- Release de reference Bitchat : v2.2.0, commit 20501fdfe0d56c7705c377d4a263b01d8f38eeeb.
- Extraction de six fichiers officiels inchangees au build, dependances fermees
  sur Android/Java/Parcelize ; aucun faux moteur BLE ni protocole maison.
- Fichiers : `app/build.gradle.kts`, nouveau `BitchatProtocolTest.kt`, workflow,
  audit amont, decisions, README et ce journal. Cinq tests de codec ajoutes, seuil CI passe a 21.
- Sources GPL Bitchat et licence amont incluses dans l'archive de sources correspondantes.
- CI [42371877884](https://github.com/azatote/HybridBridge/actions/runs/42371877884),
  commit 04a216ba60057cb02ae51db85b186c058553b0f5 : `completed / success`.
  Compilation, tests du codec et tests existants reussis ; controle CI d'au
  moins 21 tests sans echec/erreur et des licences embarquees reussi.
- Aucun changement de l'interface/transport Meshtastic ; lien APK materiel 0.4.1 conserve.
- Limites : pas encore de scan/advertising GATT Bitchat, de participation au mesh,
  d'identites/sessions raccordees ou d'echange de messages avec un client officiel.

## 2026-11-07 - Retour materiel positif sur la 0.4.1

- L'utilisateur confirme : ouverture sans crash et connexion Bluetooth OK
  sur le Samsung Android 14 utilise pour le test.
- Le crash de demarrage n'est plus observe lors de cet essai de la 0.4.1.
  La cause exacte de la fermeture 0.4 reste non demontree.
- Retour suivant : l'utilisateur confirme l'etat Meshtastic synchronise,
  avec firmware et canaux affiches. Version exacte du firmware non communiquee.
- Retour suivant : test d'envoi et reponse recue confirme OK par l'utilisateur.
  Echanges Meshtastic aller-retour valides sur son installation de test.
  Ne pas generaliser ce resultat a tous les firmwares ou telephones.
- Fichiers documentaires : README et ce journal ; aucun changement de code ni nouvel APK.
- Etape suivante : audit et integration Bitchat BLE, puis test avec un client
  officiel sur un autre telephone ; le pont inter-reseaux reste non livre.

## 2026-11-07 - Incident : fermeture au demarrage de la 0.4

- Retour utilisateur : application 0.4 fermee des son ouverture ; vider le
  cache, desinstaller et reinstaller n'a pas resolu le probleme.
- Les tests precedents couvraient le protocole, mais ne demarraient pas l'Activity.
- Ajout de Robolectric 4.14.1 et d'un test de lancement Android 15 (API 35)
  de MainActivity, sans connexion Bluetooth.
- Fichiers de diagnostic : `app/build.gradle.kts`, nouveau `MainActivityTest.kt`
  et ce journal. Aucun correctif affirme avant la reproduction.
- CI de reproduction : [42366598698](https://github.com/azatote/HybridBridge/actions/runs/42366598698).
  Demarrage simule Android 15 reussi, sans reproduction du crash.
- L'utilisateur confirme Android 14 sur le Samsung.
- CI [42366918289](https://github.com/azatote/HybridBridge/actions/runs/42366918289) :
  demarrage, mesure et dessin initiaux reussis sous Android 14 et 15 simules.
  Cela ne reproduit pas les particularites du firmware Samsung.
- Version 0.4.1 de diagnostic : remplacement de TabHost/TabWidget par une
  navigation RadioGroup et vues explicites ; aucune cause OEM affirmee comme prouvee.
- Exception RuntimeException pendant la construction de l'interface : affichage
  d'un diagnostic partageable (API Android, classe d'exception et pile de methodes),
  sans message d'exception, contenu de conversation, identifiant Bluetooth ni cle.
  Les erreurs hors de cette construction peuvent encore necessiter un log Android.
- Tests renforces : les trois vues normales doivent demarrer et etre dessinees
  sous API 34/35 ; un ecran de diagnostic seul ne compte pas comme succes.
- VersionCode 5, versionName 0.4.1 ; seuil CI releve a 16 tests.
  Fichiers : MainActivity, MainActivityTest, Gradle app, workflow et journal.
- CI 0.4.1 [42369864068](https://github.com/azatote/HybridBridge/actions/runs/42369864068),
  commit `fda9571c0e8658310f48c8186addfdec89bd058f` : `completed / success`.
  Tests Android 14/15 des trois vues, protocole et compilation reussis ;
  verification CI d'au moins 16 tests sans erreur et des licences de l'APK.
  Artefact APK 13572958710, 2622249 octets ; sources 13572978426,
  rapports 13572854293 ; expiration annoncee le 5 fevrier 2027.
- README mis a jour : lien 0.4.1 et premier essai sans connexion Heltec.
- ADB non trouve sur le PC lors de cette intervention ; crash reel non collecte.
- Statut actualise : ouverture sans crash et Bluetooth confirmes par
  l'utilisateur avec la 0.4.1 sur son Samsung Android 14, voir le retour ci-dessus.

## 2026-11-07 - Version 0.4 : synchronisation et messagerie Meshtastic

- Licence GPLv3 et sources publiques approuvees par l'utilisateur ; ajout
  de `LICENSE` officiel et licences embarquees GPLv3/runtime protobuf.
- Generation Java protobuf depuis les schemas epingles du firmware 2.7.26,
  sans imposer le Kotlin 2.4.10 de l'artefact amont a Kotlin 2.0.21 du projet.
- Synchronisation MyInfo/Metadata/Channels, verification du nonce de fin,
  firmware 2.7.x requis, lectures/ecritures GATT serialisees et timeouts.
- Vues Appareils, Messages et Etat. Messages de canal confirmes avant envoi,
  limites UTF-8/MTU, historique borne et statut Transmis au Heltec explicite.
- Messages directs/chiffres non affiches, aucun relais prive ou Bitchat.
- Fichiers : Gradle racine/app, LICENSE, MainActivity, MeshtasticGattClient,
  nouveau MeshtasticProtocol, nouveau MeshtasticProtocolTest, workflow,
  audit amont et ce journal. Neuf tests de protocole ajoutes aux cinq tests GATT.
- CI complete lancee depuis le commit
  [43c313e](https://github.com/azatote/HybridBridge/commit/43c313eeb2aaba947d2c16cc4efaea82930efa84) :
  [42365094752](https://github.com/azatote/HybridBridge/actions/runs/42365094752).
  Echec de compilation Kotlin DSL : `java.net` masque par le DSL et configuration
  `proto` incorrecte ; corrige par import URI et bloc `proto { ... }`.
- Deuxieme CI [42365245941](https://github.com/azatote/HybridBridge/actions/runs/42365245941) :
  descriptor.proto manquant pour les extensions nanopb. Ajout du descripteur
  officiel et passage au runtime Java complet, sans duplication de classes Google.
- Troisieme CI [42365419514](https://github.com/azatote/HybridBridge/actions/runs/42365419514),
  commit `efcc69968b5555c4f6b21429799a7ded1cc3090a` : compilation et tests reussis.
- Ajout d'un intervalle minimal de 15 secondes entre deux emissions utilisateur.
- CI finale [42365776287](https://github.com/azatote/HybridBridge/actions/runs/42365776287),
  commit `77da7e18d8af33e51fb04d0d2e19be92347c5261`, `completed / success`.
  Tests, compilation et verification XML (au moins 14 tests, zero echec/erreur)
  reussis ; presence des deux licences verifiee dans le ZIP APK par la CI.
  Artefact APK 13571880617, 2620236 octets, sources 13571114910,
  rapports XML 13572145016 ; expiration annoncee le 5 fevrier 2027.
  Diagnostics editeur sans nouvelles erreurs.
- La CI doit publier APK, resultats XML et archive des sources correspondantes.
- Limites : echanges reels non encore testes ; Bitchat, pont public, E2EE,
  persistance et service de fond restent a realiser. Ceci n'est pas la version finale.

## 2026-11-07 - Version 0.3 : connexion GATT de test

- Client GATT independant : connexion BLE directe, verification du service
  et des caracteristiques officielles Meshtastic, lecture FromNum et batterie
  facultative. Timeout de connexion 90 secondes et deconnexion explicite.
- Selection d'un appareil detecte, boutons Connecter/Deconnecter et etats
  distincts de scan, liaison BLE, service trouve et lecture GATT verifiee.
- Scanner diagnostic 0.2 conserve ; duree de scan portee de 30 a 90 secondes
  pour tenir compte du retour de detection tardive.
- Fichiers : `MainActivity.kt`, nouveau `MeshtasticGattClient.kt`,
  `app/build.gradle.kts`, nouveau `MeshtasticGattClientTest.kt`, workflow CI,
  `docs/UPSTREAM_AUDIT.md`, README, cahier des charges et ce journal.
- VersionCode 3, versionName 0.3 ; artefact `HybridBridge-0.3-debug-apk`.
- Reference officielle verifiee : firmware 2.7.26, commit
  `54e0d8d0ab2ff56b3a9ce967e53f79e49af560fb`, detail dans l'audit amont.
- Tests ajoutes : little-endian, compteur non signe, zero, taille tronquee
  et taille excessive. Commande CI :
  `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
- Sources publiees au commit
  [11bc3dc](https://github.com/azatote/HybridBridge/commit/11bc3dc4eda77314a611b312da6e1134e0e011a8).
- CI : [42363246117](https://github.com/azatote/HybridBridge/actions/runs/42363246117),
  `completed / success`, tests unitaires et assembleDebug reussis.
  Artefact 13571085179, `HybridBridge-0.3-debug-apk`, 822238 octets,
  non expire, expiration annoncee le 5 fevrier 2027.
  Aucun diagnostic signale par l'editeur sur les fichiers Kotlin/workflow/tests.
- README : lien APK remplace par la version 0.3 et procedure de test GATT ajoutee.
- Limites : test physique de connexion non realise ; aucun message, aucune
  synchronisation protobuf du noeud, aucun flash ni modification de firmware.
  L'appairage reste gere par Android ; accepter la demande systeme si presente.

## 2026-11-07 - Cibles Android recents et Meshtastic 2.7

- Demande utilisateur : compatibilite avec les Android recents et Meshtastic 2.7.
- Fichiers : `HYBRIDBRIDGE_SPECIFICATION.md` (section 2.4) et `CHANGELOG.md`.
- Priorite Android 12 et plus, dernieres versions stables incluses ; matrice
  de validation prevue pour Android 12 a 16. Minimum actuel Android 8 conserve.
- Firmware Meshtastic cible : 2.7.x ; correctif/build de chaque Heltec a relever.
  Ne pas confondre version du firmware et version de la dependance SDK.
- SDK Android actuel : compileSdk/targetSdk 35, sans changement Gradle ici.
- Verification : coherence avec la configuration Gradle locale inspectee.
- Limites : aucune certification Android 12 a 16 ou firmware 2.7.x complete ;
  aucun nouvel APK, test materiel ou changement du code dans cette intervention.

## 2026-11-06 - Correction du retour de test sur telephone

- L'utilisateur retire son premier constat d'echec : apres une longue attente,
  la recherche de l'APK a abouti et le Heltec est apparu, decrit comme connecte.
- Resultat rapporte : installation et decouverte BLE reussies sur le telephone.
  Duree exacte, version de l'APK, version Android et firmware a relever.
- Le code inspecte ne contient aucune connexion GATT ni echange de messages :
  la detection ne constitue pas une preuve de connexion Meshtastic fonctionnelle.
- Le filtre UUID n'est donc pas confirme comme cause d'un echec de detection.
- Les changements locaux de diagnostic 0.2 sont conserves, sans publication
  du code ni nouvelle compilation dans cette intervention.
- Fichiers documentaires mis a jour : `CHANGELOG.md` et `README.md`.
- Prochaine verification : reproduire la detection et mesurer le delai.

## 2026-11-06 - Version 0.2 : diagnostic de decouverte BLE

- Retour initial de l'utilisateur : Meshtastic officiel detecte le Heltec,
  HybridBridge semble ne pas le detecter. Ce constat est retire par le retour
  ulterieur de detection apres une longue attente, consigne ci-dessus.
- Hypothese : le filtre UUID applique par Android masque des annonces BLE.
  Ce n'est pas une cause prouvee sans le prochain essai sur telephone.
- Fichiers : `MainActivity.kt`, `app/build.gradle.kts`, workflow Android et ce journal.
- Scan sans filtre systeme, classification locale par UUID annonce ; option
  explicite pour afficher tous les appareils BLE et distinguer les non identifies.
- Compteurs BLE/affichage, scan limite a 30 secondes, resultats defilants,
  traitement des resultats par lots et messages de refus de permissions.
- Version Android : versionCode 2, versionName 0.2 ; artefact CI nomme
  `HybridBridge-0.2-debug-apk`.
- Verification editeur : aucun diagnostic Kotlin signale. Compilation CI a verifier.
- Test discriminant : cocher Tous les appareils BLE avant Rechercher les Heltec ;
  relever les compteurs, le nom du Heltec et la presence ou non du service annonce.
  Puis comparer avec une recherche sans cette option, sans connexion officielle concurrente.
- Limites : aucun appareil non identifie n'est affirme compatible ; pas de GATT,
  pas de messages, pas de flash ; diagnostic 0.2 non encore valide sur telephone.

## 2026-11-06 - Lien de telechargement APK en tete du README

- Fichiers : `README.md` et `CHANGELOG.md`.
- Ajout du lien vers l'artefact 13536763865 de la compilation reussie 42285264911.
- Le README precise : connexion GitHub, ZIP a extraire, APK debug de test,
  expiration le 4 fevrier 2027 sauf suppression anticipee.
- Suppression de la mention obsolete indiquant que l'APK precompile est absent.
- Verification API GitHub : artefact present et non expire au moment de l'ajout.
- Limites : ce lien d'artefact n'est pas une release permanente ; aucun nouveau
  test materiel ni changement du code Android dans cette intervention.

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

- Installer l'APK 0.4 et valider synchronisation, canaux et messages de test
  dans les deux sens entre deux noeuds Meshtastic, sans connexion officielle concurrente.
- Completer l'audit Bitchat et son integration, le pont public anti-boucles,
  l'E2EE, la persistance, Compose, le service de fond et la signature release.
- Reproduire la detection rapportee, mesurer le delai et relever les versions
  de l'APK, d'Android et du firmware Meshtastic.
- Poursuivre ensuite les phases du cahier des charges, sans annoncer une
  connexion ou une passerelle fonctionnelle avant validation sur appareils reels.
