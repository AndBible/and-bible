# AI Bible Study Assistant

*AndBible* includes a built-in AI assistant that can translate, explain, summarize,
annotate, and perform word studies on Bible text. It works by sending selected
text to an external AI provider and displaying the result directly in the app.

!!! note

    The AI assistant requires an internet connection and an API key from a
    supported provider. Usage may incur costs depending on your chosen provider
    and model.

!!! warning

    When you use an AI prompt, the selected text is sent to your chosen external
    AI provider for processing. Do not use this feature with content you wish to
    keep private.

## Getting Started

### Choosing a Provider

*AndBible* supports a range of AI providers, including:

- **Google Gemini** – Google’s AI models. Has a generous free tier; a good
    starting point.
- **OpenAI (ChatGPT)** – The company behind ChatGPT.
- **Anthropic (Claude)** – Maker of the Claude family of models.
- **xAI (Grok)** – AI models from xAI.
- **Mistral** – European AI provider with efficient models.
- **DeepSeek** – Cost-effective models with strong multilingual capabilities.
- **Groq** – Extremely fast inference with open-source models.
- **Alibaba Cloud (Qwen)** – Multilingual models.
- **OpenRouter** – Aggregator that provides access to models from many
    providers through a single API key.
- **Custom** – Connect to any OpenAI- or Anthropic-compatible API endpoint
    (self-hosted or other providers).

The list of providers and the available models for each one is maintained
inside the app. To see the exact, up-to-date list, open `AI Settings` >
`Configure Connection` > `Add provider`.

If you are new to AI services, **Google Gemini** is a good starting point
because it offers a free usage tier that is sufficient for casual use.

### Getting an API Key

To use the AI assistant, you need an API key from your chosen provider:

1. Visit the provider’s API key console (*AndBible* provides direct links in the
    setup screen).
2. Create an account if you don’t have one.
3. Generate a new API key.
4. Copy the key – you will paste it into *AndBible*.

!!! note

    API keys are stored securely on your device. They are **never** included
    in backups or synchronized to the cloud.

### Adding a Provider

1. Open the top left main menu (☰).
2. Tap `AI Settings`.
3. Tap `Configure Connection`.
4. Tap `Add provider`.
5. Select your provider from the list.
6. Paste your API key.
7. Choose a model (a sensible default is pre-selected).
8. Tap `Save`.

The first provider you add becomes the default. You can change this later.

### Multiple Providers

You can configure multiple providers simultaneously. This is useful if you
want to use a cheaper model for simple tasks (like translation) and a more
capable model for complex tasks (like word studies).

The first provider is used by default. Individual prompts can be configured to
use a specific provider, overriding the default.

### Available Models

*AndBible* ships with a built-in list of recommended models for each provider.
For providers that publish a public model catalogue, *AndBible* also fetches
the current list **automatically** the first time a provider is set up
through the Easy Setup wizard, so newly released models become available
without an app update.

In the **AI Models** screen you can also:

- **Add a custom model** – Type a model name manually if you want to use a
    model that is not in the built-in or fetched list.
- **Set the default model** – Choose which model is used when a prompt
    doesn’t specify its own.

!!! note

    Not every provider supports model listing. For those that do not, the
    built-in list and manually added models are the only options.

## Using AI Prompts

Prompts are instructions that tell the AI what to do with your selected text.
*AndBible* comes with several built-in prompts and allows you to create your own.

### Built-in Prompts

*AndBible* includes a set of built-in prompts. They are organised into three
categories.

#### Study prompts

These prompts focus on understanding and analysing Bible text. Most of them
are *Bible-only*, meaning they only appear when the active document is a
Bible.

**Explain Verses**
:   Explains the meaning, context, and theological significance of the
    selected verses, grounded in the available commentaries.

**Explain Verses (Study Pad)**
:   Like *Explain Verses*, but builds a Study Pad with a structured
    verse-by-verse explanation, bookmarks, key themes, and an application
    section.

