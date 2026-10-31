# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

Jednoduchý nativní hudební přehrávač pro Android pro vaše **lokální audio soubory**. Přehrává hudbu pouze z jediné složky, kterou si vyberete, takže vyzvánění, hlasové zprávy a další zvuky ve vašem telefonu se nikdy nedostanou do knihovny.

Žádné reklamy, žádné účty, žádný přístup k internetu. Vše zůstává ve vašem zařízení.

> **Jazyky:** angličtina, němčina, ruština, ukrajinština, čínština, japonština, francouzština, čeština, španělština a polština. Vyberte jazyk v **Nastavení → Jazyk aplikace**, nebo nechte *Systém* a aplikace bude používat jazyk telefonu (pokud jazyk telefonu není podporován, použije se angličtina).

## Funkce

- **Vaše hudba, vaše složka.** Složku vyberete jednou; Radiant prohledá ji i její podsložky a při každém spuštění aplikace ji prohledá znovu. Využívá Storage Access Framework systému Android, takže nevidí nic mimo tuto složku.
- **Knihovna:** Playlisty, Alba (automaticky seskupená podle tagů v souborech) a Skladby, s vyhledáváním a řazením v každé sekci.
- **Playlisty:** vytváření, přejmenování, mazání, přidávání a odebírání skladeb. *Oblíbené skladby* jsou vždy připnuty nahoře.
- **Fronty:** čtvrtá sekce s přesně čtyřmi pevnými frontami (nelze je vytvářet ani mazat). Skladby přidáte tlačítkem v přehrávači nebo v nabídce „⋮“, můžete je odebírat a měnit jejich pořadí dlouhým stisknutím a přetažením nebo pomocí „posunout nahoru / dolů“.
- **Změna pořadí přetažením** playlistů, alb a skladeb dlouhým stisknutím.
- **Přehrávání na pozadí** s oznámením o médiích, ovládáním na zamykací obrazovce a podporou tlačítek sluchátek.
- **Přehrávač:** přehrát/pozastavit, další/předchozí, posun, náhodné pořadí, opakování (vypnuto / vše / jedna), obal alba a štítek kvality s formátem souboru a bitrate.
- **Texty písní:** tlačítko pod „i“ v přehrávači otevře text písně. Radiant hledá texty ve značkách (tazích) souboru (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) i v souborech `.lrc` / `.txt` se stejným názvem vedle skladby a kontroluje je při každém otevření aplikace. Synchronizované texty (`.lrc`) se posouvají společně s písní, zvýrazňují aktuální řádek a zeslabují již odzpívané řádky; klepnutím na řádek na něj přeskočíte. Prostý text se zobrazuje v kompaktním zobrazení.
- **Rychlost přehrávání** od 3× pomaleji do 3× rychleji po krocích 0,1, se zachováním výšky tónu.
- **Nastavení zvuku:** mono/stereo, normalizace hlasitosti, zesílení basů a výšek, subsonický filtr a 12pásmový ekvalizér s 10 předvolbami. Ekvalizér může použít jednu křivku pro všechny skladby, nebo si pamatovat samostatnou křivku pro každou skladbu.
- **Jazyk rozhraní:** *Nastavení → Jazyk aplikace* otevře okno s položkou *Systém* nahoře a deseti jazyky pod ní v abecedním pořadí. Změna se projeví okamžitě, bez přerušení přehrávání.
- **Motivy:** světlý, skutečně černý tmavý (skvělý pro OLED displeje) nebo podle systému, s dynamickými barvami na Androidu 12+.
- **Úspora energie** (Nastavení): *Vypnuto*, *Střední* (téměř žádné animace, řidší aktualizace průběhu) nebo *Intenzivní* (navíc vynucený černý motiv, hlasitost omezená na 40 %, pouze mono, vynechání ekvalizéru/basů/výšek/subsonického filtru/normalizace, výstup pouze přes kabel nebo reproduktor telefonu bez Bluetooth, ruční prohledání složky, menší mezipaměť obalů).
- **Podrobnosti o skladbě:** tagy, formát, bitrate, vzorkovací frekvence, kanály, velikost souboru a další.
- **Formáty:** MP3, FLAC, OGG/Opus, M4A/AAC, ALAC, WAV a cokoli dalšího, co Android dokáže dekódovat.

