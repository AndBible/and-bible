# Studypad-kursorin toteutussuunnitelma

## Tavoite

Lisätään studypadeihin "kursori", joka määrittää mihin kohtaan uudet kirjanmerkit lisätään automaattisesti. Tällä hetkellä kirjanmerkit menevät aina studypadin loppuun.

## Käyttäjäkokemuksen vaatimukset

1. **Näkyvyys**: Kursori näkyy kaikissa studypadeissa, jotka on määritelty auto-assigniksi (voi olla useampia)
2. **Liikkuminen**: Kun kirjanmerkki lisätään, kursori siirtyy yhden paikan alas (jatkaa samasta kohdasta)
3. **Konteksti**: Kursorin sijainti on workspace-kohtainen (eri workspacet voivat käyttää eri kursoreita)
4. **Käyttöliittymä**:
   - Valikosta "Move cursor here" -painike jokaisessa itemissä
   - Kursori on klikattava → klikkaus siirtää kursorin klikkauskohteen
   - Kursori ei vie paljoa tilaa eikä vaikuta sivun renderöintiin (CSS: `height: 0`)

## Tekninen toteutus

### 1. TypeScript-tyyppien päivitys

**Tiedosto**: `app/bibleview-js/src/composables/config.ts`

Lisätään `AppSettings`-tyyppiin:
```typescript
studyPadCursors: Record<IdType, number>,  // labelId → orderNumber
autoAssignLabels: IdType[],  // lista auto-assign studypadeista
```

Lisätään default-arvot:
```typescript
const appSettings: AppSettings = reactive({
    // ...
    studyPadCursors: {},
    autoAssignLabels: [],
    // ...
})
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
val autoAssignLabels = json.encodeToString(serializer(), workspaceSettings.autoAssignLabels.toList())
// appSettings-objektissa:
// studyPadCursors: $studyPadCursors,
// autoAssignLabels: $autoAssignLabels,
```

### 3. Backend: Kirjanmerkin lisäyslogiikka

**Tiedosto**: `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt`

Muutetaan `addOrUpdateBookmark()` (~rivit 170-217):
- Haetaan kursori: `workspaceSettings.studyPadCursors[labelId]`
- Jos kursori löytyy:
  1. **Ensin** kasvatetaan kaikkien >= cursor orderNumberien arvoja: `incrementOrderNumbersFrom(labelId, cursor)`
  2. Lisätään uusi bookmark orderNumberilla `cursor`
  3. Päivitetään kursori: `cursor + 1`
  4. Päivitetään UI: `sanitizeStudyPadOrder(labelId, updateAllInUi = true)`
- Jos ei: `orderNumber = dao.countStudyPadEntities(labelId)` (lisätään loppuun)
- Tallennetaan workspace-tietokantaan: `windowRepository.saveIntoDb()`
- Lähetetään `AppSettingsUpdated` event (synkronoi ikkunat)

Lisätään apufunktio:
```kotlin
private fun incrementOrderNumbersFrom(labelId: IdType, fromOrder: Int) {
    // Kasvatetaan kaikkien itemien (bookmarks + text entries) orderNumber++
    // joilla orderNumber >= fromOrder
    val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
        .filter { it.orderNumber >= fromOrder }.onEach { it.orderNumber++ }
    val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)
        .filter { it.orderNumber >= fromOrder }.onEach { it.orderNumber++ }
    val studyPadTextEntries = dao.studyPadTextEntriesByLabelId(labelId)
        .filter { it.orderNumber >= fromOrder }.onEach { it.orderNumber++ }

    dao.updateBibleBookmarkToLabels(bookmarkToLabels)
    dao.updateGenericBookmarkToLabels(genericBookmarkToLabels)
    updateStudyPadTextEntries(studyPadTextEntries)
}
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

### 7. Frontend: TypeScript-rajapinta

**Tiedosto**: `app/bibleview-js/src/composables/android.ts`

Lisätään `BibleJavascriptInterface`-tyyppiin:
```typescript
setStudyPadCursor: (labelId: IdType, orderNumber: number) => void,
```

Lisätään `useAndroid`-funktio ja `exposed`-objekti:
```typescript
function setStudyPadCursor(labelId: IdType, orderNumber: number) {
    window.android.setStudyPadCursor(labelId, orderNumber);
}

