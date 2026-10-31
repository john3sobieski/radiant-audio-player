# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

Ein einfacher, nativer Android-Musikplayer für deine **lokalen Audiodateien**. Er spielt Musik nur aus dem einen Ordner ab, den du auswählst, sodass Klingeltöne, Sprachnachrichten und andere Sounds auf deinem Handy nie in deiner Bibliothek landen.

Keine Werbung, keine Konten, kein Internetzugriff nötig. Alles bleibt auf deinem Gerät.

> **Sprachen:** Englisch, Deutsch, Russisch, Ukrainisch, Chinesisch, Japanisch, Französisch, Tschechisch, Spanisch und Polnisch. Wähle eine unter **Einstellungen → App-Sprache** oder lass sie auf *System*, um der Sprache des Handys zu folgen (wird die Handysprache nicht unterstützt, wird Englisch verwendet).

## Funktionen

- **Deine Musik, dein Ordner.** Wähle einmal einen Ordner aus; Radiant durchsucht ihn samt Unterordnern und scannt bei jedem Öffnen der App erneut. Es nutzt Androids Storage Access Framework und kann daher nichts außerhalb dieses Ordners sehen.
- **Bibliothek:** Playlists, Alben (automatisch anhand der Datei-Tags gruppiert) und Titel, mit Suche und Sortierung in jedem Bereich.
- **Playlists:** erstellen, umbenennen, löschen, Titel hinzufügen und entfernen. *Gefällt mir* ist immer ganz oben angeheftet.
- **Warteschlangen:** ein vierter Bereich mit genau vier festen Warteschlangen (sie können nicht erstellt oder gelöscht werden). Titel über die Schaltfläche im Player oder das „⋮“-Menü hinzufügen, entfernen und per langem Drücken und Ziehen oder mit „nach oben / unten“ umsortieren.
- **Per Ziehen umsortieren:** Playlists, Alben und Titel mit langem Drücken.
- **Wiedergabe im Hintergrund** mit Medienbenachrichtigung, Sperrbildschirm-Steuerung und Unterstützung für Headset-Tasten.
- **Player:** Wiedergabe/Pause, vor/zurück, Spulen, Zufallswiedergabe, Wiederholen (aus / alle / eins), Cover und ein Qualitätsabzeichen für Dateiformat und Bitrate.
- **Liedtexte:** Eine Schaltfläche unter dem „i“ im Player öffnet die Liedtexte. Radiant sucht Texte in den Datei-Tags (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) sowie in gleichnamigen `.lrc`- / `.txt`-Dateien neben dem Titel und prüft bei jedem Öffnen der App erneut. Synchronisierte Texte (`.lrc`) scrollen mit dem Song mit, heben die aktuelle Zeile hervor und blenden bereits gesungene Zeilen aus; tippe auf eine Zeile, um dorthin zu springen. Einfache Texte werden in einer kompakten Ansicht angezeigt.
- **Wiedergabegeschwindigkeit** von 3× langsamer bis 3× schneller in 0,1er-Schritten, bei erhaltener Tonhöhe.
- **Klangeinstellungen:** Mono/Stereo, Lautstärkenormalisierung, Bass- und Höhenanhebung, Subsonic-Filter und ein 12-Band-Equalizer mit 10 Voreinstellungen. Der Equalizer kann eine Kurve auf alle Titel anwenden oder sich für jeden Titel eine eigene Kurve merken.
- **Oberflächensprache:** *Einstellungen → App-Sprache* öffnet ein Fenster mit *System* ganz oben und den zehn Sprachen alphabetisch darunter. Die Änderung wird sofort übernommen, ohne die Wiedergabe zu unterbrechen.
- **Designs:** hell, echtes Schwarz (ideal für OLED-Displays) oder Systemstandard, mit dynamischen Farben ab Android 12.
- **Energiesparen** (Einstellungen): *Aus*, *Mittel* (kaum Animationen, seltenere Fortschrittsaktualisierungen) oder *Intensiv* (zusätzlich erzwungenes schwarzes Design, Lautstärke auf 40 % begrenzt, nur Mono, EQ/Bass/Höhen/Subsonic/Normalisierung umgangen, Ausgabe nur über Kabel oder Handylautsprecher ohne Bluetooth, manueller Ordnerscan, kleinerer Cover-Cache).
- **Titeldetails:** Tags, Format, Bitrate, Abtastrate, Kanäle, Dateigröße und mehr.
- **Formate:** MP3, FLAC, OGG/Opus, M4A/AAC, WAV und alles, was Android sonst noch dekodieren kann.

