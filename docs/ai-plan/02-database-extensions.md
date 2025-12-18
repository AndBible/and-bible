# Tietorakenteiden laajennukset

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Laajentaa olemassa olevia tietokantaentiteettejä tukemaan AI-ominaisuuksia: lähdemerkintä (`sourcePromptId`), sisältötyypit (HTML/Markdown), ja bookmark kokonaiselle GenBook-sivulle.

**Nämä laajennukset ovat edellytyksiä agentin write-työkaluille.**

---

## 1. AI-lähdemerkintä (sourcePromptId)

AI:n luomiin entiteetteihin lisätään `sourcePromptId` jotta voidaan jäljittää mikä prompt loi ne.

### Bookmark-entiteetit

```kotlin
// BibleBookmarkEntities.kt
@Entity
data class BibleBookmark(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi bookmarkin (null = käyttäjän luoma)
)

// GenericBookmarkEntities.kt
@Entity
data class GenericBookmark(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi bookmarkin (null = käyttäjän luoma)
)
```

### Notes-entiteetit

```kotlin
// Jos AI lisää noten olemassa olevaan bookmarkkiin
@Entity
data class BibleBookmarkNotes(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi/muokkasi noten (null = käyttäjän luoma)
)

@Entity
data class GenericBookmarkNotes(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi/muokkasi noten (null = käyttäjän luoma)
)
```

### Hyödyt

- Voidaan näyttää UI:ssa "AI-generoitu" -merkintä
- Käyttäjä voi suodattaa/etsiä AI:n luomia bookmarkkeja
- Mahdollistaa "poista kaikki tämän promptin luomat" -toiminnon

---

## 2. Note/Entry-tyypit ja Markdown-tuki

Laajennetaan bookmark-noteja ja StudyPad-entryjä tukemaan erilaisia sisältötyyppejä (plain text, HTML, Markdown).

### Nykyinen tilanne

**Bookmark notes:**
- `BibleBookmarkNotes.notes: String` - HTML-fragmentti (voi olla myös pelkkää tekstiä ilman tageja)
- `GenericBookmarkNotes.notes: String` - sama

**StudyPad entries:**
- `StudyPadTextEntryText.text: String` - HTML-fragmentti

**Huom:** Nykyään ei ole type-kenttää. Sisältö on käytännössä HTML, mutta voi olla myös pelkkää tekstiä jos käyttäjä ei ole lisännyt formatointeja.

### Uusi tietorakenne

```kotlin
enum class TextContentType {
    HTML,           // Oletus - nykyinen tyyppi (HTML-fragmentti tai pelkkä teksti)
    MARKDOWN        // Uusi - Markdown (AI-generoitu tai käyttäjän kirjoittama)
}

// Bookmark notes - lisätään type-kenttä ja sourcePromptId
@Entity
data class BibleBookmarkNotes(
    @PrimaryKey val bookmarkId: IdType,
    var notes: String?,
    var contentType: TextContentType = TextContentType.HTML,  // UUSI, oletus HTML
    var sourcePromptId: IdType? = null                        // UUSI, mikä prompt loi/muokkasi (null = käyttäjän)
)

@Entity
data class GenericBookmarkNotes(
    @PrimaryKey val bookmarkId: IdType,
    var notes: String?,
    var contentType: TextContentType = TextContentType.HTML,  // UUSI, oletus HTML
    var sourcePromptId: IdType? = null                        // UUSI, mikä prompt loi/muokkasi (null = käyttäjän)
)

// StudyPad entry - lisätään type-kenttä ja lähdetiedot
@Entity
data class StudyPadTextEntry(
    @PrimaryKey val id: IdType = IdType(),
    val labelId: IdType,
    var orderNumber: Int,
    var indentLevel: Int,
    var contentType: TextContentType = TextContentType.HTML,  // UUSI, oletus HTML
    var sourceDocumentInitials: String? = null,  // UUSI - mistä dokumentista (jos genbook-sivu)
    var sourceKey: String? = null,               // UUSI - mikä sivu/avain
    var sourcePromptId: IdType? = null           // UUSI - mikä prompt loi tämän (jos AI)
)
```

