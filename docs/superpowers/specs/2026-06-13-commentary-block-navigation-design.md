# Kommentaarien lohkopohjainen navigointi + infinite scroll

**Päivämäärä:** 2026-06-13
**Tila:** Hyväksytty suunnitelma (odottaa toteutussuunnitelmaa)

## Tausta ja ongelma

AndBiblen kommentaarit (`BookCategory.COMMENTARY`) navigoidaan tällä hetkellä
jaekohtaisesti (`CurrentCommentaryPage.next()/previous()` → `nextVerse()`/
`previousVerse()`). Monet kommentaarit tallentavat saman sisällön jaeväleille:
yksi entry voi kattaa esim. jakeet 1–5 tai jopa luvunvaihdoksen yli. Tästä seuraa
kaksi ongelmaa:

1. **Navigointi**: "seuraava" näyttää saman tekstin uudestaan kunnes jaeväli
   loppuu — käyttäjä joutuu painamaan montaa kertaa nähdäkseen uutta sisältöä.
2. **Infinite scroll**: ei ole käytössä kommentaareille lainkaan
   (`infinite-scroll.ts` `enabledCategories` = vain `BIBLE`, `GENERAL_BOOK`).
   Jos se otettaisiin käyttöön sellaisenaan, sama sisältö toistuisi useaan kertaan.

Sovelluksessa on jo todistettu deduplikointilogiikka samaan ongelmaan LLM-työkalun
puolella: `GetCommentariesTool.deduplicateConsecutiveBlocks()`
(`app/src/main/java/net/bible/service/llm/tools/read/GetCommentariesTool.kt:167-187`).
Se yhdistää peräkkäiset jakeet, joilla on identtinen **renderöity** sisältö —
vertailee renderöityä sisältöä eikä raakaa OSIS-fragmenttia, joten luvunvaihdoksen
yli ulottuva entry collapsoituu yhdeksi lohkoksi (regressiotesti OSTicket #3303,
`GetCommentariesDedupTest.kt`).

## Tavoite

Kommentaareissa navigoinnin ja infinite scrollin yksikkö on **lohko** (block) —
peräkkäisten jakeiden joukko, joilla on identtinen renderöity sisältö — eikä
yksittäinen jae. Sama sisältö ei toistu; tyhjät jakeet ohitetaan.

## Laajuus

- **Vain kommentaarit** (`BookCategory.COMMENTARY`). Ei yleiskirjoja, sanakirjoja
  eikä muita tyyppejä.

## Suunnittelupäätökset (hyväksytyt)

| Päätös | Valinta | Perustelu |
|--------|---------|-----------|
| Aktiivinen avain lohkolle | **Lohkon alkujae** (yksi `Verse`) | Range-avaimen vieminen läpi `CurrentCommentaryPage`/sync/bookmark/`PageManager`-koneiston olisi iso, riskialtis muutos. Avainmalli pysyy yhden jakeen mallina. |
| Range-näyttö (esim. "1–5") | **Erillinen esitys-/navigointitieto** | Lasketaan dedup-logiikalla, välitetään Vue-puolelle. Otsikko näyttää välin; "seuraava" tietää mistä jatkaa. |
| Tyhjät jakeet (ei entryä) | **Ohitetaan** navigoinnissa/skrollauksessa | Lukukokemus sujuva, ei tyhjiä sivuja. Tyhjä jae toimii lohkorajana. |
| Infinite scroll -tapa | **Automaattinen, asetuksen `config.infiniteScroll` mukaan** | Yhtenäinen Biblen kanssa. |
| Lähestymistapa | **Laiska inkrementaalinen lohkonratkaisu** | Ei esilasketa koko kirjaa; kävely on lyhyt paitsi yhden ison entryn tapauksessa. Kierrättää todistetun dedup-logiikan. |

## Arkkitehtuuri

### Lohkon määritelmä

