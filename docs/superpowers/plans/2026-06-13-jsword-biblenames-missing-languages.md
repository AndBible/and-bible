# JSword BibleNames for Missing UI Languages — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add JSword `BibleNames` resource files for the six AndBible UI languages that currently fall back to English book names (`ca`, `fil`, `ms`, `ne`, `ur`, `uz`), and fix the Indonesian (`in`) locale that wrongly resolves to Hindi book names.

**Architecture:** All changes live in the `jsword` git submodule (`AndBible/jsword`). Each language gets a `BibleNames_<locale>.properties` file translating the 66 canonical books; intros and deuterocanonical books are intentionally omitted so they inherit English via the `ResourceBundle` parent chain — matching the existing `id` (Indonesian) file's behavior. A JUnit test per locale verifies the file parses and is actually localized. Finally, the main repo's submodule pointer is bumped.

**Tech Stack:** Java properties files (raw UTF-8), JSword `ResourceBundle`-based `BibleNames`, JUnit 4, Gradle, git submodules.

**Spec:** `docs/superpowers/specs/2026-06-13-jsword-biblenames-missing-languages-design.md`

---

## Shared conventions (read once, applied by every language task)

### Where files go
`jsword/src/main/resources/BibleNames_<locale>.properties`

All paths below are **relative to the `jsword` submodule root** unless prefixed with `and-bible/`. Run all `gradlew`/`git` commands for language tasks from inside `jsword/`.

### Encoding
Write files as **raw UTF-8** (no `\uXXXX` escapes). This matches existing non-Latin files (`ru`, `ar`, `he`, `hi`). Critical for `ne` (Devanagari) and `ur` (Arabic script, RTL). The Write tool produces UTF-8 — do not re-encode.

### License header (top of every new file, verbatim)
```properties
# Distribution License:
# JSword is free software; you can redistribute it and/or modify it under
# the terms of the GNU Lesser General Public License, version 2.1 or later
# as published by the Free Software Foundation. This program is distributed
# in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
# the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# The License is available on the internet at:
#      http://www.gnu.org/copyleft/lgpl.html
# or by writing to:
#      Free Software Foundation, Inc.
#      59 Temple Place - Suite 330
#      Boston, MA 02111-1307, USA
#
# Copyright CrossWire Bible Society, 2005 - 2016
#
```

### What to translate
- **Required:** the 66 canonical books, keys `<OSIS>.Full` and `<OSIS>.Short`.
- **`.Alt`:** conservative. Default to the undefined placeholder `<OSIS>.Alt=#<OSIS>.Alt`. Add real comma-separated alternates **only** where the language has a genuinely established, commonly-typed abbreviation convention. Never guess — `.Alt` only affects reference-parsing input, and Full/Short are already matched, so a missing `.Alt` costs nothing.
- **Intros and deuterocanonical books:** OMIT entirely. They inherit English from the base bundle (exactly what the `id` file does). Do not add them.

### Canonical 66 OSIS keys, in order (with English reference)
Use this exact key set and order in every file. The English column is for reference only — replace with the target-language name.

| OSIS | English Full | OSIS | English Full |
|------|------|------|------|
| Gen | Genesis | Nah | Nahum |
| Exod | Exodus | Hab | Habakkuk |
| Lev | Leviticus | Zeph | Zephaniah |
| Num | Numbers | Hag | Haggai |
| Deut | Deuteronomy | Zech | Zechariah |
| Josh | Joshua | Mal | Malachi |
| Judg | Judges | Matt | Matthew |
| Ruth | Ruth | Mark | Mark |
| 1Sam | 1 Samuel | Luke | Luke |
| 2Sam | 2 Samuel | John | John |
| 1Kgs | 1 Kings | Acts | Acts |
| 2Kgs | 2 Kings | Rom | Romans |
| 1Chr | 1 Chronicles | 1Cor | 1 Corinthians |
| 2Chr | 2 Chronicles | 2Cor | 2 Corinthians |
| Ezra | Ezra | Gal | Galatians |
| Neh | Nehemiah | Eph | Ephesians |
| Esth | Esther | Phil | Philippians |
| Job | Job | Col | Colossians |
| Ps | Psalms | 1Thess | 1 Thessalonians |
| Prov | Proverbs | 2Thess | 2 Thessalonians |
| Eccl | Ecclesiastes | 1Tim | 1 Timothy |
| Song | Song of Solomon | 2Tim | 2 Timothy |
| Isa | Isaiah | Titus | Titus |
| Jer | Jeremiah | Phlm | Philemon |
| Lam | Lamentations | Heb | Hebrews |
| Ezek | Ezekiel | Jas | James |
| Dan | Daniel | 1Pet | 1 Peter |
| Hos | Hosea | 2Pet | 2 Peter |
| Joel | Joel | 1John | 1 John |
| Amos | Amos | 2John | 2 John |
| Obad | Obadiah | 3John | 3 John |
| Jonah | Jonah | Jude | Jude |
| Mic | Micah | Rev | Revelation of John |