const exposed = {
    // ...
    setStudyPadCursor,
    // ...
}
```

### 8. Frontend: Visualisointi

**Tiedosto**: `app/bibleview-js/src/components/documents/StudyPadDocument.vue`

Lisätään computed propertyt:
```typescript
const cursorPosition = computed(() =>
    appSettings.studyPadCursors?.[label.id] ?? journalEntries.value.length
)

const isAutoAssignLabel = computed(() =>
    appSettings.autoAssignLabels?.includes(label.id) ?? false
)

const showCursor = computed(() =>
    isAutoAssignLabel.value && !exportMode.value
)

function moveCursorTo(orderNumber: number) {
    android.setStudyPadCursor(label.id, orderNumber);
}
```

Renderöinti:
- Ohut vihreä viiva + ▼-ikoni itemien välissä
- CSS: `position: relative; height: 0` - ei vaikuta layouttiin
- Näytetään kahdessa paikassa:
  1. Ennen ensimmäistä itemä jos `cursorPosition === 0`
  2. Jokaisen itemin jälkeen jos `cursorPosition === index + 1`
- Klikattava → kutsuu `moveCursorTo(orderNumber)`

### 9. Frontend: Valikko

**Tiedosto**: `app/bibleview-js/src/components/StudyPadRow.vue`

Lisätään itemin valikkoon:
```typescript
const isAutoAssignLabel = computed(() =>
    appSettings.autoAssignLabels?.includes(props.label.id) ?? false
)

function moveCursorHere() {
    // Move cursor after this item (orderNumber + 1)
    android.setStudyPadCursor(props.label.id, props.journalEntry.orderNumber + 1);
}
```

HTML:
```vue
<div v-if="isAutoAssignLabel" class="journal-button" @click="moveCursorHere">
  <FontAwesomeIcon :icon="faArrowDown"/>