Lohko = peräkkäiset jakeet, joiden `renderComparable`-tulos on identtinen.
Tyhjä jae (`null`-sisältö: blank tai `<div/>`) flushaa nykyisen lohkon ja
ohitetaan (toimii erottimena). Vertailu tehdään renderöidystä sisällöstä, ei
raa'asta OSIS-fragmentista, jotta per-jae-metadata (esim. luvunvaihdos) ei estä
collapsointia.

### Komponentti 1: jaettu render-funktio

Eristetään `GetCommentariesTool`:sta yhteinen funktio:

```
renderComparable(book: SwordBook, verse: Verse): String?
  // raaka readOsisFragment → XMLOutputter-string
  // null jos blank tai "<div/>"
```

Sekä `CommentaryBlockResolver` että `GetCommentariesTool` käyttävät tätä, jottei
vertailulogiikka duplikoidu. Verrataan **raakaa** `readOsisFragment`-tulosta
(ennen sivukohtaista addAnchors-/unwrap-käsittelyä), kuten LLM-työkalu tekee nyt.

### Komponentti 2: `CommentaryBlockResolver`

Sijainti: `net.bible.service.sword` (tai `control.page`). Laiska, inkrementaalinen,
per-jae render-cache (tyhjennetään dokumentin/avaimen vaihtuessa).

```
resolveBlock(book, verse) -> (startVerse, endVerse, content?)
  // kävelee taaksepäin alkuun ja eteenpäin loppuun renderComparablea vertaillen.
  // Käytetään kun sync tuo kommentaarin keskelle lohkoa (Bible jakeessa 3 → 1–5).
  // Jos verse itse on tyhjä → palauttaa tyhjän tilan (ei snappausta).

nextBlockStart(book, fromVerse) -> Verse?
  // eteenpäin ohittaen tyhjät; seuraavan ei-tyhjän lohkon alkujae. null = loppu.

prevBlockStart(book, beforeVerse) -> Verse?
  // taaksepäin ohittaen tyhjät edelliseen ei-tyhjään jakeeseen, sitten ko.
  // lohkon alkuun. null = alku.
```

Kävely käyttää `bibleTraverser.getNextVerse`/`getPrevVerse` -semantiikkaa
(kulkee kirjojen yli koko raamatun sisällä); pysähtyy raamatun rajalla
(kun traverser palauttaa saman jakeen).

`GetCommentariesTool.deduplicateConsecutiveBlocks` säilyy ennallaan
(list-pohjainen omiin tarpeisiinsa), mutta rakentuu samalle `renderComparable`-
periaatteelle.

### Komponentti 3: navigointi — `CurrentCommentaryPage`

- `next()`:
  - `val (_, end, _) = resolver.resolveBlock(book, currentVerse)`
  - `val start = resolver.nextBlockStart(book, end)`
  - `if (start != null) setKey(start)` muuten pysytään paikallaan.
- `previous()`:
  - `val (start, _, _) = resolver.resolveBlock(book, currentVerse)`
  - `val prevStart = resolver.prevBlockStart(book, start)`
  - `if (prevStart != null) setKey(prevStart)`.

Boundary: `null` → pysytään paikallaan (kuten nyt jaerajalla).

### Komponentti 4: sivun sisältö + range-tiedon välitys

- `getPageContent(blockStartVerse)` tuottaa jo oikean sisällön (lohkon jaettu
  teksti = alkujakeen fragment).
- Lisätään commentaary-`OsisDocument`:iin lohkon **range-tieto**
  (start/end osisRef + näyttönimi), laskettuna `resolveBlock`:lla.
- Vue-tyypit (`documents.ts` / `client-objects.ts`): valinnainen
  `commentaryRange`-kenttä (start/end osisRef + näyttönimi).

### Komponentti 5: infinite scroll -syöttö

**Kotlin `BibleView.kt` (`requestMoreToBeginning`/`requestMoreToEnd`):**
Lisätään kommentaarihaara nykyisen `isBible` / general book -haaran rinnalle.
- Seurataan lohkon alkuavaimia `firstKey`/`lastKey`-tyyliin (kuten general book).
- Loppuun: `nextBlockStart(book, lastBlockEnd)` → `getPageContent(start)` →
  `response`. `null` → `response(callId, null)` (reachedEnd).
