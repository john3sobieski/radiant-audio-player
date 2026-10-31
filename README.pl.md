# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

Prosty, natywny odtwarzacz muzyki na Androida dla Twoich **lokalnych plików audio**. Odtwarza muzykę tylko z jednego wybranego przez Ciebie folderu, dzięki czemu dzwonki, wiadomości głosowe i inne dźwięki z telefonu nigdy nie trafiają do biblioteki.

Bez reklam, bez kont, bez dostępu do internetu. Wszystko zostaje na Twoim urządzeniu.

> **Języki:** angielski, niemiecki, rosyjski, ukraiński, chiński, japoński, francuski, czeski, hiszpański i polski. Wybierz język w **Ustawienia → Język aplikacji** lub zostaw *System*, aby aplikacja korzystała z języka telefonu (jeśli język telefonu nie jest obsługiwany, używany jest angielski).

## Funkcje

- **Twoja muzyka, Twój folder.** Wybierasz folder raz; Radiant skanuje go wraz z podfolderami i skanuje ponownie przy każdym otwarciu aplikacji. Korzysta z Storage Access Framework systemu Android, więc nie widzi niczego poza tym folderem.
- **Biblioteka:** Playlisty, Albumy (grupowane automatycznie na podstawie tagów w plikach) i Utwory, z wyszukiwaniem i sortowaniem w każdej sekcji.
- **Playlisty:** tworzenie, zmiana nazwy, usuwanie, dodawanie i usuwanie utworów. *Polubione utwory* są zawsze przypięte na górze.
- **Kolejki:** czwarta sekcja z dokładnie czterema stałymi kolejkami (nie można ich tworzyć ani usuwać). Dodawaj utwory przyciskiem w odtwarzaczu lub z menu „⋮”, usuwaj je i zmieniaj ich kolejność długim przytrzymaniem i przeciągnięciem albo opcjami „przesuń w górę / w dół”.
- **Zmiana kolejności przeciąganiem:** playlisty, albumy i utwory po długim przytrzymaniu.
- **Odtwarzanie w tle** z powiadomieniem multimedialnym, sterowaniem na ekranie blokady i obsługą przycisków słuchawek.
- **Odtwarzacz:** odtwarzanie/pauza, następny/poprzedni, przewijanie, losowo, powtarzanie (wyłączone / wszystkie / jeden), okładka oraz plakietka jakości z formatem pliku i bitrate.
- **Teksty piosenek:** przycisk pod „i” w odtwarzaczu otwiera tekst. Radiant szuka tekstów w tagach pliku (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) oraz w plikach `.lrc` / `.txt` o tej samej nazwie obok utworu i sprawdza je ponownie przy każdym otwarciu aplikacji. Teksty zsynchronizowane (`.lrc`) przewijają się razem z piosenką, podświetlają bieżący wers i wygaszają wersy, które już wybrzmiały; dotknij wersu, aby do niego przeskoczyć. Zwykłe teksty są wyświetlane w widoku kompaktowym.
- **Prędkość odtwarzania** od 3× wolniej do 3× szybciej z krokiem 0,1, z zachowaniem wysokości dźwięku.
- **Ustawienia dźwięku:** mono/stereo, normalizacja głośności, podbicie basów i wysokich tonów, filtr subsoniczny oraz 12-pasmowy korektor z 10 presetami. Korektor może stosować jedną krzywą do wszystkich utworów lub zapamiętywać osobną krzywą dla każdego utworu.
- **Język interfejsu:** *Ustawienia → Język aplikacji* otwiera okno z pozycją *System* na górze i dziesięcioma językami poniżej w kolejności alfabetycznej. Zmiana działa od razu, bez przerywania odtwarzania.
- **Motywy:** jasny, ciemny z prawdziwą czernią (świetny do ekranów OLED) lub systemowy, z kolorami dynamicznymi na Androidzie 12+.
- **Oszczędzanie energii** (Ustawienia): *Wyłączone*, *Średnie* (prawie bez animacji, rzadsze aktualizacje postępu) lub *Intensywne* (dodatkowo wymuszony czarny motyw, głośność ograniczona do 40%, tylko mono, pominięcie korektora/basów/tonów wysokich/subsonicznego/normalizacji, wyjście tylko przewodowe lub głośnik telefonu bez Bluetooth, ręczne skanowanie folderu, mniejsza pamięć podręczna okładek).
- **Szczegóły utworu:** tagi, format, bitrate, częstotliwość próbkowania, kanały, rozmiar pliku i więcej.
- **Formaty:** MP3, FLAC, OGG/Opus, M4A/AAC, ALAC, WAV i wszystko, co Android potrafi odtworzyć.

