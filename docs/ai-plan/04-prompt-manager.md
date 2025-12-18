# Prompt Manager

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Keskitetty hallinta kaikille LLM-prompteille. Korvaa myös kovakoodatun `translateTo`-asetuksen ja mahdollistaa käyttäjän omat promptit.

---

## Tietokantarakenne

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

---

## Promptin näkyvyys ja konteksti

**Käyttäjä valitsee promptia luodessa/muokatessa** missä paikoissa se näkyy. **Sama prompt voi toimia sekä LLM Modessa että LLM Actionina** - konteksti (mistä prompt triggeröidään) määrittelee käyttäytymisen.

### Esimerkkejä

- **"Käännä suomeksi"**: `showIn = {TDS, VERSE_SELECTION, WINDOW_MENU}` - toimii kaikkialla
  - TDS:stä: online-prosessointi dokumentille (LLM Mode -putki)
  - One Tap Actionista: luo käännös AI Documents -sivulle (LLM Action -putki)
- **"Tiivistelmä"**: `showIn = {VERSE_SELECTION, WINDOW_MENU}` - ei TDS:ssä (ei järkevä online-prosessointiin)
- **"Analysoi valinta"**: `showIn = {VERSE_SELECTION, TEXT_SELECTION}` - vain tekstivalinnoista

### Kontekstin vaikutus suoritukseen

- **TDS-konteksti → LLM Mode -putki:** Jae-kerrallaan prosessointi, tallennus cacheen, näytetään dokumentissa inline
- **Muu konteksti → LLM Action -putki:** AgentExecutor, tool calls, tulos tallennetaan AI Documents -sivulle ja avataan

---

## AgentPrompt on yksinkertainen

AgentPrompt sisältää vain promptin tekstin ja metatiedot. LLM itse päättää promptin perusteella:
- Mitä työkaluja käyttää (bookmarkit, dokumentit, studypadit, jne.)
- Mitä lisäkontekstia se tarvitsee tehtävän suorittamiseksi (käyttämällä työkaluja)
- Mihin labeliin/dokumenttiin tallennetaan
- Mitä kieltä käytetään käännöksessä
- jne.

---

## Konfirmointi

- **LLM Mode:** Konfirmointi on käyttäjävalinta (asetus), koska LLM:n käyttö on implisiittisempää (tapahtuu taustalla dokumenttia selatessa)
- **LLM Action:** Ei tarvitse konfirmointia, koska käyttäjä triggeröi manuaalisesti valitsemalla promptin
- **Write-operaatiot:** Permissions-järjestelmä hoitaa erikseen → [05b-permissions.md](05b-permissions.md)

---

## Esimerkkiprompteja

### 1. Käännösprompt (LLM_MODE)

```
Käännä seuraava teksti suomeksi. Säilytä XML-rakenne muuttumattomana,
käännä vain tekstisisältö tagien välissä.
```

### 2. Tiivistelmä kommentaareista (LLM_ACTION)

```
Hae kaikki asennetut kommentaarit valituille jakeille ja tee niistä tiivistelmä.
```
(Tallennus AI Documents -sivulle tapahtuu automaattisesti default behaviorin mukaan)

### 3. Korostuskirjanmerkit (LLM_ACTION)

```
Analysoi valittu teksti ja tunnista teologisesti tärkeimmät kohdat.
Luo bookmark jokaiselle tärkeälle kohdalle sopivalla notella.
```

---

## Oletuspromptit (Default Prompts)

**Kun LLM konfiguroidaan ensimmäistä kertaa** (API key asetetaan), luodaan automaattisesti joukko oletusprompteja - samaan tapaan kuin esimerkkibookmarkit luodaan uudelle käyttäjälle.

### Oletuspromptit

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

### Toteutus

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

---

## Promptin rakenne (System Prompt)

LLM:lle lähetettävä system prompt koostuu kahdesta osasta:

### 1. Automaattinen konteksti (generoidaan aina)

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

### 2. Käyttäjän prompti (AgentPrompt.promptTemplate)

```
Tee tiivistelmä valitusta tekstistä ja tallenna se StudyPadiin.
```

**Käyttäjän ei tarvitse** kirjoittaa template-muuttujia promptiinsa - kaikki konteksti annetaan automaattisesti. Käyttäjän prompti on yksinkertainen kuvaus siitä mitä hän haluaa tehdä.

---

## Dokumentaatiotyökalut (Reference Tools)

LLM:t eivät välttämättä tunne erikoisformaatteja kuten OSIS XML. Sen sijaan että arvailisimme milloin dokumentaatiota tarvitaan, LLM voi itse hakea sen tarvittaessa MCP-tyylisesti.

### Lukutyökaluihin lisätään

```kotlin
// Dokumentaatio/referenssit
suspend fun getOsisDocumentation(): String      // OSIS XML -formaatin referenssi
suspend fun getStrongsDocumentation(): String   // Strongs-numeroiden käyttöohje
suspend fun getLinkProtocolDocumentation(): String  // AndBible linkkiprotokollien ohje (sword://, osis://, ab-w://)
```

### Esimerkki: getOsisDocumentation() palauttaa

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

### Hyödyt

- LLM päättää itse milloin tarvitsee dokumentaatiota
- Ei turhia tokeneita system promptissa
- Helppo laajentaa uusilla dokumentaatiotyökaluilla
- Sama pattern kuin muissakin lukutyökaluissa

---

## UI-integraatio

- **Settings → "AI Prompt Manager"** - Promptien hallinta ja luonti
- **Text Display Settings (TDS)** - Valitaan käytössä oleva LLM Mode -prompt
- **Tekstivalinta-dialogi** - Näyttää relevantit LLM Action -promptit
- **Window Menu** - Dokumenttikohtaiset promptit

Katso tarkemmin → [06-ui-integrations.md](06-ui-integrations.md)

---

## Toteutustehtävät

- [ ] AgentPrompt tietokantaentiteetti ja DAO
- [ ] Prompt Manager UI (päävalikko → AI → Prompt Manager)
- [ ] Oletuspromptien luonti (käännös, tiivistelmä)
- [ ] `showIn` (PromptContext) -filtteröinti

**Huom:** Device Sync -toteutus erillisessä dokumentissa → [07-device-sync.md](07-device-sync.md)
