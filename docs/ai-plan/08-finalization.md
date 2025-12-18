# Viimeistely

> Lue ensin: [00-overview.md](00-overview.md)

## Tavoite

Viimeistellä AI-ominaisuudet julkaisukuntoon käyttäjätestauksen ja palautteen perusteella.

---

## Toteutustehtävät

- [ ] Käyttäjätestaus ja palautteen keräys
- [ ] TDS vs manuaalinen käännös -päätös (kumpi on käyttäjälle kätevin?)
- [ ] Ylimääräisten ominaisuuksien siivous ennen julkaisua
- [ ] Dokumentaatio (käyttöohjeet, API-dokumentaatio)

---

## Käyttäjätestaus

### Testattavat asiat

1. **LLM Mode (TDS):**
   - Onko automaattinen käännös intuitiivinen?
   - Toimiiko konfirmointi-dialogi sujuvasti?
   - Onko suorituskyky riittävä?

2. **LLM Actions:**
   - Löytävätkö käyttäjät toiminnot (One Tap, Window Menu)?
   - Onko promptivalikko selkeä?
   - Ymmärtävätkö käyttäjät mitä agentti tekee?

3. **My Documents:**
   - Onko dokumenttien hallinta selkeä?
   - Toimiiko Markdown-renderöinti oikein?
   - Ovatko linkit intuitiivisia?

4. **Permissions:**
   - Ovatko dialogit ymmärrettäviä?
   - Onko "session permission" -konsepti selkeä?

### Palautteen keräys

- Beta-testaajat
- GitHub Issues
- Sovelluksen sisäinen palaute

---

## TDS vs manuaalinen käännös -päätös

### Vaihtoehto A: TDS-lähestymistapa (nykyinen)

- Käännös tapahtuu automaattisesti taustalla
- Käyttäjä näkee aina käännetyn version
- Hyvä kun halutaan jatkuvasti käännetty teksti

### Vaihtoehto B: Manuaalinen triggeri

- Käyttäjä painaa nappia käynnistääkseen käännöksen
- Ei yllättäviä API-kutsuja
- Parempi kontrolli kustannuksista

### Päätös

Tehdään käyttäjätestauksen perusteella. Molemmat voidaan tukea (TDS = automaattinen, Window Menu = manuaalinen).

---

## Dokumentaatio

### Käyttöohjeet

- Miten konfiguroida LLM API
- Miten käyttää LLM Mode -käännöksiä
- Miten käyttää LLM Actions -toimintoja
- Miten hallita My Documents -dokumentteja
- Miten luoda omia prompteja

### Kehittäjädokumentaatio

- Arkkitehtuurikuvaus
- API-dokumentaatio (työkalut)
- Laajennusohjeet (uudet työkalut, promptit)

---

## Ennen julkaisua

1. **Koodin siivous:**
   - Poista debug-tulosteet
   - Siivoa TODO-kommentit
   - Tarkista error handling

2. **Suorituskyky:**
   - Profiloi API-kutsut
   - Optimoi tietokantakyselyt
   - Tarkista muistinkäyttö

3. **Turvallisuus:**
   - API-avaimen tallennus turvallisesti
   - Ei arkaluontoista dataa logeissa
   - Permission-järjestelmä toimii oikein

4. **Lokalisointi:**
   - Käännökset UI-teksteille
   - Oletuspromptien lokalisointi