## Požadavky

- Android 8.0 (API 26) nebo novější

## Instalace

Nejnovější stabilní verze je vždy dostupná na stránce **[Releases](../../releases/latest)** jako soubor `.apk`.

1. Stáhněte `.apk` z nejnovějšího vydání do telefonu.
2. Otevřete jej a pokud se Android zeptá, povolte instalaci z neznámých zdrojů.
3. Spusťte Radiant.

Nové verze se instalují přes staré bez odinstalace, takže vaše playlisty a nastavení zůstanou zachovány.

## Začínáme

1. Otevřete aplikaci a klepněte na **Vybrat složku**.
2. Vyberte složku s hudbou. Knihovna se naplní automaticky.
3. Procházejte **Playlisty**, **Alba** a **Skladby** a klepnutím na skladbu ji přehrajte.
4. Chcete-li vytvořit playlist, klepněte na **⋮** vedle libovolné skladby a zvolte **Přidat do playlistu**.

## Sestavení ze zdrojového kódu

Potřebujete JDK 17 a buď Android Studio, nebo Android SDK s Gradle 8.9+.

- **Android Studio:** otevřete projekt, přepněte variantu sestavení na **release** (debug sestavení jsou znatelně pomalejší) a zvolte *Build → Build APK(s)*.
- **Příkazová řádka:** spusťte `gradle assembleRelease`.
- **GitHub Actions:** vytvořte fork repozitáře a spusťte workflow **Build APK**; APK se k běhu připojí jako artefakt.

APK se uloží do `app/build/outputs/apk/release/app-release.apk`.

> **Podepisování.** Oficiální vydání jsou podepsána soukromým klíčem, který není součástí tohoto repozitáře. Sestavení, které si vytvoříte sami, je podepsáno standardním ladicím klíčem Androidu, takže se bez problémů nainstaluje, ale neaktualizuje oficiální vydání (před instalací jedné verze odinstalujte druhou). Chcete-li ve svém GitHub Actions vlastní podepsaná sestavení, přidejte tajné klíče repozitáře `KEYSTORE_BASE64` (váš soubor `.jks` zakódovaný v base64) a `KEYSTORE_PASSWORD`; alias klíče je ve výchozím nastavení `radiant`.

## Vytvořeno pomocí

Kotlin, Jetpack Compose (Material 3) a AndroidX Media3 (ExoPlayer).

## Přispívání

Issues a pull requesty jsou vítány. U větších změn prosím nejprve otevřete issue a probereme, co chcete změnit. Odesláním pull requestu souhlasíte s tím, že váš příspěvek je poskytnut pod stejnou licencí jako projekt.

## Komponenty třetích stran

Přehrávání ALAC (Apple Lossless) využívá dekodér FFmpeg z AndroidX Media3. Workflow **Build APK** jej sestaví z neupraveného zdrojového kódu [FFmpeg](https://ffmpeg.org) 6.0 (pouze dekodér ALAC, žádné komponenty GPL) spolu s rozšířením Media3 1.5.1 (Apache-2.0) a připojí do aplikace jako nativní knihovnu.

FFmpeg zůstává pod vlastní licencí, [LGPL 2.1 nebo novější](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), nikoli PolyForm Noncommercial. Zdrojový kód je k dispozici na <https://github.com/FFmpeg/FFmpeg> (větev `release/6.0`). Aplikaci můžete sestavit s vlastním upraveným FFmpeg úpravou workflow. Sestavení bez tohoto kroku (například lokální, bez souboru `.aar` v `app/libs`) funguje normálně, ale nepřehraje ALAC.

## Licence

Radiant je licencován pod [PolyForm Noncommercial License 1.0.0](LICENSE.md).

Můžete jej svobodně používat, studovat, upravovat a sdílet pro jakékoli **nekomerční** účely, včetně osobního použití, hobby projektů, vzdělávání a neziskových organizací. **Komerční použití není bez samostatné dohody povoleno**, takže aplikaci nesmíte prodávat ani ji vydávat za účelem zisku. Pokud chcete Radiant používat komerčně, kontaktujte nás otevřením issue v tomto repozitáři.

Ke každé kopii, kterou sdílíte, prosím ponechte soubor `LICENSE.md` a jeho řádek `Required Notice`.
