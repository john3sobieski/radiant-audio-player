# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/I5I829ZD02) [![Buy Me a Coffee](https://img.shields.io/badge/Support%20the%20project-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/radiant_player)

Ein einfacher, nativer Android-Musikplayer für deine **lokalen Audiodateien**. Er spielt Musik nur aus dem einen Ordner ab, den du auswählst, sodass Klingeltöne, Sprachnachrichten und andere Sounds auf deinem Handy nie in deiner Bibliothek landen.

Keine Werbung, keine Konten, kein Internetzugriff nötig. Alles bleibt auf deinem Gerät; das Internet wird nur genutzt, wenn du Musik aus deiner eigenen Cloud (Dropbox) herunterladen oder die KI-Modelle für Stems laden möchtest (einmalig; sie lassen sich auch aus Dateien importieren).

> **Sprachen:** Englisch, Deutsch, Russisch, Ukrainisch, Chinesisch, Japanisch, Französisch, Tschechisch, Spanisch und Polnisch. Wähle eine unter **Einstellungen → App-Sprache** oder lass sie auf *System*, um der Sprache des Handys zu folgen (wird die Handysprache nicht unterstützt, wird Englisch verwendet).

## Funktionen

- **Deine Musik, dein Ordner.** Wähle einmal einen Ordner aus; Radiant durchsucht ihn samt Unterordnern und scannt bei jedem Öffnen der App erneut. Es nutzt Androids Storage Access Framework und kann daher nichts außerhalb dieses Ordners sehen.
- **Download aus der Cloud (optional).** Unter *Einstellungen → Musikordner* kannst du dein Dropbox verbinden und Musik aus einem gewählten Cloud-Ordner in einen eigenen Ordner `Dropbox` innerhalb deines Musikordners herunterladen. Nur in eine Richtung: In der Cloud wird nichts hochgeladen, geändert oder gelöscht, und auf dem Gerät wird nichts gelöscht oder überschrieben. Die Anmeldung läuft im Browser, die App sieht dein Passwort also nie.
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
- **App-Sperre** (Einstellungen → Sicherheit, standardmäßig aus): Ein 4-stelliger, 6-stelliger oder eigener Code wird bei jedem Öffnen der App abgefragt. Wenn du ihn vergisst, entsperrt eine Wiederherstellungsdatei mit 12 zufälligen Wörtern die App. Details stehen unten im Abschnitt *App-Sperre*.
- **Energiesparen** (Einstellungen): *Aus*, *Mittel* (kaum Animationen, seltenere Fortschrittsaktualisierungen) oder *Intensiv* (zusätzlich erzwungenes schwarzes Design, Lautstärke auf 40 % begrenzt, nur Mono, EQ/Bass/Höhen/Subsonic/Normalisierung umgangen, Ausgabe nur über Kabel oder Handylautsprecher ohne Bluetooth, manueller Ordnerscan, kleinerer Cover-Cache). **KI-Stems sind in den Stufen Mittel und Intensiv nicht verfügbar** (ein laufender Auftrag wird beim Umschalten abgebrochen); zum Erstellen von Stems wähle *Aus*.
- **Titeldetails:** Tags, Format, Bitrate, Abtastrate, Kanäle, Dateigröße und mehr.
- **Stems (KI):** der Eintrag *Stems erstellen* im „⋮“-Menü eines Titels teilt ihn mit Open-Unmix (umxhq, ONNX) in **Gesang, Schlagzeug, Bass und den Rest** und mischt die angehakten Teile zu **einem Titel**: alles außer Bass ergibt den vollständigen Titel ohne Bass, alles außer Gesang das Instrumental. Es läuft auf dem Gerät, nicht in Echtzeit, mit Fortschrittsbalken, Restzeitschätzung und Abbrechen-Taste und arbeitet im Hintergrund mit einer Benachrichtigung weiter. Das Ergebnis ist eine WAV-Datei (16 Bit / 44,1 kHz) in `Stems/<Interpret - Titel>/` in deinem Musikordner und erscheint sofort in der Bibliothek. Siehe „Stems (KI)“ unten.
- **Formate:** MP3, FLAC, OGG/Opus, M4A/AAC, ALAC, WAV und alles, was Android sonst noch dekodieren kann.

## App-Sperre

Optionaler Schutz für die App, **standardmäßig ausgeschaltet**. Einschalten unter **Einstellungen → Sicherheit**.