### Standard test pattern (added per locale to `src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`)
The existing tests only call `load()`. Each new test additionally asserts that the locale's preferred name for Genesis **differs from English**, proving the file actually localizes rather than silently falling back. This requires two imports added once (Task 0b).

```java
@Test
public void testLoad<UPPER>() {
    Locale locale = new Locale("<locale>");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

### Commit granularity
One commit per language (resource file + its test). The Indonesian fix is its own commit. The main-repo submodule pointer bump is its own commit.

---

## Task 0: Prepare the jsword submodule

**Files:** none yet (branch + build setup)

- [ ] **Step 1: Confirm submodule state and check out the tracked branch**

The submodule is in detached HEAD. Determine and check out the branch the main repo tracks.

Run (from `and-bible/` root):
```bash
grep -A2 'path = jsword' .gitmodules
git -C jsword branch -a
```
Expected: a `branch = ...` line in `.gitmodules` (commonly `master`). If `.gitmodules` has no `branch` key, use the remote default branch shown by `git -C jsword branch -a` (e.g. `remotes/origin/master`).

- [ ] **Step 2: Check out that branch in the submodule**

Run (from `and-bible/` root, replace `<branch>` with the value from Step 1):
```bash
git -C jsword checkout <branch>
git -C jsword pull --ff-only
```
Expected: jsword now on a named branch, up to date.

- [ ] **Step 3: Baseline test run**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest
```
Expected: PASS (existing tests green). This confirms the test harness works before we add to it.

---

## Task 0b: Add test imports (one-time)

**Files:**
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

- [ ] **Step 1: Add the imports needed by the new assertions**

Add these import lines alongside the existing `import java.util.Locale;` and `import org.junit.Test;`:
```java
import static org.junit.Assert.assertNotEquals;
```
(`BibleNames` and `BibleBook` are in the same package `org.crosswire.jsword.versification`, so no import needed for them.)

- [ ] **Step 2: Verify it still compiles**

Run (from `and-bible/jsword/`):
```bash
./gradlew compileTestJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add assertNotEquals import for BibleNames locale tests"
```

---

## Task 1: Malay (`ms`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_ms.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Start from the real Indonesian file `BibleNames_id.properties` (Malay and Indonesian share nearly all canonical book names). Verify each name against standard Malay (Alkitab Berita Baik / Terjemahan Malaysia Baharu) usage and adjust spelling where Malay differs (e.g. Indonesian uses some forms not used in Malay). The content below is the Indonesian baseline to start from — confirm/adjust for Malay before writing.

- [ ] **Step 1: Write the failing test**

Add to `BibleNamesTest.java`:
```java
@Test
public void testLoadMS() {
    Locale locale = new Locale("ms");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadMS
```
Expected: FAIL on `assertNotEquals` (no `ms` file yet → falls back to English → localized equals english).

- [ ] **Step 3: Create `BibleNames_ms.properties`**

