# AndBible AI-ominaisuudet - Kokonaissuunnitelma

**Versio:** 3.0 (Draft)
**Päivitetty:** 2025-12-11
**Tila:** Brainstorming & suunnittelu

## Yleiskatsaus

Tämä dokumentti kattaa AndBiblen kaikki AI/LLM-ominaisuudet:

### Keskeiset käsitteet

1. **Prompt Manager** - Käyttäjän määrittelemät ja valmiit promptit
   - **LLM Mode -promptit** - Passiivinen dokumenttien prosessointi (käännökset, tiivistelmät)
   - **LLM Action -promptit** - Manuaalisesti triggeröitävät toiminnot (tekstivalinnasta, window menusta)

2. **Agentin työkalut (Tools)** - Sisäiset toiminnot joita LLM voi kutsua promptin suorituksen aikana
   - Read-työkalut: `getCommentaries()`, `getCrossReferences()`, `getBookmarks()`, `searchBible()`, `getStrongsDefinition()`, jne.
   - Write-työkalut: `createBookmark()`, `addStudyPadEntry()`, `addMyDocumentPage()`, jne.

3. **Permissions & UI** - Käyttäjän kontrolli ja läpinäkyvyys

### Arkkitehtuurin yksinkertaistus

**Vanha lähestymistapa (MVP):**
- `translateTo` -asetus TDS:ssä → kovakoodattu käännöslogiikka

**Uusi lähestymistapa:**
- Kaikki LLM-prosessointi perustuu `AgentPrompt`-entiteetteihin (ei kovakoodattuja prompteja)
- Käännökset ovat vain yksi prompttityyppi (LLM Mode)
- Oletuspromptina käännös UI:n kielelle
- Käyttäjä voi luoda omia prompteja Prompt Manager -UI:ssa (käännös tietylle kielelle, tiivistelmä, jne.)
- Sovellus sisältää valmiita oletusprompteja yleisiin käyttötapauksiin

---

## Osa 1: LLM Mode (nykyinen toteutus)

### Toteutetut ominaisuudet (MVP)

- ✅ **Pseudo-book arkkitehtuuri** (`LlmProcessedBook`) - Decorator pattern JSword-kirjoille
- ✅ **Rinnakkainen jae-kerrallaan prosessointi** - Max 15 samanaikaista API-kutsua
- ✅ **Jae-pohjainen cache** - Sama jae = sama käännös
- ✅ **TranslationProcessor** - Käännösprosessori (OpenAI-yhteensopiva API)
- ✅ **Text Display Settings -integraatio** - `translateTo` -asetus per ikkuna/workspace
- ✅ **Testimoodi** - Kehitystä varten (1s delay + uppercase)

### Arkkitehtuuri

```
LlmProcessedBook (wrappaa alkuperäisen Book:in)
├── LlmProcessedBackend (AbstractKeyBackend)
│   ├── readToOsis() → Rinnakkainen jae-prosessointi
│   └── readRawContent() → Yksittäisen jakeen prosessointi
└── LlmProcessingService
    ├── getCached() → Cache-tarkistus
    ├── processAndCache() → API-kutsu + tallennus
    └── LlmProcessor interface (laajennettava)
```

### Tärkeät tiedostot

- `app/src/main/java/net/bible/service/llm/LlmProcessedBook.kt`
- `app/src/main/java/net/bible/service/llm/LlmProcessingService.kt`
- `app/src/main/java/net/bible/service/llm/LlmProcessor.kt`
- `app/src/main/java/net/bible/service/llm/processors/TranslationProcessor.kt`
- `app/src/main/java/net/bible/android/database/llmprocessing/LlmProcessingEntities.kt`

### Tunnetut ongelmat

1. **Konfirmointi per jae** - Rinnakkaisessa prosessoinnissa dialogi näytetään jokaiselle jakeelle
   - Ratkaisu: Siirrä konfirmointi `readToOsis`-tasolle (yksi dialogi per luku)

2. **Linkki-ikkunat perivät LLM-moodin** - Ei haluttua käytöstä
   - Ratkaisu: Estä periytyminen `CurrentPageBase.currentDocument`:ssa

3. ~~**TDS-dokumentin vaihto** - Kun asetusta vaihtaa, dokumentti pitäisi vaihtua kokonaan~~ ✅ KORJATTU

### Jatkokehitys (LLM Mode)

- [ ] Korjaa konfirmointi-dialogi
- [ ] Estä LLM-moodin periytyminen linkki-ikkunoihin
- [ ] Window Menu: "Translate this document" -action (manuaalinen triggeri)
- [ ] Muut prosessorit (tiivistelmä, selitys, jne.)
- [ ] "Request button" -moodi vaihtoehtoina automaattiselle lataukselle

---

## Osa 2: LLM Actions & Agent (uusi kehitys)

Tämä osio kattaa uudet ominaisuudet jotka laajentavat AI-kyvykkyyksiä käännöstuesta täysimittaiseksi agenttiympäristöksi:

1. **Prompt Manager** - Käyttäjän määrittelemät valmiit promptit pikavalintoina
2. **Agenttityökalut** - Kovakoodatut MCP-tyyliset toiminnot (read/write bookmarks, documents, studypads)
3. **Permissions-järjestelmä** - Käyttäjän hyväksyntä muokkaaviin operaatioihin
4. **Logi/Status-ikkuna** - Agentin toiminnan seuranta ja vuorovaikutus

---

## Arkkitehtuurin komponentit

### 1. Prompt Manager

**Tarkoitus:** Keskitetty hallinta kaikille LLM-prompteille. Korvaa myös kovakoodatun `translateTo`-asetuksen ja mahdollistaa käyttäjän omat promptit.

**Tietokantarakenne:**
```kotlin
@Entity
data class AgentPrompt(
    @PrimaryKey val id: IdType = IdType(),
    var name: String,                    // "Käännä suomeksi", "Tiivistelmä"
    var description: String?,            // Lyhyt kuvaus käyttäjälle
    var promptTemplate: String,          // Varsinainen prompt-teksti
    var showIn: Set<PromptContext>,      // Missä paikoissa prompt näytetään
    var orderNumber: Int,                // Järjestys valikossa
    var createdAt: Long
)

enum class PromptContext {
    TDS,                // Text Display Settings - LLM Mode (online-prosessointi)
    VERSE_SELECTION,    // Kokonaisia jakeita valittu napauttamalla (vain Raamatut, One Tap Actions)
    TEXT_SELECTION,     // Vapaa tekstivalinta (pitkä painallus) TAI One Tap Actions GenBookeissa/kommentaareissa
    WINDOW_MENU,        // Window Button popup-menu (ikkunan oikea ylälaita) - dokumenttikohtaiset
    WORKSPACE_MENU,     // Toolbar 3-dot menu (oikea laita) - workspace-kohtaiset
    NOTE_EDITOR,        // Bookmark-noten tai StudyPad-entryn muokkaus
}
```

