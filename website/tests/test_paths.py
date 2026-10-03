from sitegen import paths


def test_paths_point_into_the_website_directory():
    assert paths.WEBSITE.name == "website"
    assert (paths.REPO / "settings.gradle.kts").is_file()
    assert paths.SITE == paths.WEBSITE / "_site"


def test_build_writes_cname(tmp_path):
    from sitegen.build import main

    content = tmp_path / "content"
    (content / "en").mkdir(parents=True)
    assert main(["--content", str(content), "--out", str(tmp_path / "out")]) == 0
    assert (tmp_path / "out" / "CNAME").read_text() == "andbible.org\n"