Write the license header (see Shared conventions) followed by the 66 canonical entries. Baseline Full/Short from Indonesian (verify for Malay):
```properties
# Old Testament
Gen.Full=Kejadian
Gen.Short=Kej
Gen.Alt=#Gen.Alt
Exod.Full=Keluaran
Exod.Short=Kel
Exod.Alt=#Exod.Alt
Lev.Full=Imamat
Lev.Short=Im
Lev.Alt=#Lev.Alt
Num.Full=Bilangan
Num.Short=Bil
Num.Alt=#Num.Alt
Deut.Full=Ulangan
Deut.Short=Ul
Deut.Alt=#Deut.Alt
Josh.Full=Yosua
Josh.Short=Yos
Josh.Alt=#Josh.Alt
Judg.Full=Hakim-hakim
Judg.Short=Hak
Judg.Alt=#Judg.Alt
Ruth.Full=Rut
Ruth.Short=Rut
Ruth.Alt=#Ruth.Alt
1Sam.Full=1 Samuel
1Sam.Short=1Sam
1Sam.Alt=#1Sam.Alt
2Sam.Full=2 Samuel
2Sam.Short=2Sam
2Sam.Alt=#2Sam.Alt
1Kgs.Full=1 Raja-raja
1Kgs.Short=1Raj
1Kgs.Alt=#1Kgs.Alt
2Kgs.Full=2 Raja-raja
2Kgs.Short=2Raj
2Kgs.Alt=#2Kgs.Alt
1Chr.Full=1 Tawarikh
1Chr.Short=1Taw
1Chr.Alt=#1Chr.Alt
2Chr.Full=2 Tawarikh
2Chr.Short=2Taw
2Chr.Alt=#2Chr.Alt
Ezra.Full=Ezra
Ezra.Short=Ezr
Ezra.Alt=#Ezra.Alt
Neh.Full=Nehemia
Neh.Short=Neh
Neh.Alt=#Neh.Alt
Esth.Full=Ester
Esth.Short=Est
Esth.Alt=#Esth.Alt
Job.Full=Ayub
Job.Short=Ayb
Job.Alt=#Job.Alt
Ps.Full=Mazmur
Ps.Short=Mzm
Ps.Alt=#Ps.Alt
Prov.Full=Amsal
Prov.Short=Ams
Prov.Alt=#Prov.Alt
Eccl.Full=Pengkhotbah
Eccl.Short=Pkh
Eccl.Alt=#Eccl.Alt
Song.Full=Kidung Agung
Song.Short=Kid
Song.Alt=#Song.Alt
Isa.Full=Yesaya
Isa.Short=Yes
Isa.Alt=#Isa.Alt
Jer.Full=Yeremia
Jer.Short=Yer
Jer.Alt=#Jer.Alt
Lam.Full=Ratapan
Lam.Short=Rat
Lam.Alt=#Lam.Alt
Ezek.Full=Yehezkiel
Ezek.Short=Yeh
Ezek.Alt=#Ezek.Alt
Dan.Full=Daniel
Dan.Short=Dan
Dan.Alt=#Dan.Alt
Hos.Full=Hosea
Hos.Short=Hos
Hos.Alt=#Hos.Alt
Joel.Full=Yoel
Joel.Short=Yl
Joel.Alt=#Joel.Alt
Amos.Full=Amos
Amos.Short=Am
Amos.Alt=#Amos.Alt
Obad.Full=Obaja
Obad.Short=Obd
Obad.Alt=#Obad.Alt
Jonah.Full=Yunus
Jonah.Short=Yun
Jonah.Alt=#Jonah.Alt
Mic.Full=Mikha
Mic.Short=Mi
Mic.Alt=#Mic.Alt
Nah.Full=Nahum
Nah.Short=Nah
Nah.Alt=#Nah.Alt
Hab.Full=Habakuk
Hab.Short=Hab
Hab.Alt=#Hab.Alt
Zeph.Full=Zefanya
Zeph.Short=Zef
Zeph.Alt=#Zeph.Alt
Hag.Full=Hagai
Hag.Short=Hag
Hag.Alt=#Hag.Alt
Zech.Full=Zakharia
Zech.Short=Za
Zech.Alt=#Zech.Alt
Mal.Full=Maleakhi
Mal.Short=Mal
Mal.Alt=#Mal.Alt

# New Testament
Matt.Full=Matius
Matt.Short=Mat
Matt.Alt=#Matt.Alt
Mark.Full=Markus
Mark.Short=Mrk
Mark.Alt=#Mark.Alt
Luke.Full=Lukas
Luke.Short=Luk
Luke.Alt=#Luke.Alt
John.Full=Yohanes
John.Short=Yoh
John.Alt=#John.Alt
Acts.Full=Kisah Para Rasul
Acts.Short=Kis
Acts.Alt=#Acts.Alt
Rom.Full=Roma
Rom.Short=Rm
Rom.Alt=#Rom.Alt
1Cor.Full=1 Korintus
1Cor.Short=1Kor
1Cor.Alt=#1Cor.Alt
2Cor.Full=2 Korintus
2Cor.Short=2Kor
2Cor.Alt=#2Cor.Alt
Gal.Full=Galatia
Gal.Short=Gal
Gal.Alt=#Gal.Alt
Eph.Full=Efesus
Eph.Short=Ef
Eph.Alt=#Eph.Alt
Phil.Full=Filipi
Phil.Short=Flp
Phil.Alt=#Phil.Alt
Col.Full=Kolose
Col.Short=Kol
Col.Alt=#Col.Alt
1Thess.Full=1 Tesalonika
1Thess.Short=1Tes
1Thess.Alt=#1Thess.Alt
2Thess.Full=2 Tesalonika
2Thess.Short=2Tes
2Thess.Alt=#2Thess.Alt
1Tim.Full=1 Timotius
1Tim.Short=1Tim
1Tim.Alt=#1Tim.Alt
2Tim.Full=2 Timotius
2Tim.Short=2Tim
2Tim.Alt=#2Tim.Alt
Titus.Full=Titus
Titus.Short=Tit
Titus.Alt=#Titus.Alt
Phlm.Full=Filemon
Phlm.Short=Flm
Phlm.Alt=#Phlm.Alt
Heb.Full=Ibrani
Heb.Short=Ibr
Heb.Alt=#Heb.Alt
Jas.Full=Yakobus
Jas.Short=Yak
Jas.Alt=#Jas.Alt
1Pet.Full=1 Petrus
1Pet.Short=1Ptr
1Pet.Alt=#1Pet.Alt
2Pet.Full=2 Petrus
2Pet.Short=2Pet
2Pet.Alt=#2Pet.Alt
1John.Full=1 Yohanes
1John.Short=1Yoh
1John.Alt=#1John.Alt
2John.Full=2 Yohanes
2John.Short=2Yoh
2John.Alt=#2John.Alt
3John.Full=3 Yohanes
3John.Short=3Yoh
3John.Alt=#3John.Alt
Jude.Full=Yudas
Jude.Short=Yud
Jude.Alt=#Jude.Alt
Rev.Full=Wahyu
Rev.Short=Why
Rev.Alt=#Rev.Alt
```

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadMS
```
Expected: PASS.

- [ ] **Step 5: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_ms.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Malay (ms) BibleNames"
```

