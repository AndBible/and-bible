import pytest

pytest.importorskip("bs4")

from sitegen.migrate.rtd_capture import extract_ids  # noqa: E402

SNIPPET = """
<div role="main"><span id="my-label"></span>
<section id="intro"><h1>Intro<a class="headerlink" href="#intro">x</a></h1>
 <section id="id1"><h2 id="heading-id">A &amp; B / C</h2></section>
 <dl><dt id="term-x">x</dt></dl>
 <section id="intro"><p id="para">not a target</p></section>
 <a id="plain-anchor"></a>
</section></div>
"""


def test_collects_section_heading_span_and_dt_ids_in_order_without_duplicates():
    assert extract_ids(SNIPPET) == ["my-label", "intro", "id1", "heading-id", "term-x"]


def test_ignores_ids_on_other_elements_and_empty_input():
    assert "para" not in extract_ids(SNIPPET) and "plain-anchor" not in extract_ids(SNIPPET)
    assert extract_ids("<p>nothing</p>") == []