## Voraussetzungen

- Android 8.0 (API 26) oder neuer

## Installation

Die neueste stabile Version ist immer auf der Seite **[Releases](../../releases/latest)** als `.apk`-Datei verfügbar.

1. Lade die `.apk` aus dem neuesten Release auf dein Handy herunter.
2. Öffne sie und erlaube die Installation aus unbekannten Quellen, falls Android danach fragt.
3. Starte Radiant.

Neue Versionen werden ohne Deinstallation über die alten installiert, sodass deine Playlists und Einstellungen erhalten bleiben.

## Erste Schritte

1. Öffne die App und tippe auf **Ordner auswählen**.
2. Wähle den Ordner mit deiner Musik. Die Bibliothek füllt sich automatisch.
3. Stöbere in **Playlists**, **Alben** und **Titel** und tippe auf einen Titel, um ihn abzuspielen.
4. Um eine Playlist zu erstellen, tippe neben einem beliebigen Titel auf **⋮** und wähle **Zur Playlist hinzufügen**.

## Aus dem Quellcode bauen

Du brauchst JDK 17 und entweder Android Studio oder das Android SDK mit Gradle 8.9+.

- **Android Studio:** Öffne das Projekt, stelle die Build-Variante auf **release** um (Debug-Builds sind spürbar langsamer) und wähle dann *Build → Build APK(s)*.
- **Kommandozeile:** Führe `gradle assembleRelease` aus.
- **GitHub Actions:** Forke das Repository und starte den Workflow **Build APK**; die APK wird dem Lauf als Artefakt angehängt.

Die APK wird unter `app/build/outputs/apk/release/app-release.apk` abgelegt.

> **Signierung.** Offizielle Releases werden mit einem privaten Schlüssel signiert, der nicht Teil dieses Repositorys ist. Ein selbst erstellter Build wird stattdessen mit dem Standard-Debug-Schlüssel von Android signiert, lässt sich also problemlos installieren, aktualisiert aber kein offizielles Release (deinstalliere das eine, bevor du das andere installierst). Wenn du eigene signierte Builds in GitHub Actions möchtest, füge die Repository-Secrets `KEYSTORE_BASE64` (deine `.jks`-Datei, Base64-kodiert) und `KEYSTORE_PASSWORD` hinzu; der Schlüsselalias ist standardmäßig `radiant`.

## Entwickelt mit

Kotlin, Jetpack Compose (Material 3) und AndroidX Media3 (ExoPlayer).

## Mitwirken

Issues und Pull Requests sind willkommen. Bei größeren Änderungen eröffne bitte zuerst ein Issue, um zu besprechen, was du ändern möchtest. Mit dem Einreichen eines Pull Requests erklärst du dich einverstanden, dass dein Beitrag unter derselben Lizenz wie das Projekt bereitgestellt wird.

## Lizenz

Radiant steht unter der [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Du darfst es für jeden **nichtkommerziellen** Zweck frei verwenden, untersuchen, ändern und weitergeben, einschließlich privater Nutzung, Hobbyprojekten, Bildung und gemeinnütziger Organisationen. **Kommerzielle Nutzung ist ohne gesonderte Vereinbarung nicht gestattet**, du darfst die App also weder verkaufen noch gewinnorientiert veröffentlichen. Wenn du Radiant kommerziell nutzen möchtest, melde dich bitte, indem du ein Issue in diesem Repository eröffnest.

Bitte behalte die Datei `LICENSE.md` und ihre `Required Notice`-Zeile bei jeder Kopie, die du weitergibst.
