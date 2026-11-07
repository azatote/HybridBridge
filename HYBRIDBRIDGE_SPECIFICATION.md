# HybridBridge — Cahier des charges technique et plan de développement

> **Document de référence pour VS Code + GitHub Copilot**  
> Version documentaire : 1.0 — 6 novembre 2026  
> Statut : **spécification de conception**, et non affirmation qu'un APK ou qu'une passerelle opérationnelle existe déjà.  
> Langue de travail : français ; code, identifiants et protocoles : anglais.

---

## 0. Instructions impératives à GitHub Copilot

Tu es un ingénieur Android senior, spécialisé en Kotlin, Bluetooth Low Energy (BLE), réseaux mesh, protocoles binaires, sécurité et intégration Meshtastic. Construis **HybridBridge**, une application Android indépendante permettant de relier **le réseau Bitchat BLE mesh** et **le réseau Meshtastic LoRa mesh** par l'intermédiaire d'un téléphone Android, **sans modifier ni flasher les Heltec**.

**Règles de travail :**

1. Lis intégralement ce document avant de modifier le code. Considère-le comme la source de vérité pour les exigences produit ; vérifie les détails d'implémentation dans les sources et documentations officielles correspondant aux **versions exactes épinglées**.
2. **N'invente jamais une API, un package, une classe, une caractéristique GATT, une version de dépendance ou un format de paquet.** Consulte les sources officielles et compile pour vérifier. Si une API est incertaine, isole-la derrière une interface et laisse un `TODO(verify-upstream)` explicite.
3. Ne prétends pas que le pont Bitchat ↔ Meshtastic est fonctionnel tant que des tests bout en bout sur de vrais appareils ne le démontrent pas.
4. Avance par **petites étapes compilables**, avec tests et critères d'acceptation. Après chaque étape, fournis les fichiers modifiés, la commande de compilation, les résultats et les limitations.
5. N'altère jamais le firmware Meshtastic des Heltec. La connexion se fait par les interfaces client supportées par Meshtastic, principalement BLE ; USB pourra être ajouté ultérieurement.
6. N'expose jamais de clés privées, PSK de canaux, identifiants sensibles ou contenus privés dans les logs, captures, exports de diagnostic ou dépôts Git.
7. Les messages privés ne doivent jamais être déchiffrés puis réémis en clair sans consentement explicite, et ne doivent pas être annoncés comme interopérables entre clients officiels tant que cela n'est pas prouvé.
8. N'ajoute pas de mécanisme de contournement des protections d'Android ou des applications tierces. HybridBridge implémente ses propres clients/protocoles ; il ne lit pas clandestinement les données d'autres applications.
9. Vérifie les licences, notices et obligations de redistribution **avant** de copier du code Bitchat ou Meshtastic. Privilégie une dépendance autorisée, un sous-module documenté ou un adaptateur indépendant selon les licences réelles.
10. En cas de contradiction entre une hypothèse de ce document et les sources amont, consigne l'écart dans `docs/DECISIONS.md`, propose une solution et conserve la compilation.

### Livrables attendus

- Un dépôt Android Studio/VS Code Gradle **compilable** ; Kotlin et Jetpack Compose.
- Un APK `debug` installable sur le téléphone de test ; puis une procédure de signature `release`.
- Une application capable de découvrir un Heltec Meshtastic, de s'y connecter en BLE, de lire son état, puis d'envoyer/recevoir des messages de test.
- Un client BLE mesh Bitchat compatible avec les versions officielles visées, **à valider par tests réels**.
- Un routeur bidirectionnel, sûr, filtrable et résistant aux boucles.
- Une gestion explicite des messages publics, des messages privés opaques et des messages privés natifs HybridBridge.
- Des tests unitaires, d'intégration, des journaux expurgés, des scénarios matériels et une CI GitHub Actions produisant l'APK.

---

## 1. Vision du produit

### 1.1 Problème

Bitchat et Meshtastic permettent des échanges sans Internet, mais reposent sur des technologies et des protocoles différents :

- **Bitchat** : découverte et transport de messages par réseau Bluetooth entre téléphones compatibles.
- **Meshtastic** : transport de messages par radio LoRa entre nœuds compatibles ; un téléphone peut communiquer avec un nœud Meshtastic par BLE.

Un message sur un réseau n'est **pas automatiquement** lisible ou transmissible sur l'autre. HybridBridge doit fournir une **passerelle de niveau application**, pas une conversion radio magique.

### 1.2 Objectif principal

Permettre à un téléphone Android exécutant HybridBridge de :

1. Participer au réseau Bitchat BLE local, avec le protocole compatible choisi.
2. Se connecter à un Heltec exécutant Meshtastic par BLE.
3. Recevoir des messages autorisés de chaque réseau.
4. Les encapsuler/traduire dans un format de pont compact et versionné.
5. Les transmettre vers l'autre réseau en contrôlant taille, confidentialité, trafic, destination et boucles.
6. Déchiffrer les messages privés **uniquement aux extrémités autorisées** ; les relais ne doivent pas exiger l'accès au contenu privé.

### 1.3 Ce que le produit n'est pas

- Ce n'est **pas** un firmware alternatif pour Heltec.
- Ce n'est **pas** une modification obligatoire de Bitchat ou de Meshtastic officiels.
- Ce n'est **pas** une garantie que les applications officielles pourront lire les messages privés de l'autre protocole.
- Ce n'est **pas** un accès Internet, un serveur central ou un service cloud nécessaire au routage.
- Ce n'est **pas** un moyen de relayer automatiquement toutes les conversations et toutes les métadonnées des utilisateurs sans leur accord.

### 1.4 Priorités produit

1. Sécurité et absence de fuite de messages privés.
2. Fiabilité des connexions BLE et des transmissions.
3. Compatibilité avec les appareils existants, sans flash.
4. Faible occupation du canal LoRa et respect de la réglementation.
5. Utilisation simple depuis Android.
6. Fonctionnement hors ligne après installation/configuration.

---

## 2. Matériel et environnement cible

### 2.1 Matériel connu