### Editori-strategia

**HTML-entryt/notet (contentType = HTML):**
- Käytetään nykyistä editoria
- Kaikki vanhat entryt toimivat automaattisesti (oletus = HTML)

**Markdown-entryt (contentType = MARKDOWN):**
- MVP: Read-only näyttö (MD renderöidään HTML:ksi)
- Myöhemmin: Markdown-editori korvaa vanhan editorin uusille merkinnöille
- AI voi muokata MD-sisältöä, käyttäjä voi muokata MD-editorilla

**Migraatio:**
- Vanhat entryt saavat oletuksena `contentType = HTML` (ei tarvitse dataa päivittää)
- Uudet AI-generoidut entryt saavat `contentType = MARKDOWN`

---

## 3. AI-actionit noteille ja entryille

### Uusi PromptContext: NOTE_EDITOR

Näkyy kun käyttäjä on muokkaamassa notea tai StudyPad-entryä. Kontekstina on noten/entryn sisältö.

**Esimerkkiprompteja:**
- "Formatoi teksti siistiksi" - korjaa muotoilu, lisää otsikoita
- "Korjaa kirjoitusvirheet" - oikoluku
- "Tiivistä" - lyhennä pitkää tekstiä
- "Laajenna" - lisää yksityiskohtia

**Toteutus:**
- Note-editorissa "AI Actions" -nappi/menu
- Valittu prompt saa noten sisällön kontekstina
- LLM palauttaa muokatun tekstin
- Käyttäjä voi hyväksyä tai hylätä muutoksen

---

## 4. Bookmark kokonaiselle GenBook-sivulle

### Käyttötapaus

Käyttäjä haluaa luoda bookmarkin kokonaiselle kommentaarisivulle tai AI-dokumentin sivulle (ilman tekstivalintaa).

### Toteutus

Luodaan `GenericBookmark` joka kohdistuu koko sivuun:
- `book` = dokumentin initials (esim. "MHC", "MyDocs")
- `key` = sivun avain
- `startOrdinal = null`, `endOrdinal = null` (koko sivu)
- `startOffset = null`, `endOffset = null` (ei tekstivalintaa)

Bookmark toimii kuten muutkin bookmarkit (labelit, notet, jne.). Jos labeliin on linkitetty → näkyy myös StudyPadissa.

### Tarvittava muutos GenericBookmark-entiteettiin

- Nykyään ordinalit/offsetit ovat pakollisia tekstivalinnalle
- Laajennetaan tukemaan "koko sivu" -tapausta: kun kaikki null → tarkoittaa koko sivua
- Tämä on pieni muutos, ei pitäisi rikkoa olemassa olevaa toiminnallisuutta
- Olemassa oleva bookmark-käyttäytyminen (StudyPad, navigointi) toimii samoin kuin ennenkin

---

## Toteutustehtävät

- [ ] `sourcePromptId` kenttä Bookmark-entiteetteihin (AI-lähdemerkintä)
- [ ] `TextContentType` enum (HTML/MARKDOWN)
- [ ] `contentType` ja `sourcePromptId` kentät BibleBookmarkNotes/GenericBookmarkNotes -entiteetteihin
- [ ] `contentType`, `sourcePromptId`, `sourceDocumentInitials`, `sourceKey` kentät StudyPadTextEntry -entiteettiin
- [ ] GenericBookmark: tuki "koko sivu" -tapaukselle (null ordinals/offsets)
- [ ] Tietokantamigraatiot

---

## Tärkeät tiedostot

- `app/src/main/java/net/bible/android/database/bookmarks/BookmarkEntities.kt`
- `app/src/main/java/net/bible/android/database/bookmarks/GenericBookmarkEntities.kt`
- `app/src/main/java/net/bible/android/database/migrations/` (uudet migraatiot)
