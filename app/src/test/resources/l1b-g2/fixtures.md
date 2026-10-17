# L1b G2 compatibility fixtures

Written by the java.util.zip code before the ZipWriter/ZipReader swap (generator: `G2FixtureWriterTest`, run with `G2_WRITE_FIXTURES=1`). Do not regenerate after the swap.

## db-backup.abdb.zip

- Producer: BackupControl.zipDatabaseFiles() over a container with one label and one bookmark
- File SHA-256: 24d16509289bd5069b0d85cf3fd2bb6e2276e927e7dcd6b63de1b65f2e67a911
- Entries (name, size, SHA-256):
  - `AndBibleBackupManifest.json` 176 d7410e3819ba2d2fcf963489b3e5b8310c7df46ab4bb98a3d3d26888c570a740
  - `db/bookmarks.sqlite3` 167936 ecaf21a91a8a3864fb61fa148ad7d48915445f5f8a7c32edf80a1a8d057ced47
  - `db/workspaces.sqlite3` 131072 6c1f0156f49dcb5ae54416e4cd6de2509740bf8107f7e8779376c54955748554
  - `db/settings.sqlite3` 45056 08bfaaf6715c8bbae693294aca89deee3ea5fac39139164e37780e4093df4777
  - `db/ai_settings.sqlite3` 172032 1258ad28be1a725f0b09a0ac3abf0e5483850f34e70a099bdf0e0ca8b4c3d9e7
  - `db/mydocuments.sqlite3` 114688 1467d17830840b49160eb31c5d8d5c3b546acf7393b2dbe8c3f8a2e7681d6a90
  - `db/progress.sqlite3` 86016 5882404558b1035665b94849f5f99091d7ef4f2cd41b685787e942ab01895ab2

## studypad-export.abdb.zip

- Producer: exportStudyPads(label "G2 export pad")
- File SHA-256: af8014a2b347a15ae3b2d2a7d8aa0813ad2a7f974376445f26e65f1bc2c13817
- Entries (name, size, SHA-256):
  - `AndBibleBackupManifest.json` 100 4a26f04a24567e9001ebb0aa3d8b2dfe8db91c1815d994e677ed91248d82bf38
  - `db/bookmarks.sqlite3` 167936 76af79120ff5f5c0c480458e1a90fdab2cc1907513ddac2b044b8d67fdcdb67c

## bookmarks-patch.sqlite3.gz

- Producer: createPatchForDatabase on a bookmarks DB with one new bookmark
- File SHA-256: b8b774ddfc37d502254d4e4b136c18eac298270a908699a849b0717484ec3886
- Decompressed: 167936 bytes, SHA-256 31afe172ad57db1e16f2475239907800aace2ef6c5372a1c2b33c92cd94bd097

## raw-llm-log.txt.gz

- Producer: RawLlmLog.gzipCompress(fixed text with ä, 𝔄, emoji)
- File SHA-256: d9b75574eb21d53a36825d5ee960a6c07ddeec92429043d82f25657152411112
- Decompressed: 63 bytes, SHA-256 584dfe76dfced0c3bcdf8d4f03cb8fb908a4638a2ed47954d9b9fea182e9786f

## old-monolithic.db.gz

- Producer: GZIPOutputStream over a tiny SQLite file, user_version = 69
- File SHA-256: 26c759dc06ef0eda3052d3466593b2c0da7db2119e69fbb69b198472b9b7b0b5
- Decompressed: 12288 bytes, SHA-256 48608176514aafac4104a78f5c8669059765a1af46f8a89f31bdb20aeb468e66
