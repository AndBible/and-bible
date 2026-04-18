#!/usr/bin/env python3
"""
Shared language metadata for AndBible translation scripts.

Provides language names, Claude translation quality tiers, and
Android ↔ Vue.js ↔ Play Store code mappings with path resolution.
"""

# Claude Sonnet translation quality tiers:
# Tier 1: Excellent — major languages with strong LLM support
# Tier 2: Good — medium-resource languages, generally reliable
# Tier 3: Fair — lower-resource, quality may vary significantly

# Each entry: (human name, tier, android dir code, vuejs file code, playstore file code or None)
# The dict key is the "canonical code" (= Android directory suffix for most languages).
LANGUAGES = {
    'af':        ('Afrikaans',              2, 'af',       'af',       'af'),
    'ar':        ('Arabic',                 1, 'ar',       'ar',       'ar'),
    'az':        ('Azerbaijani',            3, 'az',       'az',       'az-AZ'),
    'b+sr+Latn': ('Serbian (Latin)',        2, 'b+sr+Latn','b+sr+Latn','sr'),
    'b+sr+RS':   ('Serbian (Cyrillic)',     2, 'b+sr+RS',  'b+sr+RS',  'sr'),
    'bg':        ('Bulgarian',              1, 'bg',       'bg',       'bg'),
    'bn':        ('Bengali',                2, 'bn',       'bn',       'bn-BD'),
    'cs':        ('Czech',                  1, 'cs',       'cs',       'cs-CZ'),
    'de':        ('German',                 1, 'de',       'de',       'de-DE'),
    'el':        ('Greek',                  1, 'el',       'el',       'el-GR'),
    'eo':        ('Esperanto',              2, 'eo',       'eo',       'eo'),
    'es':        ('Spanish',                1, 'es',       'es',       'es-ES'),
    'et':        ('Estonian',               2, 'et',       'et',       'et'),
    'fil':       ('Filipino',               2, 'fil',      'fil',      'fil'),
    'fi':        ('Finnish',                1, 'fi',       'fi',       'fi-FI'),
    'fr':        ('French',                 1, 'fr',       'fr',       'fr-FR'),
    'hi':        ('Hindi',                  1, 'hi',       'hi',       'hi-IN'),
    'hr':        ('Croatian',               1, 'hr',       'hr',       'hr'),
    'hu':        ('Hungarian',              1, 'hu',       'hu',       'hu-HU'),
    'in':        ('Indonesian',             2, 'in',       'in',       'id'),
    'it':        ('Italian',                1, 'it',       'it',       'it-IT'),
    'iw':        ('Hebrew',                 1, 'iw',       'he',       'iw-IL'),
    'ja':        ('Japanese',               1, 'ja',       'ja',       'ja-JP'),
    'ca':        ('Catalan',                1, 'ca',       'ca',       'ca'),
    'da':        ('Danish',                 1, 'da',       'da',       'da-DK'),
    'kk':        ('Kazakh',                 3, 'kk',       'kk',       'kk'),
    'ko':        ('Korean',                 1, 'ko',       'ko',       'ko-KR'),
    'lt':        ('Lithuanian',             2, 'lt',       'lt',       'lt'),
    'ml':        ('Malayalam',              3, 'ml',       'ml',       'ml-IN'),
    'ms':        ('Malay',                  2, 'ms',       'ms',       'ms-MY'),
    'my':        ('Burmese',                3, 'my',       'my',       'my-MM'),
    'nb':        ('Norwegian Bokmål',       2, 'nb',       'nb',       'no-NO'),
    'ne':        ('Nepali',                 2, 'ne',       'ne',       'ne-NP'),
    'nl':        ('Dutch',                  1, 'nl',       'nl',       'nl-NL'),
    'pl':        ('Polish',                 1, 'pl',       'pl',       'pl-PL'),
    'pt':        ('Portuguese',             1, 'pt',       'pt',       'pt-PT'),
    'pt-rBR':    ('Portuguese (Brazil)',    1, 'pt-rBR',   'pt-BR',    'pt-BR'),
    'ro':        ('Romanian',               1, 'ro',       'ro',       'ro'),
    'ru':        ('Russian',                1, 'ru',       'ru',       'ru-RU'),
    'sk':        ('Slovak',                 2, 'sk',       'sk',       'sk'),
    'sl':        ('Slovenian',              2, 'sl',       'sl',       'sl'),
    'sv':        ('Swedish',                1, 'sv',       'sv',       'sv-SE'),
    'sw':        ('Swahili',                2, 'sw',       'sw',       'sw'),
    'ta':        ('Tamil',                  2, 'ta',       'ta',       'ta-IN'),
    'te':        ('Telugu',                 2, 'te',       'te',       'te-IN'),
    'th':        ('Thai',                   1, 'th',       'th',       'th'),
    'tr':        ('Turkish',                1, 'tr',       'tr',       'tr-TR'),
    'uk':        ('Ukrainian',              1, 'uk',       'uk',       'uk'),
    'ur':        ('Urdu',                   2, 'ur',       'ur',       'ur'),
    'uz':        ('Uzbek',                  3, 'uz',       'uz',       'uz'),
    'vi':        ('Vietnamese',             1, 'vi',       'vi',       'vi'),
    'yue':       ('Cantonese',              3, 'yue',      'yue',      'yue'),
    'zh-rCN':    ('Chinese (Simplified)',   1, 'zh-rCN',   'zh-CN',    'zh-CN'),
    'zh-rTW':    ('Chinese (Traditional)',  1, 'zh-rTW',   'zh',       'zh-TW'),
}