- **Drei Codearten:** 4 Ziffern, 6 Ziffern oder ein eigener Code (lateinische Buchstaben und Ziffern, 4–64 Zeichen, keine Leerzeichen).
- **Wann gefragt wird:** bei jedem Start und jedes Mal, wenn die App aus dem Hintergrund zurückkehrt. Bildschirmdrehung oder Sprachwechsel sperren sie nicht.
- **Sperrbildschirm:** ein schlichter weißer oder rein schwarzer Hintergrund (je nach Design) mit einem Codefeld. Ziffern-Codes nutzen eine minimalistische Bildschirmtastatur, ein eigener Code die normale Systemtastatur. Mit dem Auge blendest du die Eingabe ein oder aus. Screenshots und Bildschirmaufnahmen sind nur auf diesem Bildschirm blockiert.
- **Ändern oder ausschalten:** *Einstellungen → Sicherheit → Code ändern* oder die Sperre ausschalten. Beides verlangt zuerst den aktuellen Code.

### So funktioniert der Schutz

- **Der Code wird nie gespeichert.** Radiant speichert nur ein zufälliges Salt und einen PBKDF2-HMAC-SHA256-Hash (30.000 Iterationen), der in konstanter Zeit verglichen wird.
- **Schutz vor Brute-Force.** Nach jeweils 5 falschen Codes in Folge musst du warten: 30 Sekunden, dann 1, 2, 4 … Minuten, höchstens 15 Minuten. Die Pause bleibt auch nach einem Neustart der App bestehen. Im ungünstigsten Fall dauert es bei der längsten Pause etwa 3 Wochen, alle 10.000 vierstelligen Codes durchzuprobieren, und über 5 Jahre für alle 1.000.000 sechsstelligen. Kurze Codes beziehen ihre Stärke aus dieser Begrenzung, nicht aus Entropie: Ein 4-stelliger Code hat etwa 13 Bit, ein 6-stelliger etwa 20 Bit. Für mehr wähle einen eigenen Code: Jedes zufällige Zeichen bringt etwa 6 Bit, 8 zufällige Zeichen also etwa 48 Bit.
- **Wiederherstellungsdatei mit hoher Entropie.** Beim Erstellen eines Codes erzeugt Radiant **12 zufällige Wörter** aus einer eingebauten Liste von 700 englischen Wörtern, ausgewählt mit einem kryptografisch sicheren Zufallsgenerator. Das sind 700¹² ≈ 2¹¹³ Kombinationen, rund **113 Bit Entropie** und damit außerhalb jeder Brute-Force-Reichweite. Du speicherst die Wörter als `.txt`-Datei an einem Ort deiner Wahl. Die Sperre wird erst eingeschaltet, nachdem die Datei geschrieben wurde, und die App behält nur einen Hash der Wörter, nicht die Wörter selbst.
- **Code vergessen?** Tippe auf dem Sperrbildschirm auf *Passwort vergessen* und wähle die Wiederherstellungsdatei. Passt sie, wird die Sperre ausgeschaltet, und du kannst sie mit neuem Code und neuer Datei wieder einschalten. Auch beim Ändern des Codes entsteht eine neue Datei; die alte funktioniert dann nicht mehr.
- **Bewahre die Datei sicher auf.** Wer sie hat, kann die Sperre entfernen – lege sie also nicht dort ab, wo andere sie finden. Gehen Code und Datei beide verloren, hilft nur das Löschen der App-Daten, wodurch auch Wiedergabelisten und Einstellungen verschwinden. Niemand, auch nicht der Entwickler, kann den Code wiederherstellen: Es gibt keine Konten und keine Server.
- **Was die Sperre nicht tut.** Sie schützt die Oberfläche der App, nicht die Audiodateien auf deinem Speicher. Die Musik läuft bei gesperrter App weiter, und die Steuerung in der Benachrichtigung funktioniert weiterhin.

## Stems (KI)

