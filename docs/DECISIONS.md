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
finale du cahier des charges. Les echanges sur deux noeuds, Bitchat officiel,
le pont bidirectionnel, l'E2EE, la persistance et le relais de fond ne sont
pas valides ni annonces comme livres. Aucun ecran simule de reseau connecte.

Le transport des messages est limite aux canaux actifs selectionnes et
aux broadcasts texte. Aucun message direct ou opaque ne doit etre exporte
vers un autre reseau. Une ecriture BLE ne vaut pas confirmation de livraison.

L'interface Android native existante est conservee pour ce jalon de protocole.
La migration Compose, la signature release et les tests Android 12 a 16
restent des exigences de la version finale, non couvertes par la compilation seule.
