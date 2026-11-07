from pathlib import Path

import pytest

from storeshots.android_actions import find_node, NodeNotFound

FIXTURES = Path(__file__).parent / "fixtures"


def dumps():
    return sorted(FIXTURES.glob("uiautomator-*.xml"))


SYNTH = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
 <node index="0" text="" resource-id="" class="android.widget.FrameLayout" content-desc="" bounds="[0,0][1440,3120]">
  <node index="0" text="Assign labels" resource-id="" class="android.widget.TextView" content-desc="" bounds="[100,200][500,260]"/>
  <node index="1" text="" resource-id="net.bible:id/workspaces" class="android.view.View" content-desc="Workspaces" bounds="[1200,100][1300,200]"/>
  <node index="2" text="Psalm 33 sermon" resource-id="" class="android.widget.TextView" content-desc="" bounds="[40,900][800,1000]"/>
  <node index="3" text="Psalm 33 sermon (current)" resource-id="" class="android.widget.TextView" content-desc="" bounds="[40,1000][800,1100]"/>
 </node>
</hierarchy>"""


def test_find_by_text_desc_and_res_id():
    assert find_node(SYNTH, text="Assign labels") == (300, 230)
    assert find_node(SYNTH, desc="Workspaces") == (1250, 150)
    assert find_node(SYNTH, res_id="net.bible:id/workspaces") == (1250, 150)


def test_text_match_is_exact_unless_regex():
    assert find_node(SYNTH, text="Psalm 33 sermon") == (420, 950)
    assert find_node(SYNTH, text_re=r"Psalm 33 sermon \(current\)") == (420, 1050)


def test_missing_node_names_the_key():
    with pytest.raises(NodeNotFound, match="Nope"):
        find_node(SYNTH, text="Nope")


@pytest.mark.parametrize("dump", dumps(), ids=lambda p: p.name)
def test_real_dump_parses(dump):
    # Real dumps captured from the app (see fixtures/README): every one has a node with bounds.
    xml = dump.read_text()
    assert "bounds=" in xml
    find_node(xml, text_re=r".+")