**Word Study**
:   Analyzes the original Hebrew or Greek words in the selected text,
    providing definitions, usage patterns, and dictionary entries.

**Cross-References**
:   Finds and explains Bible passages related to the selected verses.

**Compare Translations**
:   Shows how different installed Bible translations render the same passage.

**Thematic Study**
:   Builds a Study Pad with passages and notes around the central theme of the
    selected verses.

#### Notes prompts

These prompts help create and improve notes and study material.

**Bookmark Annotate**
:   Creates a bookmark with an AI-generated study note for the selected verses.

**Enhance Note**
:   Improves the grammar, clarity, and readability of an existing bookmark
    note. This prompt rewrites the note text in place (see *Text
    transformation prompts* below).

**Study Layout**
:   Opens commentary and parallel translation windows for further study of
    the selected passage.

#### General prompts

These prompts work on any document type, not only Bibles.

**Translate**
:   Translates the selected text into the language your app interface is set
    to. If a translation in that language is already installed, it can use
    it directly instead of generating a new translation. The prompt name in
    the menu shows the actual target language, e.g. “Translate to Finnish”.
    This is a text-transformation prompt (see below).

**Summary**
:   Creates a concise summary of the selected text.

**Ask Question**
:   Lets you ask a free-form question about the selected passage.

**Custom Prompt**
:   Runs a free-form, one-off instruction on the selected passage without
    creating a saved custom prompt.

**Workspace Assistant**
:   Manages windows on your behalf: it can create, close, rearrange and
    change documents in the current workspace based on your instructions.

### Prompt Categories

Prompts are organised into categories (**Study**, **Notes**, **General**).
Categories control how prompts are grouped in the *AI Actions* menus and
in `AI Settings`.

You can:

- Create your own categories for custom prompts.
- Move custom prompts between categories.
- Hide an entire category (including its prompts) from the AI Actions menus
    – useful for parking your own work-in-progress prompts out of the way.

Built-in prompts are pre-assigned to the appropriate categories and cannot
be moved.

### Special Prompt Behaviors

Some prompts behave differently from the default “ask a question, get an
answer” pattern.

**Bible-only prompts**
:   Many study-oriented prompts (like *Explain Verses*, *Cross-References*
    or *Word Study*) only appear when the active document is a Bible. They
    are hidden in commentaries, dictionaries and other documents because
    their templates assume Bible text.

**Text-transformation prompts**
:   A few prompts replace text instead of producing a separate result:

    - **Translate** – replaces (or augments) the selected text with its
        translation.
    - **Enhance Note** – rewrites the existing bookmark note in place.

    When you run a text-transformation prompt, the result is written back to
    the source rather than shown as a new AI document.

**Tool restrictions**
:   Each prompt declares which tools it is allowed (or denied) to use. For
    example, *Summary* uses no tools at all, while *Explain Verses* is
    limited to Bible-reading tools. This means the same AI provider may have
    fewer tools available in one prompt than in another – this is
    intentional and helps each prompt stay focused.

### Where Prompts Appear

AI prompts are available in several places throughout the app. Each prompt is
configured to appear in the contexts where it is most useful. In menus they
are typically grouped under an `AI Actions` submenu.

**AI Text Processing (document display)**
:   Process an entire chapter or document through AI. Enable by selecting an
    AI Text Processing prompt in the window’s text display settings. The result
    replaces the document view.

**Verse selection**
:   When you tap a verse and the verse action dialog appears, available AI
    prompts are shown as action buttons.

**Text selection**
:   When you select text by long-pressing and dragging, AI prompts appear in
    the selection menu, under `AI Actions`.

**Window menu**
:   Long-press the window button at the bottom of the screen to open the
    window popup menu. AI prompts are listed under `AI Actions`.

**Workspace menu**
:   AI prompts also appear in the workspace toolbar’s three-dot menu, under
    `AI Actions`.

**Note editor**
:   When editing a bookmark note, AI prompts can assist with writing.

### Running a Prompt

**Example: Explaining a verse**