---

## Task 2: Catalan (`ca`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_ca.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Established Catalan book names (Bíblia Catalana Interconfessional / standard usage). Cross-reference `BibleNames_es.properties` and `BibleNames_fr.properties` for structure. Anchor examples: Gen=`Gènesi`, Exod=`Èxode`, Ps=`Salms`, Matt=`Mateu`, John=`Joan`, Rev=`Apocalipsi`.

- [ ] **Step 1: Write the failing test**

Add to `BibleNamesTest.java`:
```java
@Test
public void testLoadCA() {
    Locale locale = new Locale("ca");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadCA
```
Expected: FAIL on `assertNotEquals`.

- [ ] **Step 3: Create `BibleNames_ca.properties`**

Write the license header, then the 66 canonical entries (`<OSIS>.Full`, `<OSIS>.Short`, `<OSIS>.Alt=#<OSIS>.Alt`) using established Catalan book names. Use the canonical key list and order from Shared conventions. Keep `.Alt` as `#<OSIS>.Alt` unless a standard Catalan abbreviation is well-known (e.g. `Gn`, `Ex`, `Sl`, `Mt`, `Jn`, `Ap` as `.Short`). Produce all 66 books; verify each name.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadCA
```
Expected: PASS.

- [ ] **Step 5: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_ca.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Catalan (ca) BibleNames"
```

---

## Task 3: Filipino (`fil`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_fil.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Established Tagalog/Filipino book names (Magandang Balita Biblia / Ang Biblia). Anchor examples: Gen=`Genesis`, Exod=`Exodo`, Ps=`Mga Awit`, Prov=`Mga Kawikaan`, Matt=`Mateo`, John=`Juan`, Acts=`Mga Gawa`, Rev=`Pahayag`. Note: some names equal English (e.g. `Genesis`) — that is fine; the test only requires Genesis OR the file as a whole to localize. Genesis in Filipino is `Genesis`, so for `fil` assert on a book that differs (see Step 1).

- [ ] **Step 1: Write the failing test**

Because Filipino `Genesis` equals English `Genesis`, assert on Exodus (`Exodo` ≠ `Exodus`) instead:
```java
@Test
public void testLoadFIL() {
    Locale locale = new Locale("fil");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.EXOD, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.EXOD, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadFIL
```
Expected: FAIL on `assertNotEquals`.

- [ ] **Step 3: Create `BibleNames_fil.properties`**

Write the license header, then all 66 canonical entries with established Filipino names, `.Alt=#<OSIS>.Alt` by default. Use the canonical key list/order from Shared conventions. Verify each name.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadFIL
```
Expected: PASS.

- [ ] **Step 5: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_fil.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Filipino (fil) BibleNames"
```

---

