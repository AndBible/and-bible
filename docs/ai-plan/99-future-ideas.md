# Tulevaisuuden ideat

**Tämä on referenssidokumentti.** Nämä ideat eivät ole MVP:ssä, mutta voivat tulla myöhemmin.

---

## Kustannusseuranta

- Token count per operaatio
- Arvioitu hinta per provider
- Budget alerts
- Overlay joka näyttää prosessoinnin laajuuden ennen hyväksyntää

### Käyttötapauksia

- "Tämä operaatio käyttää arviolta 5000 tokenia (~$0.05)"
- "Olet käyttänyt 50% kuukausibudjetistasi"
- Varoitus ennen kalliita operaatioita

---

## Request Button -moodi

Vaihtoehtoinen tapa: LLM-sisältö ei lataudu automaattisesti.

- Käyttäjä painaa nappia ladatakseen käännetyn/prosessoidun version
- Voi olla parempi UX kuin automaattinen lataus
- Ei yllättäviä kustannuksia

### Toteutus

- TDS-asetuksessa: "Automaattinen" vs "Pyynnöstä"
- Nappi jakeissa/luvuissa: "Käännä" / "Prosessoi"
- Cache säilyttää prosessoidut jakeet

---

## Edistyneet Prompt Manager -ominaisuudet

### Prompt-templatet joissa muuttujia

```
Käännä teksti kielelle {{target_language}}.
Käytä {{style}} tyyliä.
```

- Muuttujat täytetään suorituksen aikana
- Käyttäjä voi valita arvot dialogissa

### Prompt-ketjut

- Yksi prompt triggeröi toisen
- Esim. "Analysoi → Luo kirjanmerkit → Tiivistä"
- Workflow-tyyppinen prosessointi

### Jaetut promptit (Community prompts)

- Käyttäjät voivat jakaa promptejaan
- Keskitetty repository tai P2P-jakaminen
- Rating/feedback-järjestelmä

---

## Multi-turn Agent Mode

Keskusteluikkuna jossa käyttäjä ja agentti voivat iteroida.

### Käyttövirta

1. Käyttäjä antaa alkuperäisen tehtävän
2. Agentti ehdottaa ratkaisua
3. Käyttäjä antaa palautetta / pyytää muutoksia
4. Agentti muokkaa ehdotustaan
5. Käyttäjä hyväksyy lopputuloksen

### Vaatimukset

- Chat-tyylinen UI (BottomSheet tai erillinen Activity)
- Keskusteluhistorian tallennus
- "Hyväksy" / "Muokkaa" / "Hylkää" -toiminnot

---

## Paikallinen LLM-tuki

- Mahdollisuus käyttää paikallista mallia (llama.cpp, MLKit)
- Offline-tuki perustoiminnoille
- Pienemmät mallit yksinkertaisille tehtäville (käännös, tiivistelmä)

### Haasteet

- Mallin koko (GB-luokkaa)
- Suorituskyky mobiililaitteilla
- Laadun varmistus

---

## Kontekstuaalinen oppiminen

- Agentti oppii käyttäjän mieltymyksistä
- Muistaa aiemmat keskustelut/päätökset
- Personoidut ehdotukset

### Esimerkkejä

- "Käyttäjä suosii lyhyitä tiivistelmiä"
- "Käyttäjä haluaa aina ristiviitteet mukaan"
- "Käyttäjä käyttää usein tätä labelia"

---

## Integraatiot

### Ulkoiset palvelut

- Bible Gateway, Blue Letter Bible yms. integraatiot
- Akateemiset lähteet (JSTOR, Google Scholar)
- Karttapalvelut Raamatun paikoille

### Export

- PDF-vienti My Documents -sivuista
- Markdown-export
- Jakaminen sosiaalisessa mediassa

---

## Huomioita

Nämä ideat vaativat lisää suunnittelua ja priorisointia. Käyttäjäpalautteen perusteella päätetään mitkä toteutetaan.
