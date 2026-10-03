import pytest

pytest.importorskip("bs4")

from sitegen.migrate.rtd_capture import extract_ids  # noqa: E402

SNIPPET = """
<div class="sidebar"><ul id="sidebar-list"><li id="toctree-li">x</li></ul></div>
<div role="main"><span id="my-label"></span>
<nav id="contents" class="contents"><p id="on-this-page">t</p>
 <ul id="toc-list"><li><a class="reference" id="id9" href="#intro">Intro</a></li></ul></nav>
<section id="intro"><h1>Intro<a class="headerlink" href="#intro">x</a></h1>
 <section id="id1"><h2 id="heading-id">A &amp; B / C</h2></section>
 <ul id="formatting-options"><li>x</li></ul>
 <dl><dt id="term-x">x</dt></dl>
 <figure id="fig"><img src="a.png"></figure>
 <table id="tbl"><tr><td id="cell">c</td></tr></table>
 <section id="intro"><p id="para">p</p></section>
 <a id="plain-anchor"></a>
 <div class="section" id="legacy">d</div>
</section></div>
"""


def test_collects_every_tag_type_inside_main_in_order_without_duplicates():
    assert extract_ids(SNIPPET) == [
        "my-label", "contents", "on-this-page", "toc-list", "id9", "intro", "id1", "heading-id",
        "formatting-options", "term-x", "fig", "tbl", "cell", "para", "plain-anchor", "legacy",
    ]


def test_ignores_ids_outside_main_and_handles_no_main_or_empty():
    ids = extract_ids(SNIPPET)
    assert "sidebar-list" not in ids and "toctree-li" not in ids
    assert extract_ids("<p>nothing</p>") == []
    assert extract_ids('<p id="x">a</p>') == ["x"]