## Task 4: Nepali (`ne`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_ne.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Established Nepali book names in **Devanagari** (Nepali Bible / Trinitarian Bible Society Nepali). Write raw UTF-8 Devanagari. Cross-reference `BibleNames_hi.properties` (Hindi, also Devanagari) for script/structure — but Nepali names differ, so verify, do not copy Hindi. Anchor examples: Gen=`उत्पत्ति`, Matt=`मत्ती`, John=`यूहन्ना`, Ps=`भजनसंग्रह`.

- [ ] **Step 1: Write the failing test**

```java
@Test
public void testLoadNE() {
    Locale locale = new Locale("ne");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadNE
```
Expected: FAIL on `assertNotEquals`.

- [ ] **Step 3: Create `BibleNames_ne.properties`**

License header + 66 canonical entries in Nepali Devanagari (raw UTF-8). `.Short` may be a Devanagari abbreviation where standard, otherwise omit the `.Short` line (the code falls back `short = full`). `.Alt=#<OSIS>.Alt`. Use the canonical key list/order from Shared conventions. Verify each name.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadNE
```
Expected: PASS.

- [ ] **Step 5: Verify UTF-8 round-trips correctly**

Run (from `and-bible/jsword/`):
```bash
grep "^Gen.Full=" src/main/resources/BibleNames_ne.properties
```
Expected: prints `Gen.Full=उत्पत्ति` (readable Devanagari, not mojibake or `\u` escapes).

- [ ] **Step 6: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_ne.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Nepali (ne) BibleNames"
```

---

## Task 5: Urdu (`ur`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_ur.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Established Urdu book names in **Arabic/Nastaliq script** (Urdu Bible / Pakistan Bible Society). Write raw UTF-8 (RTL text is stored normally; no special handling in the properties file). Anchor examples: Gen=`پیدائش`, Exod=`خروج`, Ps=`زبور`, Matt=`متی`, John=`یوحنا`, Rev=`مکاشفہ`.

- [ ] **Step 1: Write the failing test**

```java
@Test
public void testLoadUR() {
    Locale locale = new Locale("ur");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadUR
```
Expected: FAIL on `assertNotEquals`.

- [ ] **Step 3: Create `BibleNames_ur.properties`**

License header + 66 canonical entries in Urdu (raw UTF-8). `.Short` only where a standard abbreviation exists, otherwise omit. `.Alt=#<OSIS>.Alt`. Use the canonical key list/order from Shared conventions. Verify each name.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadUR
```
Expected: PASS.

- [ ] **Step 5: Verify UTF-8 round-trips correctly**

Run (from `and-bible/jsword/`):
```bash
grep "^Gen.Full=" src/main/resources/BibleNames_ur.properties
```
Expected: prints `Gen.Full=پیدائش` (readable Urdu script).

- [ ] **Step 6: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_ur.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Urdu (ur) BibleNames"
```

---

## Task 6: Uzbek (`uz`)

**Files:**
- Create: `jsword/src/main/resources/BibleNames_uz.properties`
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Translation source:** Established Uzbek book names in **Latin script** (Muqaddas Kitob / Uzbek Bible). Anchor examples: Gen=`Ibtido`, Exod=`Chiqish`, Ps=`Zabur`, Matt=`Matto`, John=`Yuhanno`, Rev=`Vahiy`.

- [ ] **Step 1: Write the failing test**

```java
@Test
public void testLoadUZ() {
    Locale locale = new Locale("uz");
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    String english = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, Locale.ENGLISH);
    assertNotEquals(english, localized);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadUZ
```
Expected: FAIL on `assertNotEquals`.

- [ ] **Step 3: Create `BibleNames_uz.properties`**

License header + 66 canonical entries in Uzbek (Latin). `.Short` where standard, otherwise omit. `.Alt=#<OSIS>.Alt`. Use the canonical key list/order from Shared conventions. Verify each name.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadUZ
```
Expected: PASS.

- [ ] **Step 5: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_uz.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Add Uzbek (uz) BibleNames"
```

---

## Task 7: Fix Indonesian (`in` → Indonesian, currently Hindi)

**Files:**
- Modify: `jsword/src/main/resources/BibleNames_in.properties` (currently identical to Hindi)
- Modify: `jsword/src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java`

**Root cause:** Java normalizes locale `id` → legacy `in`, so the Indonesian locale resolves to `BibleNames_in.properties`, which currently holds Hindi content. The correct Indonesian file `BibleNames_id.properties` exists but is never loaded. Fix: make `in` carry the Indonesian content.

