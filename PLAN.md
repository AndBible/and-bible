# Studypad-kursorin toteutussuunnitelma

## Tavoite

Lisätään studypadeihin "kursori", joka määrittää mihin kohtaan uudet kirjanmerkit lisätään automaattisesti. Tällä hetkellä kirjanmerkit menevät aina studypadin loppuun.

## Käyttäjäkokemuksen vaatimukset

1. **Näkyvyys**: Kursori näkyy kaikissa studypadeissa, jotka on määritelty auto-assigniksi (voi olla useampia)
2. **Liikkuminen**: Kun kirjanmerkki lisätään, kursori siirtyy yhden paikan alas (jatkaa samasta kohdasta)
3. **Konteksti**: Kursorin sijainti on workspace-kohtainen (eri workspacet voivat käyttää eri kursoreita)
4. **Käyttöliittymä**:
   - Mobiilikäyttäjä: Valikosta "Siirrä kursori tähän"
   - Hiiri-käyttäjä: Kursoria voi raahata drag & drop -toiminnolla
   - Kursori ei vie paljoa tilaa eikä vaikuta sivun renderöintiin

## Tekninen toteutus

### 1. TypeScript-tyyppien päivitys

**Tiedosto**: `app/bibleview-js/src/composables/config.ts`

Lisätään `AppSettings`-tyyppiin:
```typescript
studyPadCursors: Record<IdType, number>  // labelId → orderNumber
```

### 2. Backend: Tietorakenteet

**Tiedosto**: `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt`

Lisätään `WorkspaceSettings`-luokkaan (~rivi 338):
```kotlin
var studyPadCursors: MutableMap<IdType, Int> = mutableMapOf()
```

**Tiedosto**: `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt`

Päivitetään `getUpdateConfigCommand()` (~rivi 1372) sisällyttämään:
```kotlin
val studyPadCursors = json.encodeToString(serializer(), workspaceSettings.studyPadCursors)
// appSettings-objektissa: studyPadCursors: $studyPadCursors
```

### 3. Backend: Kirjanmerkin lisäyslogiikka

**Tiedosto**: `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt`

Muutetaan `addOrUpdateBookmark()` (~rivit 170-183):
- Haetaan kursori: `workspaceSettings.studyPadCursors[labelId]`
- Jos kursori löytyy:
  - `orderNumber = cursor`
  - Kasvatetaan myöhempien itemien orderNumbers
  - Päivitetään kursori: `cursor + 1`
- Jos ei: `orderNumber = dao.countStudyPadEntities(labelId)` (nykyinen toiminta)
- Päivitetään workspace-tietokantaan
- Lähetetään `AppSettingsUpdated` event

Lisätään apufunktiot:
```kotlin
private fun insertAtCursor(labelId: IdType, cursorPos: Int)
private fun incrementOrderNumbersFrom(labelId: IdType, fromOrder: Int)
```

### 4. Backend: TypeConverter

**Tiedosto**: `app/src/main/java/net/bible/android/database/Converters.kt`

Lisätään TypeConverter Map<IdType, Int>:lle JSON-serialisointia varten:
```kotlin
@TypeConverter
fun strToMapIdTypeInt(s: String?): MutableMap<IdType, Int>

@TypeConverter
fun mapIdTypeIntToStr(obj: Map<IdType, Int>?): String?
```

### 5. Backend: Tietokantamigraatio

**Tiedosto**: `app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt`

- Nostetaan `WORKSPACE_DATABASE_VERSION` arvoon 7 (oli 6)
- Lisätään migraatio 6→7:
```kotlin
private val addStudyPadCursors = makeMigration(6..7) { _db ->
    _db.execSQL("ALTER TABLE `Workspace` ADD COLUMN `workspace_settings_studyPadCursors` TEXT DEFAULT NULL")
}
```

### 6. JavaScript Bridge

**Tiedosto**: `app/src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt`

Uusi funktio:
```kotlin
@JavascriptInterface
fun setStudyPadCursor(labelId: String, orderNumber: Int) {
    val workspaceSettings = windowRepository.workspaceSettings
    workspaceSettings.studyPadCursors[IdType(labelId)] = orderNumber
    windowRepository.saveIntoDb()  // tallenna DB:hen
    ABEventBus.post(AppSettingsUpdated())  // synkronoi ikkunat
}
```

### 5. Frontend: Visualisointi

**Tiedosto**: `app/bibleview-js/src/components/documents/StudyPadDocument.vue`

- Lisää computed property kursorin sijaintiin:
  ```typescript
  const cursorPosition = computed(() =>
    appSettings.studyPadCursors[label.id] ?? journalEntries.value.length
  )
  ```
- Renderöi ohut viiva/ikoni itemien väliin cursor-position kohdalla
- CSS: `position: absolute` tai vastaava, ei vaikuta layouttiin
- Näytä vain jos `appSettings.autoAssignLabels.includes(label.id)`

**Kursorin drag & drop**:
- Lisää draggable-elementti kursorille
- Raahattaessa kutsuu: `android.setStudyPadCursor(labelId, newOrder)`

### 6. Frontend: Valikko

**Tiedosto**: `app/bibleview-js/src/components/StudyPadRow.vue`

Lisää itemin valikkoon:
- "Siirrä kursori tähän" / "Move cursor here"
- Kutsuu: `android.setStudyPadCursor(label.id, item.orderNumber)`

### 7. Edge caset

- **Kursori > items.length**: Näytä lopussa
- **Item poistetaan kursorin kohdalta**: Kursori pysyy samassa orderNumberissa
- **Label poistetaan auto-assign-listalta**: Kursori säilyy datassa, ei vain näy
- **Studypad avataan ensimmäistä kertaa**: Ei kursoria → kirjanmerkit menevät loppuun
- **Drag & drop siirtää itemejä**: Kursori pysyy samassa orderNumberissa (ei liiku automaattisesti)

## Edut AppSettings-lähestymistavasta

✅ Ei tarvetta uudelle getStudyPadCursors-funktiolle
✅ Synkronointi tapahtuu automaattisesti olemassa olevalla mekanismilla (ABEventBus + AppSettingsUpdated)
✅ Yhdenmukainen muiden workspace-asetusten kanssa (recentLabels, hideCompareDocuments, jne.)
✅ Yksinkertaisempi toteutus
✅ Workspace-kohtainen tieto tallennetaan samaan paikkaan kuin muutkin workspace-asetukset

## Toteutusjärjestys

1. ✅ Backend: `WorkspaceSettings.studyPadCursors`
2. ✅ Backend: Päivitä `getUpdateConfigCommand()`
3. ✅ TypeScript: Päivitä `AppSettings` type
4. ✅ Backend: Lisää TypeConverter Map<IdType, Int>:lle
5. ✅ Backend: Tietokantamigraatio 6→7
6. ✅ Backend: Muuta kirjanmerkin lisäys käyttämään kursoreita
7. ✅ Bridge: `setStudyPadCursor()`
8. ✅ Frontend: Kursorin visualisointi
9. ✅ Frontend: Valikko-toiminto

## Huomioita

- Kursoritiedon päivitys synkronoituu kaikkiin ikkunoihin, myös niihin joissa ei ole studypadia auki
- Tämä ei ole ongelma, koska:
  - Päivitys on kevyt (vain Map<IdType, Int>)
  - AppSettings-mekanismi on jo käytössä tälle tyyppiseen synkronointiin
  - Ei aiheuta käyttäjälle havaittavaa viivettä tai rasitusta
