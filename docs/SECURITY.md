# Securite du pont public 0.6

## Perimetre

Trajet vise : Heltec A -> LoRa Meshtastic -> Heltec B -> BLE HybridBridge
sur telephone A -> BLE public Bitchat sur telephone B.

Le pont fonctionne uniquement au premier plan, apres confirmation explicite
d'un canal Meshtastic. Le prefixe `/hb` suivi d'un espace indique que le texte est destine au
relais public. L'operateur doit utiliser un canal de test dedie et obtenir
l'accord des auteurs ; ce prefixe n'est pas une preuve cryptographique de consentement.

## Controles implementes

- Desactive par defaut ; arret des nouvelles remises et vidage de la file du pont
  a la deconnexion du noeud, au changement de canaux et au passage en arriere-plan.
  Un paquet deja confie a la file BLE ne peut pas etre rappele comme une publication privee.
- Seuls les broadcasts TEXT_MESSAGE_APP du canal autorise sont eligibles.
  Pas de messages directs, ciphertexts, administration ou telemetrie relayes.
- Messages emis par le noeud local et textes contenant la marque `[HB` suivie d'un espace ignores.
  Pas de liaison Bitchat -> LoRa dans cette version, donc pas de boucle locale bidirectionnelle.
- Attribution explicite avec origine radio declaree et identifiant de paquet.
  La signature Bitchat est celle de HybridBridge, pas celle de l'auteur LoRa.
- Deduplication bornee a 512 identifiants, persistes sans contenu de message.
  File RAM de 8 messages maximum, expiration 30 secondes, tentative de remise
  au BLE au plus toutes les 5 secondes ; pas de retransmission infinie.
- Texte complet limite a 120 octets UTF-8, attribution incluse ; rejet plutot
  que troncature silencieuse. Aucun fichier ou fragmentation sur ce trajet.
- Identite Bitchat chiffree au repos avec Android Keystore/AES-GCM.
  Sauvegarde de l'application declaree desactivee ; aucun contenu de conversation
  persiste dans le cache de deduplication ou les rapports de securite.
- Aucun acces Internet, contacts, SMS, microphone, camera, accessibilite,
  superposition ou installation d'autres applications demande par le manifeste.
  Permissions BLE et localisation uniquement sur les anciens Android pour le scan.
- Aucun firmware modifie ou flashe ; les regions/canaux radio existants restent
  sous la responsabilite de l'operateur.

## Verification de build

La CI execute tests unitaires et demarrage Android simule, verifie les licences
de l'APK, controle les permissions empaquetees par liste autorisee, verifie sa
signature avec apksigner et publie un checksum SHA-256 et les sources correspondantes.

Ces controles ne sont pas une analyse antivirus complete ni une certification
Google Play Protect. Un APK debug signe est un artefact de test, pas une release.

## Distribution et limites

- Android/Play Protect/Samsung peuvent afficher un avertissement pour un APK
  installe hors store. Aucun code ne garantit l'absence de faux positif.
- Ne pas desactiver Play Protect, Auto Blocker ou d'autres protections pour
  contourner une alerte. Relever son libelle exact et suspendre l'installation
  si l'application est signalee dangereuse, puis analyser la cause.
- La distribution finale exige une signature release stable geree par le
  proprietaire, avec secrets hors du depot, et un canal de distribution reconnu.
  Ne jamais envoyer les mots de passe/cles de signature dans le chat.
- Les annonces Bitchat auto-signees ne constituent pas une authentification
  Noise complete ni une verification humaine. Les messages relayes sont publics,
  pas E2EE entre l'auteur LoRa et le destinataire Bitchat.
- Des membres d'un canal radio peuvent annoncer une origine trompeuse ; ne pas
  presenter l'origine rapportee comme une identite authentifiee de bout en bout.
- L'interoperabilite Bitchat et le trajet sur les quatre appareils doivent
  encore etre valides physiquement ; les tests simules ne prouvent pas une livraison.