</div>
```

**Huom**: Kursori asetetaan `orderNumber + 1`:een, jolloin se menee itemin **jälkeen**, ei päälle.

### 10. Edge caset

- **Kursori > items.length**: Näytetään studypadin lopussa (fallback)
- **Item poistetaan kursorin kohdalta**: Kursori pysyy samassa orderNumberissa (seuraava item tulee sen paikalle)
- **Label poistetaan auto-assign-listalta**: Kursori säilyy datassa, mutta ei näy (ei poisteta)
- **Studypad avataan ensimmäistä kertaa**: Ei kursoria → kirjanmerkit menevät loppuun (normaali toiminta)
- **Drag & drop siirtää itemejä**: Kursori pysyy samassa orderNumberissa (ei liiku automaattisesti)
- **Uusi item lisätään kursorin kohtaan**:
  1. Ensin kasvatetaan myöhempien itemien orderNumberit
  2. Uusi item saa kursorin orderNumberin
  3. Kursori siirtyy yhden eteenpäin
  4. UI päivittyy `sanitizeStudyPadOrder` + `StudyPadOrderEvent`

## Edut AppSettings-lähestymistavasta

✅ Ei tarvetta uudelle getStudyPadCursors-funktiolle
✅ Synkronointi tapahtuu automaattisesti olemassa olevalla mekanismilla (ABEventBus + AppSettingsUpdated)
✅ Yhdenmukainen muiden workspace-asetusten kanssa (recentLabels, hideCompareDocuments, jne.)
✅ Yksinkertaisempi toteutus
✅ Workspace-kohtainen tieto tallennetaan samaan paikkaan kuin muutkin workspace-asetukset

## Toteutusjärjestys

1. ✅ Backend: `WorkspaceSettings.studyPadCursors` (WorkspaceEntities.kt:347)
2. ✅ Backend: TypeConverter Map<IdType, Int>:lle (Converters.kt:264-278)
3. ✅ Backend: Tietokantamigraatio 6→7 (WorkspacesMigrations.kt:41-54)
4. ✅ Backend: Päivitä `getUpdateConfigCommand()` + `autoAssignLabels` (BibleView.kt:1372-1410)
5. ✅ Backend: Lisää `incrementOrderNumbersFrom()` (BookmarkControl.kt:747-756)
6. ✅ Backend: Muuta `addOrUpdateBookmark()` käyttämään kursoreita (BookmarkControl.kt:170-217)
7. ✅ Bridge: `setStudyPadCursor()` (BibleJavascriptInterface.kt:327-336)
8. ✅ TypeScript: Päivitä `AppSettings` type + default-arvot (config.ts:95-192)
9. ✅ TypeScript: Lisää `BibleJavascriptInterface.setStudyPadCursor` (android.ts:64, 449-451, 564)
10. ✅ Frontend: Kursorin visualisointi (StudyPadDocument.vue:29-58, 250-274, 318-349)
11. ✅ Frontend: Valikko-toiminto (StudyPadRow.vue:48-50, 105, 119-121, 201-204)
12. ✅ Backend: Käännöksen testaus
13. ✅ Frontend: TypeScript type-check

## Tärkeimmät korjaukset toteutuksessa

### Korjaus 1: Kursorin sijainti valikossa
**Ongelma**: "Move cursor here" asetti kursorin itemin päälle (orderNumber), ei sen jälkeen.
**Ratkaisu**: Muutettu `moveCursorHere()` asettamaan `orderNumber + 1`, jolloin kursori menee itemin jälkeen.

### Korjaus 2: Insertin järjestys
**Ongelma**: Kun kaksi itemä sai saman orderNumberin, `sanitizeStudyPadOrder` järjesti ne epädeterministisesti.
**Ratkaisu**: Lisätty `incrementOrderNumbersFrom()` joka kasvattaa myöhempien itemien orderNumberit **ennen** uuden itemin insertiä.

### Korjaus 3: autoAssignLabels puuttui frontendistä
**Ongelma**: Frontend ei tiennyt mitkä studypadit ovat auto-assign, joten kursoria ei voitu näyttää oikein.
**Ratkaisu**: Lisätty `autoAssignLabels` AppSettings-tyyppiin ja backendiin.

## Testausohjeita

1. **Aseta studypad auto-assigniksi**: Valitse studypad kirjanmerkkien automaattiseen lisäykseen
2. **Tarkista kursorin näkyvyys**: Vihreä viiva + ▼ pitäisi näkyä studypadin lopussa
3. **Lisää kirjanmerkki**: Uusi kirjanmerkki menee kursorin kohtaan, kursori siirtyy alas
4. **Siirrä kursoria**: Klikkaa "Move cursor here" -painiketta itemin valikossa → kursori menee itemin jälkeen
5. **Testaa synkronointia**: Avaa sama studypad toisessa ikkunassa → kursori näkyy samassa kohdassa
6. **Testaa useampaa studypadia**: Aseta useampi studypad auto-assigniksi → jokaisella oma kursorinsa

## Huomioita

- Kursoritiedon päivitys synkronoituu kaikkiin ikkunoihin, myös niihin joissa ei ole studypadia auki
- Tämä ei ole ongelma, koska:
  - Päivitys on kevyt (vain Map<IdType, Int>)
  - AppSettings-mekanismi on jo käytössä tälle tyyppiseen synkronointiin
  - Ei aiheuta käyttäjälle havaittavaa viivettä tai rasitusta
- Kursori säilyy workspace-kohtaisena, joten eri workspacet voivat käyttää eri kursoripositioita samaan studypadiin
