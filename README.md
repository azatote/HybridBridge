# HybridBridge 0.1 — première étape

**[Télécharger l'APK Android de test (ZIP)](https://github.com/azatote/HybridBridge/actions/runs/42285264911/artifacts/13536763865)**

Connexion GitHub requise. Extraire le ZIP puis installer `app-debug.apk`.
Build debug pour test uniquement ; lien valable jusqu'au 4 février 2027,
sauf suppression anticipée de l'artefact.

Application Android Kotlin native pour **découvrir les appareils Meshtastic en BLE** sans flasher les Heltec.

## État réel
- Inclus : permissions Android 12+, scan BLE filtré sur le service Meshtastic, affichage nom/adresse/RSSI, arrêt du scan.
- Non inclus : connexion GATT, SDK Meshtastic, lecture/envoi de messages, Bitchat, passerelle, chiffrement.
- Cette étape est volontairement limitée et vérifiable. Ne pas la présenter comme une passerelle fonctionnelle.

## Compiler
1. Ouvrir ce dossier avec Android Studio récent (JDK 17, Android SDK 35).
2. Laisser Gradle synchroniser et installer les dépendances.
3. Build > Build APK(s) puis installer sur Android 8.0+ (Android 12+ testé en priorité).
4. Activer le Bluetooth et Meshtastic sur le Heltec, autoriser les permissions, lancer la recherche.

## Suite
Ajouter un client GATT/SDK Meshtastic validé contre la version choisie, puis le moteur Bitchat et les règles de routage/chiffrement. Le service UUID est celui couramment documenté pour Meshtastic BLE ; vérifier qu'il correspond au firmware de l'appareil.

## Prudence
Aucune écriture en mémoire flash, aucune modification de firmware, aucune émission LoRa depuis cette application.

## Historique du projet

Consulter [CHANGELOG.md](CHANGELOG.md) pour les modifications, leurs raisons,
les validations réalisées, les limitations et les prochaines actions.
Chaque évolution doit y être consignée, sans secrets ni données privées.

## Build without a PC (GitHub Actions)
Upload the contents of this `HybridBridge` folder to the root of a GitHub repository.
Open Actions > Build HybridBridge APK > Run workflow. After completion,
download the artifact `HybridBridge-0.1-debug-apk`, extract it and install
`app-debug.apk` on Android. This debug build is for local testing only.
The debug compilation succeeded in GitHub Actions on 2026-11-06 and the APK
artifact was produced. The user reported successful installation and Heltec
discovery after a long wait. Detection latency still needs measurement; GATT
connection and messaging are not implemented. See [CHANGELOG.md](CHANGELOG.md).
