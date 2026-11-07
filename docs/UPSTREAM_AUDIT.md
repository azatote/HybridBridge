# Audit amont - Meshtastic 0.4

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
- La validation physique des echanges est encore attendue. Bitchat, routage
  inter-reseaux, E2EE, reprise durable et service de fond restent non livres.

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