- **Heltec WiFi LoRa 32 V4.3**, identifié par l'utilisateur comme `HTIR-WB32LAF V4.3`, avec firmware Meshtastic déjà présent.
- **Second Heltec de génération V3**, révision exacte **à confirmer** (V3/V3.2) ; firmware Meshtastic déjà présent.
- Téléphone Android Samsung ; version Android et comportement BLE précis à relever pendant les tests.
- Liaison USB-C déjà utilisée pour les diagnostics ; **le projet HybridBridge ne nécessite pas de flash USB**.

La version matérielle des Heltec est importante pour Meshtastic, mais HybridBridge doit utiliser les **interfaces logicielles Meshtastic** et ne doit pas coder de broches SX1262 ou de pilotes LoRa.

### 2.2 Prérequis

- Bluetooth activé, permissions Android accordées.
- Firmware Meshtastic fonctionnel et BLE activé sur le Heltec.
- Nœuds Meshtastic sur une région radio compatible (en France, paramètres régionaux EU_868 adaptés au matériel et à la réglementation).
- Pour tester une liaison distante complète : idéalement **deux téléphones Android et deux Heltec**, un ensemble à chaque extrémité.
- Les téléphones doivent pouvoir maintenir une connexion BLE avec leur Heltec et participer au BLE mesh Bitchat, sous réserve de leurs capacités et restrictions Android.

### 2.3 Contraintes matérielles et Android

- Un Heltec peut limiter les connexions BLE simultanées : Meshtastic officiel et HybridBridge ne doivent pas supposer un accès concurrent garanti.
- Un téléphone peut devoir fonctionner simultanément comme client GATT Meshtastic et comme participant BLE Bitchat ; tester les limites de publicité BLE, scan, GATT et économie d'énergie.
- Les scans BLE et le service de fond sont soumis aux restrictions Android et aux permissions selon le niveau d'API.
- Un service de premier plan avec notification persistante pourra être nécessaire pour un relais durable, avec déclaration correcte du type de service et respect des règles de la version Android ciblée.
- Les liens LoRa sont à faible débit, avec contraintes de temps d'antenne et de taille de paquets : les retransmissions doivent être strictement limitées.

### 2.4 Cibles de compatibilité retenues le 7 novembre 2026

- **Android récents** : priorité à Android 12 et plus, y compris les dernières
    versions stables. Prévoir une matrice Android 12, 13, 14, 15 et 16 pour les
    permissions, la découverte BLE, la connexion GATT et le fonctionnement en fond.
- Le minimum actuel Android 8 (`minSdk 26`) n'est pas relevé par cette décision.
    `compileSdk` et `targetSdk` sont actuellement 35 ; leur évolution doit être
    vérifiée avec les versions Gradle/AGP et les changements de comportement Android.
- **Meshtastic** : la famille de firmware cible est **2.7.x**, sans flashage
    imposé des Heltec. Relever le correctif exact et le suffixe de build de chaque
    appareil, puis vérifier les UUID, caractéristiques GATT et protobufs dans
    les sources officielles correspondant à ces versions.
- La version du firmware 2.7.x ne détermine pas le numéro de version du SDK
    Meshtastic : choisir et épingler une dépendance dont la compatibilité est vérifiée.
- Ces éléments sont des **exigences de compatibilité**, pas une certification
    acquise. Consigner les versions réellement testées et les résultats dans le
    journal ; ne pas généraliser une détection réussie à toute la famille 2.7.x.

---

## 3. Architecture réseau et scénarios

### 3.1 Vue d'ensemble

```text
   ZONE A / BLE LOCAL                          ZONE B / BLE LOCAL

 Bitchat A1 ──BLE── Bitchat A2              Bitchat B1 ──BLE── Bitchat B2
                       │                                      │
                 HybridBridge A                         HybridBridge B
                   │         │                            │         │
          moteur Bitchat    BLE GATT              BLE GATT   moteur Bitchat
                             │                            │
                        Heltec V4.3 ── LoRa mesh ── Heltec V3
                          Meshtastic                 Meshtastic

      Aucun Internet requis pour le transport des messages.
      Les Heltec conservent leur firmware Meshtastic.
```

### 3.2 Parcours d'un message public Bitchat → Meshtastic

1. Un participant Bitchat émet un message public.
2. Le moteur Bitchat intégré à HybridBridge A le reçoit comme participant autorisé du réseau.
3. Un filtre vérifie si ce message appartient au périmètre du pont : canal autorisé, taille acceptable, type pris en charge, consentement et absence de doublon.
4. Le routeur crée une enveloppe de pont avec un identifiant stable, une origine, un identifiant de passerelle et un nombre de relais autorisés.
5. L'adaptateur Meshtastic encode une charge utile compatible, choisit un canal Meshtastic dédié et transmet via le Heltec A.
6. Les nœuds Meshtastic acheminent le paquet selon leur propre fonctionnement.
7. HybridBridge B reçoit le message, valide l'enveloppe, vérifie son historique et les règles de sécurité.
8. Le moteur Bitchat B émet une représentation compatible dans le réseau Bitchat local.

**Limite** : la représentation d'un message d'origine peut différer selon les champs que les clients Bitchat officiels permettent de renseigner. Ne pas usurper l'identité d'un utilisateur ; afficher une attribution explicite au pont si nécessaire.

### 3.3 Parcours Meshtastic → Bitchat

Le processus est symétrique pour les messages provenant de canaux Meshtastic **explicitement autorisés**. Le pont ne doit pas exporter aveuglément tous les canaux ou toutes les communications locales.

### 3.4 Parcours privé de bout en bout

Deux stratégies doivent être étudiées séparément :

**A. Transport opaque de messages privés Bitchat**

- Objectif : transporter les **octets chiffrés d'origine**, sans déchiffrement par le relais.
- Condition : comprendre le protocole Bitchat exact, son routage, son adressage, ses sessions et la possibilité d'injecter une enveloppe reçue d'un autre transport.
- Risque : une session privée Bitchat peut dépendre d'une présence ou d'un échange de clés spécifique au mesh BLE ; le simple transport d'un ciphertext ne garantit pas sa lisibilité par le destinataire.
- Statut : **recherche et preuve de concept**, pas une capacité acquise.