## Wymagania

- Android 8.0 (API 26) lub nowszy

## Instalacja

Najnowsza stabilna wersja jest zawsze dostępna na stronie **[Releases](../../releases/latest)** jako plik `.apk`.

1. Pobierz plik `.apk` z najnowszego wydania na telefon.
2. Otwórz go i zezwól na instalację z nieznanych źródeł, jeśli Android o to zapyta.
3. Uruchom Radiant.

Nowe wersje instaluje się na starych bez odinstalowywania, więc Twoje playlisty i ustawienia zostają zachowane.

## Pierwsze kroki

1. Otwórz aplikację i dotknij **Wybierz folder**.
2. Wybierz folder z muzyką. Biblioteka wypełni się automatycznie.
3. Przeglądaj **Playlisty**, **Albumy** i **Utwory** i dotknij utworu, aby go odtworzyć.
4. Aby utworzyć playlistę, dotknij **⋮** obok dowolnego utworu i wybierz **Dodaj do playlisty**.

## Budowanie ze źródeł

Potrzebujesz JDK 17 oraz Android Studio albo Android SDK z Gradle 8.9+.

- **Android Studio:** otwórz projekt, przełącz wariant kompilacji na **release** (kompilacje debug są zauważalnie wolniejsze), a następnie wybierz *Build → Build APK(s)*.
- **Wiersz poleceń:** uruchom `gradle assembleRelease`.
- **GitHub Actions:** zrób fork repozytorium i uruchom workflow **Build APK**; plik APK zostanie dołączony do przebiegu jako artefakt.

Plik APK zostaje zapisany w `app/build/outputs/apk/release/app-release.apk`.

> **Podpisywanie.** Oficjalne wydania są podpisywane prywatnym kluczem, który nie jest częścią tego repozytorium. Kompilacja wykonana samodzielnie jest podpisywana standardowym kluczem debugowania Androida, więc zainstaluje się bez problemu, ale nie zaktualizuje oficjalnego wydania (odinstaluj jedną wersję przed zainstalowaniem drugiej). Jeśli chcesz mieć własne podpisane kompilacje w GitHub Actions, dodaj sekrety repozytorium `KEYSTORE_BASE64` (plik `.jks` zakodowany w base64) i `KEYSTORE_PASSWORD`; domyślny alias klucza to `radiant`.

## Zbudowano przy użyciu

Kotlin, Jetpack Compose (Material 3) i AndroidX Media3 (ExoPlayer).

## Współtworzenie

Issues i pull requesty są mile widziane. W przypadku większych zmian najpierw otwórz issue, aby omówić, co chcesz zmienić. Przesyłając pull request, zgadzasz się, że Twój wkład jest udostępniany na tej samej licencji co projekt.

## Komponenty firm trzecich

Odtwarzanie ALAC (Apple Lossless) korzysta z dekodera FFmpeg z AndroidX Media3. Workflow **Build APK** buduje go z niezmodyfikowanego kodu źródłowego [FFmpeg](https://ffmpeg.org) 6.0 (tylko dekoder ALAC, bez komponentów GPL) wraz z rozszerzeniem Media3 1.5.1 (Apache-2.0) i dołącza do aplikacji jako bibliotekę natywną.

FFmpeg pozostaje na własnej licencji, [LGPL 2.1 lub nowszej](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), a nie PolyForm Noncommercial. Kod źródłowy jest dostępny pod adresem <https://github.com/FFmpeg/FFmpeg> (gałąź `release/6.0`). Możesz przebudować aplikację z własnym, zmodyfikowanym FFmpeg, zmieniając workflow. Kompilacje wykonane bez tego kroku (na przykład lokalnie, bez pliku `.aar` w `app/libs`) działają normalnie, ale nie odtwarzają ALAC.

## Licencja

Radiant jest udostępniany na licencji [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Możesz swobodnie go używać, badać, modyfikować i udostępniać w dowolnym celu **niekomercyjnym**, w tym do użytku osobistego, projektów hobbystycznych, edukacji i organizacji non-profit. **Użycie komercyjne jest niedozwolone** bez odrębnej umowy, więc nie możesz sprzedawać aplikacji ani publikować jej w celach zarobkowych. Jeśli chcesz używać Radiant komercyjnie, skontaktuj się z nami, otwierając issue w tym repozytorium.

Zachowaj plik `LICENSE.md` i jego wiersz `Required Notice` w każdej udostępnianej kopii.
