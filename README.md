# HybridBridge 0.3 — test de connexion Meshtastic BLE

**[Télécharger l'APK Android 0.3 de test (ZIP)](https://github.com/azatote/HybridBridge/actions/runs/42363246117/artifacts/13571085179)**

Connexion GitHub requise. Extraire le ZIP puis installer `app-debug.apk`.
Build debug pour test uniquement ; lien valable jusqu'au 5 février 2027,
sauf suppression anticipée de l'artefact.

Application Android Kotlin native pour **découvrir et tester la connexion GATT Meshtastic**, sans flasher les Heltec.

## État réel

- Inclus : permissions BLE, recherche de 90 secondes, filtre local Meshtastic, mode diagnostic tous appareils, sélection, connexion/déconnexion GATT, vérification des caractéristiques, lecture FromNum et batterie facultative.
- Non inclus : synchronisation protobuf du nœud, version firmware, SDK Meshtastic, messages, Bitchat, passerelle, reconnexion automatique et service de fond.
- `GATT Meshtastic verifie` signifie service/caractéristiques vérifiés et compteur FromNum lu, pas livraison de messages ni certification de la version firmware.
- Cette étape est volontairement limitée et vérifiable. Ne pas la présenter comme une passerelle fonctionnelle.

## Test Sur Téléphone

1. Installer l'APK 0.3. Si Android refuse la mise à jour pour signature différente, désinstaller l'ancien APK de test puis installer le nouveau ; les données locales de l'ancien seront perdues.
2. Allumer le Heltec, activer son Bluetooth et déconnecter Meshtastic officiel de ce nœud.
3. Accorder les permissions Appareils à proximité sur Android 12+ ; sur Android 8 à 11, autoriser la localisation et activer sa fonction système.
4. Garder HybridBridge au premier plan, rechercher et sélectionner le Heltec dans la liste. Utiliser le mode diagnostic si nécessaire ; un appareil BLE non identifié peut être rejeté après connexion.
5. Appuyer sur Connecter. Si Android demande l'appairage, saisir le PIN du Heltec uniquement dans la fenêtre système.
6. Attendre `GATT Meshtastic verifie ; FromNum = ...`. La batterie peut ensuite apparaître. Ce compteur n'est pas un nombre de messages livrés.
7. Appuyer sur Déconnecter, puis Connecter pour tester une deuxième connexion.
8. Relever version Android, firmware Meshtastic 2.7.x exact, délai et état affiché ; masquer les adresses Bluetooth avant partage de capture. Aucun message LoRa ne sera envoyé.

Si l'appairage ou la lecture échoue, noter le texte/code exact puis vérifier
la demande Android et reconnecter. Ne pas flasher le Heltec pour ce test.

## Compatibilité

Cibles : Android récents (priorité 12 à 16) et firmware Meshtastic 2.7.x.
Contrat vérifié dans les sources 2.7.26 ; la version installée sur le Heltec
reste à relever. Minimum Android 8, compileSdk/targetSdk 35. Les tests matériels
de connexion et la matrice Android restent à réaliser, pas à supposer réussis.
Voir [l'audit amont](docs/UPSTREAM_AUDIT.md).

## Compiler

1. Ouvrir ce dossier avec Android Studio récent (JDK 17, Android SDK 35).
2. Laisser Gradle synchroniser et installer les dépendances.
3. Exécuter `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` avec Gradle 8.10.2, ou Build > Build APK(s) dans Android Studio.
4. Activer le Bluetooth et Meshtastic sur le Heltec, autoriser les permissions, lancer la recherche.

## Suite

Valider la connexion GATT sur le Samsung et le Heltec, puis ajouter la
synchronisation du nœud et les messages Meshtastic via un protocole/SDK vérifié.
Bitchat et le routage inter-réseaux viennent ensuite.

## Prudence

Aucune écriture en mémoire flash, aucune modification de firmware, aucune émission LoRa depuis cette application.

## Historique du projet

Consulter [CHANGELOG.md](CHANGELOG.md) pour les modifications, leurs raisons,
les validations réalisées, les limitations et les prochaines actions.
Chaque évolution doit y être consignée, sans secrets ni données privées.

## Build without a PC (GitHub Actions)

Upload the contents of this `HybridBridge` folder to the root of a GitHub repository.
Open Actions > Build HybridBridge APK > Run workflow. After completion,
download the artifact `HybridBridge-0.3-debug-apk`, extract it and install
`app-debug.apk` on Android. This debug build is for local testing only.
Unit tests and debug compilation succeeded in GitHub Actions on 2026-11-07.
The 0.3 APK artifact was produced. Hardware GATT validation is still pending.
See [CHANGELOG.md](CHANGELOG.md) for the build reference and limitations.