- **Was du bekommst.** Die vier Teile (Gesang, Schlagzeug, Bass, Rest) ergeben zusammen exakt das Original, jede Auswahl ist also ein sauberer Teilmix: hake die Teile an, die bleiben sollen, und sie werden zu einer WAV gemischt. Ein erneuter Lauf mit derselben Auswahl überschreibt die Datei.
- **Modelle.** Vier ONNX-Dateien mit je ~34 MB (Gesang, Schlagzeug, Bass, Rest) sind nicht im APK enthalten. Unter *Einstellungen → Stems (KI)* kannst du sie herunterladen (SHA-256 geprüft) oder aus Dateien importieren, falls du sie vorher geholt hast (`model.onnx` aus den Hugging-Face-Repos `MixDirective/open-unmix-umxhq-{vocals,drums,bass,other}-onnx`; Dateien werden an der Prüfsumme erkannt, der Name ist egal). Danach funktioniert alles offline.
- **So funktioniert es.** Der Titel wird vom System-Decoder in 44,1-kHz-Stereo umgewandelt, in Blöcke von ~12 s mit je 2 s Kontext an den Rändern geschnitten, jeder Block durchläuft die Netze (STFT 4096/1024), und die Ergebnisse werden nahtlos zusammengesetzt. Der Speicherbedarf hängt nicht von der Titellänge ab.
- **Qualität.** Es laufen immer alle vier Netze, ihre Schätzungen werden mit einer weichen Maske (v² / Σv²) abgeglichen, sodass die Teile zusammen das Original ergeben und keiner zu leise ist. Dafür braucht es alle vier Modelle und etwa 4 Netzdurchläufe pro Block; die Verarbeitung benötigt etwa 250 MB RAM.
- **Geschwindigkeit.** Auf einem aktuellen Handy meist einige Minuten pro Titel; Prozessor und Akku werden stark beansprucht. Die Arbeit läuft als Vordergrunddienst mit Benachrichtigung (mit *Abbrechen*-Taste) und hält den Prozessor wach, bis sie fertig ist. In den Energiesparstufen Mittel und Intensiv sind Stems gesperrt.
- **Einschränkungen.** Einfache Verhältnismaske ohne Wiener-Filter (etwas unter der Referenzqualität von umxhq); Gesang, Schlagzeug und Bass sind oberhalb von ~16 kHz leer (die Netze wurden nur bis zu dieser Frequenz trainiert); bei Mehrkanaldateien werden die ersten beiden Kanäle verwendet; Formate, die Android nicht dekodieren kann (z. B. ALAC auf manchen Geräten), lassen sich nicht trennen. Schreibzugriff auf den Musikordner ist nötig (wähle den Ordner erneut, wenn die App danach fragt).

## Voraussetzungen

- Android 8.0 (API 26) oder neuer
- Für Stems (optional): Energiesparen auf *Aus*; etwa 142 MB freier Speicher für die vier Modelle (je ~34 MB); etwa 250 MB freier RAM während der Verarbeitung; temporärer Speicherplatz von etwa 85 MB je 4 Minuten Audio (wird danach gelöscht); das Ergebnis ist eine WAV von etwa 42 MB je 4 Minuten. ONNX Runtime ist im APK enthalten, es muss nichts weiter installiert werden.

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

## Komponenten von Drittanbietern

Die Wiedergabe von ALAC (Apple Lossless) nutzt den FFmpeg-Decoder aus AndroidX Media3. Der Workflow **Build APK** baut ihn aus dem unveränderten Quellcode von [FFmpeg](https://ffmpeg.org) 6.0 (nur der ALAC-Decoder, keine GPL-Komponenten aktiviert) zusammen mit der Media3-1.5.1-Erweiterung (Apache-2.0) und bindet ihn als native Bibliothek in die App ein.

FFmpeg bleibt unter seiner eigenen Lizenz, [LGPL 2.1 oder neuer](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), und nicht unter PolyForm Noncommercial. Der Quellcode ist unter <https://github.com/FFmpeg/FFmpeg> verfügbar (Branch `release/6.0`). Du kannst die App mit deinem eigenen, veränderten FFmpeg neu bauen, indem du den Workflow anpasst. Builds ohne diesen Schritt (zum Beispiel lokal, ohne `.aar` in `app/libs`) funktionieren normal, können aber kein ALAC abspielen.

Die Stem-Trennung nutzt [ONNX Runtime](https://onnxruntime.ai) (MIT) und die [Open-Unmix](https://github.com/sigsep/open-unmix-pytorch)-umxhq-Modelle (MIT, © 2019 Inria) als ONNX-Export von MixDirective. Die umxhq-Gewichte wurden auf MUSDB18-HQ trainiert; prüfe die Bedingungen der Trainingsdaten und Gewichte, wenn du die App über die private Nutzung hinaus einsetzen willst.

## Lizenz

Radiant steht unter der [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Du darfst es für jeden **nichtkommerziellen** Zweck frei verwenden, untersuchen, ändern und weitergeben, einschließlich privater Nutzung, Hobbyprojekten, Bildung und gemeinnütziger Organisationen. **Kommerzielle Nutzung ist ohne gesonderte Vereinbarung nicht gestattet**, du darfst die App also weder verkaufen noch gewinnorientiert veröffentlichen. Wenn du Radiant kommerziell nutzen möchtest, melde dich bitte, indem du ein Issue in diesem Repository eröffnest.

Bitte behalte die Datei `LICENSE.md` und ihre `Required Notice`-Zeile bei jeder Kopie, die du weitergibst.
