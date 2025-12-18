# UI-integraatiot ja Status/Logi

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Integroida LLM-toiminnot AndBiblen käyttöliittymään ja tarjota käyttäjälle näkyvyys agentin toimintaan.

---

## 1. Status/Logi-ikkuna

### Tarkoitus

Käyttäjä näkee reaaliaikaisesti mitä agentti tekee, ja voi hyväksyä/hylätä toimintoja.

### Toteutus: BottomSheetDialogFragment

Material Design -komponentti joka tarjoaa:
- **Draggable**: Käyttäjä voi vetää ylös/alas
- **Kolme tilaa:**
  - Collapsed (pieni badge/status)
  - Half-expanded (lista logeista)
  - Fully expanded (yksityiskohdat + permission-dialogit)
- **Non-blocking**: Ei estä BibleViewin käyttöä
- **Workspace-scoped**: Logi on workspace-kohtainen

### Toteutusrakenne

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

### Sessio

- Workspace-kohtainen (eri workspacet = eri sessiot)
- Säilyy niin kauan kuin workspace on aktiivinen tai sovellus elossa
- Voidaan tallentaa väliaikaisesti tietokantaan jos prosessi tapetaan

### Integraatio offset-systeemiin

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

### Logi-entryn rakenne

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

## 2. One Tap Actions -integraatio (ensisijainen LLM Actions -paikka)

### "LLM Action" One Tap Actionina

- Uusi vaihtoehto One Tap Actions -listaan (Copy, Share, Bookmark, Compare, **LLM Action**, jne.)
- Kun käyttäjä valitsee jakeet ja klikkaa "LLM Action":
  1. Avautuu promptivalikko (lista käytettävissä olevista prompteista)
  2. Promptit filtteröidään `showIn`-kentän mukaan (VERSE_SELECTION)
  3. Käyttäjä valitsee promptin → agentti suorittaa sen valituille jakeille

### Käyttövirta

```
Valitse jakeet → One Tap Action "LLM Action" → Valitse prompt → Agentti suorittaa
```

**Tulevaisuudessa (ei MVP):** Prompt Managerissa voisi olla "Add to One Tap Actions" -valinta, jolla yksittäisen promptin saa suoraan One Tap Actioniksi ilman välivalikoita.

---

## 3. TEXT_SELECTION -konteksti

TEXT_SELECTION kattaa kaksi käyttötapausta:

### 1. Vapaa tekstivalinta (pitkä painallus → selection → kontekstivalikko)
- Kun käyttäjä valitsee tekstiä vapaasti missä tahansa dokumentissa
- Kontekstivalikossa "LLM Actions" -vaihtoehto

### 2. One Tap Actions GenBookeissa/kommentaareissa
- Kun käyttäjä valitsee tekstiä napauttamalla ei-Raamattu-dokumenteissa
- One Tap Actions -valikossa "LLM Action" -vaihtoehto

Promptit filtteröidään `showIn = TEXT_SELECTION` mukaan molemmissa tapauksissa.

### Ero VERSE_SELECTION vs TEXT_SELECTION

- `VERSE_SELECTION`: Kokonaisia jakeita valittu napauttamalla (vain Raamatut)
- `TEXT_SELECTION`: Vapaa tekstivalinta TAI One Tap Actions GenBookeissa/kommentaareissa

---

## 4. Window Menu (Window Button popup)

### Ikkunan oikean ylälaidan Window Button → popup-menu

- Dokumenttikohtaiset LLM-actionit
- Promptit filtteröidään `showIn = WINDOW_MENU` mukaan
- Esimerkkejä:
  - "Translate this document" (→ kääntää nykyisen dokumentin)
  - "Summarize chapter" (→ tiivistelmä luvusta)
  - Muut dokumenttikohtaiset Prompt Manager -promptit

**Huomio:** Tämä voi korvata/täydentää nykyistä TDS-translateTo -lähestymistapaa.

---

## 5. Workspace Menu (Toolbar 3-dot menu)

### Toolbarin oikean laidan 3-dot menu

- Workspace-kohtaiset LLM-actionit
- Promptit filtteröidään `showIn = WORKSPACE_MENU` mukaan
- Esimerkkejä:
  - "Summarize all open windows" (→ tiivistelmä kaikista avoimista ikkunoista)
  - "Create study plan for current reading" (→ luo lukusuunnitelma)
  - Muut workspace-laajuiset toiminnot

**Huom:** Tämä on matalan prioriteetin ominaisuus - useimmat promptit toimivat dokumentti- tai tekstivalintatasolla.

---

## 6. LLM-ikoni toolbarissa

