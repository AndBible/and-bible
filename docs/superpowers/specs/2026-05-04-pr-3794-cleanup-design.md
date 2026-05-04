# PR #3794 — Track Multiple Chapter Reads — siivous mergattavaksi

- **Status**: Hyväksytty brainstormauksessa 2026-05-04
- **PR**: [AndBible/and-bible#3794](https://github.com/AndBible/and-bible/pull/3794)
- **Issue**: [#3790](https://github.com/AndBible/and-bible/issues/3790) — "Advanced Read progress"
- **Branch**: `Track-Multiple-Chapter-Reads` (current-stable mergetty merge-commitilla `6f6ff41fd`)
- **Strategia**: Branch pohjana, refaktoroivat commitit päälle (ei uudelleenkirjoitusta)
- **Pohjareview**: [`ai-local/reports/2026-05-04-pr-3794-track-multiple-chapter-reads-katselmointi.md`](../../../ai-local/reports/2026-05-04-pr-3794-track-multiple-chapter-reads-katselmointi.md)

## Tausta

Andrew Rogersin PR lisää AndBibleen ominaisuuden seurata **monta lukukertaa per luku** — pelkän binäärisen "luettu / ei luettu" sijaan. UX-suunnittelu on hyvä (lämpökarttojen värit, "x N"-laskuri tickin vieressä, long-press → historia-dialogi, kalenterin lämpökartta), ja idea kannattaa toteuttaa.

PR:n **arkkitehtuurissa** on kuitenkin valintoja, joita ei kannata mergata sellaisenaan:

1. **Tuplamallinnus**: vanha taulu `ChapterReadingRecord` jää käyttöön toggle-modea varten ja uusi taulu `ChapterReadHistory` lisätään count-modea varten. Tämä haarauttaa lähes joka datapolun kahteen rinnakkaiseen versioon ikuisesti.
2. **`useReadCountMode`-asetus** on opt-in-toggle, jolla kaksi UX:ää (binäärinen ja count) elävät rinnakkain.
3. **Migraatioketju** kasvaa kolmen migraation verran (`7→8→9→10`), koska schema on suunniteltu iteratiivisesti.
4. **`ReadingProgressActivity` paisuu 1340 LOC:iin**.
5. **`BibleView.kt` injektoi `useReadCountMode`-asetuksen** Vue-puolelle epäsiististi, ohittaen `TextDisplaySettings`-rajapinnan.

Tämä spec dokumentoi viisi siivouspäätöstä, joiden jälkeen PR voidaan mergata.

## Päätökset

### 1. Datamalli — `ChapterReadingRecord` poistetaan

**Valinta**: Yksi taulu (`ChapterReadHistory`). Vanha `ChapterReadingRecord` poistetaan.

**Perustelu**: Toggle-mode on käytännössä count-mode jossa max=1. Pitämällä molemmat taulut elossa joudumme ylläpitämään kaksi rinnakkaista DAO-pinoa, kaksi datapolkua jokaisessa kutsupisteessä, ja jokainen tuleva luennanseurantaominaisuus (esim. tilastot, kalenteri-laajennukset) joutuu huomioimaan molemmat haarat. Yhden taulun mallissa tickkaus on aina append-rivi, ja "yksi vai monta lukukertaa" on pelkkä `count(*)`-kysymys.

**Vaikutus**:
- DAO:ssa kaikki `*FromHistory`-versiot säilyvät ja jäävät ainoiksi versioiksi. Vanhat `ChapterReadingRecord`-kyselyt (`isChapterRead`, `deleteChapterReadingRecord`, `insertChapterReadingRecord`, `countReadChaptersForBook`, `getReadChaptersForBook`, `getReadingRecordForChapter`, `getReadingRecordsForBook`, `getReadingRecordsForDay`, `getReadingCalendar`) poistetaan.
- `ProgressControl`-luokassa rinnakkaiset `getChapterReadEntriesFor*` / `getReadHistoryFor*` -metodit yhdistyvät yhteen.
- Toggle-modea koskeva `markChapterRead` / `unmarkChapterRead` -polku korvataan: tickkaus = `incrementChapterReadCount(...)`, "untick" = poista historia-dialogista.

### 2. `useReadCountMode`-asetus poistetaan

**Valinta**: Asetus poistetaan kokonaan. `showMarkAsReadButton` on ainoa portti — jos tickkaus-nappi on käyttäjälle näkyvissä, niin samalla näkyy `x N`-laskuri (kun count > 1), count-värit, long-press-historia, jne.

**Perustelu**: Kun data on aina sama (Q1), erillinen UI-toggle olisi pelkkä piilotus joka tuo takaisin haarautumisen. Kerran luettu luku näyttää tickin (kuten ennenkin), kahdesti luettu näyttää tickin + `x 2`. Toggle-käyttäjä joka ei re-readata ei näe mitään uutta. Re-readaava käyttäjä saa "ilmaiseksi" rikkaamman näkymän.

**Vaikutus**:
- Poistetaan `<SwitchPreferenceCompat android:key="use_read_count_mode" .../>` tiedostosta `app/src/main/res/xml/reading_progress_settings.xml`.
- Poistetaan `prefs_use_read_count_mode_title` ja `prefs_use_read_count_mode_summary` strings.xml:stä.
- Poistetaan `ReadingProgressSettings.useReadCountMode`-getter (Kotlin-puoli).
- Poistetaan `useReadCountMode` `GlobalReadingProgressSettings`-entityn kentästä (jos sellainen lopulta on — Q3:n migraatio ei lisää sitä).
- Poistetaan TS-puolen `config.useReadCountMode` ja kaikki sen lukijat (`reading-tracker.ts`, `BibleDocument.vue`).

### 3. Migraatio — yksi puhdas `7→8`

**Valinta**: Andrewn ketju `7→8→9→10` korvataan yhdellä migraatiolla `7→8`, joka rakentaa lopullisen schemen kerralla.

**Lähtötilanne** (vahvistettu Explore-agentin raportilla): `ProgressDatabase` on `current-stable`-haarassa edelleen versiossa 7, eikä `ChapterReadHistory`-taulua ole olemassa. Andrewn väliversiot 8 ja 9 eivät ole päässeet beta-käyttäjille (käyttäjä vahvisti).

**Migraation sisältö**:

```sql
-- 1. Luo lopullinen ChapterReadHistory-taulu
CREATE TABLE ChapterReadHistory (
    id BLOB PRIMARY KEY NOT NULL,
    kjvBookOrdinal INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    cycle INTEGER NOT NULL DEFAULT 1,
    readAt INTEGER NOT NULL,
    bookInitials TEXT NOT NULL DEFAULT ''
);
CREATE INDEX index_ChapterReadHistory_kjvBookOrdinal_chapter_cycle
    ON ChapterReadHistory(kjvBookOrdinal, chapter, cycle);

-- 2. Käännä vanhat tickkaukset 1:1 historia-riveiksi
INSERT INTO ChapterReadHistory (id, kjvBookOrdinal, chapter, cycle, readAt, bookInitials)
    SELECT randomblob(16), kjvBookOrdinal, chapter, cycle, readAt, ''
    FROM ChapterReadingRecord;

-- 3. Pudota vanha taulu
DROP TABLE ChapterReadingRecord;
```

**`useReadCountMode`-saraketta `GlobalReadingProgressSettings`-tauluun EI lisätä** (Q2-päätös).

**Beta-/produktio-käyttäjät**:
- Tuotannossa olevat (v7) → ajavat tämän yhden migraation, säilyttävät kaiken historiansa
- Andrewn tämän branchin kanssa testanneet (Andrew itse) → Room havaitsee `ProgressDatabase`-version yhteensopimattomuuden ja vaatii destructive migrationin tai branch-resetin. Andrewille kerrotaan tämä mergeen liittyvänä yksittäistapauksena.

**Vaikutus**:
- `app/src/main/java/net/bible/android/database/migrations/ProgressMigrations.kt`: kolme migraatiota (`MIGRATION_7_8`, `MIGRATION_8_9`, `MIGRATION_9_10`) korvautuvat yhdellä migraatiolla `MIGRATION_7_8`.
- `ProgressDatabase`-versio: 8 (oli Andrewn branchissa 10).
- Vanhat schema-JSON:it (`8.json`, `9.json`, `10.json`) `app/schemas/net.bible.android.database.progress.ProgressDatabase/`-hakemistosta poistuvat. Uusi `8.json` syntyy kun Room-buildi ajetaan.

### 3b. Tickin poisto UI:sta

**Toiminta**:
- **Lyhyt napautus tickillä** = lisää uusi historia-rivi (`incrementChapterReadCount`)
- **Long-press tickillä** = avaa historia-dialogi
- **Historia-dialogissa**:
  - Yksittäisen rivin "x"-nappi → pending-delete + vahvistus → `dao.deleteChapterReadHistoryById(id)`
  - Erillinen "Poista kaikki" -toiminto poistaa kaikki rivit `(kjvBookOrdinal, chapter, cycle)`-yhdistelmälle (toggle-moden "untick" -ekvivalentti)

**Tämä on jo Andrewn kanssa hyvin lähellä lopullista — yksinkertaistuu kun moodien välinen haarautuminen häviää.**

### 4. `ReadingProgressActivity` — minimimuutos

**Valinta**: Activitya **ei refaktoroida** tässä PR:ssä. Vain värifunktiot ja niiden vakiot eriytetään omaan tiedostoon `ReadingProgressColors.kt`.

**Perustelu**: Q1+Q2-päätösten myötä Activitysta katoaa arviolta ~200 LOC haarautumisten poistuessa (`if (useReadCountMode) ... else ...` toistuu seitsemässä kohdassa). Tämä on jo merkittävä taantumus. Loppu (~1100 LOC) on yhden vastuun (lukemisen edistyminen) ympärillä — iso, mutta ei sen monimuotoisempi kuin ennen Andrewn PR:ää. Memorize-tabin erotus, Presenter-rakenne ja Fragment-siirtymä jäävät myöhempään refaktorointiin koko projektin tasolla, ei tämän PR:n osana.

**Vaikutus** (siirretään `ReadingProgressColors.kt`-tiedostoon):
- Värivakiot: `COLOR_HEAT_MIN`, `COLOR_HEAT_MID`, `COLOR_HEAT_MAX`, `COLOR_COUNT_BOOK_BLUE_LOW`, `COLOR_COUNT_BOOK_BLUE_HIGH`, `COLOR_COUNT_BOOK_RED`, `COLOR_READ`, `COLOR_EMPTY`, ym.
- Funktiot: `countToHeatColor`, `countBookProgressToColor`, `progressToColor`, `interpolateColor`, `textColorForBackground`, `buildBookPercentScaleSteps`, `resolveBookPercentScaleMax`
- **Rikkinäinen testi `ReadingProgressActivityScaleTest.buildBookPercentScaleSteps`** korjataan samalla siirrolla — testi ja toteutus saadaan vihdoin synkroniin.

### 5. `BibleView.kt` config-injektio

**Valinta**: Ratkeaa Q2:n sivutuotteena, ei erillistä toimintaa.

**Perustelu**: Andrewn lisäys

```kotlin
config: Object.assign(${displaySettings.toJson()}, {useReadCountMode: $useReadCountMode}),
```

palautuu siistiksi muotoon

```kotlin
config: ${displaySettings.toJson()},
```

kun `useReadCountMode` poistuu (Q2). Vue-puolen `BibleDocument.vue`:n `v-if="config.useReadCountMode && chapterReadCount > 0"` pelkistyy `v-if="chapterReadCount > 0"`. `chapterReadCount` on per-chapter-data (`getChapterReadCount()`-kutsu), ei config — joten BibleView-config-injektioon ei jää uutta lisättävää.

### Lisäksi: poistettavat artefaktit

- **`IMPLEMENTATION_SUMMARY.md`** repon juuressa: AI-työkalun generoima muistiinpano, ei kuulu repoon.
- **Andrewn Gradle/AGP-downgrade**: kumotaan
  - `gradle/wrapper/gradle-wrapper.properties` → palauta Gradle 9.1.0
  - `build.gradle.kts` → palauta AGP 9.0.0, poista Kotlin-plugin classpathista (built-in AGP 9:ssä)
  - `app/build.gradle.kts` → poista `org.jetbrains.kotlin.android`-plugin
  - `settings.gradle.kts` → poista `foojay-resolver-convention`-plugin
  - `gradle/gradle-daemon-jvm.properties` → poista (Andrewn lisäämä)

## Lopputuote

Mergauksen jälkeen koodikannan tila tämän ominaisuuden osalta:

- **Yksi `ChapterReadHistory`-taulu** ainoana tallennusrakenteena. Tickkaus = append-rivi.
- **Yksi puhdas `7→8`-migraatio** vanhojen `ChapterReadingRecord`-rivien siirtoon.
- **Ei `useReadCountMode`-asetusta** — `showMarkAsReadButton` on ainoa portti UX:lle.
- **`ReadingProgressActivity`** ~1100 LOC, ei refaktoroitu, mutta yksinkertaisempi kuin Andrewn versio (haarautumiset poistuneet).
- **`ReadingProgressColors.kt`** sisältää väri-matematiikan ja sen testit (yksi rikkinäinen testi korjattu).
- **`BibleView.kt`** ennallaan, ei erityistä config-injektiota.
- **Vue-puoli**: `BibleDocument.vue` näyttää tickin + valinnaisesti `x N`-laskurin kun count > 1. Long-press avaa historia-dialogin. Tämä toimii kaikille käyttäjille, ei opt-iniä.
- **Build-konfiguraatio**: Gradle 9.1.0 + AGP 9.0.0 säilyy (Andrewn downgrade kumottu).

## Mitä tämä spec EI kata

- Memorize-tabin erottaminen omaksi luokakseen / Fragmentiksi (jätetty myöhempään refaktorointiin)
- Presenter-rakenteen tai Fragment-pohjaisten tabbien tuonti AndBible-projektiin
- Mahdollisten tilastonäkymien laajennukset (esim. "lukemisen vauhti per kuukausi") — ydinsiivous riittää PR:n mergeen
- Käännökset muille kielille — käsitellään erikseen `update-translations`-skillin kautta jos uusia stringejä jää (todennäköisesti ei jää, koska Q2 poistaa juuri lisätyt)

## Toteutusjärjestys (alustava — varsinainen plan tehdään `writing-plans`-skillillä)

Karkea järjestys joka minimoi konflikteja ja pitää välitilat ehjinä:

1. Poista `IMPLEMENTATION_SUMMARY.md` ja kumoa Gradle/AGP-downgrade
2. Migraatioiden squashaus (`MIGRATION_7_8` korvaa kaikki kolme; schema-JSON:it päivittyvät)
3. DAO-yhdistäminen (`ChapterReadingRecord`-kyselyt poistetaan, `*FromHistory`-versiot uudelleennimetään ilman suffiksia)
4. `ProgressControl`-yhdistäminen (rinnakkaiset metodit yhdeksi)
5. `useReadCountMode`-asetuksen poisto (XML, strings, Kotlin-singleton, Vue/TS)
6. `ReadingProgressActivity`-haarautumisten purku (`if (useReadCountMode)` blokit)
7. `BibleView.kt` config-rivin palautus alkuperäiseksi
8. Värifunktioiden eriytys → `ReadingProgressColors.kt` + rikkinäisen testin korjaus
9. Testit ajoon (`./gradlew testStandardGoogleplayDebugUnitTest`, `cd app/bibleview-js && npm run test:ci`)
10. Manuaalitesti: vanha v7-tietokanta → migraatio → ominaisuus toimii

Yksittäiset commitit pidetään loogisina paloina (esim. yksi commit per yllä oleva askel) niin että katselmointi pysyy hallittavana.