**B. Messages privés natifs HybridBridge (recommandé pour un MVP sécurisé)**

- Les deux correspondants utilisent HybridBridge et échangent des identités/clefs de manière authentifiée.
- Le message est chiffré sur le téléphone de l'émetteur et déchiffré uniquement sur le téléphone du destinataire.
- Les réseaux Bitchat et Meshtastic transportent des enveloppes opaques ; les relais n'ont pas les clefs privées.
- Les clients officiels Bitchat/Meshtastic ne sont **pas supposés** afficher ces messages privés en clair.
- Les métadonnées nécessaires au routage peuvent rester visibles ; les minimiser.

### 3.5 Scénarios d'usage

- Deux téléphones proches communiquent via Bitchat sans LoRa.
- Un téléphone HybridBridge communique avec son Heltec et un autre nœud Meshtastic.
- Deux zones Bitchat séparées communiquent via deux passerelles et un chemin LoRa.
- Un message public autorisé est transféré de Bitchat vers un canal Meshtastic dédié.
- Un message privé HybridBridge traverse BLE + LoRa sans déchiffrement par les relais.
- Une passerelle se déconnecte ; les messages expirent ou attendent selon la politique de file d'attente, sans boucle ni émission infinie.

---

## 4. Compatibilité amont et recherche préalable obligatoire

### 4.1 Dépôts de référence

- Bitchat Android : <https://github.com/permissionlesstech/bitchat-android>
- Organisation/projet Meshtastic : <https://github.com/meshtastic>
- SDK Meshtastic (vérifier dépôt actif, versions, packages et API réelles) : <https://github.com/meshtastic/meshtastic-sdk>
- Application Meshtastic Android : <https://github.com/meshtastic/Meshtastic-Android>
- Documentation Meshtastic : <https://meshtastic.org/docs/>

**Ces liens sont des points de départ, pas une preuve que les signatures de méthodes évoquées dans des échanges antérieurs existent toujours.**

### 4.2 Audit technique avant toute intégration

Créer `docs/UPSTREAM_AUDIT.md` avec :

| Sujet | Questions à résoudre |
|---|---|
| Bitchat Android | Licence actuelle ? Architecture BLE ? API d'intégration ? Format des messages ? Mécanisme de relais ? Versions compatibles ? |
| Bitchat privé | Protocole de chiffrement exact ? Sessions ? Format des enveloppes ? Injection de messages opaques possible ? |
| Meshtastic SDK | Dépôt et artefacts actuels ? Versions stables ? API BLE ? Méthodes d'envoi/réception ? Exemples compilables ? |
| Meshtastic BLE | UUID du service et caractéristiques officiels ? Pairing ? Limites GATT ? Reconnexion ? |
| Meshtastic messages | `MeshPacket`, `Data`, `PortNum`, canaux, limites payload, IDs et ACK : règles actuelles ? |
| Licences | Copie/liaison de code permise ? Obligations de redistribution et notices ? |
| Android | Version min/target SDK, permissions, restrictions BLE et service en arrière-plan ? |

**Décision d'architecture obligatoire :** si Bitchat ne fournit pas de bibliothèque stable, choisir entre (a) extraction/licence conforme d'un module, (b) fork documenté, (c) réimplémentation compatible testée sur des clients officiels. Ne pas appeler « compatible » un simple protocole maison.

---

## 5. Modules logiciels

```text
HybridBridge/
├── README.md
├── HYBRIDBRIDGE_SPECIFICATION.md
├── LICENSE                          # À choisir après audit licences
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/...
├── .github/workflows/android-build.yml
├── docs/
│   ├── UPSTREAM_AUDIT.md
│   ├── DECISIONS.md
│   ├── SECURITY.md
│   ├── PROTOCOL.md
│   ├── TEST_PLAN.md
│   └── RELEASE.md
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/.../hybridbridge/
│           ├── MainActivity.kt
│           ├── HybridBridgeApplication.kt
│           ├── service/BridgeForegroundService.kt
│           ├── ui/screens/{Dashboard,Devices,Chat,Channels,Settings,Diagnostics}Screen.kt
│           ├── ui/viewmodel/
│           └── di/
├── core/
│   └── src/main/kotlin/.../
│       ├── model/{BridgeEnvelope,BridgeIdentity,BridgePolicy}.kt
│       ├── routing/{BridgeRouter,DeduplicationStore,RoutePolicy}.kt
│       ├── codec/{BridgeCodec,Fragmenter,Reassembler}.kt
│       ├── transport/MeshTransport.kt
│       └── security/{IdentityStore,PrivateMessageCrypto}.kt
├── transport-meshtastic/
│   └── src/main/kotlin/.../
│       ├── MeshtasticBleScanner.kt
│       ├── MeshtasticClientAdapter.kt
│       └── MeshtasticPacketMapper.kt
├── transport-bitchat/
│   └── src/main/kotlin/.../
│       ├── BitchatClientAdapter.kt
│       └── BitchatPacketMapper.kt
└── storage/
    └── src/main/kotlin/.../
        ├── BridgeDatabase.kt
        ├── MessageDao.kt
        ├── DeduplicationDao.kt
        └── OutboxDao.kt
```

L'arborescence est une **cible modulaire**. Pour accélérer le MVP, il est acceptable de commencer avec un seul module `app`, tant que les interfaces métier restent découplées. Ne pas créer des modules vides uniquement pour respecter le schéma.

### 5.1 `app`

- Point d'entrée Android, navigation Compose, permissions, notifications.
- Démarrage/arrêt explicite du pont.
- Présentation des états de connexion, des canaux et des erreurs.
- Aucun traitement réseau complexe dans `Activity` ou `Composable`.

### 5.2 `core`

- Modèle indépendant des SDK.
- Validation et routage des messages.
- Déduplication, TTL/hop limit, filtrage, politiques de confidentialité.
- Codec binaire, fragmentation/reconstitution, suivi des transmissions.
- Interfaces testables par des implémentations fictives.

### 5.3 `transport-meshtastic`