1. Tap a verse to open the verse action dialog.
2. Tap the `Explain Verses` button.
3. The AI processes your request and displays the result.

**Example: Using AI Text Processing for a whole chapter**

1. Long-press the window button at the bottom of the screen.
2. Tap `Text Options`, then `All Text Options`.
3. Under the AI Text Processing section, select a prompt (e.g. `Translate`).
4. The entire chapter is processed through the selected prompt.

**Example: Word study on selected text**

1. Long-press a word in the Bible text to start a text selection.
2. Extend the selection if needed.
3. Tap `Word Study` from the context menu.

### The Agent Log

When an AI prompt runs, a log panel shows the progress in real time. You can
see:

- Which tools the AI is calling (e.g. reading verses, looking up dictionaries)
- Permission requests for write operations
- Token usage and estimated cost

You can cancel a running prompt at any time by tapping the cancel button.

## AI Tools

AI prompts can use *tools* – specialized functions that let the AI read data
from your app or make changes. This is what allows the AI to look up cross-
references, search your Bible, read your bookmarks, and more.

### Read Tools

Read tools retrieve information without making any changes. They run without
asking — there is no runtime permission prompt for them. If you want to keep
a read tool away from the AI, disable it instead (globally or for a single
prompt) using the tool list described under [Permissions](#permissions). For
example, you can keep prompts from reading your bookmarks by disabling the
bookmark read tools either globally or just for that one prompt.

- **Read verse content** – Retrieve the text of any verse or chapter from any
    installed Bible translation.
- **Search Bible** – Search for verses containing specific keywords.
- **Read commentaries** – Look up commentary on specific verses from installed
    commentary modules.
- **Look up dictionary entry** – Look up entries in installed dictionaries and
    lexicons (e.g. Strong’s, Robinson’s).
- **Get bookmarks for verse** – Retrieve your bookmarks at a specific verse.
- **Get bookmarks with label** – Get all bookmarks assigned to a specific label.
- **List all labels** – Get a list of all your labels.
- **Read study pad content** – Read entries from a study pad.
- **Search study pads** – Search study pad entries by keyword.
- **List installed documents** – Get a list of installed Bibles, commentaries,
    and dictionaries.

### Write Tools

Write tools can create or modify data in your app. They are subject to the
[Permissions](#permissions) system: each write operation either runs after asking
you (the default), follows a saved permission decision, or is blocked
entirely by a per-tool override.

- **Create bookmark** – Create a new bookmark at a verse, optionally with a
    note.
- **Add bookmark note** – Add a note to an existing bookmark.
- **Update bookmark note** – Modify an existing bookmark’s note.
- **Create label** – Create a new label for organizing bookmarks and study pads.
- **Add label to bookmark** – Assign a label to a bookmark.
- **Add study pad entry** – Add a text entry to a study pad.
- **Set document title** – Set the title for an AI-generated document.
- **Open study pad** – Complete the task and open a specified study pad.
- **Finish without document** – End the task without producing a document
    (for action-only prompts that create bookmarks, etc.).

!!! tip

    You can view all available tools and their descriptions directly in the app
    by opening the **Available tools** menu item in the prompt editor.

## Available Data and Documents

The AI agent can access a wide range of data through its tools. Understanding
what data is available helps you write more effective prompts.

### Installed Bible Modules

The AI has access to **all installed modules** on your device:

- **Bible translations** – Any installed Bible version (e.g. KJV, ESV, NIV).
    The AI can read and compare text across multiple translations.
- **Commentaries** – All installed commentary modules. The AI can look up
    commentary entries for specific verses.
- **Dictionaries and lexicons** – Including Strong’s concordance, Robinson’s
    morphological codes, and any other installed dictionary modules. These
    allow in-depth word studies.

!!! tip

    The more modules you have installed, the richer data the AI can work with.
    Installing additional commentaries, dictionaries, and translations gives the
    AI more resources for answering your questions.

### Cross-Referencing Capabilities

The AI can combine information from multiple sources in a single response:

- **Translation comparison** – Compare how different Bible versions render
    the same passage.
- **Commentary + dictionary integration** – Read a verse, look up commentary,
    then look up individual words in a lexicon – all in one prompt.
- **Word studies** – Follow a chain from a verse to its Strong’s numbers to
    dictionary definitions to find the original meaning.
- **Bible text search** – Search for verses containing specific keywords across
    installed translations.

### Your Personal Data

The AI can also access your personal study data:

- **Bookmarks** – Find bookmarks at specific verses or by label.
- **Labels** – List all your labels for organizing and filtering.
- **Notes** – Read bookmark notes you have written.
- **Study pads** – Read and search study pad content.

### Writing Effective Prompts

To get the most out of the AI agent, mention the specific actions you want it
to perform. The AI uses its tools based on your instructions.

**Refer to capabilities by name:**

- “Look up what Matthew Henry’s commentary says about this verse”
- “Search for all verses mentioning ‘grace’ in KJV”
- “Find the Strong’s dictionary entry for the main Greek word in this verse”
- “Check my bookmarks with the ‘Study’ label”

**Combine multiple tools:**

- “Compare this verse in KJV, ESV, and NIV, then look up the commentary”
- “Read this passage, look up the key Greek words in Strong’s dictionary, and
    summarize the findings in a study pad”

**Specify the output format:**

- “Create a study pad with a verse-by-verse analysis”
- “Add a bookmark note summarizing the cross-references”
- “Give a brief answer without creating any documents”

**Ask for sources:**

- “Include verse references for all claims”
- “Cite which commentary or dictionary you are quoting”

## Permissions

Write tools require your permission before the AI can use them. This ensures
the AI cannot modify your data without your knowledge.

!!! note

    Read tools never ask for runtime permission. If you want to keep a read
    tool away from the AI, disable it entirely — either globally in
    `Default tool settings` or for one prompt in the prompt editor. Write
    tools have a separate runtime permission system, described below.

### Permission Modes

There are four permission modes:

**Always ask** (default)
:   The AI asks for your permission every time it wants to use a write tool.
    A dialog shows what the tool will do and lets you approve or deny.

**Ask once per run**
:   The AI asks permission the first time it uses a write tool during a prompt
    run. After you approve, subsequent write operations in the same run proceed
    without asking.

**Allow all**
:   All write operations are allowed automatically without asking. Use this only
    if you fully trust the prompt.

**Deny all**
:   All write operations are denied automatically. The AI can only read data.

### Setting Permissions

Permissions can be configured at three levels, with finer levels overriding
coarser ones:

1. **Global default** – In `AI Settings` > `Configure Connection` >
    `Manage tool permissions`. This sets the default permission mode for
    all prompts.
2. **Per-prompt** – When editing a custom prompt, you can set a permission
    mode that overrides the global default for that prompt only.
3. **Per-tool override** – In the same tool permissions dialog, you can
    force individual write tools to **Always allow** or **Always deny**
    regardless of the current permission mode. Per-tool overrides are useful
    when you trust certain operations (e.g. creating bookmarks) but want to
    keep approving others manually.

## Custom Prompts

### Creating a Custom Prompt

You can create your own prompts for specialized tasks:

1. Open the top left main menu (☰).
2. Tap `AI Settings`.
3. Tap the `+` button.
4. Fill in the prompt details:

    - **Name** – A short name shown in menus.
    - **Description** – What the prompt does (shown in the prompt list).
    - **Template** – The instructions sent to the AI. This is the core of your
        prompt.
    - **Contexts** – Where this prompt should appear (verse selection, text
        selection, window menu, etc.).
    - **Provider and model** – Optionally override which provider and model to
        use for this prompt.
5. Tap `Save`.

### Editing Built-in Prompts

Built-in prompts cannot be modified directly. To customize one:

1. Open the built-in prompt you want to modify.
2. Use the `Copy to customize` option.
3. Edit the copy as needed and save.

### Prompt Template Tips

- **Be specific** – Clearly describe what you want the AI to do and what
    format the output should be in.
- **Mention tools** – If you want the AI to look up commentaries or
    dictionaries, mention it in the prompt. For example: “Look up relevant
    commentary entries to support your explanation.”
- **Define the output format** – Specify whether you want bullet points,
    paragraphs, a table, or other formats.
- **Set the language** – If you want output in a specific language, say so
    explicitly.

### Sharing Prompts (CSV)

Custom prompts can be exported and imported as CSV files, making it easy to
share your prompts with other *AndBible* users:

- **Export** – From the prompt list, use the export option to save your custom
    prompts to a CSV file.
- **Import** – Use the import option to load prompts from a CSV file. You
    will be asked whether to import the file as a *normal CSV import* (the
    prompts become regular, editable custom prompts) or as an *add-on prompt
    pack* (see below).

### Add-on Prompt Packs

In addition to importing prompts directly into your prompt list, you can
install prompt packs as *add-on modules*. When a CSV is imported as an
add-on:

- Prompts from the pack appear in your prompt list with an **add-on badge**
    showing where they came from.
- The prompts are **read-only** – you cannot edit them, but you can copy
    them to customize.
- Removing the add-on module removes all its prompts at once.
- Add-on prompts are not synchronized via cloud sync; the underlying CSV
    file lives with the add-on module.

This is useful for distributing curated prompt collections (for example
study packages produced by a third party) without polluting your personal
prompt list with prompts you cannot easily remove later.

## Cost Tracking

*AndBible* tracks token usage and provides estimated costs for each provider:

- **Token counts** – Input tokens (text sent to the AI), output tokens
    (text received), and prompt-cache tokens (when the provider’s own prompt
    caching kicks in) are tracked separately.
- **Estimated cost** – Calculated based on each model’s published pricing.
- **Per-provider breakdown** – Each configured provider shows its own usage
    and cost.
- **Reset** – You can reset the usage counters at any time.

View cost tracking in `AI Settings` > `Configure Connection` under the
Usage section.

!!! note

    Cost estimates are approximate. Always check your provider’s billing
    dashboard for actual charges.

## Cloud Sync

When [Cloud Sync](cloud_sync.md) is enabled, the following AI data is synchronized
between your devices:

- **Synced:**

    - Custom prompts (including their categories and per-prompt settings)
    - Prompt categories (built-in and user-created)
    - Provider configurations (provider type, endpoint, configured models)
    - Default model selection and other global AI settings
    - Usage and cost statistics
- **Not synced:**

    - API keys – these remain on each device for security

API keys are intentionally left out of sync. You need to enter your API key
separately on each device. Add-on prompt packs are also not synced through
this mechanism – they come with the add-on module they live in.

## Tips and Troubleshooting

### Recommended Setup for Beginners

1. Start with **Google Gemini** – it has a free tier that is sufficient for
    trying out the AI features.
2. Try the **Explain Verses** prompt first – tap a verse and select it from
    the verse action dialog.
3. Keep the permission mode set to **Always ask** until you are comfortable
    with how the AI uses tools.

### Reducing Costs

- Use a smaller, cheaper model for simple tasks like translation.
- Use a more capable (and expensive) model only for complex tasks like word
    studies.
- Configure multiple providers and assign them to specific prompts.

### Common Issues

**“API key invalid” or authentication errors**
:   Double-check that your API key is entered correctly. Visit your provider’s
    console to verify the key is active.

**Slow responses or timeouts**
:   AI responses can take several seconds, especially for longer passages or
    complex prompts. Check your internet connection if responses consistently
    fail.

**Unexpected or low-quality results**
:   Try a more capable model, or refine your prompt to be more specific about
    what you want. Different models have different strengths.

**Higher costs than expected**
:   Check which model you are using – larger models cost significantly more.
    Review the cost tracking in AI Settings to identify which prompts consume
    the most tokens. Consider switching to a smaller model for routine tasks.