# Android directory codes to skip in "all languages" mode
SKIP_CODES = {
    'en',    # Source language
    'b+sr',  # Use specific variants b+sr+Latn / b+sr+RS
    'id',    # Android legacy duplicate of 'in'
    'zh',    # Older/smaller fallback, duplicate of zh-rTW
}

# Android directories that must receive a mirror copy whenever the primary
# target is updated. Key = canonical lang code, value = list of additional
# Android directory codes to keep in sync with the primary.
#
# Why this exists: Transifex's lang_map can route a translation resource
# (e.g. zh_TW) to a directory different from the Android locale folder
# (values-zh/ instead of values-zh-rTW/). Makefile then `cp`s one to the
# other on tx-pull. If a translator updates only values-zh-rTW/ without
# also updating values-zh/, the next tx-pull overwrites the new work
# because Transifex still has the stale source.
ANDROID_MIRRORS = {
    # Transifex zh_TW -> values-zh/; Makefile then cp -> values-zh-rTW/.
    # Keep both in sync so tx-push picks up new translations for zh_TW.
    'zh-rTW': ['zh'],
}


def get_android_mirror_paths(lang):
    """Return list of additional Android target paths for a language.

    Empty list if no mirrors are configured. Paths are repo-relative.
    """
    canonical = normalize_code(lang)
    mirrors = ANDROID_MIRRORS.get(canonical, [])
    return [f'app/src/main/res/values-{code}/strings.xml' for code in mirrors]

# Reverse lookup: Vue.js code → canonical code (for languages where codes differ)
_VUEJS_TO_CANONICAL = {}
for _key, (_name, _tier, _android, _vuejs, _playstore) in LANGUAGES.items():
    if _vuejs and _vuejs != _android:
        _VUEJS_TO_CANONICAL[_vuejs] = _key


def normalize_code(lang):
    """Normalize a language code to canonical form.

    Accepts either an Android code (canonical) or a Vue.js code and
    returns the canonical key used in LANGUAGES.
    """
    if lang in LANGUAGES:
        return lang
    if lang in _VUEJS_TO_CANONICAL:
        return _VUEJS_TO_CANONICAL[lang]
    return lang


def resolve_paths(lang):
    """Resolve Android and Vue.js file paths for a language code.

    Accepts either canonical (Android) or Vue.js code.
    Returns dict with android_base, android_target, vuejs_base, vuejs_target.
    vuejs_target is None if the language has no Vue.js translations.
    """
    canonical = normalize_code(lang)
    entry = LANGUAGES.get(canonical)

    if entry:
        _name, _tier, android_code, vuejs_code, _playstore = entry
    else:
        # Unknown language — assume same code for both
        android_code = canonical
        vuejs_code = canonical

    android_base = 'app/src/main/res/values/strings.xml'
    vuejs_base = 'app/bibleview-js/src/lang/default.yaml'
    android_target = f'app/src/main/res/values-{android_code}/strings.xml'
    vuejs_target = f'app/bibleview-js/src/lang/{vuejs_code}.yaml' if vuejs_code else None

    # Play Store paths
    playstore_code = None
    if entry:
        playstore_code = entry[4]
    playstore_base = 'play/playstore-description.yml'
    playstore_target = f'play/description-translations/{playstore_code}.yml' if playstore_code else None

    return {
        'lang': canonical,
        'android_code': android_code,
        'vuejs_code': vuejs_code,
        'playstore_code': playstore_code,
        'android_base': android_base,
        'android_target': android_target,
        'vuejs_base': vuejs_base,
        'vuejs_target': vuejs_target,
        'playstore_base': playstore_base,
        'playstore_target': playstore_target,
    }


def get_language_name(lang):
    """Get human-readable name for a language code."""
    canonical = normalize_code(lang)
    if canonical in LANGUAGES:
        return LANGUAGES[canonical][0]
    return lang


def get_tier(lang):
    """Get Claude quality tier (1=excellent, 2=good, 3=fair)."""
    canonical = normalize_code(lang)
    if canonical in LANGUAGES:
        return LANGUAGES[canonical][1]
    return 3


def get_translatable_languages(tiers=None):
    """Return list of canonical codes for translatable languages.

    Excludes SKIP_CODES. If tiers is specified (e.g. [1, 2]),
    only returns languages in those tiers.
    """
    result = []
    for code, (_name, tier, _android, _vuejs, _playstore) in LANGUAGES.items():
        if code in SKIP_CODES:
            continue
        if tiers and tier not in tiers:
            continue
        result.append(code)
    return result
