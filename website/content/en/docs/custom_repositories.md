# Custom Repositories

*AndBible* (since version 5.0) supports adding custom SWORD module repositories.

## Repository Manifest File

A SWORD repository should contain a manifest JSON file in the following format:

```
{
  "name": "AndBible test",
  "description": "Test module repository maintained by AndBible development team",
  "manifestUrl": "https://andbible.github.io/data/andbible/test/manifest.json",
  "type": "sword-https",
  "host": "andbible.github.io",
  "catalogDirectory": "/data/andbible/test",
  "packageDirectory": "/data/andbible/test/zip"
}
```

In this example, the repository structure is found at
`https://andbible.github.io/data/andbible/test`
(*AndBible* looks for a `mods.d.tar.gz` file for the module conf files index)
and module zip files at
`https://andbible.github.io/data/andbible/test/zip`.

!!! note

    Only HTTPS repositories are supported (i.e. the type can only be `sword-https`).

## Usage

Custom repositories are managed from the download screen:

1. Open the top left main menu (☰) and click *Download Documents*.
2. Click the three vertical dot menu and choose *Custom repositories*.
3. Tap `+` to add a repository. Tap an existing repository in the list to edit it.
4. To remove a repository, open it and tap the trash can icon; *AndBible* asks *Remove the
    custom repository “…”?* and you confirm with *Remove*.

In the editor you can paste either the full manifest URL or the repository root
folder URL into the “Repository URL” field (the clipboard icon at the end of the field
pastes for you):

- `https://andbible.github.io/data/andbible/test/manifest.json`
- `https://andbible.github.io/data/andbible/test`

While the address is checked a progress circle is shown; a check mark appears when it is
valid, and the repository’s name and description are then shown under the field. The
second field, *Packages directory (Zip files)*, holds the location of the module zip files;
it is filled in for you when the manifest names it. Confirm with the check mark at the top
right. An address that starts with `http://` is refused with the message *Custom
repositories must use a secure https:// address.*

## Non-Manifest Repositories

If a repository does not contain a manifest file, you can paste the repository
root folder URL into the “Repository URL” field. The repository must contain a
`packages` directory (containing zip files) directly under the repository root
folder.

## MyBible Repositories

MyBible repositories are also supported. The only limitation is that only HTTPS
connections are supported — *AndBible* does not download over unencrypted HTTP. If
HTTP URLs are present, HTTPS is tried instead.

## Some Custom Repositories

- [Crosswire Attic](https://andbible.github.io/data/crosswire-attic-manifest.json)
    — older modules that are no longer in the main Crosswire repository.
