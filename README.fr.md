# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/I5I829ZD02) <a href="https://www.buymeacoffee.com/radiant_player"><img src="https://img.buymeacoffee.com/button-api/?text=Support%20the%20project&emoji=%F0%9F%92%BB&slug=radiant_player&button_colour=FFDD00&font_colour=000000&font_family=Comic&outline_colour=000000&coffee_colour=ffffff" alt="Buy Me a Coffee" height="36"></a>

Un lecteur de musique Android natif et simple pour vos **fichiers audio locaux**. Il ne lit la musique que du seul dossier que vous choisissez, de sorte que les sonneries, les messages vocaux et autres sons de votre téléphone n'atterrissent jamais dans votre bibliothèque.

Pas de publicités, pas de comptes, aucun accès à Internet requis. Tout reste sur votre appareil ; Internet n'est utilisé que si vous choisissez de télécharger de la musique depuis votre propre cloud (Dropbox) ou de télécharger les modèles d'IA pour les stems (une seule fois ; ils peuvent aussi être importés depuis des fichiers).

> **Langues :** anglais, allemand, russe, ukrainien, chinois, japonais, français, tchèque, espagnol et polonais. Choisissez-en une dans **Paramètres → Langue de l'application**, ou laissez *Système* pour suivre la langue du téléphone (l'anglais est utilisé si la langue du téléphone n'est pas prise en charge).

## Fonctionnalités

