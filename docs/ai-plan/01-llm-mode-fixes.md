# LLM Mode -bugfixit

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Korjata nykyisen LLM Mode -toteutuksen tunnetut ongelmat ennen uusien ominaisuuksien kehittämistä.

---

## Nykyinen toteutus (MVP)

### Toteutetut ominaisuudet

- **Pseudo-book arkkitehtuuri** (`LlmProcessedBook`) - Decorator pattern JSword-kirjoille
- **Rinnakkainen jae-kerrallaan prosessointi** - Max 15 samanaikaista API-kutsua
- **Jae-pohjainen cache** - Sama jae = sama käännös
- **TranslationProcessor** - Käännösprosessori (OpenAI-yhteensopiva API)
- **Text Display Settings -integraatio** - `translateTo` -asetus per ikkuna/workspace
- **Testimoodi** - Kehitystä varten (1s delay + uppercase)

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

---

## Tunnetut ongelmat

### 1. Konfirmointi per jae

**Ongelma:** Rinnakkaisessa prosessoinnissa konfirmointidialogi näytetään jokaiselle jakeelle erikseen.

**Ratkaisu:** Siirrä konfirmointi `readToOsis`-tasolle (yksi dialogi per luku).

### 2. Linkki-ikkunat perivät LLM-moodin

**Ongelma:** Kun LLM-käännösikkunasta avataan uusi ikkuna (esim. linkki), uusi ikkuna perii LLM-asetukset. Tämä ei ole haluttua käytöstä.

**Ratkaisu:** Estä periytyminen `CurrentPageBase.currentDocument`:ssa.

**Perustelu:**
- Käyttäjä saattaa haluta nähdä alkuperäisen tekstin vertailua varten
- Käännösoperaatiot voivat olla kalliita (API-kutsut)
- Käyttäjä voi aina manuaalisesti aktivoida LLM-moodin uudessa ikkunassa

### 3. TDS-dokumentin vaihto ~~(KORJATTU)~~

~~**Ongelma:** Kun asetusta vaihtaa, dokumentti pitäisi vaihtua kokonaan.~~

**Tila:** ✅ KORJATTU

---

## Toteutustehtävät

- [ ] Korjaa konfirmointi-dialogi (per luku, ei per jae)
- [ ] Estä LLM-moodin periytyminen linkki-ikkunoihin
- [ ] Window Menu: "Translate this document" -action (manuaalinen triggeri)
- [ ] Muut prosessorit (tiivistelmä, selitys, jne.)
- [ ] "Request button" -moodi vaihtoehtona automaattiselle lataukselle

---

## Jatkokehitys

Nämä ominaisuudet liittyvät muihin dokumentteihin:
- Window Menu -integraatio → [06-ui-integrations.md](06-ui-integrations.md)
- Muut prosessorit → [04-prompt-manager.md](04-prompt-manager.md)