- Découverte BLE du Heltec.
- Connexion, synchronisation, état du nœud, canaux autorisés.
- Écoute des messages entrants et envoi de paquets supportés.
- Reconnexion avec backoff, événements d'erreur, gestion de la concurrence.
- Ne jamais présumer que le SDK permet un type de message sans vérification.

### 5.4 `transport-bitchat`

- Découverte et participation BLE mesh Bitchat.
- Traduction des enveloppes Bitchat ↔ modèle commun.
- Respect des identités, des mécanismes de chiffrement et des règles de routage Bitchat.
- Séparation claire entre les messages publics, les messages privés et les messages de contrôle.

### 5.5 `storage`

- Room/SQLite pour messages, historique minimal, file d'attente, déduplication persistante et configuration.
- Les secrets ne doivent pas être enregistrés en clair dans la base.
- Limites de conservation et fonction de suppression des données.

---

## 6. Contrats Kotlin recommandés

> Ces interfaces sont **notre propre API interne**, pas des signatures promises par les SDK externes.

```kotlin
package org.hybridbridge.core

import kotlinx.coroutines.flow.Flow

enum class NetworkKind { BITCHAT, MESHTASTIC }
enum class PrivacyKind { PUBLIC, PRIVATE_OPAQUE, PRIVATE_HYBRID }
enum class DeliveryState { QUEUED, SENT_TO_TRANSPORT, ACKNOWLEDGED, FAILED, EXPIRED }

data class BridgeEnvelope(
    val protocolVersion: Int,
    val messageId: ByteArray,           // 16 octets générés aléatoirement
    val originNetwork: NetworkKind,
    val originGatewayId: ByteArray,
    val senderAlias: String?,
    val destinationId: ByteArray?,
    val privacy: PrivacyKind,
    val contentType: Int,
    val payload: ByteArray,
    val hopLimit: Int,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long
)

sealed interface TransportState {
    data object Disconnected : TransportState
    data object Connecting : TransportState
    data class Connected(val peerLabel: String?) : TransportState
    data class Error(val reason: String) : TransportState
}

interface MeshTransport {
    val kind: NetworkKind
    val state: Flow<TransportState>
    val incoming: Flow<BridgeEnvelope>
    suspend fun connect()
    suspend fun send(envelope: BridgeEnvelope): Result<Unit>
    suspend fun disconnect()
}
```

**Attention Kotlin** : les `ByteArray` dans les `data class` ne bénéficient pas d'une égalité de contenu avec `==`. Pour les identifiants de déduplication, utiliser un type immuable normalisé (`UUID`, chaîne hexadécimale contrôlée ou wrapper avec `contentEquals` / `contentHashCode`). Valider toutes les longueurs et ne jamais conserver une référence mutable non copiée.

### 6.1 Interface de routage

```kotlin
interface BridgePolicy {
    fun mayForward(message: BridgeEnvelope, to: NetworkKind): Boolean
}

interface DeduplicationStore {
    suspend fun seen(messageId: ByteArray): Boolean
    suspend fun markSeen(messageId: ByteArray, expiresAtEpochMs: Long)
}

interface BridgeCodec {
    fun encode(message: BridgeEnvelope): ByteArray
    fun decode(bytes: ByteArray): Result<BridgeEnvelope>
}
```

**Important** : `seen` puis `markSeen` séparés ne sont pas atomiques. La vraie implémentation doit offrir une opération **atomique** `markIfNew(...)` pour éviter les doublons sous concurrence.

---

## 7. Protocole d'interconnexion HybridBridge (HBP/1)

### 7.1 Principe

Créer une **enveloppe binaire compacte** transportée à l'intérieur d'un message Meshtastic autorisé et d'une représentation compatible Bitchat. Le protocole doit être **versionné**, vérifié et documenté dans `docs/PROTOCOL.md` avant implémentation définitive.

Ne pas supposer que les octets de Bitchat peuvent être envoyés tels quels sur LoRa : la taille, les métadonnées et les contraintes de routage diffèrent.

### 7.2 Proposition de champs logiques

| Champ | Rôle | Exigence |
|---|---|---|
| `magic` | Reconnaître une enveloppe HybridBridge | Constante courte documentée |
| `version` | Version du protocole | Refuser ou négocier les versions inconnues |
| `flags` | Public/privé, fragment, ACK logique | Valeurs strictement définies |
| `messageId` | Déduplication inter-réseaux | Aléatoire, stable de bout en bout |
| `gatewayId` | Identifier le pont d'origine | Pseudonyme stable ou rotatif selon politique |
| `destination` | Routage éventuel vers destinataire | Minimiser les données identifiantes |
| `hopLimit` | Prévenir les boucles | Décrémenté à chaque traversée de pont |
| `expiry` | Expiration du message | Rejet après expiration |
| `fragmentInfo` | Reconstitution de messages longs | Nombre/indice/ID, quotas stricts |
| `payload` | Texte ou ciphertext | Borné en taille, jamais journalisé en privé |
| `auth` | Authentification si nécessaire | Défini par mode de sécurité |

### 7.3 Règles de routage

1. Ne relayer que les canaux **explicitement autorisés** par l'utilisateur.
2. Ne jamais relayer automatiquement les messages de découverte, présence, télémétrie, administration, configuration ou contrôle des applications.
3. Ne jamais relayer un paquet identifié comme provenant de HybridBridge vers son réseau d'origine sans vérification de l'historique et de la politique.
4. Décrémenter `hopLimit` à chaque **passage de passerelle** ; distinguer ce compteur du nombre de sauts natif Bitchat/Meshtastic.
5. Déduplication persistante et atomique, avec expiration et quotas.
6. Ne pas prétendre que l'ACK du Heltec prouve une livraison à l'utilisateur final.
7. Introduire un budget de débit : file bornée, priorité raisonnable, backpressure, délai et expiration.
8. Ne pas réémettre à l'infini en cas de déconnexion.
9. Afficher la provenance et le caractère relayé d'un message ; éviter l'usurpation d'identité.
10. Pour la première version, limiter à un canal de pont de test et aux messages courts.

### 7.4 Taille des messages et fragmentation

