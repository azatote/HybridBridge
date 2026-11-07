# Audit amont - connexion GATT 0.3

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

Client independant utilisant les API publiques Android BluetoothGatt.
Aucune source Bitchat/Meshtastic copiee, aucun SDK/protobuf integre ici.
Les UUID et le format du compteur sont les faits de protocole verifies.
L'audit des licences des SDK, de leurs notices et de leur compatibilite reste
obligatoire avant la future integration de dependances ou de code amont.

## Limites de validation

- Reference logicielle verifiee : 2.7.26 ; autres correctifs 2.7.x a tester.
- Pas de lecture de la version firmware : un service compatible ne certifie
  pas que l'appareil execute 2.7.x.
- Pas de demande de configuration protobuf, de synchronisation, de notifications
  FromNum, de messages, de reconnexion automatique ou de relais en fond.
- Android minimum 8 ; compileSdk/targetSdk 35. Android 12 a 16 sont les cibles
  de test, sans certification materielle dans cette intervention.
- GATT verifie est affiche seulement apres validation du service, des
  caracteristiques et lecture reussie d'un FromNum de 4 octets.
