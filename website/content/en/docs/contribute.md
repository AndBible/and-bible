# Contribute

AndBible is an open-source community project, and contributions of many kinds are welcome. If you are unsure where to start, [contact us](support.md) or [open an issue](https://github.com/AndBible/and-bible/issues/new/choose) to discuss your idea before you begin. For a small, self-contained code change, you can also open a pull request directly; discuss larger changes first so the approach can be agreed before significant work begins.

## Build the community

- Help other users in our [Telegram or Matrix groups](support.md#general-questions). The groups are bridged, so you can use either one.
- Share AndBible with others, write about it, make a tutorial, or leave a review in your app store.
- Help test new features and share feedback through the [support channels](support.md).

## Report a bug or suggest a feature

Use the in-app **Feedback / bug report** option when possible; it includes information that helps us investigate. You can also [report a bug or suggest a feature on GitHub](https://github.com/AndBible/and-bible/issues/new/choose). See [Support](support.md) for details.

## Develop for AndBible

AndBible is built with Kotlin, JavaScript and Vue.js, among other technologies. Browse [good first issues](https://github.com/AndBible/and-bible/issues?q=is%3Aopen+is%3Aissue+label%3A%22Good+for+starting+contributors%22), or contact us if an issue needs more detail before you can start. Contributions are submitted as pull requests to the [AndBible repository](https://github.com/AndBible/and-bible).

## Create or improve modules

You can help extend the library by creating SWORD modules or by helping obtain permission to distribute existing texts. See [Module Creation](customisation/module_creation.md) for AndBible-specific module guidance. For requests to include a module, see the [FAQ](faq.md#please-add-module-x-to-andbible).

## Improve the documentation and media

Suggestions and corrections to the user documentation are welcome. Start a discussion in [GitHub Issues](https://github.com/AndBible/and-bible/issues/new/choose) so we can agree on the change and its destination. You can also suggest or create tutorial videos, screenshots, and other media; ask in the [community groups](support.md#general-questions) what would be most useful.

## Translating the user interface {#translating-the-user-interface}

AndBible uses [Transifex](https://app.transifex.com/mjdenham/andbible/) to manage user interface translations. Create a free Transifex account and join the project to get started. Translators should be comfortable with written English, fluent in their language, and familiar with how AndBible works. As the app continues to change, ongoing review and updates are especially helpful.

### Review AI-assisted translations

New and updated strings are pre-translated by AI for languages that are well supported by modern language models. Volunteer translators remain essential: AI translations can contain mistakes, particularly for less-resourced languages, and need review by people who know both the app and the language.

In Transifex:

1. Review new, unreviewed translations for your language.
2. Mark accurate translations as reviewed so other contributors can see they have been checked by a person.
3. Correct terminology, phrasing, context, or other errors you find.

Transifex is primarily used for review rounds, rather than as the primary translation method. Translating strings from scratch is also welcome, especially for languages less well supported by AI or when that is your preference.

### Translation priorities

The main translation resources are Android interface strings, BibleView interface strings, Bible book names, and store descriptions. Start with short, frequently seen interface strings when time is limited. Bible book names and abbreviations help users navigate their documents; alternative names can also help AndBible recognize Bible references in imported texts.

For Bible book alternative names, enter a comma-separated list, for example `Psalms, Ps, Psalm`.
Spaces and capitalization do not matter. If your language has no alternative names for a book,
leave the default value (for example `#Gen.Alt`) unchanged, including the leading `#`.
These alternatives are used to recognize Bible references, including those in imported EPUB texts.

Some strings include context notes. If a string is unclear, check its context in the app or ask for clarification in Transifex or through the [support channels](support.md).

## Support development

You can [support the project financially](https://shop.andbible.org/) by purchasing development work.