- Mesurer les **limites réelles** de payload des versions Meshtastic utilisées, en tenant compte des en-têtes et du port applicatif.
- Fixer une taille maximale de message utilisateur, une taille maximale d'enveloppe et un nombre maximal de fragments ; documenter les valeurs après mesures.
- La fragmentation doit porter un identifiant, un index, un total, un délai d'expiration et une limite mémoire.
- Protéger contre fragments dupliqués, malformés, trop nombreux ou hors ordre.
- Éviter les gros messages, pièces jointes et transferts de fichiers sur LoRa dans le MVP.
- Évaluer l'impact du temps d'antenne et de la réglementation avant d'autoriser la fragmentation sur le réseau réel.

### 7.5 Compatibilité des messages Meshtastic

- Utiliser un **canal dédié** HybridBridge pour le tunnel, avec paramètres convenus sur les nœuds concernés.
- Vérifier le `PortNum` ou mécanisme d'application approprié dans la version Meshtastic ciblée. Ne pas réutiliser arbitrairement un port réservé.
- Les messages de texte destinés à être lisibles dans Meshtastic officiel peuvent être envoyés comme texte avec préfixe d'origine explicite, dans les limites de la compatibilité vérifiée.
- Les enveloppes privées binaires peuvent ne pas être visibles comme messages lisibles dans les applications officielles : c'est normal et doit être indiqué dans l'interface.

---

## 8. Confidentialité et cryptographie

### 8.1 Objectifs de sécurité

- Confidentialité du contenu des messages privés de bout en bout.
- Authenticité des correspondants et protection contre la substitution de clés.
- Intégrité et anti-rejeu des enveloppes privées.
- Minimisation des métadonnées et des journaux.
- Aucun accès aux secrets d'autres applications sans mécanisme explicitement supporté et autorisé.

### 8.2 Stratégie de chiffrement privé HybridBridge

Pour le mode privé natif, utiliser une **bibliothèque cryptographique auditée** et un protocole documenté, pas un assemblage improvisé. Étudier une solution éprouvée de type Noise ou protocole de messagerie E2EE adapté aux contraintes hors ligne. Si un schéma X25519 + HKDF + AEAD est retenu, il faut spécifier entièrement :

- authentification des identités et comparaison des empreintes ;
- génération, échange et renouvellement des clés ;
- nonces uniques et contexte associé (`AAD`) ;
- anti-rejeu et persistance des compteurs ;
- gestion des changements de clés, pertes de téléphone et révocations ;
- stockage sécurisé des secrets (Android Keystore pour les clés prises en charge, protection des secrets logiciels) ;
- politique de sauvegarde/restauration ;
- résistance aux erreurs de désynchronisation et aux retransmissions.

**Ne pas affirmer une sécurité équivalente à Signal/Noise sans audit du protocole complet.**

### 8.3 Messages privés Bitchat existants

- Les sessions privées Bitchat doivent rester privées ; ne pas les convertir automatiquement en texte Meshtastic.
- Le relais opaque n'est permis que si le protocole Bitchat supporte la réception/réémission des messages chiffrés sans briser la session ni falsifier l'identité.
- Si cette condition n'est pas démontrée, afficher clairement **« transfert de messages privés Bitchat non pris en charge »** et proposer le mode privé HybridBridge.

### 8.4 Autorisations de transfert

- Paramètres **désactivés par défaut** pour le transfert privé inter-réseaux.
- Choix des canaux publics autorisés.
- Choix des correspondants et de la portée du relais privé.
- Indicateur visible quand le pont fonctionne.
- Bouton d'arrêt immédiat.

### 8.5 Modèle de menace minimal

Documenter et tester : écoute radio, appareil non autorisé, paquet forgé, rejeu, duplication, injection de fragments, déni de service par trafic, perte/vol du téléphone, fuite des logs, mauvaise configuration de canal, métadonnées révélatrices, boucle de routage, collision d'identifiants et fausse confirmation de livraison.

---

## 9. Bluetooth et Android

### 9.1 Meshtastic BLE

- Découverte des appareils annonçant le service Meshtastic selon la documentation officielle de la version ciblée.
- Affichage : nom, adresse/identifiant si accessible, RSSI, état de connexion.
- Connexion GATT et synchronisation initiale.
- Réception d'événements et de messages.
- Envoi d'un texte test sur un canal autorisé.
- Déconnexion propre et reconnexion avec backoff exponentiel borné.
- Traitement des permissions refusées et du Bluetooth désactivé.

### 9.2 Bitchat BLE

- Participation réelle au mesh Bitchat via les primitives et formats officiels vérifiés.
- Scan, advertising et connexions selon le protocole Bitchat actuel.
- Compatibilité testée avec l'application officielle sur un **autre téléphone**.
- Prise en charge des messages publics en premier.
- Ne pas se limiter à un scanner générique BLE : découvrir un appareil n'est pas participer au mesh.

### 9.3 Permissions Android

Adapter le manifeste et les demandes d'autorisations à `minSdk` et `targetSdk` réellement retenus :

