# AndBible AI-ominaisuudet - Yleiskatsaus

**Tämä on referenssidokumentti.** Lue tämä aina ennen jokaista taski-dokumenttia kontekstiksi.

---

## Yleiskatsaus

Tämä dokumentaatio kattaa AndBiblen kaikki AI/LLM-ominaisuudet.

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

## Tiedostorakenne-ehdotus

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

---

## Avaintiedostot nykyisestä koodipohjasta

### LLM-prosessointi (nykyinen)
- `app/src/main/java/net/bible/service/llm/LlmProcessedBook.kt` - Pseudo-book arkkitehtuuri
- `app/src/main/java/net/bible/service/llm/LlmProcessingService.kt` - API-kutsut ja cache
- `app/src/main/java/net/bible/service/llm/LlmProcessor.kt` - Prosessori-interface
- `app/src/main/java/net/bible/service/llm/processors/TranslationProcessor.kt` - Käännösprosessori
- `app/src/main/java/net/bible/android/database/llmprocessing/LlmProcessingEntities.kt` - Cache-entiteetit

### Bookmarkit ja StudyPadit
- `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt` - Bookmark-operaatiot
- `app/src/main/java/net/bible/android/database/bookmarks/BookmarkEntities.kt` - Bookmark-entiteetit (Bible)
- `app/src/main/java/net/bible/android/database/bookmarks/GenericBookmarkEntities.kt` - Generic bookmark-entiteetit

### Tietokanta
- `app/src/main/java/net/bible/service/db/DatabaseContainer.kt` - Singleton, hallitsee kaikki Room-tietokannat
- `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt` - Workspace/Window-entiteetit

### Android ↔ Vue.js kommunikaatio
- `app/src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt` - @JavascriptInterface metodit
- `app/bibleview-js/src/composables/android.ts` - Vue.js-puolen Android-kutsujen wrapper
- `app/bibleview-js/src/components/BibleView.vue` - Pää-Vue-komponentti

### JSword-integraatio
- `app/src/main/java/net/bible/service/sword/FakeBookFactory.kt` - Malli pseudodokumenttien rekisteröintiin

---

## Riippuvuudet olemassa olevaan koodiin

- **LlmProcessingService**: Käytetään API-kutsuihin (laajennettava tool call -tuella)
- **BookmarkControl**: Käytetään bookmark-operaatioihin
- **StudyPad-entiteetit**: Käytetään StudyPad-kirjoitukseen
- **FakeBookFactory**: Malli pseudodokumenttien rekisteröintiin
- **BibleJavascriptInterface**: Laajennetaan agent-metodeilla

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

## Dokumenttien riippuvuudet ja järjestys

```
01 LLM Mode Fixes      (itsenäinen, voi tehdä heti)

02 Database Extensions ──┐
        ↓                │
03 My Documents          │ (nämä tarvitaan ennen agenttia)
        ↓                │
04 Prompt Manager ───────┘
        ↓
05 Agent Infrastructure (käyttää 02, 03, 04 rakenteita; ilman permissioneja aluksi)
        ↓
06 UI Integrations     (tarvitaan testaamista varten!)
        ↓
[Tässä vaiheessa voi testata end-to-end, permissions = "always accept"]
        ↓
05b Permissions        (lisätään jälkikäteen 05:een)
        ↓
07 Device Sync         (lisätään kantojen synkronointi jälkikäteen)
        ↓
08 Finalization        (viimeistely kun kaikki muu valmis)
```

**Huomiot:**
- 01 voidaan tehdä rinnakkain muiden kanssa, se on itsenäinen bugfix-paketti
- **06 UI Integrations pitää tehdä heti 05:n jälkeen** jotta voi testata
- Permissions (osa 05:stä) voidaan toteuttaa myöhemmin - aluksi "always accept"
