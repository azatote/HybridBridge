# HybridBridge 0.5 — test Bitchat BLE public

**[Télécharger l'APK Android 0.5 de test (ZIP)](https://github.com/azatote/HybridBridge/actions/runs/42387811211/artifacts/13580083731)**

[Sources correspondantes au build](https://github.com/azatote/HybridBridge/actions/runs/42387811211/artifacts/13580049074)
et [rapports de tests](https://github.com/azatote/HybridBridge/actions/runs/42387811211/artifacts/13580585698).

Connexion GitHub requise. Extraire le ZIP puis installer `app-debug.apk`.
Build debug pour test uniquement ; lien valable jusqu'au 5 février 2027,
sauf suppression anticipée de l'artefact.

Application Android Kotlin native pour **synchroniser un Heltec et tester les messages de canal Meshtastic**, sans flasher les appareils.
Version de développement : le pont Bitchat et le chiffrement privé ne sont pas encore livrés.

La 0.4 a été signalée comme se fermant au démarrage sur un Samsung Android 14.
La 0.4.1 remplace les anciens onglets et ajoute un diagnostic d'erreur pendant
la construction de l'écran. Démarrage et dessin des trois vues testés sous
Android 14/15 simulés. L'utilisateur confirme ensuite une ouverture sans crash
et une connexion Bluetooth OK sur son Samsung Android 14, puis la synchronisation
et un échange de messages aller-retour réussi sur son installation. La cause
exacte du crash 0.4 n'est pas démontrée ; ce test ne certifie pas tous les appareils.

La 0.5 ajoute un endpoint GATT public de test, fondé sur le codec, les annonces
et le validateur d'identité officiels Bitchat 2.2.0. La connexion et les échanges
avec le client officiel restent à valider sur les téléphones. Ce n'est pas un
moteur mesh complet : pas de sessions privées Noise, de fragmentation ou de relais.

## État réel

- Inclus : découverte et connexion BLE, génération protobuf officielle, synchronisation du nœud/firmware/canaux, batterie facultative, envoi et réception de textes de canal, historique en mémoire borné et licences.
- Bitchat : annonce du service BLE officiel, identité locale signée, serveur GATT et notifications publiques, contrôle des signatures et des identités annoncées. Identité locale chiffrée avec une clé Android Keystore dans un fichier exclu des sauvegardes.
- La vérification d'une annonce auto-signée ne prouve ni une session Noise authentifiée ni l'identité humaine du pair. Les clés des pairs sont mémorisées pour la session, pas vérifiées par un échange humain.
- Messages directs et opaques rejetés ; aucun relais inter-réseaux. Envois limités à 180 octets UTF-8, à la MTU négociée et à un message toutes les 15 secondes.
- Bitchat public de test : 120 octets UTF-8 par envoi, 2 secondes entre envois, MTU suffisante requise. Le mode BLE s'arrête lorsque l'application quitte le premier plan.
- Non inclus : moteur Bitchat complet, privés Noise, pont, E2EE natif, historique persistant, reconnexion automatique, service de fond et signature release.
- `Transmis au Heltec` signifie écriture GATT réussie, pas réception radio ou lecture par un correspondant.
- `Notifie au client BLE` signifie fin de l'opération de notification, pas affichage confirmé dans Bitchat officiel.
- Cette étape est volontairement limitée et vérifiable. Ne pas la présenter comme une passerelle fonctionnelle.

## Test Sur Téléphone

1. Installer l'APK 0.5. Vérifier d'abord l'ouverture et les vues Appareils, Messages, Bitchat et Etat, sans connecter le Heltec. Si un écran d'erreur apparaît, utiliser Partager le diagnostic. Si Android refuse la mise à jour pour signature différente, désinstaller l'ancien APK de test puis installer le nouveau ; les données locales de l'ancien seront perdues.
2. Allumer le Heltec, activer son Bluetooth et déconnecter Meshtastic officiel de ce nœud.
3. Accorder les permissions Appareils à proximité sur Android 12+ ; sur Android 8 à 11, autoriser la localisation et activer sa fonction système.
4. Garder HybridBridge au premier plan, rechercher et sélectionner le Heltec dans la liste. Utiliser le mode diagnostic si nécessaire ; un appareil BLE non identifié peut être rejeté après connexion.
5. Appuyer sur Connecter. Si Android demande l'appairage, saisir le PIN du Heltec uniquement dans la fenêtre système.
6. Attendre `Meshtastic synchronise ; firmware ...`. Dans Etat, vérifier le nom, le firmware 2.7.x et les canaux. Un firmware hors cible est refusé.
7. Dans Messages, choisir un canal de test actif, écrire un texte court, appuyer sur Envoyer puis confirmer. Cette action émet réellement sur LoRa selon la configuration existante du Heltec : vérifier région radio et canal autorisé avant le test.
8. Vérifier sa réception sur le second nœud/client Meshtastic configuré sur ce canal ; envoyer ensuite un texte depuis ce correspondant et vérifier son apparition dans HybridBridge. Attendre 15 secondes entre deux émissions HybridBridge.
9. Tester Déconnecter/Connecter. Relever versions exactes, délais et erreurs ; masquer adresses Bluetooth et messages privés avant partage. Aucun flashage n'est requis.

Si l'appairage ou la lecture échoue, noter le texte/code exact puis vérifier
la demande Android et reconnecter. L'historique n'est pas sauvegardé après fermeture de l'application.

## Test Bitchat Public

1. Téléphone A : HybridBridge 0.5. Téléphone B : Bitchat officiel 2.2.0, déjà installé. Aucun Heltec n'est nécessaire pour ce test BLE.
2. Activer le Bluetooth, autoriser les permissions et garder les deux applications au premier plan, à proximité. Utiliser la conversation publique du mesh BLE, pas un privé ni un canal Internet.
3. Dans HybridBridge, ouvrir Bitchat et appuyer sur Activer Bitchat BLE. Autoriser l'annonce Bluetooth si Android le demande.
4. Attendre la liaison et les annonces signées ; chercher HybridBridge parmi les pairs du client officiel. Si aucune liaison n'apparaît, noter le statut exact et vérifier le mode BLE et les permissions du client officiel.
5. Envoyer un texte public court depuis Bitchat officiel vers HybridBridge, puis depuis HybridBridge vers Bitchat officiel. Confirmer chaque émission et vérifier l'affichage réel de chaque côté, pas seulement le statut de notification.
6. Tester Arreter Bitchat BLE. Après retour d'arrière-plan, réactiver explicitement le mode de test.
7. Relever version exacte de Bitchat, Android, compteurs de liaisons/annonces et erreurs. Aucun privé, fichier, relais automatique ni transfert vers LoRa n'est pris en charge par cet endpoint.

## Compatibilité

Cibles : Android récents (priorité 12 à 16) et firmware Meshtastic 2.7.x.
Schémas épinglés au firmware 2.7.26 ; version installée lue pendant la
synchronisation. Minimum Android 8, compileSdk/targetSdk 35. Les échanges
matériels et la matrice Android restent à tester, pas à supposer réussis.
Voir [l'audit amont](docs/UPSTREAM_AUDIT.md).

## Compiler

1. Ouvrir ce dossier avec Android Studio récent (JDK 17, Android SDK 35).
2. Laisser Gradle synchroniser et installer les dépendances.
3. Exécuter `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` avec Gradle 8.10.2, ou Build > Build APK(s) dans Android Studio.
4. Activer le Bluetooth et Meshtastic sur le Heltec, autoriser les permissions, lancer la recherche.

## Suite

Les messages Meshtastic aller-retour sont confirmés sur l'installation de test.
Valider maintenant le public Bitchat sur les deux téléphones. Le mesh complet,
le pont public avec anti-boucles, les privés, la persistance, Compose, le fond
et la release exigent encore leurs implémentations et validations.

## Prudence

Aucune écriture en mémoire flash ni modification de firmware. Les messages
confirmés par l'utilisateur peuvent émettre sur LoRa via le Heltec. Aucun relais automatique.

## Licence

Projet GPLv3 avec sources publiques, choix approuvé le 7 novembre 2026.
Voir [LICENSE](LICENSE) et [les décisions](docs/DECISIONS.md).
Les dépendances conservent leurs licences ; les textes sont accessibles dans Etat > Licences.

## Historique du projet

Consulter [CHANGELOG.md](CHANGELOG.md) pour les modifications, leurs raisons,
les validations réalisées, les limitations et les prochaines actions.
Chaque évolution doit y être consignée, sans secrets ni données privées.

## Build without a PC (GitHub Actions)

Upload the contents of this `HybridBridge` folder to the root of a GitHub repository.
Open Actions > Build HybridBridge APK > Run workflow. After completion,
download the artifact `HybridBridge-0.5-debug-apk`, extract it and install
`app-debug.apk` on Android. This debug build is for local testing only.
The workflow runs unit tests, builds the APK, checks test totals and embedded
licenses, then publishes the APK, XML test results and corresponding source archive.
See [CHANGELOG.md](CHANGELOG.md) for the verified build reference and limitations.