- Android 12+ : `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, éventuellement `BLUETOOTH_ADVERTISE` selon l'implémentation Bitchat.
- Versions antérieures : permissions Bluetooth et éventuellement localisation selon les règles applicables.
- Notifications Android 13+ si requises.
- Foreground service et permissions/type de service adaptés aux exigences Android en vigueur.

Ne pas ajouter `neverForLocation` sans vérifier ses effets possibles sur la visibilité des balises recherchées. Ne pas réclamer la localisation précise si elle n'est pas nécessaire.

### 9.4 Gestion d'énergie

- Relais activable/désactivable explicitement.
- Notification persistante en fonctionnement si nécessaire.
- Scan BLE optimisé et non permanent à pleine puissance.
- Politique de reprise après suspension/reconnexion.
- Mesures de consommation sur plusieurs heures.

---

## 10. Interface utilisateur

### 10.1 Navigation principale

1. **Tableau de bord** : état Bitchat, état Meshtastic, statut du pont, nombre de messages relayés, erreurs récentes.
2. **Appareils** : scan BLE, liste des Heltec, connexion/déconnexion, nom du nœud, signal, batterie si disponible.
3. **Messages** : messages publics compatibles et conversations privées HybridBridge, avec provenance et état de livraison honnête.
4. **Canaux et routage** : sélection explicite des canaux Bitchat et Meshtastic autorisés, direction du relais, taille et quotas.
5. **Sécurité** : identité, vérification des correspondants, clés, autorisations de relais privé.
6. **Diagnostics** : logs expurgés, versions, erreurs, tests de connectivité, export volontaire sans secrets.

### 10.2 États à afficher

- Bluetooth désactivé / permissions manquantes.
- Scan en cours / aucun Heltec trouvé.
- Connexion Meshtastic / synchronisation / connecté / erreur.
- Moteur Bitchat initialisé / actif / indisponible.
- Pont désactivé / activé / dégradé.
- Message en attente / transmis au transport / confirmé si preuve disponible / expiré / erreur.

### 10.3 UX de première installation

1. Présentation du fonctionnement hors ligne et des limites.
2. Demande progressive des permissions.
3. Détection du Heltec et connexion.
4. Sélection du canal de test.
5. Envoi d'un message Meshtastic test.
6. Activation ultérieure du mesh Bitchat et du pont.
7. Validation explicite de tout transfert de messages privés.

---

## 11. Stockage, observabilité et fiabilité

### 11.1 Données locales

- Configuration utilisateur et canaux autorisés.
- Historique minimal des messages selon durée de conservation configurable.
- File d'attente bornée des transmissions non terminées.
- Cache de déduplication persistant avec expiration.
- Métadonnées de connexion, sans secrets.

### 11.2 Journalisation

- Logs structurés et niveaux `ERROR`, `WARN`, `INFO`, `DEBUG`.
- Jamais de contenu privé, clés, PSK ou enveloppes chiffrées complètes dans les logs de production.
- Identifiants pseudonymisés ou tronqués si nécessaire.
- Export des diagnostics sur action volontaire de l'utilisateur.

### 11.3 Résilience

- Reconnexion BLE avec temporisation et plafond de tentatives.
- Gestion de la perte de connexion en cours de transmission.
- Idempotence du traitement.
- Files bornées et stratégie de rejet explicite.
- Pas de promesse de livraison garantie sans mécanisme d'ACK applicatif testé.

---

## 12. Phases de développement et critères d'acceptation

### Phase 0 — Audit, squelette et CI

**Objectif :** obtenir un projet reproductible et compilable.

Tâches :

- Auditer versions, licences et interfaces Bitchat/Meshtastic.
- Créer un projet Kotlin + Compose avec versions Gradle/AGP/Kotlin cohérentes.
- Définir les interfaces de transport et les faux adaptateurs pour tests.
- Ajouter `./gradlew test` et `./gradlew assembleDebug`.
- Ajouter GitHub Actions avec JDK/Android SDK compatibles, cache Gradle et publication de l'APK debug en artefact.
- Ajouter `.gitignore` pour secrets, keystores et fichiers locaux.

**Acceptation :** build vert dans GitHub Actions ; APK installable ; écran d'accueil et états fictifs explicitement signalés.

### Phase 1 — Meshtastic BLE réel

**Objectif :** communiquer avec le Heltec V4.3 sans flash.

Tâches :

- Permissions BLE.
- Scan et sélection du Heltec.
- Connexion via API officielle vérifiée.
- Lecture de l'état du nœud.
- Envoi et réception d'un message de test sur canal choisi.
- Reconnexion et gestion d'erreurs.

**Acceptation :** un message envoyé par HybridBridge est reçu par un autre client/nœud Meshtastic, et inversement, sur de vrais appareils. Conserver captures et logs expurgés.

### Phase 2 — Bitchat BLE réel

**Objectif :** échanger avec un Bitchat officiel sur un deuxième téléphone.

Tâches :

- Intégrer un moteur Bitchat conforme à la licence.
- Découverte, participation au mesh et routage de base.
- Réception/émission de messages publics.
- Tests de compatibilité de versions.

**Acceptation :** un message public traverse Bitchat officiel ↔ HybridBridge, dans les deux sens, sans Internet.

### Phase 3 — Pont public Bitchat ↔ Meshtastic

**Objectif :** transporter les messages publics autorisés.

Tâches :

- Définir/implémenter HBP/1.
- Sélection des canaux et règles de transfert.
- Encodage compact, taille maximale et éventuelle fragmentation bornée.
- Déduplication persistante et TTL.
- File d'attente et limitation du trafic.
- Provenance visible des messages.

**Acceptation :** message public Bitchat A → HybridBridge A → Heltec A → LoRa → Heltec B → HybridBridge B → Bitchat B, puis test inverse ; aucune boucle après retransmission.

### Phase 4 — Messages privés HybridBridge

**Objectif :** E2EE natif entre correspondants HybridBridge.

Tâches :

- Protocole de sécurité audité et bibliothèque adaptée.
- Gestion des identités, échange et vérification des clés.
- Chiffrement/déchiffrement uniquement aux extrémités.
- Transport opaque et protection anti-rejeu.
- Tests de sécurité et de perte/reprise de connexion.

**Acceptation :** deux téléphones HybridBridge échangent des messages privés via LoRa ; un relais et les logs ne peuvent pas lire le contenu ; le destinataire vérifie l'identité de l'émetteur.

### Phase 5 — Étude de compatibilité des privés Bitchat

**Objectif :** déterminer ce qui est techniquement possible avec les sessions privées Bitchat officielles.

Tâches :

- Documenter le modèle de sessions privées Bitchat.
- Évaluer le transport opaque de ciphertext sans modifier le chiffrement.
- Tester la réception par le client officiel.
- Documenter les incompatibilités et les risques de métadonnées.

**Acceptation :** démonstration reproductible **ou** limitation formalisée et fonction désactivée. Ne jamais simuler une réussite.

### Phase 6 — Stabilisation et distribution

**Objectif :** application utilisable durablement.

Tâches :

- Service de fond conforme Android.
- Tests batterie, BLE simultané et radio sur plusieurs heures.
- Gestion des mises à jour et migration des données.
- Signature release et instructions d'installation.
- Vérification des licences et notices de redistribution.

**Acceptation :** build reproductible, tests documentés, pas de crash majeur sur les scénarios de référence, limites connues publiées.

---

## 13. Plan de tests détaillé

### 13.1 Tests unitaires

- Encodage/décodage HBP/1, version inconnue, données tronquées, tailles invalides.
- Identifiants, doublons, expiration, TTL, ordre de réception.
- Filtrage des canaux et du privé.
- Fragmentation/reconstitution : perte, doublon, hors ordre, quotas.
- Routage concurrent : `markIfNew` atomique.
- File pleine, backpressure, erreurs de transport.
- Aucun contenu privé dans les logs.

### 13.2 Tests d'intégration simulés

- Deux transports fictifs connectés au routeur.
- Bitchat → Meshtastic et inversement.
- Deux passerelles simulées et prévention des boucles.
- Reconnexion, échec d'envoi, expiration, reprise.
- Chiffrement/déchiffrement entre deux identités de test, sans secrets dans les traces.

### 13.3 Tests matériels

| Test | Matériel | Résultat attendu |
|---|---|---|
| Découverte Heltec | Android + V4.3 | Appareil visible et sélectionnable |
| Connexion Meshtastic | Android + V4.3 | Session BLE stable |
| Message Meshtastic | Android + V4.3 + V3 | Message envoyé/reçu |
| Découverte Bitchat | 2 Android | Participation au mesh confirmée |
| Pont public complet | 2 Android + 2 Heltec | Message traverse les deux réseaux |
| Pont privé HybridBridge | 2 Android + 2 Heltec | Contenu E2EE et destinataire authentifié |
| Réseau indisponible | Android en mode avion, BLE réactivé si nécessaire | Aucun Internet requis pour les transports locaux |
| Perte BLE | 1 Android + 1 Heltec | Reconnexion et absence de boucle |
| Batterie et durée | Android + Heltec | Mesures documentées, comportement stable |

**Remarque :** le mode avion peut couper Bluetooth par défaut ; le réactiver manuellement et documenter le protocole de test.

### 13.4 Mesures

- Taux de livraison par direction.
- Latence médiane et percentiles, sans promesse de temps réel.
- Nombre de doublons supprimés.
- Messages rejetés par règle et taille.
- Temps d'antenne LoRa estimé/mesuré selon le matériel.
- Consommation batterie et stabilité de connexion.

---

## 14. CI/CD GitHub Actions et VS Code

### 14.1 Développement

- Éditeur : VS Code avec extensions Kotlin/Gradle pertinentes et GitHub Copilot.
- Build officiel : Gradle Wrapper du dépôt.
- JDK : version compatible avec l'AGP retenu, vérifiée lors de l'audit.
- SDK Android : installé/configuré dans l'environnement de build.
- Dépendances épinglées ; pas de versions dynamiques (`+`) en production.
- Toute contribution doit passer les tests et la compilation.

### 14.2 Workflow GitHub Actions attendu

Créer `.github/workflows/android-build.yml` avec :

- Déclenchement `push`, `pull_request` et `workflow_dispatch`.
- Checkout du dépôt.
- Installation du JDK compatible.
- Préparation Android SDK si nécessaire.
- Exécution `./gradlew --no-daemon testDebugUnitTest assembleDebug` (adapter la tâche aux modules réels).
- Publication de `app/build/outputs/apk/debug/*.apk` comme artefact.
- Permissions GitHub minimales (`contents: read`).
- Échec explicite si aucun APK n'est produit.

### 14.3 Installation sur téléphone

1. Ouvrir l'exécution GitHub Actions réussie.
2. Télécharger l'artefact APK.
3. Extraire le ZIP d'artefact si nécessaire.
4. Installer l'APK depuis Android en autorisant temporairement l'installation depuis la source choisie.
5. Accorder les permissions Bluetooth requises.
6. Tester la découverte du Heltec.

Pour une diffusion durable, utiliser une clé de signature release gérée de manière sécurisée, **jamais committée** dans Git.

---

## 15. Risques et points bloquants

| Risque | Impact | Mesure |
|---|---|---|
| Bitchat n'expose pas de SDK stable | Intégration complexe | Audit amont, extraction/fork conforme licence, tests de compatibilité |
| Concurrence BLE scan/advertising/GATT | Pont instable | Tests sur le Samsung réel, service et gestion de ressources |
| Clients officiels privés incompatibles | Fonction privée partielle | Mode E2EE HybridBridge distinct ; transparence UX |
| Débit LoRa insuffisant | Messages lents/perdus | Messages courts, quotas, filtrage, file bornée |
| Boucles de retransmission | Saturation radio | IDs stables, TTL, déduplication persistante |
| Métadonnées révélées | Atteinte à la vie privée | Minimisation, autorisations, canal dédié |
| Licences incompatibles | Distribution impossible | Audit et avis juridique si nécessaire |
| API Meshtastic modifiée | Échec de compilation | Versions épinglées, adaptateur isolé, CI |
| Restrictions Android en arrière-plan | Déconnexions | Foreground service approprié, tests et UX explicite |
| Configuration radio non conforme | Risque réglementaire | Paramètres régionaux Meshtastic officiels, limitation trafic |

---

## 16. Définition de terminé (DoD)

Une fonctionnalité n'est considérée comme terminée que si :

- le code compile localement ou dans la CI ;
- les tests pertinents passent ;
- les permissions et erreurs sont gérées ;
- les secrets et données privées ne sont pas journalisés ;
- les dépendances et licences sont documentées ;
- un test matériel est réalisé quand la fonctionnalité en dépend ;
- la documentation décrit les limitations réelles ;
- aucune interface simulée n'est présentée comme un réseau réellement connecté.

---

## 17. État actuel réel du projet

### Ce qui est connu

- L'utilisateur possède deux Heltec avec Meshtastic et souhaite **ne pas les reflasher**.
- Le Heltec V4.3 a déjà communiqué par port série avec Meshtastic ; ceci ne prouve pas à lui seul que la connexion BLE de HybridBridge fonctionne.
- HybridBridge 0.4 ajoute synchronisation du nœud/firmware/canaux et messages
    de canal via les protobufs épinglés au firmware 2.7.26, avec file GATT
    sérialisée, limites de taille et d'émission. Bitchat et le pont ne sont pas livrés.
- La CI [42365776287](https://github.com/azatote/HybridBridge/actions/runs/42365776287)
    a réussi les tests, la compilation et le contrôle des licences de l'APK 0.4
    le 7 novembre 2026. Licence GPLv3 et sources publiques approuvées par l'utilisateur.
- L'utilisateur confirme l'ouverture sans crash, le Bluetooth, la synchronisation
    et les échanges Meshtastic aller-retour avec la 0.4.1 sur son installation.
- La 0.5 ajoute un endpoint BLE public de test, basé sur le codec, les annonces
    et le validateur Bitchat 2.2.0, avec identité locale signée/protégée au repos.
    La CI [42387811211](https://github.com/azatote/HybridBridge/actions/runs/42387811211)
    est verte : tests, quatre vues Android 14/15 simulées et licences vérifiés.
    L'interopérabilité sur les téléphones reste à tester. Ce n'est pas un moteur
    mesh complet ; les privés Noise ne sont pas livrés.
- La 0.6 implémente le relais public unidirectionnel Heltec -> LoRa -> Heltec
    -> HybridBridge -> Bitchat, sur canal autorisé et textes préfixés /hb,
    désactivé par défaut, avec attribution, déduplication, quotas et expiration.
    La CI [42393495373](https://github.com/azatote/HybridBridge/actions/runs/42393495373)
    est verte : tests, permissions, signature APK et licences vérifiés.
    Le parcours physique complet reste à valider ; aucun privé ni pont inverse.
- Ces contrôles ne certifient pas Play Protect et ne garantissent pas l'absence
    d'alerte Android. Ne pas contourner les protections ; une signature release
    stable et un canal de distribution reconnu restent nécessaires avant diffusion finale.
- Le dépôt GitHub de l'utilisateur est nommé « Pont hybride » dans son interface ; son chemin visible dans la capture était `azatote/HybridBridge`. Vérifier le dépôt local réellement ouvert avant de modifier quoi que ce soit.

### Ce qui n'est pas encore validé

- Compilation actuelle du dépôt local.
- Version Gradle/Kotlin/Android SDK et qualité du squelette existant.
- Connexion BLE HybridBridge ↔ Heltec.
- Intégration d'un moteur Bitchat compatible.
- Passerelle publique fonctionnelle.
- Chiffrement privé HybridBridge.
- Transport opaque de conversations privées Bitchat.
- Compatibilité Bitchat et matrice matérielle complète Android/Meshtastic.

**Ne pas cocher une fonctionnalité comme livrée sans preuve.**

---

## 18. Première mission à confier à GitHub Copilot

> **Copier ce texte dans le chat Copilot de VS Code, après avoir ajouté ce fichier à la racine du dépôt.**

```text
Lis intégralement HYBRIDBRIDGE_SPECIFICATION.md et inspecte tous les fichiers
actuellement présents dans le dépôt. Ne suppose pas que le projet compile.

Ta première mission est la PHASE 0 puis, si elle réussit, le début de la PHASE 1 :

1. Dresse l'inventaire du dépôt et identifie les erreurs/manques de Gradle,
   AndroidManifest, Kotlin, dépendances, wrapper et GitHub Actions.
2. Vérifie les versions et les licences des projets Bitchat Android et
   Meshtastic SDK dans leurs sources officielles. N'invente aucune API.
3. Crée ou corrige un projet Android Kotlin + Jetpack Compose compilable.
4. Ajoute les permissions Bluetooth adaptées aux versions Android ciblées.
5. Crée un écran « Appareils Meshtastic » avec scan BLE réel et états explicites.
6. Prépare un adaptateur Meshtastic isolé pour la future connexion GATT.
7. Crée une CI GitHub Actions qui lance les tests et produit app-debug.apk.
8. Exécute les tests et la compilation ; corrige jusqu'à obtenir un build vert.
9. Fournis un bilan précis : fichiers modifiés, versions retenues, commandes,
   tests passés, limites, et prochaine étape de test sur mon téléphone.

Contraintes : aucun flashage Heltec ; pas de fausse connexion simulée affichée
comme réelle ; pas de secrets dans Git ; pas de fonctionnalité privée annoncée
comme compatible avant vérification. Si tu n'as pas accès au réseau ou à un
outil de build, signale-le explicitement et ne prétends pas avoir vérifié.
```

---

## 19. Questions ouvertes à trancher progressivement

1. Quelle version Android exacte et quelles capacités BLE du téléphone de test ?
2. Quelle version Meshtastic est installée sur chaque Heltec ?
3. Quel canal Meshtastic sera réservé à HybridBridge, avec quelle politique d'accès ?
4. Quelle version officielle Bitchat Android sera la référence de compatibilité ?
5. Quelle stratégie d'intégration Bitchat respecte les licences et facilite les mises à jour ?
6. Quel protocole E2EE éprouvé sera choisi pour les messages privés HybridBridge ?
7. Faut-il une interface de messagerie complète ou seulement une passerelle avec suivi des messages dans le MVP ?
8. Quelle durée de conservation locale et quels quotas de transmission par défaut ?
9. Quel niveau de compatibilité des messages privés officiels est démontrable sans casser leur modèle de sécurité ?

Les réponses devront être consignées dans `docs/DECISIONS.md` avec date, justification, alternatives et impact.

---

## 20. Résumé exécutable

**Construire d'abord un APK Android qui découvre et communique réellement avec un Heltec Meshtastic en BLE, sans flasher l'appareil.** Ensuite intégrer un moteur Bitchat BLE compatible, puis relier les messages publics autorisés via une enveloppe compacte, une déduplication persistante et des quotas radio. Enfin ajouter une messagerie privée native HybridBridge chiffrée de bout en bout, et étudier séparément la faisabilité d'un transport opaque des messages privés Bitchat officiels. Toute étape doit être compilée, testée et documentée avant de passer à la suivante.
