# PR #3794 — Track Multiple Chapter Reads — Cleanup Refinements

- **Status**: Hyväksytty brainstormauksessa 2026-05-04 (täydentää v1-speciä)
- **PR**: [AndBible/and-bible#3794](https://github.com/AndBible/and-bible/pull/3794)
- **Issue**: [#3790](https://github.com/AndBible/and-bible/issues/3790) — "Advanced Read progress"
- **Branch**: `Track-Multiple-Chapter-Reads` (cleanup v1 jo committattu, viimeisin commit `3213b0163`)
- **Edeltävä spec**: [`2026-05-04-pr-3794-cleanup-design.md`](2026-05-04-pr-3794-cleanup-design.md) (v1 — Q1–Q5 + 3b)
- **Pohjareview**: [`ai-local/reports/2026-05-04-pr-3794-track-multiple-chapter-reads-katselmointi.md`](../../../ai-local/reports/2026-05-04-pr-3794-track-multiple-chapter-reads-katselmointi.md)

## Tausta

V1-cleanup on toteutettu (5 committia merge-commitin `6f6ff41fd` jälkeen) ja tekninen verifikaatio on vihreänä (Vue-testit, Android-yksikkötestit, full debug build). Manuaalitestin sijaan ennen mergeä Tuomas teki vielä syvemmän koodikatselmuksen ja löysi neljä lisäkysymystä, joista jokainen muutti yhden suunnittelupäätöksen.

Tämä spec dokumentoi nuo neljä lisäpäätöstä. Ne **eivät korvaa** v1-speciä — ne täydentävät sitä. V1:n päätökset Q1–Q5 + 3b pysyvät voimassa siltä osin kuin tämä spec ei eksplisiittisesti yliaja niitä.

## Yhteenveto päätöksistä

| # | Aihe | Päätös |
|---|---|---|
| R1 | `markChapterRead` vs. `incrementChapterReadCount` | Yksi funktio nimeltä `recordChapterRead` sekä JS- että Kotlin-puolella |
| R2 | `chapterReadCount` JS-puolella | Lähetetään document-payloadissa initial-arvona ja event-payloadissa päivityksinä |
| R3 | `bookInitials`-sarake `ChapterReadHistory`-taulussa | Säilyy nykyisellään |
| R4 | `ReadingSource`-enum | Palautetaan täydellisenä (`MANUAL` / `AUTO_SCROLL` / `AUTO_TTS`) |

R3 on no-op — kirjattu vain dokumentointia varten, jotta päätös on jäljitettävissä jatkossa. R1, R2 ja R4 vaativat muutoksia sekä Kotlin- että JS-koodiin ja yhden migraation deltan.

## R1 — Yksi `recordChapterRead`-funktio

### Ongelma

V1-cleanupin jälkeen `ProgressControl` ja `BibleJavascriptInterface` tarjoavat kaksi tietä uuden lukurivin tallentamiseen:

- `markChapterRead(...)` — idempotentti: lisää rivin vain jos `getChapterReadCount(...) == 0`. Käytössä auto-track-while-scrolling-toiminnossa.
- `incrementChapterReadCount(...)` — lisää aina uuden rivin. Käytössä manuaalisissa tappauksissa.

JS-puoli mirroroi rajapinnan 1:1: `reading-tracker.ts:52` kutsuu auto-trackissä `markChapterRead`, rivi 91 kutsuu manuaalissa `incrementChapterReadCount`.

### Havainto

Auto-trackerin idempotenssi tulee jo JS-puolen `autoTrackDone`-lipusta (`reading-tracker.ts:39, 48`) ja `IntersectionObserver`:in `cleanup()`-kutsusta. Yhdessä komponentin mount-elinkaaressa rivi 52 voi lauetaa fyysisesti korkeintaan kerran. Kotlin-puolen `if (count == 0)` -vahti on redundantti defensiivinen tarkistus.

Greppi vahvisti että `markChapterRead`-funktiolla on yksi kutsupiste Kotlin-puolella (`BibleJavascriptInterface.kt:574`), joka kutsutaan vain JS-bridgen kautta. Ei TTS-pohjaista kutsujaa, ei `ReadingPlan`-kutsujaa, ei `MainBibleActivity`-kutsujaa.

### Päätös

Yksi funktio nimeltä **`recordChapterRead`** sekä Kotlin- että JS-puolella. Funktio ottaa `source: ReadingSource = MANUAL` -parametrin (R4:stä) ja **aina lisää uuden rivin**. Idempotenssi auto-trackerille jää JS-tason vastuulle.

Nimeämisperustelu: `incrementChapterReadCount` paljastaa tallennusratkaisun (laskuri) ja `markChapterRead` lupaa idempotenssin jota uusi semantiikka ei toteuta. `recordChapterRead` kuvaa vain semantiikkaa — "kirjaa luvun lukukerta".

### Vaikutus

Poistetaan:
- `ProgressControl.markChapterRead(...)`
- `ProgressControl.incrementChapterReadCount(...)`
- `BibleJavascriptInterface.markChapterRead(...)`
- `BibleJavascriptInterface.incrementChapterReadCount(...)`
- `android.ts`:n `markChapterRead` ja `incrementChapterReadCount` -wrap-funktiot ja tyypit

Lisätään:
- `ProgressControl.recordChapterRead(v11n, book, chapter, bookInitials, source = MANUAL)`
- `BibleJavascriptInterface.recordChapterRead(bookInitials, startOrdinal, chapter, source: String)`
- `android.ts`:n `recordChapterRead(bookInitials, startOrdinal, chapter, source)`

`reading-tracker.ts`:
- Rivi 52 (auto-track): `android.recordChapterRead(bookInitials, ordinalRange[0], chapterNumber, "AUTO_SCROLL")`
- Rivi 91 (manual tap): `android.recordChapterRead(bookInitials, ordinalRange[0], chapterNumber, "MANUAL")`

`unmarkChapterRead`-bridge säilyy ennallaan (kutsuu `deleteAllReadsForChapter`:ia) — manuaalitestin "untick" käyttää sitä polkua eikä se ole osa R1:n yhdistystä.

## R2 — `chapterReadCount` document- ja event-payloadeissa

### Ongelma

Andrewn lisäys rikkoi projektin konvention. Muu koodikanta toimittaa initial-tilan dokumentin JSONin mukana ja päivityksiä eventin payloadissa. `chapterReadCount` haetaan sen sijaan synkronisesti JNI-kutsulla joka chapter-renderissä (`reading-tracker.ts:44`) ja joka eventissä (`reading-tracker.ts:103`).

```typescript
// nykyinen
chapterReadCount.value = android.getChapterReadCount(bookInitials, ordinalRange[0], chapterNumber);
// vrt. konventio (sama tiedosto, rivi 90):
useReadingTracker(..., props.document.chapterRead ?? false)
```

`ChapterReadStatusChangedEvent` ei sisällä count-kenttää, joten event-handlerin täytyy re-fetchaa.

### Päätös

`chapterReadCount` toimitetaan:
1. **Initial-arvo**: `BibleDocument`-JSONin kentässä `chapterReadCount: Int`, `chapterRead`-boolin rinnalla
2. **Päivitykset**: `ChapterReadStatusChangedEvent`:n payloadissa uudella `count: Int` -kentällä

`getChapterReadCount` poistetaan `BibleJavascriptInterface`:sta — ei kutsujia jäljellä.

### Vaikutus

Kotlin-puoli:
- `ChapterReadStatusChangedEvent` saa `val count: Int` -kentän.
- Kaikki `ABEventBus.post(ChapterReadStatusChangedEvent(...))`-kutsupisteet `ProgressControl`:ssa päivitetään: count haetaan samalla DAO-kutsulla kuin `isRead`. Kutsupisteet:
  - `recordChapterRead` (uusi, R1)
  - `deleteAllReadsForChapter`
  - `deleteReadHistoryEntries`
- `BibleDocument`-JSONin rakentaja (etsittävä paikka — todennäköisesti `ClientPageObjects.kt` tai `BibleDocument.kt`-sarjallistaja) lisää `chapterReadCount`-kentän.
- `BibleJavascriptInterface.getChapterReadCount(...)` poistetaan.

JS-puoli:
- `BibleDocumentType` (TypeScript-tyyppi) saa `chapterReadCount: number` -kentän.
- `useReadingTracker` ottaa uuden parametrin `initialReadCount: number`.
- `BibleDocument.vue:90` välittää `props.document.chapterReadCount ?? 0`.
- `reading-tracker.ts`:
  - Rivi 44 (sync-fetch initial) poistetaan.
  - Rivi 103 (sync-fetch event-handlerissa) korvautuu `chapterReadCount.value = data.count`:lla — count tulee event-payloadista.
- `update_chapter_read_status`-eventin TS-tyyppi laajenee `{chapter, isRead, count}`.

## R3 — `bookInitials` säilyy

### Päätös

Sarake `ChapterReadHistory.bookInitials: String = ""` säilyy ennallaan. `ReadingProgressActivity:533`:n historia-dialogin "version"-label säilyy ennallaan.

### Perustelu

Multi-Bible-käyttäjälle on legitiimi UX-arvo nähdä mistä käännöksestä luki. Migrated v7-rivit ovat tyhjiä (`""`), mutta uudet rivit sisältävät version-leiman. Cleanup-PR ei pyri kapenemaan dataa ennakkoon.

### Vaikutus

Ei muutoksia. Tämä päätös on kirjattu vain jäljitettävyyden vuoksi.

## R4 — `ReadingSource`-enumin palautus

### Ongelma

Andrewn PR poisti `source`-parametrin `markChapterRead`-bridge-funktiosta ja jätti uuden `incrementChapterReadCount`:in ottamatta sitä. V1-cleanupin squash-vaihe poisti `ReadingSource`-enumin orphan-koodina koska kukaan ei viitannut siihen Andrewn poiston jälkeen.

Git-historia osoittaa että alkuperäinen tracking-koodi (`b82718358` + auto-tracker `0fe3b5333`) käytti enumia aktiivisesti — auto-tracker tallensi `AUTO_SCROLL`, manuaaliset taput tallensivat `MANUAL`. `AUTO_TTS` oli stub tulevalle TTS-pohjaiselle trackingille.

### Päätös

Palautetaan **täydellinen** enum:

```kotlin
enum class ReadingSource { MANUAL, AUTO_SCROLL, AUTO_TTS }
```

`ChapterReadHistory`-entityyn lisätään:

```kotlin
@ColumnInfo(defaultValue = "MANUAL") val source: ReadingSource = ReadingSource.MANUAL,
```

`recordChapterRead`-funktio (R1) ottaa `source: ReadingSource = MANUAL` -parametrin oletusarvolla.

### Migraatio v7→v8

V1-cleanup on jo squashannut migraatioketjun yhdeksi `7→8`:ksi (`addChapterReadHistoryTable` `ProgressMigrations.kt`:ssä). R4 päivittää tätä migraatiota:

```sql
-- Lopullinen ChapterReadHistory-schema
CREATE TABLE IF NOT EXISTS ChapterReadHistory (
    id BLOB NOT NULL PRIMARY KEY,
    kjvBookOrdinal INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    cycle INTEGER NOT NULL DEFAULT 1,
    readAt INTEGER NOT NULL,
    bookInitials TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'MANUAL'
);
CREATE INDEX IF NOT EXISTS index_ChapterReadHistory_kjvBookOrdinal_chapter_cycle
    ON ChapterReadHistory(kjvBookOrdinal, chapter, cycle);

-- Kopioi vanhat ChapterReadingRecord-rivit säilyttäen source-arvon
INSERT INTO ChapterReadHistory (id, kjvBookOrdinal, chapter, cycle, readAt, bookInitials, source)
    SELECT randomblob(16), kjvBookOrdinal, chapter, cycle, readAt, '', source
    FROM ChapterReadingRecord;

DROP TABLE IF EXISTS ChapterReadingRecord;
```

Avain ero v1:een: `INSERT`-lauseen `SELECT`-osio sisältää nyt `source`-sarakkeen vanhasta taulusta. Vanhassa `ChapterReadingRecord`:ssä oli jo source-sarake (sinun alkuperäisestä tracking-implementaatiosta), joten data säilyy.

### Vaikutus

Kotlin-puoli:
- Palautetaan `enum class ReadingSource` `ProgressEntities.kt`:hen (tai erilliseen tiedostoon).
- Palautetaan Room-converter `ReadingSource`-enumille `Converters.kt`:hen — mahdollisesti palautettava commit `79ee751eb`:n diffistä, koska se poisti converterin.
- `ChapterReadHistory`:hen `source`-kenttä.
- `recordChapterRead`-funktio (R1) ottaa source-parametrin.

JS-puoli:
- `recordChapterRead` ottaa `source: string`-parametrin (`"MANUAL"` / `"AUTO_SCROLL"` / `"AUTO_TTS"`).
- `BibleJavascriptInterface.recordChapterRead` parsii stringin enumiksi `ReadingSource.valueOf(source)` ja default-arvolla `MANUAL` jos parsinta epäonnistuu (palautetaan alkuperäinen koodi `b82718358`:sta).

Schema-JSON: `8.json` regeneroidaan (`./gradlew :app:kspStandardGoogleplayDebugKotlin`) — sisältää `source`-sarakkeen.

## Yhteisvaikutus toteutukseen

Refinement-toteutus jakautuu loogisiin paloihin, joista jokainen pidetään omana committina (samoin kuin v1-cleanup):

1. **R4 datamalli**: palauta enum + Converter, lisää `source` `ChapterReadHistory`:hen, päivitä migraatio. (Tällä commitilla rakennus rikkoutuu kunnes R1:n callerit on päivitetty — sallittua välitilana, sama strategia kuin v1.)
2. **R1 + R4 API**: yhdistä funktiot `recordChapterRead`:ksi, lisää source-parametri, päivitä `BibleJavascriptInterface`-bridget, `android.ts` ja `reading-tracker.ts`-kutsupisteet.
3. **R2 data flow**: `chapterReadCount` document-payloadiin + event-payloadiin, poista sync `getChapterReadCount`-kutsut, poista `BibleJavascriptInterface.getChapterReadCount`.
4. **Verifikaatio**: Vue-testit (`npm run test:ci && npm run lint && npm run type-check`), Android-yksikkötestit (`./gradlew testStandardGoogleplayDebugUnitTest`), full debug build (`./gradlew assembleStandardGithubDebug`).

R3 ei tarvitse omaa committia.

## Mitä tämä spec EI kata

- **Manuaalitesti** (v1-planin Step 10.6 + 10.7) — siirretään refinement-implementaation jälkeen samaan vaiheeseen.
- **`BibleJavascriptInterface.openChapterReadHistory`-eagerness-fix** — review-raportin kohta 2.7 mainitsee että funktio kutsuu `Books.installed().getBook()` UI-säikeen ulkopuolella. Tämä jää erilliseksi pikkukorjaukseksi tai tulevaisuuden tikettiin.
- **UX-policy "näytä count vain MANUAL-riveistä"** — R4 mahdollistaa tämän schema-tasolla, mutta tällä hetkellä historia-dialogi ja count-laskuri näyttävät kaikki rivit yhtenä. Erillinen päätös myöhemmin.
- **Andrewn 3 commitin yhdistäminen Co-authored-by-tagilla** — päätetään vasta kun manuaalitesti on vihreänä.

## Toteutusjärjestys

Kun spec hyväksytty, kirjoitetaan `superpowers:writing-plans`-skillillä toteutussuunnitelma neljälle commitille (R4 → R1+R4 → R2 → verifikaatio). Plan dokumenttina `docs/superpowers/plans/2026-05-04-pr-3794-cleanup-refinements.md`.
