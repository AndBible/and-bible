# My Documents (Pseudo-GenBooks)

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Mahdollistaa käyttäjän ja AI:n luomat omat dokumentit (GenBook-tyyppisiä), joiden sivut tallennetaan tietokantaan. Jokainen dokumentti rekisteröidään omana GenBookinaan JSwordille.

**Tämä on edellytys agentin `addMyDocumentPage()` -työkalulle.**

---

## Konsepti

- `MyDocument` = yksi dokumentti (rekisteröidään JSwordille GenBookina)
- `MyDocumentPage` = dokumentin sivu (TOC-entry)
- Käyttäjä voi luoda dokumentteja My Documents -näkymästä
- AI tallentaa sivuja oletuksena "AI Documents" -dokumenttiin (ei luo uusia dokumentteja, ellei promptissa erikseen pyydetä)
- Dokumentit näkyvät sekä My Documents -näkymässä että dokumenttivalitsimessa

### Oletusdokumentti "AI Documents"

- Luodaan automaattisesti kun AI tallentaa ensimmäisen sivun (tai sovelluksen käynnistyksessä)
- AI käyttää tätä oletuksena säiliönä luomilleen sivuille
- Promptissa voi erikseen määritellä toisen kohteen (esim. "Tallenna uuteen dokumenttiin nimeltä X")
- Käyttäjä voi halutessaan siirtää sivuja toisiin dokumentteihin tai poistaa oletusdokumentin

---

## Tietokantarakenne

```kotlin
enum class MyDocumentContentType {
    MARKDOWN,   // Oletus - Markdown (voi sisältää sword://, osis://, ab-w:// -linkkejä)
    HTML,       // HTML-fragmentti (kun tarvitaan enemmän kontrollia kuin MD tarjoaa)
    OSIS        // OSIS XML -fragmentti (säilyttää alkuperäisen muotoilun)
}

@Entity
data class MyDocument(
    @PrimaryKey val id: IdType = IdType(),
    var name: String,                    // Dokumentin nimi, esim. "Vuorisaarna-tutkielma"
    var description: String?,            // Valinnainen kuvaus
    var initials: String,                // JSword-initials, esim. "MyDoc_abc123" (generoitu)
    var createdAt: Long,
    var updatedAt: Long,
    var sourcePromptId: IdType?          // Mikä prompt loi dokumentin (null = käyttäjän luoma)
)

@Entity
data class MyDocumentPage(
    @PrimaryKey val id: IdType = IdType(),
    val documentId: IdType,              // Viittaus dokumenttiin (ForeignKey)
    var title: String,                   // Sivun otsikko (näkyy TOC:ssa)
    var pageKey: String,                 // Uniikki avain dokumentin sisällä
    var content: String,                 // Sisältö (MD tai OSIS)
    var contentType: MyDocumentContentType = MyDocumentContentType.MARKDOWN,
    var orderNumber: Int,                // Sivujen järjestys dokumentissa
    var createdAt: Long,
    var updatedAt: Long,
    var sourcePromptId: IdType?,         // Mikä prompt loi sivun (null = käyttäjän luoma)
    var sourceContext: String?           // Esim. "Matt.5:1-12"
)
```

---

## Sisältötyypit

