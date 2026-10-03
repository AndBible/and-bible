from sitegen import paths


def test_paths_point_into_the_website_directory():
    assert paths.WEBSITE.name == "website"
    assert (paths.REPO / "settings.gradle.kts").is_file()
    assert paths.SITE == paths.WEBSITE / "_site"