- [ ] **Step 1: Write the failing test**

Add to `BibleNamesTest.java` — assert the `in` locale yields the Indonesian Genesis name `Kejadian` (it currently yields Hindi `उत्पत्ति`):
```java
@Test
public void testLoadIndonesianInLocale() {
    Locale locale = new Locale("in"); // Java normalizes "id" to "in"
    BibleNames.instance().load(locale);
    String localized = BibleNames.instance().getPreferredNameInLocale(BibleBook.GEN, locale);
    assertEquals("Kejadian", localized);
}
```
Add the import (alongside the existing test imports):
```java
import static org.junit.Assert.assertEquals;
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadIndonesianInLocale
```
Expected: FAIL — actual is Hindi `उत्पत्ति`, not `Kejadian`.

- [ ] **Step 3: Replace `in` content with the Indonesian `id` content**

Run (from `and-bible/jsword/`):
```bash
cp src/main/resources/BibleNames_id.properties src/main/resources/BibleNames_in.properties
```
Verify:
```bash
grep "^Gen.Full=" src/main/resources/BibleNames_in.properties
```
Expected: `Gen.Full=Kejadian`.

- [ ] **Step 4: Run test to verify it passes**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest.testLoadIndonesianInLocale
```
Expected: PASS.

- [ ] **Step 5: Commit**

Run (from `and-bible/jsword/`):
```bash
git add src/main/resources/BibleNames_in.properties src/test/java/org/crosswire/jsword/versification/BibleNamesTest.java
git commit -m "Fix Indonesian (in) BibleNames showing Hindi book names

Java normalizes locale id to legacy code in, so the Indonesian locale
resolved to BibleNames_in.properties which contained Hindi content. Make
it carry the Indonesian names from BibleNames_id.properties."
```

---

## Task 8: Run full BibleNames test suite and push jsword

**Files:** none

- [ ] **Step 1: Run the whole BibleNamesTest class**

Run (from `and-bible/jsword/`):
```bash
./gradlew test --tests org.crosswire.jsword.versification.BibleNamesTest
```
Expected: PASS — all existing tests plus the 7 new ones green.

- [ ] **Step 2: Push the jsword branch**

Run (from `and-bible/jsword/`):
```bash
git push origin HEAD
```
Expected: branch updated on `AndBible/jsword`. (May require YubiKey touch for the signed/SSH push — if it times out, retry after touching the key.)

---

## Task 9: Bump submodule pointer in the main repo

**Files:**
- Modify: `and-bible/.git` submodule reference for `jsword`

- [ ] **Step 1: Stage the new submodule commit**

Run (from `and-bible/` root):
```bash
git add jsword
git status
```
Expected: `modified: jsword (new commits)` staged.

- [ ] **Step 2: Verify the pointer moved to the new commit**

Run (from `and-bible/` root):
```bash
git submodule status
```
Expected: the jsword SHA matches `git -C jsword rev-parse HEAD`.

- [ ] **Step 3: Commit**

Run (from `and-bible/` root):
```bash
git commit -m "Bump jsword: BibleNames for ca/fil/ms/ne/ur/uz + Indonesian fix"
```

- [ ] **Step 4: Build sanity check (optional, requires network + dangerouslyDisableSandbox)**

Run (from `and-bible/` root):
```bash
./gradlew :app:compileStandardGithubDebugKotlin
```
Expected: BUILD SUCCESSFUL — confirms the submodule bump integrates.

---

## Self-Review notes

- **Spec coverage:** all six missing languages (Tasks 1–6) + Indonesian fix (Task 7) + verification (Tasks 0b, 8) + submodule bump (Task 9). Conservative `.Alt` policy and 66-canonical/omit-deuterocanon coverage encoded in Shared conventions. ✓
- **Encoding:** Devanagari/Urdu UTF-8 round-trip checks in Tasks 4 and 5. ✓
- **Test consistency:** every test uses `getPreferredNameInLocale(BibleBook.X, locale)` + `assertNotEquals(english, localized)`; `fil` uses `EXOD` (since Filipino Genesis = English `Genesis`); Indonesian fix uses exact `assertEquals("Kejadian", ...)`. Imports added in Task 0b (`assertNotEquals`) and Task 7 (`assertEquals`). ✓
- **Open verification item for the executor:** the anchor book names in Tasks 2–6 are starting points; each book name must be verified against an established Bible translation in that language before committing. The `assertNotEquals` test only proves localization happened, not that every name is correct — name accuracy is a manual review responsibility.