- **MARKDOWN** (oletus): Markdown-teksti joka renderöidään HTML:ksi. Voi sisältää AndBible-linkkejä (sword://, osis://, ab-w://). Helpoin ja yleisin vaihtoehto.
- **HTML**: HTML-fragmentti. Käytetään kun tarvitaan enemmän kontrollia kuin Markdown tarjoaa (esim. monimutkaiset taulukot, erityismuotoilut).
- **OSIS**: OSIS XML -fragmentti. Renderöidään kuten muutkin OSIS-dokumentit (Raamatut, kommentaarit). Hyödyllinen kun AI prosessoi OSIS-lähdettä ja säilyttää muotoilun.

### Käyttötapauksia

- **Markdown**: Tiivistelmät, analyysit, muistiinpanot - AI kirjoittaa vapaamuotoista tekstiä
- **OSIS**: Kun AI prosessoi OSIS-sisältöä (esim. lisää annotaatioita jakeisiin, yhdistää jakeita eri käännöksistä säilyttäen muotoilun)

---

## JSword-integraatio

### MyDocumentBook

- Jokainen `MyDocument` rekisteröidään omana GenBookinaan
- `MyDocumentBook` extends `AbstractBook` / implementoi GenBook-rajapinnan
- Rekisteröidään `Books.installed().addBook()` kun dokumentti luodaan
- Poistetaan rekisteröinnistä kun dokumentti poistetaan
- Näkyy dokumenttivalitsimessa GenBook-kategorian alla
- TOC generoidaan dokumentin `MyDocumentPage`-riveistä

### Malli

Katso `FakeBookFactory.kt` mallina pseudodokumenttien rekisteröintiin.

---

## Markdown ja linkit

My Documents käyttää Markdownia sisältöformaattina. Ristiviittaukset käyttävät AndBiblen olemassa olevia protokollia:

```markdown
## Vuorisaarnan analyysi

Jeesus aloittaa opetuksensa [autuaaksijulistuksilla](sword://KJV/Matt.5.3-12).

Vertaa myös Luukkaan versiota [Luuk.6:20-23](sword://KJV/Luke.6.20-23).

Katso myös [kommentaari](sword://MHC/Matt.5.3) Matthew Henryltä.

Lisätietoa [sanan "makarios" merkityksestä](sword://MyDocs/strongs-makarios).
```

### Olemassa olevat linkkiprotokollat AndBiblessa

```
sword://<module>/<key>     - Yleisin, avaa dokumentin sivun
  sword://KJV/Matt.5.3     → Avaa KJV Matt.5.3
  sword://MHC/Matt.5.3     → Avaa Matthew Henry Commentary
  sword://MyDocs/page-key  → Avaa My Documents -sivu

osis://?osis=<ref>&v11n=<versification>  - OSIS-viittaus
  osis://?osis=John.3.16&v11n=KJV

ab-w://?strong=<number>    - Strongs-sanalinkki
  ab-w://?strong=H430      → Avaa Strongs-sanakirja
```

### Linkkien käsittely

- Markdown-renderöinnissä tunnistetaan nämä protokollat
- Vue.js: `window.location.assign(url)` → WebViewClient sieppaa
- Android: `BibleView.openLink(Uri)` käsittelee navigoinnin
- Olemassa oleva infra: `UriAnalyzer.kt`, `BibleView.kt`

---

## Markdown-prosessointi

**Päätös:** Vue.js-puolella (marked.js tai vastaava)

- Markdown-sisältö lähetetään sellaisenaan frontille
- Vue.js renderöi MD → HTML (marked.js tai vastaava)
- Linkit muunnetaan klikattaviksi (sword://, osis://, ab-w:// jne.)
- Myöhemmin tuleva MD-editori on myös frontissa → yhtenäinen ratkaisu

---

## My Documents -näkymä (UI)

- Erillinen näkymä, päävalikossa (main menu) uutena entrynä
- Lista kaikista dokumenteista:
  - Nimi, kuvaus, sivumäärä, luontiaika
  - AI-merkintä jos `sourcePromptId != null`
- Toiminnot:
  - "Luo uusi dokumentti" -nappi
  - Dokumentin muokkaus (nimi, kuvaus)
  - Dokumentin poisto (varoitusdialogi)
  - Avaa dokumentti (siirtyy dokumenttivalitsimeen)

---

## Toteutustehtävät

- [ ] MyDocument ja MyDocumentPage tietokantaentiteetit ja DAO
- [ ] MyDocumentBook (JSword GenBook -integraatio, per dokumentti)
- [ ] Dokumenttien rekisteröinti/poisto JSwordilta dynaamisesti
- [ ] TOC-generointi dokumentin sivuista
- [ ] My Documents -hallintanäkymä (lista, luonti, muokkaus, poisto)
- [ ] Markdown-renderöinti Vue.js-puolella
- [ ] Linkkiprotokollien (sword://, osis://) käsittely Markdown-renderöinnissä
- [ ] Document selector -integraatio

**Huom:** Device Sync -toteutus erillisessä dokumentissa → [07-device-sync.md](07-device-sync.md)

---

## Tärkeät tiedostot

- `app/src/main/java/net/bible/service/sword/FakeBookFactory.kt` (malli)
- `app/src/main/java/net/bible/android/view/util/UriAnalyzer.kt`
- `app/bibleview-js/src/components/BibleView.vue`
