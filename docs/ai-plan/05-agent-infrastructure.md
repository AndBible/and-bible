# Agentin perusinfrastruktuuri

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Toteuttaa agentin työkalut ja suoritusputki joka mahdollistaa LLM:n käyttää AndBiblen toimintoja promptin suorituksen aikana.

**Huom:** Permissions toteutetaan myöhemmin → [05b-permissions.md](05b-permissions.md). Aluksi käytetään "always accept" -logiikkaa.

---

## Agentin työkalut (Tools)

Sisäiset toiminnot joita LLM voi kutsua promptin suorituksen aikana.

**Esimerkki:** Käyttäjä valitsee LLM Actionin "Tiivistelmä kommentaareista". LLM suorittaa tämän kutsumalla työkalua `getCommentaries()` saadakseen kommentaarit, ja palauttaa sitten tiivistelmän.

---

## 1. Lukutyökalut (Read-only, ei vaadi lupaa)

```kotlin
interface AgentReadTools {
    // Dokumenttien lukeminen
    suspend fun getVerseContent(book: String, verseRef: String): OsisFragment
    suspend fun getChapterContent(book: String, chapter: String): OsisFragment

    // Haku
    suspend fun searchBible(
        query: String,                    // Hakusana tai -fraasi
        books: List<String>? = null,      // Rajoita tiettyihin käännöksiin (null = kaikki)
        scope: String? = null             // Rajoita kirjoihin, esim. "Gen-Deut" tai "Matt-Rev"
    ): List<SearchResult>                 // Lista jakeista jotka sisältävät hakusanan

    suspend fun searchByStrongs(
        strongsNumber: String,            // Esim. "H430" tai "G2316"
        books: List<String>? = null       // Rajoita tiettyihin käännöksiin
    ): List<SearchResult>                 // Lista jakeista jotka sisältävät Strongs-numeron

    suspend fun searchStudyPads(
        query: String                     // Vapaa tekstihaku
    ): List<StudyPadSearchResult>         // Lista StudyPadeista joista teksti löytyy

    suspend fun searchBookmarks(
        query: String                     // Haku notesista ja tekstisisällöstä
    ): List<BookmarkSearchResult>

    // Ristiviitteet
    suspend fun getCrossReferences(verseRef: String): List<CrossReference>
    suspend fun getCrossReferencesFromBook(book: String, verseRef: String): List<CrossReference>

    // Kommentaarit
    suspend fun getCommentaries(verseRef: String): List<CommentaryEntry>

    // Sanakirjat (Dictionaries)
    suspend fun getDictionaryEntry(dictionary: String, key: String): DictionaryEntry
    suspend fun getStrongsDefinitions(
        strongsNumber: String,
        dictionaries: List<String>? = null  // null = kaikki asennetut Strongs-sanakirjat
    ): List<StrongsEntry>  // Yksi entry per sanakirja

    // Bookmarkit ja labelit
    suspend fun getBookmarksForVerse(verseRef: String): List<BookmarkInfo>
    suspend fun getBookmarksWithLabel(labelId: IdType): List<BookmarkInfo>
    suspend fun getAllLabels(): List<LabelInfo>

    // StudyPadit
    suspend fun getStudyPadContent(labelId: IdType): StudyPadContent

    // Asennetut dokumentit
    suspend fun getInstalledDocuments(
        category: BookCategory? = null  // BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS, null = kaikki
    ): List<BookInfo>

    // My Documents (read-only)
    suspend fun listMyDocuments(): List<MyDocumentInfo>
    suspend fun getMyDocumentPage(documentId: IdType, pageKey: String): MyDocumentPage?

    // Dokumentaatio/referenssit (LLM hakee tarvittaessa)
    suspend fun getOsisDocumentation(): String      // OSIS XML -formaatin referenssi
    suspend fun getStrongsDocumentation(): String   // Strongs-numeroiden käyttöohje
    suspend fun getLinkProtocolDocumentation(): String  // AndBible linkkiprotokollien ohje (sword://, osis://, ab-w://)
}
```

---

## 2. Strongs-numeroiden käsittely

LLM saa OSIS XML:n suoraan syötteenä ja tunnistaa siitä Strongs-numerot (`<w lemma="strong:H430">`). LLM voi sitten hakea määritelmiä `getStrongsDefinitions()` -työkalulla:

```kotlin
// Esimerkki: LLM hakee Strongs-määritelmän kaikista asennetuista sanakirjoista
getStrongsDefinitions("H430")
// Palauttaa listan (yksi entry per asennettu Strongs-sanakirja):
listOf(
    StrongsEntry(
        dictionary = "StrongsHebrew",        // Mistä sanakirjasta
        number = "H430",
        originalWord = "אֱלֹהִים",
        transliteration = "elohim",
        pronunciation = "el-o-heem'",
        definition = "gods in the ordinary sense; but specifically used...",
        kjvUsage = "angels, exceeding, God (gods), ...",
        references = ["Gen.1:1", "Gen.1:2", ...]
    ),
    StrongsEntry(
        dictionary = "TWOT",                 // Toinen sanakirja
        number = "H430",
        // ... eri/täydentävä sisältö
    )
)

// Tai haetaan vain tietystä sanakirjasta:
getStrongsDefinitions("H430", listOf("StrongsHebrew"))
```

### Käyttötapauksia

- "Selitä valitun jakeen alkukieliset sanat" - LLM tunnistaa Strongs-numerot OSIS:sta, hakee määritelmät
- "Etsi muut jakeet joissa käytetään samaa heprean sanaa" - LLM käyttää `searchByStrongs()` -työkalua
- "Tee sanatutkielma sanasta 'rakkaus'" - LLM tunnistaa Strongs-numeron ja hakee määritelmän + esiintymät

---

## 3. Kirjoitustyökalut (Vaativat luvan myöhemmin)

```kotlin
interface AgentWriteTools {
    // Bookmarkit (sourcePromptId lisätään automaattisesti jos AI luo)
    suspend fun createBookmark(
        verseRef: String,
        labels: List<IdType>?,
        note: String?,
        wholeVerse: Boolean
    ): BookmarkResult  // sourcePromptId asetetaan automaattisesti

    suspend fun addBookmarkNote(bookmarkId: IdType, note: String): Result
    suspend fun updateBookmarkNote(bookmarkId: IdType, note: String): Result
    suspend fun addLabelToBookmark(bookmarkId: IdType, labelId: IdType): Result

    // Labelit
    suspend fun createLabel(name: String, color: Int?): LabelResult

    // StudyPadit
    suspend fun addStudyPadEntry(labelId: IdType, text: String, contentType: TextContentType): Result
    suspend fun updateStudyPadEntry(entryId: IdType, text: String): Result

    // My Documents (write-operaatiot, read-operaatiot AgentReadTools:ssa)
    suspend fun createMyDocument(name: String, description: String? = null): MyDocumentResult
    suspend fun addMyDocumentPage(
        documentId: IdType,                            // Mihin dokumenttiin
        title: String,
        content: String,                               // MD tai OSIS
        contentType: MyDocumentContentType = MARKDOWN  // Oletus: Markdown
    ): MyDocumentPageResult

    suspend fun updateMyDocumentPage(
        documentId: IdType,
        pageKey: String,
        content: String,
        contentType: MyDocumentContentType? = null     // null = säilytä nykyinen
    ): Result

    // Ikkunat (perus)
    suspend fun openWindow(documentInitials: String, key: String?): Result
}
```

**Huom:** Aluksi kaikki write-operaatiot hyväksytään automaattisesti ("always accept"). Permissions-järjestelmä lisätään myöhemmin → [05b-permissions.md](05b-permissions.md)

---

## 4. Laajemmat lukutyökalut (tulevaisuus, matala prioriteetti)

Workspace- ja ikkunatilan lukeminen:

```kotlin
interface AgentAdvancedReadTools {
    // Workspace-tilan lukeminen
    suspend fun getWorkspaceInfo(): WorkspaceInfo  // Aktiivisen workspacen tiedot
    suspend fun getOpenWindows(): List<WindowInfo> // Kaikki avoimet ikkunat
    suspend fun getActiveWindow(): WindowInfo?     // Aktiivinen ikkuna

    // Text Display Settings -lukeminen
    suspend fun getTextDisplaySettings(
        windowId: IdType? = null       // null = workspace-taso, muuten ikkunakohtainen
    ): TextDisplaySettings
}

data class WorkspaceInfo(
    val id: IdType,
    val name: String,
    val windowCount: Int
)

data class WindowInfo(
    val id: IdType,
    val documentInitials: String,      // Esim. "KJV"
    val documentName: String,          // Esim. "King James Version"
    val currentKey: String?,           // Esim. "Matt.5.3"
    val isActive: Boolean,
    val isPinned: Boolean,
    val isLinked: Boolean
)
```

---

## 5. Laajemmat kirjoitustyökalut (tulevaisuus, matala prioriteetti)

Ikkunoiden ja asetusten muokkaaminen:

```kotlin
interface AgentAdvancedWriteTools {
    // Ikkunoiden hallinta
    suspend fun openLinkedWindow(documentInitials: String, key: String?): Result
    suspend fun closeWindow(windowId: IdType): Result
    suspend fun setActiveWindow(windowId: IdType): Result

    // Text Display Settings -asetusten säätö (aktiivinen workspace)
    suspend fun setTextDisplaySetting(
        setting: String,
        value: Any,
        windowId: IdType? = null       // null = workspace-taso, muuten ikkunakohtainen
    ): Result

    // Navigointi
    suspend fun navigateToVerse(verseRef: String, windowId: IdType?): Result
    suspend fun navigateToKey(documentInitials: String, key: String, windowId: IdType?): Result
}
```

### Käyttötapauksia (tulevaisuus)

- "Mitä ikkunoita on auki?" - LLM kutsuu `getOpenWindows()`
- "Avaa rinnakkainen ikkuna kreikankielisellä tekstillä"
- "Suurenna fonttikokoa" (workspace- tai ikkunatasolla)
- "Näytä jakeet Matt.5:3 ja Room.8:28 rinnakkain"

**Huom:** Agentti toimii aina aktiivisessa workspacessa - ei voi luoda/vaihtaa workspaceja.

---

## Agentin suoritusputki (Execution Pipeline)

### Kokonaisvirta

```
1. Käyttäjä valitsee tekstin/jakeet (tai koko dokumentin)
   ↓
2. Käyttäjä valitsee promptin (tekstivalinnasta, window menusta, tai TDS:stä)
   ↓
3. AgentExecutor käynnistyy:
   a. Kerää kontekstin (valittu teksti, jakeet, dokumentti, aktiivinen label)
   b. Rakentaa system promptin automaattisella kontekstilla + käyttäjän promptilla
   c. Lisää työkalukuvaukset (function definitions)
   d. Lähettää LLM API:lle
   ↓
4. LLM analysoi promptin ja päättää mitä tehdä:
   - Kutsuu tarvittavia työkaluja (read/write)
   - Voi kutsua useita työkaluja peräkkäin tai rinnakkain
   ↓
5. AgentExecutor prosessoi tool calls:
   - Read-operaatiot: suoritetaan heti, palautetaan tulos LLM:lle
   - Write-operaatiot: [myöhemmin: kysytään lupa] → suoritetaan
   ↓
6. LLM voi jatkaa lisäkutsuilla tai palauttaa lopullisen vastauksen
   ↓
7. Lopputulos:
   - LLM palauttaa lopullisen vastauksen (ilman pending tool calleja) → taski valmis
   - Jos LLM kutsui write-työkaluja:
     → Luodut bookmarkit/dokumentit/studypad-entryt päivittyvät UI:hin
     → Voidaan navigoida luotuun sisältöön
   - LLM:n tekstivastaus (summary/status message) näkyy Agent Logissa
   - Agent Log piiloutuu/sulkeutuu automaattisesti (riippuen asetuksista)
   - Robotti-ikoni toolbarissa sammuu
```

### Tool Call -formaatti (OpenAI function calling)

```json
{
  "tool_calls": [
    {
      "id": "call_123",
      "type": "function",
      "function": {
        "name": "getCommentaries",
        "arguments": "{\"verseRef\": \"Matt.5:3-12\"}"
      }
    }
  ]
}
```

### LLM voi ketjuttaa kutsuja

```
1. LLM kutsuu getCommentaries("Matt.5:3-12")
2. Saa kommentaarit → analysoi
3. LLM kutsuu addMyDocumentPage("Vuorisaarnan tiivistelmä", markdownContent)
4. Sivu luotu My Documents -dokumenttiin → LLM palauttaa valmis-viestin
```

### Keskeinen periaate

AgentPrompt määrittelee vain mitä käyttäjä haluaa saavuttaa. LLM päättää itse miten tavoite saavutetaan käyttämällä saatavilla olevia työkaluja.

---

## Toteutustehtävät

- [ ] AgentExecutor luokka
- [ ] Tool call parsing (OpenAI function calling -formaatti)
- [ ] AgentReadTools interface ja toteutus
- [ ] AgentWriteTools interface ja toteutus (käyttää 02, 03, 04 rakenteita)
- [ ] Aluksi "always accept" write-operaatioille (permissions myöhemmin)

---

## Tärkeät tiedostot

- `app/src/main/java/net/bible/service/llm/LlmProcessingService.kt` (laajennettava)
- `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt`
- `app/src/main/java/net/bible/android/database/bookmarks/BookmarkEntities.kt`
