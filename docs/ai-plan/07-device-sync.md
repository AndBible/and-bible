# Device Sync -tuki

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

LLM-prosessoidut sisällöt, promptit ja My Documents synkronoituvat laitteiden välillä samalla tavalla kuin bookmarkit ja workspacet.

---

## Tietokantajaottelu

### LlmDatabase (uudelleennimetty LlmProcessingDatabase:sta, synkronoitava)

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

### MyDocumentsDatabase (uusi, synkronoitava)

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

---

## Miksi LlmProcessingCacheEntry synkronoidaan?

`LlmProcessingCacheEntry` EI ole pelkkä välimuisti vaan **pysyvä tallennus**:

1. **LLM ei ole deterministinen** - sama pyyntö voi tuottaa eri tuloksen eri kerroilla
2. **Bookmarkit viittaavat prosessoituun sisältöön** - referenssit (ordinalit, offsetit) rikkoutuvat jos sisältö muuttuu
3. **Käyttäjän odotus** - sama käännetty teksti pitää näkyä kaikilla laitteilla
4. **Kustannukset** - ei tarvitse maksaa uudelleen API-kutsusta

---

## SyncableRoomDatabase -vaatimukset

Jokainen synkronoitava tietokanta tarvitsee:

1. `LogEntry` entity - muutosloki synkronointia varten
2. `SyncConfiguration` entity - sync-asetukset
3. `SyncStatus` entity - synkronoinnin tila
4. Perii `SyncableRoomDatabase` luokasta

---

## Toteutus DatabaseContainerissa

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

---

## Migraatio

- `llm_processing.sqlite3` → `llm.sqlite3` (uudelleennimeäminen koodissa, migraatiota ei tarvita koska ei olla vielä tuotannossa)
- Lisätään `LogEntry`, `SyncConfiguration`, `SyncStatus` entiteetit
- Lisätään `AgentPrompt` taulu
- Muutetaan `RoomDatabase` → `SyncableRoomDatabase`

---

## Toteutustehtävät

- [ ] LlmDatabase: lisää sync-entiteetit ja peri SyncableRoomDatabase
- [ ] LlmDatabase: lisää AgentPrompt taulu
- [ ] LlmDatabase: uudelleennimeä llm_processing.sqlite3 → llm.sqlite3
- [ ] MyDocumentsDatabase: luo uusi synkronoitava tietokanta
- [ ] DatabaseContainer: päivitä viittaukset
- [ ] Testaa synkronointi laitteiden välillä

---

## Huomioita

- Tämä vaihe voidaan tehdä myöhemmin kun muu toiminnallisuus on valmis
- Synkronointi toimii automaattisesti kun tietokanta perii SyncableRoomDatabase:sta
- Olemassa oleva sync-infrastruktuuri hoitaa varsinaisen synkronoinnin
