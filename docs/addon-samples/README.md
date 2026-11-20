# Add-on samples

Two small, complete add-on modules for testing and as templates (author guide: [`../addon-modules.md`](../addon-modules.md)):

- `collection-sample/` (`MannaSample`): a bookmark collection. Two labels, "Comfort" (6 verses, one note) and
  "John study" (a StudyPad: a text entry, 4 verses, a paragraph break), 10 Bible bookmarks from John and the Psalms.
  The collection file is `modules/genbook/rawgenbook/mannasample/and-bible/manna.sqlite3`; rebuild it with
  `python3 docs/addon-samples/make_collection_sample.py` (it reads the latest Room schema export).
- `prompt-sample/` (`PromptSample`): an AI prompt pack with two prompts.

`build.sh [dir]` builds `repo/mods.d.tar.gz` and `repo/packages/*.zip`: a SWORD repository layout. Serve it over
HTTPS and add it under *Download documents -> Custom repositories* (the app refuses plain HTTP), or install a
package zip directly with *Install from zip*.

`SampleModulesTest` (`:sharedLogic` jvmTest) loads both samples through the add-on scanner, the collection importer
and the prompt CSV parser and asserts their contents, so the samples cannot rot unnoticed.

To test an update, raise `Version` in the conf and change a verse, rebuild, and re-install.