Nykyinen `llmIcon` toolbarissa:
- **Pieni ikoni** - ei tilaa badgelle/numerolle
- **Näyttää onko LLM-prosessi käynnissä** (LLM Mode tai LLM Action)
- **Ei klikattava** - liian pieni interaktiiviseen käyttöön

### Laajennettu tieto Agent Log -komponentissa

- Badge/numero pending requesteista
- Yksityiskohtainen status
- Klikkaus avaa täyden lokin

---

## 7. Text Display Settings (TDS)

TDS:ssä valitaan **LLM Mode -prompt** (korvaa vanhan `translateTo`):
- Dropdown jossa LLM_MODE-tyyppiset promptit Prompt Managerista
- "Ei LLM-prosessointia" (oletus)
- "Käännä käyttöliittymän kielelle" (oletusprompt)
- Käyttäjän luomat LLM Mode -promptit

**Huom:** `translateTo`-asetus korvataan `llmModePromptId`-kentällä joka viittaa AgentPrompt-entiteettiin.

---

## 8. Settings → AI

### LLM-perusasetukset (Settings)

- API key, endpoint, model
- Nämä ovat aina näkyvissä Settingsissä (käyttäjä voi konfiguroida)

---

## 9. Päävalikko

### My Documents (näkyy aina)

- Päävalikossa StudyPadien vieressä/alla
- Ei riipu LLM-konfiguraatiosta
- Käyttäjä voi luoda dokumentteja myös ilman AI:ta

### AI -osio (näkyy vain kun LLM konfiguroitu)

- **Prompt Manager** - Hallitse ja luo prompteja
- **AI Settings** - Permissions, cache, historia

### Kaikki LLM-toiminnot piilotetaan kun LLM ei konfiguroitu

- "LLM Action" ei näy One Tap Actionsissa
- "LLM Actions" ei näy kontekstivalikossa
- Window Menu ei näytä LLM-toimintoja
- TDS:ssä ei näy LLM Mode -valintaa
- Päävalikosta puuttuu "AI" -osio (mutta My Documents näkyy)

**Tarkistus:** `CommonUtils.settings.llmApiKey.isNotBlank()` tai vastaava

---

## 10. Uusien ikkunoiden LLM-periytyminen (TDS)

### Ongelma

Kun LLM-käännösikkunnasta avataan uusi ikkuna (esim. linkki), pitäisikö uusi ikkuna myös olla käännösmoodissa?

### Ratkaisu

Uusi ikkuna EI peri LLM-asetuksia:
- Linkki-ikkunat (linked windows): ei koskaan peri
- Itsenäiset uudet ikkunat: ei peri oletuksena

### Perustelu

- Käyttäjä saattaa haluta nähdä alkuperäisen tekstin vertailua varten
- Käännösoperaatiot voivat olla kalliita (API-kutsut)
- Käyttäjä voi aina manuaalisesti aktivoida LLM-moodin uudessa ikkunassa

**Toteutus:** `CurrentPageBase.currentDocument` -getterissä tarkistetaan onko kyseessä linkki-ikkuna tai uusi ikkuna.

---

## 11. TDS LLM Mode vs. LLM Actions

**Sama prompt voi toimia molemmissa moodeissa** - konteksti määrittelee käyttäytymisen:

### 1. LLM Mode (TDS:ssä) - Passiivinen, automaattinen

- Prompt triggeröidään TDS:n kautta
- Jae-kerrallaan prosessointi, tallennus cacheen
- Näytetään dokumentissa inline
- Sopii: käännökset, kun halutaan aina nähdä prosessoitu versio

### 2. LLM Action (manuaalinen) - Aktiivinen, triggeröidään erikseen

- Prompt triggeröidään tekstivalinnasta, window menusta tms.
- AgentExecutor + tool calls
- Tulos tallennetaan AI Documents -sivulle ja avataan
- Sopii: tiivistelmät, analyysit, kirjanmerkkien luonti

### Esimerkkiprompt

"Käännä suomeksi" voi olla `showIn = {TDS, VERSE_SELECTION, WINDOW_MENU}`:
- TDS:stä: online-käännös dokumentille (LLM Mode -putki)
- One Tap Actionista: käännös tallennetaan AI Documents -sivulle (LLM Action -putki)

### Hyödyt

- Käyttäjä ei tarvitse luoda duplikaattiprompteja
- Yksinkertaisempi Prompt Manager UI
- Joustavampi käyttö - sama prompt, eri konteksti

### Tekninen toteutus

- `showIn` sisältää `TDS` → prompt näkyy TDS:n dropdownissa
- Kun triggeröidään TDS:stä → LLM Mode -putki (LlmProcessedBook, cache)
- Kun triggeröidään muualta → LLM Action -putki (AgentExecutor, tool calls, AI Documents)

---

## Toteutustehtävät

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
