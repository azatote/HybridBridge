# Audit amont - HybridBridge 0.6

## Pont public 0.6

- Les broadcasts Meshtastic deja decodes par le client autorise sont les seuls
  candidats ; le noeud local, les messages directs, ciphertexts et autres ports
  ne sont pas transferes. Canal choisi et prefixe /hb avec espace requis.
- Relais unidirectionnel vers le texte public Bitchat signe par HybridBridge.
  L'origine radio est une attribution declaree, pas une signature de l'auteur.
- Sans fragmentation : texte et attribution <=120 octets UTF-8, rejets visibles.
- Deduplication de 512 identifiants persiste sans contenu ; file RAM8, expiration30s,
  remise BLE limitee a une tentative/5s. Marque HB refusee en reinjection.
- Desactive par defaut, confirmation requise ; desactive a la deconnexion,
  au changement du noeud/canaux et au passage en arriere-plan.
- CI [42393495373](https://github.com/azatote/HybridBridge/actions/runs/42393495373) verte :
  au moins37 tests, licences, signature et permissions APK verifiees ; checksum publie.
- Pas de garantie antivirus/Play Protect ; distribution debug de test seulement.
  La signature release stable et le test physique du parcours restent attendus.
  Voir SECURITY.md pour les limites et la conduite en cas d'alerte Android.

## Preparation de l'application complete - 7 novembre 2026

- Le SDK officiel actuel depend des protobufs generes publies sur Maven Central.
  L'artefact publie 2.7.26 depend de Kotlin 2.4.10, plus recent que Kotlin 2.0.21
  du projet ; cet artefact n'est pas utilise dans l'application 0.4.
- Artefact verifie dans les metadonnees Maven Central :
  `org.meshtastic:protobufs:2.7.26` (egalement 2.7.25 disponible).
- Le SDK et le depot des schemas protobufs Meshtastic portent la licence GPLv3.
  Sources consultees : `meshtastic/meshtastic-sdk/LICENSE` et
  `meshtastic/protobufs/LICENSE` ; ces dependances ne sont pas encore integrees.
- L'utilisateur a approuve la distribution GPLv3 avec sources publiques.
  Le texte officiel est ajoute dans `LICENSE` et embarque dans l'APK.
- Bitchat Android est egalement declare GPL-3.0 par GitHub. Aucun moteur Bitchat
  n'est encore integre : licence seule ne prouve pas l'API ou la compatibilite.

## Protobuf et messagerie 0.4

- Schemas : commit `6b1ded439633cd03d4af85b44231b91d1d106278` du depot
  `meshtastic/protobufs`, submodule du firmware 2.7.26 verifie dans son arbre Git.
- Classes Java generees par protoc 3.25.5, plugin Gradle protobuf 0.9.4,
  runtime `com.google.protobuf:protobuf-java:3.25.5`.
- Les schemas contiennent des extensions d'options nanopb ; descriptor.proto
  officiel v25.5 est fourni comme import, sans regenerer les classes du runtime.
- Licence runtime : texte BSD officiel du depot protobuf v25.5, embarque sous
  `licenses/Protobuf-License.txt`. Les sources correspondantes sont publiees
  dans un artefact CI avec le build, les schemas et les references de dependances.
- `want_config_id` demande la configuration ; `config_complete_id` doit
  correspondre au nonce de la session. Le controleur PhoneAPI du firmware
  envoie MyInfo, OwnNodeInfo, Metadata, Channels avant ConfigComplete.
- Le noeud doit avoir un identifiant valide, un firmware commencant par 2.7.
  et au moins un canal actif. Les cles PSK ne sont ni affichees ni journalisees.
- Lectures/ecritures GATT serialisees ; timeout de chaque operation 15 secondes,
  synchronisation 90 secondes ; polling FromRadio toutes les secondes a vide.
- Messages `TEXT_MESSAGE_APP` uniquement, destination broadcast `0xffffffff`,
  canal choisi parmi les canaux actifs et confirmation avant envoi.
  Limite d'emission conservatrice : 180 octets UTF-8, MTU BLE negociee
  et intervalle de 15 secondes entre deux emissions utilisateur.
- Les messages directs, chiffres opaques, autres ports et UTF-8 invalide sont
  rejetes par l'affichage. Aucun transfert Bitchat ni export de contenu prive.
- Le statut Transmis au Heltec signifie ecriture GATT terminee, pas livraison
  radio ni reception par un correspondant.
- Historique en memoire borne a 300 messages, deduplication a 512 identifiants,
  file d'envoi bornee a 16 operations ; pas de persistance des messages.
- L'utilisateur confirme les echanges Meshtastic aller-retour sur son installation
  avec la 0.4.1. Bitchat, routage inter-reseaux, E2EE, reprise durable et service
  de fond restent non livres.

## Endpoint Bitchat BLE public 0.5

- Reference : Bitchat Android v2.2.0, commit 20501fdfe0d56c7705c377d4a263b01d8f38eeeb.
- Le service/characteristique/CCCD sont repris depuis AppConstants.Mesh.Gatt.
  BluetoothGattServerManager confirme READ/WRITE/WRITE_NO_RESPONSE/NOTIFY,
  annonce du service et scan-response avec peer ID sur 8 octets.
- BluetoothGattClientManager demande une MTU de 517 ; endpoint limite aux
  paquets entiers jusqu'a 512 octets et refuse les prepared writes/offsets.
- sendMessage officiel emet du texte UTF-8 dans MESSAGE broadcast. L'endpoint
  utilise ce format, les annonces TLV et le validateur officiels, sans formats supposes.
- Le peer ID correspond aux 8 premiers octets de SHA-256 de la cle publique
  X25519. Signatures Ed25519 sur toBinaryDataForSigning, padding canonique conserve.
- Capabilities.NONE annonce : pas de private media, groupes, bridge ou prekeys.
  Aucune session Noise privee n'est implementee ou contournee.
- Bouncy Castle bcprov-jdk18on 1.80, licence permissive officielle r1rv80 incluse
  dans l'APK. Identite chiffree au repos avec AES-GCM/Android Keystore et fichier atomique noBackup.
- Les annonces auto-signees et les cles memorisees pour la session ne constituent
  pas une authentification Noise complete ni une verification humaine de l'identite.
- Aucun texte/cle privee journalise par les nouvelles classes. Types Noise et
  fichiers ignores avant decodage ; messages adresses rejetes avant affichage.
  Les logs du codec examines portent sur tailles/erreurs.
- Interop physique et stockage Keystore a valider sur Samsung ; CI
  [42387811211](https://github.com/azatote/HybridBridge/actions/runs/42387811211) verte,
  au moins 28 tests et licences verifies. Notifications finies ne prouvent pas une lecture distante.
- Limites : serveur BLE public seulement, pas de scan/client GATT Bitchat,
  de relais, fragments, gossip sync, Noise prive, pont LoRa ou fonctionnement de fond.

## Premier socle de protocole Bitchat BLE

- Release stable retenue : v2.2.0, publiee le 28 octobre 2026, commit
  `20501fdfe0d56c7705c377d4a263b01d8f38eeeb`.
- Socle compile depuis six sources officielles inchangees : BinaryProtocol,
  CompressionUtil, DecompressionResourcePool, MessagePadding, PacketPayloadLimits
  et AppConstants. Kotlin Parcelize active pour le modele Parcelable officiel.
- Sources telechargees au build depuis le commit epingle, licence amont conservee
  et archive des sources correspondantes etendue a ces fichiers.
- Tests : aller-retour binaire, en-tete/payload tronques, paquet vide,
  conservation de la representation recue apres decompression/reencodage.
  Ces tests locaux ne prouvent pas un echange avec un client officiel.
- CI de ce socle : [42371877884](https://github.com/azatote/HybridBridge/actions/runs/42371877884),
  compilation et tests reussis ; au moins 21 tests sans echec/erreur verifies.
- Aucun demarrage du moteur Bitchat ni emission BLE ajoute dans cette etape.
  Le numero et le lien de l'APK de test materiel restent 0.4.1 ; pas de nouvelle
  version a installer tant que l'adaptateur BLE Bitchat n'est pas raccorde.
- Revision officielle inspectee : `2fc6f4747275bdbfec2288ac1a45f7d5bedcd9ac`
  du depot permissionlesstech/bitchat-android ; licence declaree GPL-3.0.
- BluetoothMeshService expose startServices/stopServices/sendMessage et depend
  de ses composants mesh, EncryptionService, Noise, GossipSync et services de routage.
- L'interface MeshService n'est pas a elle seule un SDK autonome : ses signatures
  referencent les modeles et etats de sessions internes.
- L'extraction doit conserver les contrats d'identite, d'annonce, de paquets
  et de securite. Une detection BLE seule ne valide pas la participation au mesh.

## Reference verifiee le 7 novembre 2026

- Firmware Meshtastic : tag `v2.7.26.54e0d8d`, commit
  `54e0d8d0ab2ff56b3a9ce967e53f79e49af560fb`.
- [UUID officiels](https://github.com/meshtastic/firmware/blob/54e0d8d0ab2ff56b3a9ce967e53f79e49af560fb/src/BluetoothCommon.h).
- [Caracteristiques, securite et batterie](https://github.com/meshtastic/firmware/blob/54e0d8d0ab2ff56b3a9ce967e53f79e49af560fb/src/nimble/NimbleBluetooth.cpp).

## Contrat utilise

| Element | UUID | Propriete utilisee |
| --- | --- | --- |
| Service Meshtastic | `6ba1b218-15a8-461f-9fa8-5dcae273eafd` | Decouverte |
| ToRadio | `f75c76d2-129e-4dad-a1dd-7866124401e7` | Presence et propriete WRITE, aucune ecriture |
| FromRadio | `2c55e69e-4993-11ed-b878-0242ac120002` | Presence et propriete READ, aucune consommation |
| FromNum | `ed9da18c-a800-4f66-a670-aa7547e34453` | Lecture de 4 octets, uint32 little-endian |
| Batterie | Service `0x180f`, caracteristique `0x2a19` | Lecture facultative de 1 octet, 0 a 100 % |

Le firmware encode FromNum avec `put_le32`. Ce compteur indique l'evolution
des donnees disponibles ; ce n'est ni une identite de noeud, ni une preuve
de reception de message ou de livraison LoRa.

Dans les modes avec PIN, les caracteristiques Meshtastic exigent chiffrement
et authentification. L'application utilise la gestion d'appairage Android,
ne collecte aucun PIN et ne contourne pas cette protection. En cas d'echec,
elle indique de verifier la demande systeme puis de reconnecter.

## Integration et licences

La version 0.3 utilisait un client independant sans schemas copies.
La version 0.4 ajoute les schemas et le runtime decrits plus haut, sous les
licences approuvees. L'audit des API et licences Bitchat reste a completer.

## Limites historiques de la version 0.3

- Reference logicielle verifiee : 2.7.26 ; autres correctifs 2.7.x a tester.
- Pas de lecture de la version firmware : un service compatible ne certifie
  pas que l'appareil execute 2.7.x.
- Pas de demande de configuration protobuf, de synchronisation, de notifications
  FromNum, de messages, de reconnexion automatique ou de relais en fond.
- Android minimum 8 ; compileSdk/targetSdk 35. Android 12 a 16 sont les cibles
  de test, sans certification materielle dans cette intervention.
- GATT verifie est affiche seulement apres validation du service, des
  caracteristiques et lecture reussie d'un FromNum de 4 octets.