**Käyttäjä valitsee promptia luodessa/muokatessa** missä paikoissa se näkyy. **Sama prompt voi toimia sekä LLM Modessa että LLM Actionina** - konteksti (mistä prompt triggeröidään) määrittelee käyttäytymisen.

**Esimerkkejä:**
- "Käännä suomeksi": `showIn = {TDS, VERSE_SELECTION, WINDOW_MENU}` - toimii kaikkialla
  - TDS:stä: online-prosessointi dokumentille (LLM Mode -putki)
  - One Tap Actionista: luo käännös AI Documents -sivulle (LLM Action -putki)
- "Tiivistelmä": `showIn = {VERSE_SELECTION, WINDOW_MENU}` - ei TDS:ssä (ei järkevä online-prosessointiin)
- "Analysoi valinta": `showIn = {VERSE_SELECTION, TEXT_SELECTION}` - vain tekstivalinnoista

**Kontekstin vaikutus suoritukseen:**
- **TDS-konteksti → LLM Mode -putki:** Jae-kerrallaan prosessointi, tallennus cacheen, näytetään dokumentissa inline
- **Muu konteksti → LLM Action -putki:** AgentExecutor, tool calls, tulos tallennetaan AI Documents -sivulle ja avataan

**Huom:** AgentPrompt on yksinkertainen - se sisältää vain promptin tekstin ja metatiedot. LLM itse päättää promptin perusteella:
- Mitä työkaluja käyttää (bookmarkit, dokumentit, studypadit, jne.)
- Mitä lisäkontekstia se tarvitse tehtävän suorittamiseksi (käyttämällä työkaluja)
- Mihin labeliin/dokumenttiin tallennetaan
- Mitä kieltä käytetään käännöksessä
- jne.

**Konfirmointi:**
- **LLM Mode:** Konfirmointi on käyttäjävalinta (asetus), koska LLM:n käyttö on implisiittisempää (tapahtuu taustalla dokumenttia selatessa)
- **LLM Action:** Ei tarvitse konfirmointia, koska käyttäjä triggeröi manuaalisesti valitsemalla promptin
- **Write-operaatiot:** Permissions-järjestelmä hoitaa erikseen (osio 3)

**Esimerkkiprompteja:**

1. **Käännösprompt (LLM_MODE):**
```
Käännä seuraava teksti suomeksi. Säilytä XML-rakenne muuttumattomana,
käännä vain tekstisisältö tagien välissä.
```

2. **Tiivistelmä kommentaareista (LLM_ACTION):**
```
Hae kaikki asennetut kommentaarit valituille jakeille ja tee niistä tiivistelmä.
```
(Tallennus AI Documents -sivulle tapahtuu automaattisesti default behaviorin mukaan)

3. **Korostuskirjanmerkit (LLM_ACTION):**
```
Analysoi valittu teksti ja tunnista teologisesti tärkeimmät kohdat.
Luo bookmark jokaiselle tärkeälle kohdalle sopivalla notella.
```

**UI-integraatio:**
- **Settings → "AI Prompt Manager"** - Promptien hallinta ja luonti
- **Text Display Settings (TDS)** - Valitaan käytössä oleva LLM Mode -prompt
- **Tekstivalinta-dialogi** - Näyttää relevantit LLM Action -promptit
- **Window Menu** - Dokumenttikohtaiset promptit

#### Oletuspromptit (Default Prompts)

**Kun LLM konfiguroidaan ensimmäistä kertaa** (API key asetetaan), luodaan automaattisesti joukko oletusprompteja - samaan tapaan kuin esimerkkibookmarkit luodaan uudelle käyttäjälle.

**Oletuspromptit:**

1. **"Käännä käyttöliittymän kielelle"** (LLM Mode)
   - `showIn = {TDS}`
   - Käännös UI:n kieleen automaattisesti
   - Tämä on oletusprompti TDS:ssä

2. **"Käännä englanniksi"** (LLM Mode + Action)
   - `showIn = {TDS, VERSE_SELECTION, WINDOW_MENU}`
   - Yleinen käännösprompt

3. **"Tiivistelmä"** (Action)
   - `showIn = {VERSE_SELECTION, WINDOW_MENU}`
   - Luo tiivistelmän valitusta tekstistä/luvusta

4. **"Selitä jakeet"** (Action)
   - `showIn = {VERSE_SELECTION}`
   - Hakee kommentaarit ja tekee yhteenvedon

5. **"Sanatutkielma"** (Action)
   - `showIn = {VERSE_SELECTION, TEXT_SELECTION}`
   - Tunnistaa Strongs-numerot ja selittää alkukieliset sanat

6. **"Luo kirjanmerkit teemoittain"** (Action)
   - `showIn = {VERSE_SELECTION, WINDOW_MENU}`
   - Analysoi tekstin ja luo bookmarkit tärkeille kohdille

**Toteutus:**
```kotlin
object DefaultPrompts {
    fun createDefaultPrompts(uiLanguage: String): List<AgentPrompt> {
        // Luodaan promptit käyttäjän UI-kielen mukaan
    }

    suspend fun initializeIfNeeded(dao: AgentPromptDao, uiLanguage: String) {
        if (dao.getCount() == 0) {
            dao.insertAll(createDefaultPrompts(uiLanguage))
        }
    }
}
```

**Huom:** Oletuspromptit lokalisoidaan UI-kielelle (promptin nimi ja kuvaus). Varsinainen `promptTemplate` voi olla englanniksi koska LLM ymmärtää sen.

#### Promptin rakenne (System Prompt)

LLM:lle lähetettävä system prompt koostuu kahdesta osasta:

**1. Automaattinen konteksti (generoidaan aina):**
```
You are an AI assistant for AndBible, a Bible study application.

Current context:
- Selected text: "Blessed are the poor in spirit..."
- Verse references: Matt.5:3-12
- Document: KJV (King James Version)
- UI language: Finnish
- Active StudyPad label: "Vuorisaarna-tutkielma" (id: xxx)
- Available tools: [lista työkaluista]

Default behavior:
- Unless the user specifies otherwise, save your output as a new page in "AI Documents"
- Use the UI language (Finnish) for your response
- Include relevant scripture references as sword:// links

User's request:
```

**2. Käyttäjän prompti (AgentPrompt.promptTemplate):**
```
Tee tiivistelmä valitusta tekstistä ja tallenna se StudyPadiin.
```

**Käyttäjän ei tarvitse** kirjoittaa template-muuttujia promptiinsa - kaikki konteksti annetaan automaattisesti. Käyttäjän prompti on yksinkertainen kuvaus siitä mitä hän haluaa tehdä.

