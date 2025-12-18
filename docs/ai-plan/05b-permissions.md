# Permissions-järjestelmä

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Käyttäjä pysyy kontrollissa - agentti kysyy luvan ennen muokkaavia toimintoja.

**Tehdään vasta kun end-to-end testaus toimii** [05-agent-infrastructure.md](05-agent-infrastructure.md):n kanssa. Aluksi käytetään "always accept" -logiikkaa.

---

## Permission-tyypit

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

---

## Asetukset (per permission)

```kotlin
enum class PermissionMode {
    ALWAYS_ASK,           // Kysy aina (oletus)
    ASK_ONCE_PER_SESSION, // Kysy kerran per sessio
    ALLOW_ALL,            // Salli kaikki (advanced users)
    DENY_ALL              // Estä kokonaan
}
```

---

## Permission-dialogi

- Näyttää mitä agentti haluaa tehdä (esim. "Luo 5 kirjanmerkkiä")
- "Salli" / "Salli kaikki tämän session aikana" / "Estä"
- Lista konkreettisista toiminnoista ennen suoritusta

---

## Toteutus

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

## Integraatio AgentWriteTools-rajapintaan

Kun AgentWriteTools-metodi kutsutaan:

1. Tarkista onko session-tason lupa (`hasSessionPermission`)
2. Jos ei → kutsu `requestPermission` näyttämään dialogi
3. Jos lupa myönnetty → suorita operaatio
4. Jos lupa estetty → palauta virhe LLM:lle

---

## Toteutustehtävät

- [ ] AgentPermissionManager luokka
- [ ] Permission-dialogit (selkeä kuvaus mitä tehdään)
- [ ] Session-tason luvat
- [ ] Settings: oletusasetukset per permission
- [ ] Integraatio AgentWriteTools-toteutukseen

---

## Huomioita

- Permission-dialogi ei saa estää UI:ta liikaa - käyttäjän pitää voida jatkaa muuta käyttöä
- Session = niin kauan kuin workspace on aktiivinen tai sovellus elossa
- Permissions eivät koske read-operaatioita - ne ovat aina sallittuja