- **Votre musique, votre dossier.** Choisissez un dossier une seule fois ; Radiant l'analyse ainsi que ses sous-dossiers, et le réanalyse à chaque ouverture de l'application. Il utilise le Storage Access Framework d'Android, il ne peut donc rien voir en dehors de ce dossier.
- **Téléchargement depuis le cloud (facultatif).** Dans *Paramètres → Dossier de musique*, vous pouvez connecter votre Dropbox et télécharger la musique d'un dossier cloud de votre choix dans un dossier distinct `Dropbox` à l'intérieur de votre dossier de musique. Strictement à sens unique : rien n'est envoyé, modifié ou supprimé dans le cloud, et rien n'est supprimé ni écrasé sur l'appareil. La connexion se fait dans le navigateur : l'application ne voit jamais votre mot de passe.
- **Bibliothèque :** Playlists, Albums (regroupés automatiquement d'après les tags des fichiers) et Titres, avec recherche et tri dans chaque section.
- **Playlists :** créer, renommer, supprimer, ajouter et retirer des titres. *Titres likés* est toujours épinglée en haut.
- **Files d'attente :** une quatrième section avec exactement quatre files fixes (elles ne peuvent être ni créées ni supprimées). Ajoutez des titres depuis le bouton du lecteur ou le menu « ⋮ », retirez-les et réorganisez-les par appui long et glissement, ou avec « monter / descendre ».
- **Glisser pour réorganiser** les playlists, albums et titres par appui long.
- **Lecture en arrière-plan** avec notification multimédia, commandes sur l'écran de verrouillage et prise en charge des boutons du casque.
- **Lecteur :** lecture/pause, suivant/précédent, recherche dans le titre, lecture aléatoire, répétition (désactivée / tout / un titre), pochette et badge de qualité indiquant le format du fichier et le débit.
- **Paroles :** un bouton sous le « i » dans le lecteur ouvre les paroles. Radiant cherche les paroles dans les tags du fichier (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) et dans les fichiers `.lrc` / `.txt` du même nom placés à côté du titre, et vérifie à nouveau à chaque ouverture de l'application. Les paroles synchronisées (`.lrc`) défilent avec la chanson, mettent en évidence la ligne en cours et estompent les lignes déjà chantées ; touchez une ligne pour y aller. Les paroles simples s'affichent dans une vue compacte.
- **Vitesse de lecture** de 3× plus lente à 3× plus rapide par pas de 0,1, avec conservation de la hauteur du son.
- **Réglages du son :** mono/stéréo, normalisation du volume, renforcement des basses et des aigus, filtre subsonique et égaliseur 12 bandes avec 10 préréglages. L'égaliseur peut appliquer une même courbe à tous les titres ou mémoriser une courbe distincte pour chaque titre.
- **Langue de l'interface :** *Paramètres → Langue de l'application* ouvre une fenêtre avec *Système* en haut et les dix langues en dessous par ordre alphabétique. Le changement s'applique immédiatement, sans interrompre la lecture.
- **Thèmes :** clair, sombre noir pur (idéal pour les écrans OLED) ou celui du système, avec couleurs dynamiques sur Android 12+.
- **Verrouillage de l'application** (Réglages → Sécurité, désactivé par défaut) : un code à 4 chiffres, à 6 chiffres ou personnalisé est demandé à chaque ouverture. Si vous l'oubliez, un fichier de récupération de 12 mots aléatoires déverrouille l'application. Détails dans la section *Verrouillage de l'application* ci-dessous.
- **Économie d'énergie** (Paramètres) : *Désactivée*, *Moyenne* (presque aucune animation, mises à jour de la progression moins fréquentes) ou *Intensive* (en plus : thème noir forcé, volume limité à 40 %, mono uniquement, égaliseur/basses/aigus/subsonique/normalisation contournés, sortie filaire ou haut-parleur du téléphone uniquement, sans Bluetooth, analyse manuelle du dossier, cache de pochettes réduit). **Les stems IA sont indisponibles en modes Moyenne et Intensive** (une tâche en cours est annulée au changement) ; pour créer des stems, choisissez *Désactivée*.
- **Détails du titre :** tags, format, débit, fréquence d'échantillonnage, canaux, taille du fichier et plus encore.
- **Stems (IA) :** l'entrée *Créer des stems* du menu « ⋮ » d'un titre le sépare en **voix, batterie, basse et le reste** avec Open-Unmix (umxhq, ONNX) et mélange les parties cochées en **un seul titre** : tout sauf la basse donne le titre complet sans basse, tout sauf la voix donne l'instrumental. Le traitement se fait sur l'appareil, pas en temps réel, avec barre de progression, estimation du temps restant et bouton d'annulation ; il continue en arrière-plan avec une notification. Le résultat est un WAV 16 bits / 44,1 kHz dans `Stems/<artiste - titre>/` à l'intérieur de votre dossier de musique, donc il apparaît tout de suite dans la bibliothèque. Voir « Stems (IA) » ci-dessous.
- **Formats :** MP3, FLAC, OGG/Opus, M4A/AAC, ALAC, WAV et tout ce qu'Android sait décoder.

## Verrouillage de l'application

Protection facultative de l'application, **désactivée par défaut**. Activez-la dans **Réglages → Sécurité**.

- **Trois types de code :** 4 chiffres, 6 chiffres ou un code personnalisé (lettres latines et chiffres, 4 à 64 caractères, sans espaces).
- **Quand il est demandé :** à chaque lancement et chaque fois que l'application revient de l'arrière-plan. La rotation de l'écran ou le changement de langue ne la verrouillent pas.
- **Écran de verrouillage :** un fond blanc uni ou noir pur (selon le thème) avec un champ de code. Les codes à chiffres utilisent un clavier minimaliste à l'écran ; un code personnalisé utilise le clavier système habituel. Le bouton en forme d'œil affiche ou masque la saisie. Les captures d'écran et l'enregistrement de l'écran sont bloqués uniquement sur cet écran.
- **Modifier ou désactiver :** *Réglages → Sécurité → Changer le code*, ou désactivez le verrouillage. Dans les deux cas, le code actuel est demandé d'abord.

### Comment fonctionne la protection

- **Le code n'est jamais enregistré.** Radiant ne conserve qu'un sel aléatoire et une empreinte PBKDF2-HMAC-SHA256 (30 000 itérations), comparée en temps constant.
- **Protection contre la force brute.** Après 5 codes erronés consécutifs, il faut patienter : 30 secondes, puis 1, 2, 4… minutes, jusqu'à 15 minutes. La pause persiste après un redémarrage de l'application. Dans le pire des cas, avec la pause la plus longue, essayer les 10 000 codes à 4 chiffres prend environ 3 semaines, et les 1 000 000 de codes à 6 chiffres plus de 5 ans. Les codes courts tirent leur force de cette limite plutôt que de l'entropie : un code à 4 chiffres vaut environ 13 bits, un code à 6 chiffres environ 20 bits. Pour plus, choisissez un code personnalisé : chaque caractère aléatoire apporte environ 6 bits, soit environ 48 bits pour 8 caractères.
- **Fichier de récupération à forte entropie.** À la création d'un code, Radiant génère **12 mots aléatoires** parmi une liste intégrée de 700 mots anglais, tirés avec un générateur aléatoire cryptographiquement sûr. Cela représente 700¹² ≈ 2¹¹³ combinaisons, soit environ **113 bits d'entropie**, hors de portée de la force brute. Vous enregistrez les mots dans un fichier `.txt` où vous le souhaitez. Le verrouillage ne s'active qu'une fois le fichier écrit, et l'application ne garde qu'une empreinte des mots, pas les mots eux-mêmes.
- **Code oublié ?** Touchez *Mot de passe oublié* sur l'écran de verrouillage et choisissez le fichier de récupération. S'il correspond, le verrouillage est désactivé et vous pouvez le réactiver avec un nouveau code et un nouveau fichier. Changer le code crée aussi un nouveau fichier ; l'ancien cesse de fonctionner.
- **Gardez le fichier en lieu sûr.** Toute personne qui le possède peut retirer le verrouillage : ne le laissez pas à portée d'autrui. Si vous perdez à la fois le code et le fichier, la seule issue est d'effacer les données de l'application, ce qui supprime aussi les playlists et les réglages. Personne, développeur compris, ne peut récupérer le code : il n'y a ni compte ni serveur.
- **Ce que le verrouillage ne fait pas.** Il protège l'interface de l'application, pas les fichiers audio de votre stockage. La musique continue pendant le verrouillage et les commandes de la notification restent actives.

## Stems (IA)

- **Ce que vous obtenez.** Les quatre parties (voix, batterie, basse, reste) s'additionnent exactement à l'original, donc toute sélection est un sous-mix propre : cochez les parties à conserver et elles sont mélangées dans un seul WAV. Relancer avec la même sélection écrase le fichier.
- **Modèles.** Quatre fichiers ONNX d'environ 34 Mo chacun (voix, batterie, basse, reste) ne sont pas inclus dans l'APK. Dans *Paramètres → Stems (IA)*, vous pouvez les télécharger (vérification SHA-256) ou les importer depuis des fichiers si vous les avez récupérés avant (`model.onnx` des dépôts Hugging Face `MixDirective/open-unmix-umxhq-{vocals,drums,bass,other}-onnx` ; les fichiers sont reconnus par leur somme de contrôle, le nom importe peu). Ensuite, tout fonctionne hors ligne.
- **Fonctionnement.** Le titre est décodé par le décodeur du système en stéréo 44,1 kHz, découpé en blocs d'environ 12 s avec 2 s de contexte de chaque côté, chaque bloc passe dans les réseaux (STFT 4096/1024) et les résultats sont raccordés sans couture. La mémoire utilisée ne dépend pas de la durée du titre.
- **Qualité.** Les quatre réseaux tournent toujours ensemble et leurs estimations sont équilibrées par un masque souple (v² / Σv²) : les parties s'additionnent à l'original et aucune n'est trop faible. Cela exige les quatre modèles et environ 4 passes de réseau par bloc ; le traitement demande environ 250 Mo de RAM.
- **Vitesse.** En général quelques minutes par titre sur un téléphone récent ; le processeur et la batterie sont fortement sollicités. Le travail s'exécute comme un service au premier plan avec une notification (avec un bouton *Annuler*) et garde le processeur éveillé jusqu'à la fin. Les stems sont bloqués dans les modes d'économie d'énergie Moyenne et Intensive.
- **Limites.** Simple masque de rapport sans filtre de Wiener (légèrement en dessous de la qualité de référence d'umxhq) ; voix, batterie et basse sont vides au-dessus d'environ 16 kHz (les réseaux n'ont été entraînés que jusqu'à cette fréquence) ; pour les fichiers multicanaux, les deux premiers canaux sont utilisés ; les formats qu'Android ne sait pas décoder (par ex. ALAC sur certains appareils) ne peuvent pas être séparés. L'accès en écriture au dossier de musique est nécessaire (choisissez-le de nouveau si l'application le demande).

## Configuration requise

- Android 8.0 (API 26) ou version ultérieure
- Pour les stems (facultatif) : économie d'énergie sur *Désactivée* ; environ 142 Mo d'espace libre pour les quatre modèles (~34 Mo chacun) ; environ 250 Mo de RAM libre pendant le traitement ; espace disque temporaire d'environ 85 Mo pour 4 minutes d'audio (supprimé ensuite) ; le résultat est un WAV d'environ 42 Mo pour 4 minutes. ONNX Runtime est inclus dans l'APK, rien d'autre à installer.

## Installation

La dernière version stable est toujours disponible sur la page **[Releases](../../releases/latest)** sous forme de fichier `.apk`.

1. Téléchargez le fichier `.apk` de la dernière version sur votre téléphone.
2. Ouvrez-le et autorisez l'installation depuis des sources inconnues si Android le demande.
3. Lancez Radiant.

Les nouvelles versions s'installent par-dessus les anciennes sans désinstallation, vos playlists et vos paramètres sont donc conservés.

## Premiers pas

1. Ouvrez l'application et touchez **Choisir un dossier**.
2. Sélectionnez le dossier contenant votre musique. La bibliothèque se remplit automatiquement.
3. Parcourez **Playlists**, **Albums** et **Titres** et touchez un titre pour le lire.
4. Pour créer une playlist, touchez **⋮** à côté d'un titre et choisissez **Ajouter à une playlist**.

## Compilation depuis les sources

Il vous faut JDK 17 et soit Android Studio, soit le SDK Android avec Gradle 8.9+.

- **Android Studio :** ouvrez le projet, passez la variante de build sur **release** (les builds debug sont nettement plus lents), puis choisissez *Build → Build APK(s)*.
- **Ligne de commande :** exécutez `gradle assembleRelease`.
- **GitHub Actions :** forkez le dépôt et lancez le workflow **Build APK** ; l'APK est joint à l'exécution en tant qu'artefact.

L'APK est généré dans `app/build/outputs/apk/release/app-release.apk`.

> **Signature.** Les versions officielles sont signées avec une clé privée qui ne fait pas partie de ce dépôt. Un build que vous créez vous-même est signé avec la clé de débogage Android standard : il s'installe sans problème, mais ne remplacera pas une version officielle lors d'une mise à jour (désinstallez l'une avant d'installer l'autre). Si vous voulez vos propres builds signés dans GitHub Actions, ajoutez les secrets de dépôt `KEYSTORE_BASE64` (votre fichier `.jks` encodé en base64) et `KEYSTORE_PASSWORD` ; l'alias de la clé est `radiant` par défaut.

## Réalisé avec

Kotlin, Jetpack Compose (Material 3) et AndroidX Media3 (ExoPlayer).

## Contribuer

Les issues et les pull requests sont les bienvenues. Pour les changements importants, ouvrez d'abord une issue afin de discuter de ce que vous souhaitez modifier. En soumettant une pull request, vous acceptez que votre contribution soit fournie sous la même licence que le projet.

## Composants tiers

La lecture d'ALAC (Apple Lossless) utilise le décodeur FFmpeg d'AndroidX Media3. Le workflow **Build APK** le compile à partir du code source non modifié de [FFmpeg](https://ffmpeg.org) 6.0 (uniquement le décodeur ALAC, aucun composant GPL) avec l'extension Media3 1.5.1 (Apache-2.0), puis l'intègre à l'application sous forme de bibliothèque native.

FFmpeg reste sous sa propre licence, [LGPL 2.1 ou ultérieure](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), et non sous PolyForm Noncommercial. Le code source est disponible sur <https://github.com/FFmpeg/FFmpeg> (branche `release/6.0`). Vous pouvez recompiler l'application avec votre propre version modifiée de FFmpeg en modifiant le workflow. Les builds réalisés sans cette étape (par exemple en local, sans `.aar` dans `app/libs`) fonctionnent normalement mais ne peuvent pas lire l'ALAC.

La séparation en stems utilise [ONNX Runtime](https://onnxruntime.ai) (MIT) et les modèles umxhq d'[Open-Unmix](https://github.com/sigsep/open-unmix-pytorch) (MIT, © 2019 Inria) dans un export ONNX de MixDirective. Les poids umxhq ont été entraînés sur MUSDB18-HQ ; vérifiez les conditions des données d'entraînement et des poids si vous comptez utiliser l'application au-delà d'un usage personnel.

## Licence

Radiant est distribué sous la [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Vous êtes libre de l'utiliser, de l'étudier, de le modifier et de le partager à toute fin **non commerciale**, y compris l'usage personnel, les projets de loisir, l'éducation et les organisations à but non lucratif. **L'usage commercial n'est pas autorisé** sans accord distinct : vous ne pouvez donc pas vendre l'application ni la publier à des fins lucratives. Si vous souhaitez utiliser Radiant à des fins commerciales, contactez-nous en ouvrant une issue dans ce dépôt.

Veuillez conserver le fichier `LICENSE.md` et sa ligne `Required Notice` avec toute copie que vous partagez.