#### Dokumentaatiotyökalut (Reference Tools)

LLM:t eivät välttämättä tunne erikoisformaatteja kuten OSIS XML. Sen sijaan että arvailisimme milloin dokumentaatiota tarvitaan, LLM voi itse hakea sen tarvittaessa MCP-tyylisesti.

**Lukutyökaluihin lisätään:**
```kotlin
// Dokumentaatio/referenssit
suspend fun getOsisDocumentation(): String      // OSIS XML -formaatin referenssi
suspend fun getStrongsDocumentation(): String   // Strongs-numeroiden käyttöohje
suspend fun getLinkProtocolDocumentation(): String  // AndBible linkkiprotokollien ohje (sword://, osis://, ab-w://)
```

**Esimerkki: getOsisDocumentation() palauttaa:**
```
## OSIS XML Format Reference

OSIS (Open Scripture Information Standard) is an XML format for Bible texts.

### Common Elements:
- <verse osisID="Matt.5.3">...</verse> - Verse container
- <chapter osisID="Matt.5">...</chapter> - Chapter container
- <w lemma="strong:G3107">Blessed</w> - Word with Strongs number
- <note type="crossReference">...</note> - Cross-reference note
- <note type="explanation">...</note> - Explanatory note
- <q who="Jesus">...</q> - Quotation with speaker
- <divineName>LORD</divineName> - Divine name (YHWH)
- <transChange type="added">...</transChange> - Added by translators
- <hi type="bold">...</hi> - Highlighting (bold, italic, etc.)
- <lb/> - Line break
- <lg>...</lg> - Line group (poetry)
- <l>...</l> - Single line (in poetry)
- <title type="psalm">Psalm 23</title> - Section title
- <reference osisRef="Gen.1.1">Genesis 1:1</reference> - Scripture reference

### Strongs Lemma Format:
- Hebrew: lemma="strong:H1234"
- Greek: lemma="strong:G5678"
- Multiple: lemma="strong:G3588 strong:G2316" (the God)

### Example:
<verse osisID="Matt.5.3">
  <w lemma="strong:G3107">Blessed</w> are the poor in spirit:
  for theirs is the kingdom of heaven.
</verse>

When outputting OSIS, preserve this structure and use appropriate elements.
```

**Hyödyt:**
- LLM päättää itse milloin tarvitsee dokumentaatiota
- Ei turhia tokeneita system promptissa
- Helppo laajentaa uusilla dokumentaatiotyökaluilla
- Sama pattern kuin muissakin lukutyökaluissa

---

### 2. Agentin työkalut (Tools)

**Tarkoitus:** Sisäiset toiminnot joita LLM voi kutsua promptin suorituksen aikana. 

**Esimerkki:** Käyttäjä valitsee LLM Actionin "Tiivistelmä kommentaareista". LLM suorittaa tämän kutsumalla työkalua `getCommentaries()` saadakseen kommentaarit, ja palauttaa sitten tiivistelmän.

#### 2.1 Lukutyökalut (Read-only, ei vaadi lupaa)

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

#### 2.2 Strongs-numeroiden käsittely

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

**Käyttötapauksia:**
- "Selitä valitun jakeen alkukieliset sanat" - LLM tunnistaa Strongs-numerot OSIS:sta, hakee määritelmät
- "Etsi muut jakeet joissa käytetään samaa heprean sanaa" - LLM käyttää `searchByStrongs()` -työkalua
- "Tee sanatutkielma sanasta 'rakkaus'" - LLM tunnistaa Strongs-numeron ja hakee määritelmän + esiintymät

#### 2.3 Kirjoitustyökalut (Vaativat luvan)

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

**Tietokantatason muutokset (AI-lähdemerkintä):**

AI:n luomiin entiteetteihin lisätään `sourcePromptId` jotta voidaan jäljittää mikä prompt loi ne:

```kotlin
// Bookmark-entiteetteihin lisätään (BibleBookmarkEntities.kt, GenericBookmarkEntities.kt):
@Entity
data class BibleBookmark(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi bookmarkin (null = käyttäjän luoma)
)

@Entity
data class GenericBookmark(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // UUSI: mikä prompt loi bookmarkin (null = käyttäjän luoma)
)

// Notes-entiteetteihin lisätään (jos AI lisää noten olemassa olevaan bookmarkkiin):
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

// StudyPad-entryihin lisätään (jo aiemmin mainittu):
@Entity
data class StudyPadTextEntry(
    // ... olemassa olevat kentät ...
    var sourcePromptId: IdType? = null   // mikä prompt loi tämän
)
```

**Hyödyt:**
- Voidaan näyttää UI:ssa "AI-generoitu" -merkintä
- Käyttäjä voi suodattaa/etsiä AI:n luomia bookmarkkeja
- Mahdollistaa "poista kaikki tämän promptin luomat" -toiminnon

#### 2.4 Laajemmat lukutyökalut (tulevaisuus, matala prioriteetti)

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

#### 2.5 Laajemmat kirjoitustyökalut (tulevaisuus, matala prioriteetti)

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

**Käyttötapauksia (tulevaisuus):**
- "Mitä ikkunoita on auki?" - LLM kutsuu `getOpenWindows()`
- "Avaa rinnakkainen ikkuna kreikankielisellä tekstillä"
- "Suurenna fonttikokoa" (workspace- tai ikkunatasolla)
- "Näytä jakeet Matt.5:3 ja Room.8:28 rinnakkain"

**Huom:** Agentti toimii aina aktiivisessa workspacessa - ei voi luoda/vaihtaa workspaceja.

---

### 3. Permissions-järjestelmä

**Tarkoitus:** Käyttäjä pysyy kontrollissa - agentti kysyy luvan ennen muokkaavia toimintoja.

**Permission-tyypit:**
```kotlin
enum class AgentPermission {
    CREATE_BOOKMARK,      // Uuden bookmarkin luonti
    MODIFY_BOOKMARK,      // Olemassa olevan muokkaus
    CREATE_LABEL,         // Uuden labelin luonti
    WRITE_STUDYPAD,       // StudyPad-merkinnän lisäys/muokkaus
    CREATE_DOCUMENT,      // Uuden pseudodokumentin luonti
    OPEN_WINDOW           // Uuden ikkunan avaaminen
}
```

**Asetukset (per permission):**
```kotlin
enum class PermissionMode {
    ALWAYS_ASK,           // Kysy aina (oletus)
    ASK_ONCE_PER_SESSION, // Kysy kerran per sessio
    ALLOW_ALL,            // Salli kaikki (advanced users)
    DENY_ALL              // Estä kokonaan
}
```

