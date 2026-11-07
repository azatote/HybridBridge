# Decisions techniques

## 2026-11-07 - Licence

L'utilisateur a accepte GPLv3 avec sources publiques avant l'integration
des schemas Meshtastic. Le texte officiel est dans LICENSE et dans l'APK.
Les dependances conservent leurs licences ; les notices du runtime protobuf
sont embarquees. Les sources correspondantes doivent accompagner chaque build.

## 2026-11-07 - Protobuf 2.7.26

L'artefact publie org.meshtastic:protobufs:2.7.26 exige Kotlin 2.4.10.
Pour conserver Kotlin 2.0.21/AGP 8.7.3, generer les classes Java depuis
le commit des schemas du firmware 2.7.26 :
6b1ded439633cd03d4af85b44231b91d1d106278.

Protoc et runtime Java sont epingles a 3.25.5 ; les extensions nanopb
exigent les descripteurs Google. Ce choix est verifie par compilation CI,
pas par une hypothese sur les signatures des classes generees.

## 2026-11-07 - Jalon de validation avant le pont

La 0.4 prepare les messages de canal Meshtastic. Elle n'est pas l'application
finale du cahier des charges. Le retour utilisateur valide les echanges
Meshtastic aller-retour avec la 0.4.1 sur son installation. Bitchat officiel,
le pont bidirectionnel, l'E2EE, la persistance et le relais de fond ne sont
pas valides ni annonces comme livres. Aucun ecran simule de reseau connecte.

Le premier socle Bitchat reutilise le codec officiel v2.2.0 sans modification,
depuis son commit epingle. Il doit rester separe du transport et des sessions
tant que le moteur BLE n'est pas raccorde et teste sur un client officiel.

## 2026-11-07 - Endpoint BLE public de test

Le service mesh complet Bitchat depend du routage, de Noise et de services de
l'application officielle. La 0.5 ajoute un endpoint BLE public limite,
reutilisant codec, annonces et validation d'identite officiels v2.2.0.

Les annonces doivent etre signees par Ed25519 et leur identifiant lie a la
cle publique X25519 selon le contrat officiel. Les messages directs, opaques,
sessions Noise privees et transferts de fichiers restent non pris en charge.
Cet endpoint ne sera pas annonce comme un moteur mesh complet. Les echanges
avec le client officiel devront etre testes sur les deux telephones.

La cle locale est chargee/generee lors de l'activation explicite et protegee
au repos par Android Keystore/AES-GCM. L'annonce auto-signee ne remplace ni
l'authentification Noise complete ni une verification humaine des correspondants.
Le pont LoRa et les messages prives restent desactives.

## 2026-11-07 - Pont public Meshtastic vers Bitchat

La 0.6 vise le trajet Heltec -> LoRa -> Heltec -> HybridBridge -> Bitchat.
Il s'agit d'un pont unidirectionnel, sans transfert Bitchat -> LoRa.

Le canal est choisi explicitement, activation desactivee par defaut et
confirmation requise. Seuls les textes prefixes par `/hb` suivi d'un espace peuvent sortir du
canal. Pas de troncature, attribution obligatoire, deduplication persistante
de metadonnees sans contenu, quotas, expiration et arret hors premier plan.

Une signature APK et une liste de permissions justifiee ne garantissent pas
l'absence d'alerte Play Protect. Fournir les controles et le checksum, ne pas
contourner les protections, puis preparer une signature release stable avant
distribution finale. Voir SECURITY.md pour les limites exactes.

Le transport des messages est limite aux canaux actifs selectionnes et
aux broadcasts texte. Aucun message direct ou opaque ne doit etre exporte
vers un autre reseau. Une ecriture BLE ne vaut pas confirmation de livraison.

L'interface Android native existante est conservee pour ce jalon de protocole.
La migration Compose, la signature release et les tests Android 12 a 16
restent des exigences de la version finale, non couvertes par la compilation seule.
