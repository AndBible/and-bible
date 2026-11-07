import io
import tarfile

from storeshots.locales import locale_for
from storeshots.modules_table import parse_mods_tar, propose, to_markdown


def mod(init, lang, desc="Holy Bible", repo="CrossWire", locked=False, lic="Public Domain", feat=(), drv="zText",
        cat="Biblical Texts"):
    return {"repo": repo, "init": init, "lang": lang, "drv": drv, "desc": desc, "feat": list(feat), "opts": [],
            "locked": locked, "lic": lic, "cat": cat, "v11n": ""}


EN = [mod("KJV", "en", "King James Version"), mod("NASB", "en", "NASB 2020", repo="Lockman__CrossWire_")]


def rows_for(catalog, *apps):
    return {r.locale.app: r for r in propose(catalog + EN, [locale_for(a) for a in apps])}


def test_modules_table_marks_fallback():
    catalog = [mod("GerA", "de", "Bibel A", lic="Copyrighted"), mod("GerB", "de", "Bibel B"),
               mod("GerLocked", "de", "Locked", locked=True)]
    rows = rows_for(catalog, "de", "sw")
    sw = rows["sw"]
    assert sw.fallback and sw.module["init"] == "NASB"
    assert "fallback: en" in sw.notes and any("needs maintainer review" in n for n in sw.notes)
    de = rows["de"]
    assert not de.fallback and de.module["init"] == "GerB"        # the freely installable one
    assert [a["init"] for a in de.alternatives] == ["GerA"]       # the other candidate is listed
    assert "GerLocked" not in {de.module["init"], *(a["init"] for a in de.alternatives)}


def test_locked_module_alone_means_fallback():
    r = rows_for([mod("OnlyLocked", "fi", locked=True)], "fi")["fi"]
    assert r.fallback


def test_full_bible_beats_new_testament_only_and_commentary_driver_ignored():
    catalog = [mod("SwNT", "sw", "Swahili New Testament"), mod("SwFull", "sw", "Swahili Bible", repo="eBible"),
               mod("SwCom", "sw", "Swahili Comm", drv="zCom", cat="Commentaries")]
    r = rows_for(catalog, "sw")["sw"]
    assert r.module["init"] == "SwFull" and r.alternatives == []  # NT-only is not offered beside a full Bible


def test_only_partial_is_flagged_but_not_fallback():
    r = rows_for([mod("SwNT", "sw", "Swahili New Testament")], "sw")["sw"]
    assert not r.fallback and any("partial" in n for n in r.notes)


def test_language_code_mapping_and_scripts():
    catalog = [mod("He1", "he-Hebr-IL"), mod("Id1", "ind"), mod("Hans", "zh-Hans"), mod("Hant", "zh-Hant"),
               mod("SrC", "sr", "Свето писмо"), mod("SrL", "sr", "Sveto Pismo ili Biblija"),
               mod("UrDeva", "ur", "Urdu Devanagari"), mod("UrAr", "ur", "Urdu", repo="eBible")]
    r = rows_for(catalog, "iw", "in", "zh-rCN", "zh-rTW", "b+sr+Latn", "b+sr+RS", "ur")
    assert r["iw"].module["init"] == "He1" and r["in"].module["init"] == "Id1"
    assert r["zh-rCN"].module["init"] == "Hans" and r["zh-rTW"].module["init"] == "Hant"
    assert r["b+sr+Latn"].module["init"] == "SrL" and r["b+sr+RS"].module["init"] == "SrC"
    assert r["ur"].module["init"] == "UrAr"       # not the Devanagari one, despite the repository order


def test_markdown_has_header_and_one_row_per_locale():
    rows = propose(EN, [locale_for("sw"), locale_for("en")])
    md = to_markdown(rows, "catalog.json from repositories.txt")
    assert "Catalog source: catalog.json from repositories.txt" in md
    assert md.count("\n| `") == 2 and "| App code | Play folder | App Store | Tier |" in md


def test_parse_mods_tar_reads_conf_fields():
    conf = (b"[GerSch]\nModDrv=zText\nLang=de\nDescription=Schlachter \\\nBibel\nFeature=StrongsNumbers\n"
            b"CipherKey=\nDistributionLicense=Public Domain\nCategory=Biblical Texts\n")
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        info = tarfile.TarInfo("mods.d/gersch.conf")
        info.size = len(conf)
        tar.addfile(info, io.BytesIO(conf))
    (row,) = parse_mods_tar(buf.getvalue(), "CrossWire")
    assert row["init"] == "GerSch" and row["lang"] == "de" and row["drv"] == "zText"
    assert row["feat"] == ["StrongsNumbers"] and row["locked"] is True and row["lic"] == "Public Domain"
    assert "Schlachter" in row["desc"]