**Permission-dialogi:**
- Näyttää mitä agentti haluaa tehdä (esim. "Luo 5 kirjanmerkkiä")
- "Salli" / "Salli kaikki tämän session aikana" / "Estä"
- Lista konkreettisista toiminnoista ennen suoritusta

**Toteutus:**
```kotlin
class AgentPermissionManager {
    private val sessionPermissions = mutableSetOf<AgentPermission>()

    suspend fun requestPermission(
        permission: AgentPermission,
        description: String,           // "Luo 3 bookmarkkia jakeille Matt.5:3-5"
        details: List<String>?         // Yksityiskohtainen lista
    ): PermissionResult

    fun hasSessionPermission(permission: AgentPermission): Boolean
    fun revokeSessionPermissions()
}
```

---

### 4. Note/Entry-tyypit ja Markdown-tuki

**Tarkoitus:** Laajennetaan bookmark-noteja ja StudyPad-entryjä tukemaan erilaisia sisältötyyppejä (plain text, HTML, Markdown).

#### 4.1 Nykyinen tilanne

**Bookmark notes:**
- `BibleBookmarkNotes.notes: String` - HTML-fragmentti (voi olla myös pelkkää tekstiä ilman tageja)
- `GenericBookmarkNotes.notes: String` - sama

**StudyPad entries:**
- `StudyPadTextEntryText.text: String` - HTML-fragmentti

**Huom:** Nykyään ei ole type-kenttää. Sisältö on käytännössä HTML, mutta voi olla myös pelkkää tekstiä jos käyttäjä ei ole lisännyt formatointeja.

#### 4.2 Uusi tietorakenne

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

#### 4.3 Editori-strategia

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

#### 4.4 AI-actionit noteille ja entryille

**Uusi PromptContext: NOTE_EDITOR**
- Näkyy kun käyttäjä on muokkaamassa notea tai StudyPad-entryä
- Kontekstina on noten/entryn sisältö

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

#### 4.5 Bookmark kokonaiselle GenBook-sivulle

**Käyttötapaus:** Käyttäjä haluaa luoda bookmarkin kokonaiselle kommentaarisivulle tai AI-dokumentin sivulle (ilman tekstivalintaa).

**Toteutus:**
- Luodaan `GenericBookmark` joka kohdistuu koko sivuun:
  - `book` = dokumentin initials (esim. "MHC", "MyDocs")
  - `key` = sivun avain
  - `startOrdinal = null`, `endOrdinal = null` (koko sivu)
  - `startOffset = null`, `endOffset = null` (ei tekstivalintaa)
- Bookmark toimii kuten muutkin bookmarkit (labelit, notet, jne.)
- Jos labeliin on linkitetty → näkyy myös StudyPadissa

**Tarvittava muutos GenericBookmark-entiteettiin:**
- Nykyään ordinalit/offsetit ovat pakollisia tekstivalinnalle
- Laajennetaan tukemaan "koko sivu" -tapausta: kun kaikki null → tarkoittaa koko sivua
- Tämä on pieni muutos, ei pitäisi rikkoa olemassa olevaa toiminnallisuutta
- Olemassa oleva bookmark-käyttäytyminen (StudyPad, navigointi) toimii samoin kuin ennenkin

---

### 5. My Documents (Pseudo-GenBooks)

**Tarkoitus:** Käyttäjä ja AI voivat luoda omia dokumentteja (GenBook-tyyppisiä), joiden sivut tallennetaan tietokantaan. Jokainen dokumentti rekisteröidään omana GenBookinaan JSwordille.

**Konsepti:**
- `MyDocument` = yksi dokumentti (rekisteröidään JSwordille GenBookina)
- `MyDocumentPage` = dokumentin sivu (TOC-entry)
- Käyttäjä voi luoda dokumentteja My Documents -näkymästä
- AI tallentaa sivuja oletuksena "AI Documents" -dokumenttiin (ei luo uusia dokumentteja, ellei promptissa erikseen pyydetä)
- Dokumentit näkyvät sekä My Documents -näkymässä että dokumenttivalitsimessa

**Oletusdokumentti "AI Documents":**
- Luodaan automaattisesti kun AI tallentaa ensimmäisen sivun (tai sovelluksen käynnistyksessä)
- AI käyttää tätä oletuksena säiliönä luomilleen sivuille
- Promptissa voi erikseen määritellä toisen kohteen (esim. "Tallenna uuteen dokumenttiin nimeltä X")
- Käyttäjä voi halutessaan siirtää sivuja toisiin dokumentteihin tai poistaa oletusdokumentin

**Tietokantarakenne:**
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