- Alkuun: `prevBlockStart(book, firstBlockStart)` → vastaavasti.

**Vue `infinite-scroll.ts`:**
- Lisätään `"COMMENTARY"` `enabledCategories`-settiin → `documentSupportsChapterNavigation`
  ja automaattilataus aktivoituvat asetuksen `config.infiniteScroll` mukaan.
- Otsikon/erottimen range-näyttö lohkojen välissä.

## Datavirta

```
Sync Bible→kommentaari (jae 3):
  setKey(3) → resolveBlock(book, 3) → start=1, end=5
  → näyttö: sisältö + otsikko "1–5", aktiivinen avain = 1

next():
  resolveBlock(book, currentVerse) → end=5
  nextBlockStart(book, 5) → ohita tyhjät 6–19 → 20
  setKey(20)

Infinite scroll alas:
  requestMoreToEnd → nextBlockStart(book, lastBlockEnd) → seuraava lohkon alku
  → getPageContent(start) → asJson → Vue lisää dokumentin
```

## Virhetilanteet / reunatapaukset

- **Sync tyhjään jakeeseen**: näytetään nykyinen "ei kommentaaria" -tila, ei
  snäpätä lähimpään lohkoon. Navigointi sieltä eteen/taakse löytää lähimmän
  ei-tyhjän lohkon.
- **Raamatun raja**: `nextBlockStart`/`prevBlockStart` palauttaa `null` →
  navigointi pysähtyy, infinite scroll `response(callId, null)` → reachedEnd.
- **Yksi iso entry koko kirjalle**: kävely renderöi kourallisen jakeita
  (collapsoituu yhdeksi lohkoksi); render-cache estää toiston edestakaisin
  skrollatessa.
- **Tiheä per-jae-kommentaari**: sisältö eroaa heti → kävely on 1 askel per lohko.

## Testaus

**Kotlin — `CommentaryBlockResolverTest`** (injektoitava render-funktio kuten
`GetCommentariesDedupTest`):
- lohko keskeltä taaksepäin alkuun (`resolveBlock`)
- eteenpäin tyhjien yli (`nextBlockStart`)
- taaksepäin tyhjien yli (`prevBlockStart`)
- yksittäisjae-lohkot
- kaikki tyhjät → null
- lohko moduulin alussa/lopussa (boundary → null)
- #3303-luvunvaihdos collapsoituu yhdeksi lohkoksi

**Vue (`*.spec.js`):**
- `COMMENTARY` aktivoi infinite scrollin (`documentSupportsChapterNavigation`)
- range-otsikon renderöinti
- tyhjien lohkojen ohitus dokumenttijonossa

## Ei muutoksia

- Ei uutta asetusta (käytetään `config.infiniteScroll`).
- Ei uusia käännösmerkkijonoja (käytetään olemassa olevaa jaeviittausformatointia).
- Range-avainmallia ei viedä koneistoon — aktiivinen avain pysyy yhtenä jakeena.

## Keskeiset tiedostot

| Tiedosto | Muutos |
|----------|--------|
| `GetCommentariesTool.kt` | Eristä `renderComparable`; käytä sitä |
| `CommentaryBlockResolver.kt` (uusi) | Lohkorajojen laskenta |
| `CurrentCommentaryPage.kt` | `next()`/`previous()` lohkopohjaisiksi |
| `CurrentPageBase.kt` / commentary `getPageContent` | range-tiedon liittäminen `OsisDocument`:iin |
| `BibleView.kt` | `requestMoreToBeginning/End` kommentaarihaara |
| `infinite-scroll.ts` | `COMMENTARY` `enabledCategories`:iin |
| `documents.ts` / `client-objects.ts` | valinnainen `commentaryRange`-kenttä |
| testit (Kotlin + Vue) | yllä kuvatut |
