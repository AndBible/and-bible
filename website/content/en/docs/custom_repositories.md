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

In *AndBible*, you can paste either the full manifest URL or the repository root
folder URL into the “Repository URL” field:

- `https://andbible.github.io/data/andbible/test/manifest.json`
- `https://andbible.github.io/data/andbible/test`

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