**Sisältötyypit:**
- **MARKDOWN** (oletus): Markdown-teksti joka renderöidään HTML:ksi. Voi sisältää AndBible-linkkejä (sword://, osis://, ab-w://). Helpoin ja yleisin vaihtoehto.
- **HTML**: HTML-fragmentti. Käytetään kun tarvitaan enemmän kontrollia kuin Markdown tarjoaa (esim. monimutkaiset taulukot, erityismuotoilut).
- **OSIS**: OSIS XML -fragmentti. Renderöidään kuten muutkin OSIS-dokumentit (Raamatut, kommentaarit). Hyödyllinen kun AI prosessoi OSIS-lähdettä ja säilyttää muotoilun.

**Integraatio JSword-järjestelmään:**
- Jokainen `MyDocument` rekisteröidään omana GenBookinaan
- `MyDocumentBook` extends `AbstractBook` / implementoi GenBook-rajapinnan
- Rekisteröidään `Books.installed().addBook()` kun dokumentti luodaan
- Poistetaan rekisteröinnistä kun dokumentti poistetaan
- Näkyy dokumenttivalitsimessa GenBook-kategorian alla
- TOC generoidaan dokumentin `MyDocumentPage`-riveistä 

**My Documents -näkymä (UI):**
- Erillinen näkymä, päävalikossa (main menu) uutena entrynä
- Lista kaikista dokumenteista:
  - Nimi, kuvaus, sivumäärä, luontiaika
  - AI-merkintä jos `sourcePromptId != null`
- Toiminnot:
  - "Luo uusi dokumentti" -nappi
  - Dokumentin muokkaus (nimi, kuvaus)
  - Dokumentin poisto (varoitusdialogi)
  - Avaa dokumentti (siirtyy dokumenttivalitsimeen)

**Markdown ja linkit:**

My Documents käyttää Markdownia sisältöformaattina. Ristiviittaukset käyttävät AndBiblen olemassa olevia protokollia:

```markdown
## Vuorisaarnan analyysi

Jeesus aloittaa opetuksensa [autuaaksijulistuksilla](sword://KJV/Matt.5.3-12).

Vertaa myös Luukkaan versiota [Luuk.6:20-23](sword://KJV/Luke.6.20-23).

Katso myös [kommentaari](sword://MHC/Matt.5.3) Matthew Henryltä.

Lisätietoa [sanan "makarios" merkityksestä](sword://MyDocs/strongs-makarios).
```

**Olemassa olevat linkkiprotokollat AndBiblessa:**
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

**Linkkien käsittely:**
- Markdown-renderöinnissä tunnistetaan nämä protokollat
- Vue.js: `window.location.assign(url)` → WebViewClient sieppaa
- Android: `BibleView.openLink(Uri)` käsittelee navigoinnin
- Olemassa oleva infra: `UriAnalyzer.kt`, `BibleView.kt`

**Markdown-prosessointi (Vue.js-puolella):**
- Markdown-sisältö lähetetään sellaisenaan frontille
- Vue.js renderöi MD → HTML (marked.js tai vastaava)
- Linkit muunnetaan klikattaviksi (sword://, osis://, ab-w:// jne.)
- Myöhemmin tuleva MD-editori on myös frontissa → yhtenäinen ratkaisu

**Työkalut dokumenteille:** Katso AgentReadTools (osio 2.1) ja AgentWriteTools (osio 2.3).

**Käyttötapauksia:**
- **Markdown**: Tiivistelmät, analyysit, muistiinpanot - AI kirjoittaa vapaamuotoista tekstiä
- **OSIS**: Kun AI prosessoi OSIS-sisältöä (esim. lisää annotaatioita jakeisiin, yhdistää jakeita eri käännöksistä säilyttäen muotoilun)

---

### 6. Status/Logi-ikkuna

**Tarkoitus:** Käyttäjä näkee reaaliaikaisesti mitä agentti tekee, ja voi hyväksyä/hylätä toimintoja.

**Toteutus: BottomSheetDialogFragment**

Material Design -komponentti joka tarjoaa:
- **Draggable**: Käyttäjä voi vetää ylös/alas
- **Kolme tilaa:**
  - Collapsed (pieni badge/status)
  - Half-expanded (lista logeista)
  - Fully expanded (yksityiskohdat + permission-dialogit)
- **Non-blocking**: Ei estä BibleViewin käyttöä
- **Workspace-scoped**: Logi on workspace-kohtainen

**Toteutusrakenne:**
```kotlin
class AgentLogBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(...): View {
        // Layout: drag handle, status text, RecyclerView logeille
        return inflater.inflate(R.layout.agent_log_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Lataa workspace-kohtainen sessio
        val workspaceId = WindowRepository.getInstance().activeWindow.workspaceId
        viewModel.loadSessionLog(workspaceId)
    }
}

class AgentSessionManager {
    // Workspace -> Session mapping
    private val activeSessions = mutableMapOf<IdType, AgentSession>()

    data class AgentSession(
        val workspaceId: IdType,
        val startTime: Long,
        val logEntries: MutableList<AgentLogEntry>
    )
}
```

**Sessio:**
- Workspace-kohtainen (eri workspacet = eri sessiot)
- Säilyy niin kauan kuin workspace on aktiivinen tai sovellus elossa
- Voidaan tallentaa väliaikaisesti tietokantaan jos prosessi tapetaan

**Integraatio offset-systeemiin:**

Bottom Sheet nostaa itseään ja window buttonseja kuten speak transport bar:
- Bottom Sheet käyttää `translationY(-bottomOffset1)` nostautuakseen navigation barin yläpuolelle
- `bottomOffsetForWebView` laajennetaan sisältämään Agent Log Bottom Sheet korkeus:
  ```kotlin
  val bottomOffsetForWebView get() =
      (if (transportBarVisible) transportBarHeight else 0) +
      (if (restoreButtonsVisible) windowButtonHeight else 0) +
      (if (agentLogVisible) agentLogHeight else 0)  // UUSI
  ```
- Tämä nostaa automaattisesti:
  - Window buttonit (restoreButtonsContainer käyttää `bottomOffset2`)
  - WebView-padding (BibleView saa `bottomOffsetForWebView`)
- Kun Bottom Sheet avataan/suljetaan → kutsutaan `updateOffsets()` jotta WebView ja window buttons päivittyvät

**Logi-entryn rakenne:**
```kotlin
data class AgentLogEntry(
    val id: IdType,
    val timestamp: Long,
    val type: LogEntryType,        // INFO, ACTION, PERMISSION_REQUEST, ERROR
    val message: String,
    val details: String?,
    val status: EntryStatus,       // PENDING, APPROVED, DENIED, COMPLETED, FAILED
    val relatedPermission: AgentPermission?
)

enum class LogEntryType {
    INFO,                // "Haetaan kommentaareja..."
    ACTION,              // "Luotiin bookmark: Matt.5:3"
    PERMISSION_REQUEST,  // "Haluatko sallia 5 bookmarkin luonnin?"
    ERROR                // "API-kutsu epäonnistui"
}
```

---

### 7. Agentin suoritusputki (Execution Pipeline)

**Kokonaisvirta:**

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
   - Write-operaatiot: kysytään lupa (jos konfirmointi päällä) → suoritetaan
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

**Tool Call -formaatti (OpenAI function calling):**
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

**LLM voi ketjuttaa kutsuja:**
```
1. LLM kutsuu getCommentaries("Matt.5:3-12")
2. Saa kommentaarit → analysoi
3. LLM kutsuu addMyDocumentPage("Vuorisaarnan tiivistelmä", markdownContent)
4. Sivu luotu My Documents -dokumenttiin → LLM palauttaa valmis-viestin
```

**Keskeinen periaate:** AgentPrompt määrittelee vain mitä käyttäjä haluaa saavuttaa. LLM päättää itse miten tavoite saavutetaan käyttämällä saatavilla olevia työkaluja.

---

### 8. UI-integraatiot

#### 8.1 One Tap Actions -integraatio (ensisijainen LLM Actions -paikka)

**"LLM Action" One Tap Actionina:**
- Uusi vaihtoehto One Tap Actions -listaan (Copy, Share, Bookmark, Compare, **LLM Action**, jne.)
- Kun käyttäjä valitsee jakeet ja klikkaa "LLM Action":
  1. Avautuu promptivalikko (lista käytettävissä olevista prompteista)
  2. Promptit filtteröidään `showIn`-kentän mukaan (VERSE_SELECTION)
  3. Käyttäjä valitsee promptin → agentti suorittaa sen valituille jakeille

**Käyttövirta:**
```
Valitse jakeet → One Tap Action "LLM Action" → Valitse prompt → Agentti suorittaa
```

**Tulevaisuudessa (ei MVP):** Prompt Managerissa voisi olla "Add to One Tap Actions" -valinta, jolla yksittäisen promptin saa suoraan One Tap Actioniksi ilman välivalikoita. Esim. "Käännä suomeksi" näkyisi suoraan One Tap Actions -listassa.

#### 8.2 TEXT_SELECTION -konteksti

**TEXT_SELECTION kattaa kaksi käyttötapausta:**

1. **Vapaa tekstivalinta (pitkä painallus → selection → kontekstivalikko):**
   - Kun käyttäjä valitsee tekstiä vapaasti missä tahansa dokumentissa
   - Kontekstivalikossa "LLM Actions" -vaihtoehto

2. **One Tap Actions GenBookeissa/kommentaareissa:**
   - Kun käyttäjä valitsee tekstiä napauttamalla ei-Raamattu-dokumenteissa
   - One Tap Actions -valikossa "LLM Action" -vaihtoehto

Promptit filtteröidään `showIn = TEXT_SELECTION` mukaan molemmissa tapauksissa.

**Ero VERSE_SELECTION vs TEXT_SELECTION:**
- `VERSE_SELECTION`: Kokonaisia jakeita valittu napauttamalla (vain Raamatut)
- `TEXT_SELECTION`: Vapaa tekstivalinta TAI One Tap Actions GenBookeissa/kommentaareissa

#### 8.3 Window Menu (Window Button popup)

**Ikkunan oikean ylälaidan Window Button → popup-menu:**
- Dokumenttikohtaiset LLM-actionit
- Promptit filtteröidään `showIn = WINDOW_MENU` mukaan
- Esimerkkejä:
  - "Translate this document" (→ kääntää nykyisen dokumentin)
  - "Summarize chapter" (→ tiivistelmä luvusta)
  - Muut dokumenttikohtaiset Prompt Manager -promptit

**Huomio:** Tämä voi korvata/täydentää nykyistä TDS-translateTo -lähestymistapaa.

#### 8.4 Workspace Menu (Toolbar 3-dot menu)

**Toolbarin oikean laidan 3-dot menu:**
- Workspace-kohtaiset LLM-actionit
- Promptit filtteröidään `showIn = WORKSPACE_MENU` mukaan
- Esimerkkejä:
  - "Summarize all open windows" (→ tiivistelmä kaikista avoimista ikkunoista)
  - "Create study plan for current reading" (→ luo lukusuunnitelma)
  - Muut workspace-laajuiset toiminnot

**Huom:** Tämä on matalan prioriteetin ominaisuus - useimmat promptit toimivat dokumentti- tai tekstivalintatasolla.

#### 8.5 LLM-ikoni toolbarissa

Nykyinen `llmIcon` toolbarissa:
- **Pieni ikoni** - ei tilaa badgelle/numerolle
- **Näyttää onko LLM-prosessi käynnissä** (LLM Mode tai LLM Action)
- **Ei klikattava** - liian pieni interaktiiviseen käyttöön

**Laajennettu tieto Agent Log -komponentissa:**
- Badge/numero pending requesteista
- Yksityiskohtainen status
- Klikkaus avaa täyden lokin

#### 8.6 Text Display Settings (TDS)

TDS:ssä valitaan **LLM Mode -prompt** (korvaa vanhan `translateTo`):
- Dropdown jossa LLM_MODE-tyyppiset promptit Prompt Managerista
- "Ei LLM-prosessointia" (oletus)
- "Käännä käyttöliittymän kielelle" (oletusprompt)
- Käyttäjän luomat LLM Mode -promptit

**Huom:** `translateTo`-asetus korvataan `llmModePromptId`-kentällä joka viittaa AgentPrompt-entiteettiin.

#### 8.7 Settings → AI

**LLM-perusasetukset (Settings):**
- API key, endpoint, model
- Nämä ovat aina näkyvissä Settingsissä (käyttäjä voi konfiguroida)

#### 8.8 Päävalikko

**My Documents (näkyy aina):**
- Päävalikossa StudyPadien vieressä/alla
- Ei riipu LLM-konfiguraatiosta
- Käyttäjä voi luoda dokumentteja myös ilman AI:ta

**AI -osio (näkyy vain kun LLM konfiguroitu):**
- **Prompt Manager** - Hallitse ja luo prompteja
- **AI Settings** - Permissions, cache, historia

**Kaikki LLM-toiminnot piilotetaan kun LLM ei konfiguroitu:**
- "LLM Action" ei näy One Tap Actionsissa
- "LLM Actions" ei näy kontekstivalikossa
- Window Menu ei näytä LLM-toimintoja
- TDS:ssä ei näy LLM Mode -valintaa
- Päävalikosta puuttuu "AI" -osio (mutta My Documents näkyy)

**Tarkistus:** `CommonUtils.settings.llmApiKey.isNotBlank()` tai vastaava

---

### 9. Uusien ikkunoiden LLM-periytyminen (TDS)

**Ongelma:** Kun LLM-käännösikkunnasta avataan uusi ikkuna (esim. linkki), pitäisikö uusi ikkuna myös olla käännösmoodissa?

**Ratkaisu:** Uusi ikkuna EI peri LLM-asetuksia:
- Linkki-ikkunat (linked windows): ei koskaan peri
- Itsenäiset uudet ikkunat: ei peri oletuksena

**Perustelu:**
- Käyttäjä saattaa haluta nähdä alkuperäisen tekstin vertailua varten
- Käännösoperaatiot voivat olla kalliita (API-kutsut)
- Käyttäjä voi aina manuaalisesti aktivoida LLM-moodin uudessa ikkunassa

**Toteutus:** `CurrentPageBase.currentDocument` -getterissä tarkistetaan onko kyseessä linkki-ikkuna tai uusi ikkuna.

---

### 10. TDS LLM Mode vs. LLM Actions

**Sama prompt voi toimia molemmissa moodeissa** - konteksti määrittelee käyttäytymisen:

1. **LLM Mode (TDS:ssä)** - Passiivinen, automaattinen
   - Prompt triggeröidään TDS:n kautta
   - Jae-kerrallaan prosessointi, tallennus cacheen
   - Näytetään dokumentissa inline
   - Sopii: käännökset, kun halutaan aina nähdä prosessoitu versio

2. **LLM Action (manuaalinen)** - Aktiivinen, triggeröidään erikseen
   - Prompt triggeröidään tekstivalinnasta, window menusta tms.
   - AgentExecutor + tool calls
   - Tulos tallennetaan AI Documents -sivulle ja avataan
   - Sopii: tiivistelmät, analyysit, kirjanmerkkien luonti

**Esimerkkiprompt "Käännä suomeksi"** voi olla `showIn = {TDS, VERSE_SELECTION, WINDOW_MENU}`:
- TDS:stä: online-käännös dokumentille (LLM Mode -putki)
- One Tap Actionista: käännös tallennetaan AI Documents -sivulle (LLM Action -putki)

**Hyödyt:**
- Käyttäjä ei tarvitse luoda duplikaattiprompteja
- Yksinkertaisempi Prompt Manager UI
- Joustavampi käyttö - sama prompt, eri konteksti

**Tekninen toteutus:**
- `showIn` sisältää `TDS` → prompt näkyy TDS:n dropdownissa
- Kun triggeröidään TDS:stä → LLM Mode -putki (LlmProcessedBook, cache)
- Kun triggeröidään muualta → LLM Action -putki (AgentExecutor, tool calls, AI Documents)

---

### 11. Device Sync -tuki

**Tarkoitus:** LLM-prosessoidut sisällöt, promptit ja My Documents synkronoituvat laitteiden välillä samalla tavalla kuin bookmarkit ja workspacet.

#### 11.1 Tietokantajaottelu

**LlmDatabase** (uudelleennimetty LlmProcessingDatabase:sta, synkronoitava):

```kotlin
@Database(
    entities = [
        LlmProcessingCacheEntry::class,  // Prosessoidut jakeet (JO OLEMASSA)
        AgentPrompt::class,               // Käyttäjän promptit (UUSI)
        LogEntry::class,                  // Sync-vaatimus
        SyncConfiguration::class,         // Sync-vaatimus
        SyncStatus::class,                // Sync-vaatimus
    ],
    version = LLM_DATABASE_VERSION
)
abstract class LlmDatabase: SyncableRoomDatabase() {
    abstract fun llmProcessingDao(): LlmProcessingDao
    abstract fun agentPromptDao(): AgentPromptDao
    companion object {
        const val dbFileName = "llm.sqlite3"  // Uudelleennimetty llm_processing.sqlite3:sta
    }
}
```

**MyDocumentsDatabase** (uusi, synkronoitava):

```kotlin
@Database(
    entities = [
        MyDocument::class,
        MyDocumentPage::class,
        LogEntry::class,                  // Sync-vaatimus
        SyncConfiguration::class,         // Sync-vaatimus
        SyncStatus::class,                // Sync-vaatimus
    ],
    version = MY_DOCUMENTS_DATABASE_VERSION
)
abstract class MyDocumentsDatabase: SyncableRoomDatabase() {
    abstract fun myDocumentsDao(): MyDocumentsDao
    companion object {
        const val dbFileName = "mydocuments.sqlite3"
    }
}
```

#### 11.2 Miksi LlmProcessingCacheEntry synkronoidaan?

`LlmProcessingCacheEntry` EI ole pelkkä välimuisti vaan **pysyvä tallennus**:

1. **LLM ei ole deterministinen** - sama pyyntö voi tuottaa eri tuloksen eri kerroilla
2. **Bookmarkit viittaavat prosessoituun sisältöön** - referenssit (ordinalit, offsetit) rikkoutuvat jos sisältö muuttuu
3. **Käyttäjän odotus** - sama käännetty teksti pitää näkyä kaikilla laitteilla
4. **Kustannukset** - ei tarvitse maksaa uudelleen API-kutsusta

#### 11.3 SyncableRoomDatabase -vaatimukset

Jokainen synkronoitava tietokanta tarvitsee:
1. `LogEntry` entity - muutosloki synkronointia varten
2. `SyncConfiguration` entity - sync-asetukset
3. `SyncStatus` entity - synkronoinnin tila
4. Perii `SyncableRoomDatabase` luokasta

#### 11.4 Toteutus DatabaseContainerissa

```kotlin
// DatabaseContainer.kt - muutokset
class DatabaseContainer {
    // Olemassa olevat (synkronoitavat)
    val bookmarkDb: BookmarkDatabase
    val workspaceDb: WorkspaceDatabase
    val readingPlanDb: ReadingPlanDatabase

    // MUUTETTU: LlmProcessingDatabase → LlmDatabase (synkronoitava)
    val llmDb: LlmDatabase  // Oli: llmProcessingDb: LlmProcessingDatabase

    // UUSI (synkronoitava)
    val myDocumentsDb: MyDocumentsDatabase

    // Ei-synkronoitavat
    val repoDb: RepoDatabase
    val settingsDb: SettingsDatabase
    val temporaryDb: TemporaryDatabase
}
```

#### 11.5 Migraatio

- `llm_processing.sqlite3` → `llm.sqlite3` (uudelleennimeäminen koodissa, migraatiota ei tarvita koska ei olla vielä tuotannossa)
- Lisätään `LogEntry`, `SyncConfiguration`, `SyncStatus` entiteetit
- Lisätään `AgentPrompt` taulu
- Muutetaan `RoomDatabase` → `SyncableRoomDatabase`

---

## Toteutusjärjestys (ehdotus)

**Periaate:** Ensin infrastruktuuri ja tietorakenteet, sitten agentin työkalut jotka käyttävät niitä.

### Vaihe 1: Bugfixit & LLM Mode -parannukset
- [ ] Konfirmointi-dialogin korjaus (per luku, ei per jae)
- [ ] Linkki-ikkunat: estä LLM-moodin periytyminen
- [x] ~~TDS-dokumentin vaihto kun asetusta muutetaan~~ ✅ KORJATTU

### Vaihe 2: Tietorakenteiden laajennukset
**Nämä ovat edellytyksiä agentin write-työkaluille:**
- [ ] `sourcePromptId` kenttä Bookmark-entiteetteihin (AI-lähdemerkintä)
- [ ] `TextContentType` enum (HTML/MARKDOWN)
- [ ] `contentType` ja `sourcePromptId` kentät BibleBookmarkNotes/GenericBookmarkNotes -entiteetteihin
- [ ] `contentType`, `sourcePromptId`, `sourceDocumentInitials`, `sourceKey` kentät StudyPadTextEntry -entiteettiin
- [ ] Tietokantamigraatiot

### Vaihe 3: My Documents (Pseudo-GenBooks)
**Edellytys agentin `addMyDocumentPage()` -työkalulle:**
- [ ] MyDocument ja MyDocumentPage tietokantaentiteetit ja DAO
- [ ] MyDocumentBook (JSword GenBook -integraatio, per dokumentti)
- [ ] Dokumenttien rekisteröinti/poisto JSwordilta dynaamisesti
- [ ] TOC-generointi dokumentin sivuista
- [ ] My Documents -hallintanäkymä (lista, luonti, muokkaus, poisto)
- [ ] Markdown-renderöinti (päätös: Android vs Vue.js)
- [ ] Linkkiprotokollien (sword://, osis://) käsittely Markdown-renderöinnissä
- [ ] Document selector -integraatio

### Vaihe 4: Prompt Manager
- [ ] AgentPrompt tietokantaentiteetti ja DAO
- [ ] Prompt Manager UI (päävalikko → AI → Prompt Manager)
- [ ] Oletuspromptien luonti (käännös, tiivistelmä)
- [ ] `showIn` (PromptContext) -filtteröinti

### Vaihe 5: Agentin perusinfrastruktuuri
- [ ] AgentExecutor luokka
- [ ] Tool call parsing (OpenAI function calling -formaatti)
- [ ] AgentReadTools interface ja toteutus
- [ ] AgentWriteTools interface ja toteutus (käyttää vaiheissa 2-3 luotuja rakenteita)

### Vaihe 6: Permissions-järjestelmä
- [ ] AgentPermissionManager
- [ ] Permission-dialogit (selkeä kuvaus mitä tehdään)
- [ ] Session-tason luvat
- [ ] Settings: oletusasetukset per permission

### Vaihe 7: UI-integraatiot
- [ ] One Tap Actions: "LLM Action" -vaihtoehto
- [ ] Kontekstivalikko: "LLM Actions" vapaa tekstivalinta
- [ ] Window Menu: LLM-toiminnot
- [ ] TDS: LLM Mode -prompt valinta (korvaa `translateTo`)
- [ ] Päävalikko → AI -osio (kun LLM konfiguroitu)

### Vaihe 8: Status/Logi & UX
- [ ] AgentLogEntry rakenne
- [ ] Agent Log Bottom Sheet
- [ ] Integraatio offset-systeemiin
- [ ] Toast-ilmoitukset kun konfirmointi off

### Vaihe 9: Viimeistely
- [ ] Käyttäjätestaus ja palautteen keräys
- [ ] TDS vs manuaalinen käännös -päätös
- [ ] Ylimääräisten ominaisuuksien siivous ennen julkaisua
- [ ] Dokumentaatio

---

## Avoimet kysymykset

1. ~~**Markdown-toteutus:** Android-puoli (commonmark-java) vs Vue.js-puoli (marked.js)?~~ **PÄÄTETTY:** Vue.js-puoli (marked.js tai vastaava). Markdown-sisältö lähetetään sellaisenaan frontille, frontti renderöi. Myöhemmin tuleva MD-editori on myös frontissa → yhtenäinen ratkaisu.
2. **Multi-turn keskustelu:** Tarvitaanko myöhemmin? Arkkitehtuurin pitäisi mahdollistaa.
3. **Agentin "muisti":** Pitäisikö agentti muistaa aiemmat keskustelut/kontekstit?
4. **Rate limiting:** Miten hallitaan useita samanaikaisia agentti-operaatioita?
5. **Offline-tuki:** Voiko joitain operaatioita tehdä paikallisella mallilla?
6. **Kustannusten näyttäminen:** Miten käyttäjälle kerrotaan token-määrät ja arvioidut hinnat?
7. **TDS vs manuaalinen käännös:** Kumpi lähestymistapa on käyttäjälle kätevin?
8. **"Request button":** Pitäisikö LLM-moodin vaatia eksplisiittinen napinpainallus ennen latausta?

---

## Muistiinpanoista poimittuja ideoita (tulevaisuus)

Nämä ovat hyviä ideoita jotka voivat tulla myöhemmin, mutta eivät ole MVP:ssä:

### Kustannusseuranta
- Token count per operaatio
- Arvioitu hinta per provider
- Budget alerts
- Overlay joka näyttää prosessoinnin laajuuden ennen hyväksyntää

### Request Button -moodi
- Vaihtoehtoinen tapa: LLM-sisältö ei lataudu automaattisesti
- Käyttäjä painaa nappia ladatakseen käännetyn/prosessoidun version
- Voi olla parempi UX kuin automaattinen lataus

### Edistyneet Prompt Manager -ominaisuudet
- Prompt-templatet joissa muuttujia
- Prompt-ketjut (yksi prompt triggeröi toisen)
- Jaetut promptit (community prompts)

### Multi-turn Agent Mode
- Keskusteluikkuna jossa käyttäjä ja agentti voivat iteroida
- Agentti ehdottaa, käyttäjä hyväksyy/muokkaa, agentti jatkaa
- Vaatii kehittyneemmän UI:n (chat view)

---

## Tekniset huomiot

### Tiedostorakenne (ehdotus)

```
app/src/main/java/net/bible/service/agent/
├── AgentExecutor.kt           # Pääsuorituslogiikka
├── AgentPermissionManager.kt  # Luvanhallinta
├── AgentPromptManager.kt      # Prompt-hallinta
├── tools/
│   ├── AgentReadTools.kt      # Lukutyökalut
│   ├── AgentWriteTools.kt     # Kirjoitustyökalut
│   └── ToolCallParser.kt      # Tool call -jäsennys
├── mydocuments/
│   ├── MyDocumentBook.kt      # Pseudo-GenBook per dokumentti
│   ├── MyDocumentBookFactory.kt # Luo ja rekisteröi dokumentit JSwordille
│   └── MarkdownProcessor.kt   # MD→HTML (jos valitaan Android-puolella)
└── log/
    ├── AgentLogEntry.kt       # Logi-rakenne
    └── AgentLogManager.kt     # Logienhallinta

app/src/main/java/net/bible/android/database/agent/
├── AgentEntities.kt           # AgentPrompt, MyDocumentPage, AgentLogEntry
└── AgentDao.kt                # DAO

app/bibleview-js/src/
├── components/agent/
│   ├── AgentOverlay.vue       # Status-overlay
│   └── PermissionDialog.vue   # Lupa-dialogi
└── composables/
    └── agent.ts               # Agent-tilan hallinta
```

### Riippuvuudet olemassa olevaan koodiin

- **LlmProcessingService**: Käytetään API-kutsuihin (laajennettava tool call -tuella)
- **BookmarkControl**: Käytetään bookmark-operaatioihin
- **StudyPad-entiteetit**: Käytetään StudyPad-kirjoitukseen
- **FakeBookFactory**: Malli pseudodokumenttien rekisteröintiin
- **BibleJavascriptInterface**: Laajennetaan agent-metodeilla

---

## Yhteenveto

Tämä suunnitelma laajentaa AndBiblen AI-kyvykkyyksiä käännöstuesta täysimittaiseksi agenttiympäristöksi. Keskeistä on:

1. **Käyttäjäkontrolli**: Prompt Manager + Permissions pitävät käyttäjän ohjauksessa
2. **Läpinäkyvyys**: Status/Logi-ikkuna näyttää mitä agentti tekee
3. **Laajennettavuus**: Kovakoodatut työkalut ovat selkeät mutta kattavat
4. **Integraatio**: Hyödynnetään olemassa olevia rakenteita (bookmarks, studypads, JSword)

Toteutus voidaan tehdä vaiheittain, aloittaen perusinfrastruktuurista ja laajentaen toiminnallisuutta iteratiivisesti.
