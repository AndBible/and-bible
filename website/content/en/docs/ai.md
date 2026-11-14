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
- **Custom (OpenAI compatible)** – Connect to any API endpoint that uses the
    OpenAI or the Anthropic format (self-hosted or other providers). You choose
    the *API format* when you add it.

The list of providers and the available models for each one is maintained
inside the app. To see the exact, up-to-date list, open `AI Settings`, then
`Providers` in the connection settings, and tap `+` (*Add provider*). The
steps are described under [Adding a Provider](#adding-a-provider).

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

### The AI disclaimer

Before you can add your first provider (by Quick Setup or by hand), *AndBible* asks
you to read the **Accept AI Disclaimer** dialog and to confirm with **I accept and take full
responsibility**. It explains that AI can make mistakes, that your data is sent to the
provider you choose, and that you remain responsible for how you use the results. You can read
the same text at any time from the first row of the connection settings,
**Important: Read before using AI tools**.

### Adding a Provider

There are two ways to connect *AndBible* to an AI provider.

**Quick Setup** is the shortest. It offers Google Gemini, Anthropic (Claude) and
OpenAI (ChatGPT) only:

1. Open the top left main menu (☰) and tap `AI Settings`.
2. While AI is not yet configured, tap `Configure Connection`. This opens the
    *Connection settings* screen.
3. Tap `Quick Setup` (*Set up AI with a recommended provider in a few steps*). The row is
    shown only while no provider exists.
4. Choose a provider, enter your API key and tap `Test connection`.
5. When *AI is ready!* appears, you can start using the prompts.

Quick Setup also adds the provider’s recommended model and makes it your default model.

**Adding a provider by hand** works for every provider in the list:

1. Open the top left main menu (☰) and tap `AI Settings`.
2. Open the *Connection settings*: tap `Configure Connection` while AI is not yet
    configured, or the gear icon (*Connection settings*) afterwards.
3. Tap `Providers`, then `+` (*Add provider*).
4. Under *Select provider type*, choose your provider.
5. Enter a *Provider name* (the name shown in the lists) and paste your *API key*.
    For **Custom (OpenAI compatible)** also enter the *API endpoint* and choose the *API format*.
6. Tap `OK`.

The dialog saves only the provider; it does not choose a model. Add the models you want
to use under `Models` (see [Available Models](#available-models)). The first model you add
becomes the default model.

For **OpenCode Go**, add a **Custom** provider using the **OpenAI** API format
and `https://opencode.ai/zen/go/v1` as the endpoint. AndBible automatically
sends the required `x-opencode-session` header to `opencode.ai`. Its random
identifier stays the same throughout one AI run, including tool calls and
retries, and changes when you start a new run. No manual header configuration
is needed.

### Multiple Providers

You can configure multiple providers simultaneously. This is useful if you
want to use a cheaper model for simple tasks (like translation) and a more
capable model for complex tasks (like word studies).

The default is a **model**, not a provider: the model marked as default (the star in the
`Models` list, *Set as default model*) is used by every prompt that does not choose its own.
Individual prompts can be set to use a specific model, overriding the default; the model
carries its provider with it (see [Custom Prompts](#custom-prompts)).

### Available Models

*AndBible* ships with a built-in list of recommended models for each provider.
For providers that publish a public model catalogue, *AndBible* also fetches
the current list **automatically** when you add the provider, and again when you open the
model picker, so newly released models become available without an app update.

In the **Models** screen (`Connection settings` > `Models`) you can also:

- **Add a model** – Pick one from the list for a provider, or type a model name manually if
    you want to use a model that is not in the built-in or fetched list. Models that
    *AndBible* knows are marked **✓ Supported**; switch on **Show also unsupported models**
    to see the others. For a model whose price is not known you can enter the input and output
    price yourself, which the cost estimates use.
- **Set the default model** – Choose which model is used when a prompt
    doesn’t specify its own (*Set as default model*).

!!! note

    Not every provider supports model listing. For those that do not, the
    built-in list and manually added models are the only options.

## Using AI Prompts

Prompts are instructions that tell the AI what to do with your selected text.
*AndBible* comes with several built-in prompts and allows you to create your own.

### Built-in Prompts

*AndBible* includes a set of built-in prompts. They are organised into three
categories. The names below are the ones shown in the app.

#### Study prompts

These prompts focus on understanding and analysing Bible text. Most of them
are *Bible-only*, meaning they only appear when the active document is a
Bible.

**Explain verses**
:   Explains the meaning, context, and theological significance of the
    selected verses, grounded in the available commentaries.

**Explain verses → Study Pad**
:   Like *Explain verses*, but builds a Study Pad with a structured
    verse-by-verse explanation, bookmarks, key themes, and an application
    section.

**Word study**
:   Analyzes the original Hebrew or Greek words in the selected text,
    providing definitions, usage patterns, and dictionary entries.

**Cross-references**
:   Finds and explains Bible passages related to the selected verses.

**Compare translations**
:   Shows how different installed Bible translations render the same passage.

**Thematic study**
:   Builds a Study Pad with passages and notes around the central theme of the
    selected verses.

#### Notes prompts

These prompts help create and improve notes and study material.

**Bookmark & annotate**
:   Creates a bookmark with an AI-generated study note for the selected verses.

**Enhance note**
:   Improves the grammar, clarity, and readability of an existing bookmark
    note. It is offered in the note editor and rewrites the note text in place
    (see *Text-transformation prompts* below).

**Open study layout**
:   Opens commentary and parallel translation windows for further study of
    the selected passage.

#### General prompts

These prompts work on any document type, not only Bibles.

**Translate to <language>**
:   Translates the selected text into the language your app interface is set
    to. The prompt name in the menu shows the actual target language, e.g.
    “Translate to Finnish”. If a translation in that language is already
    installed, it can use it directly instead of generating a new translation.
    It is shown in the verse selection and window menus, and its result is
    shown as an AI document like that of other prompts.

**Summary**
:   Creates a concise summary of the selected text.

**Ask a question**
:   Lets you ask a free-form question about the selected passage. It asks you
    to *Specify task* first.

**Custom prompt**
:   Runs a free-form, one-off instruction on the selected passage without
    creating a saved custom prompt. It asks you to *Specify task* first.

**Workspace assistant**
:   Manages windows on your behalf: it can create, close, rearrange and
    change documents in the current workspace based on your instructions. It
    is offered in the workspace menu only and asks you to *Specify task* first.

### Prompt Categories

Prompts are organised into categories (**Study**, **Notes**, **General**).
Categories control how prompts are grouped in the *AI Actions* menus and
in `AI Settings`. A **Favorites** group (the star on a prompt’s row) collects the
prompts you have marked as favorites at the top of the list.

You can:

- Create your own categories for custom prompts (⋮ menu > *New category*).
- Move custom prompts between categories (*Move to category…*).
- Hide an entire category (including its prompts) from the AI Actions menus
    with *Hide from AI actions*, and bring it back with *Show in AI actions*
    – useful for parking your own work-in-progress prompts out of the way.
- Hide a single prompt, built-in or your own, with *Hide* in its row menu.
    *Restore hidden prompts* in the top ⋮ menu shows the hidden prompts again.

Built-in prompts are pre-assigned to the appropriate categories and cannot
be moved.

### Special Prompt Behaviors

Some prompts behave differently from the default “ask a question, get an
answer” pattern.

**Bible-only prompts**
:   Many study-oriented prompts (like *Explain verses*, *Cross-references*
    or *Word study*) only appear when the active document is a Bible. They
    are hidden in commentaries, dictionaries and other documents because
    their templates assume Bible text.

**Text-transformation prompts**
:   A prompt marked as a *Text transformation* uses a simplified system prompt
    that keeps the formatting (links, headings, bold and italic) and uses no tools.
    *Translate to <language>* and *Enhance note* are such prompts.

    Only in the note editor is the result written back to the source:
    *Enhance note* rewrites the existing bookmark note in place. Run from
    anywhere else, for example *Translate to <language>* from the verse menu,
    a text-transformation prompt produces an AI document like any other prompt.

**Tool restrictions**
:   Each prompt declares which tools it is allowed (or denied) to use. For
    example, *Summary* uses no tools at all, while *Explain verses* is
    limited to Bible-reading tools. This means the same AI provider may have
    fewer tools available in one prompt than in another – this is
    intentional and helps each prompt stay focused.

**Asking once per run**
:   Several built-in prompts that change your data (*Explain verses → Study
    Pad*, *Thematic study*, *Bookmark & annotate*, *Open study layout* and
    *Workspace assistant*) use the permission mode *Ask once per run*, whatever
    your global mode is, unless your global mode is stricter. See
    [Permissions](#permissions).

### Where Prompts Appear

AI prompts are available in several places throughout the app. Each prompt is
configured to appear in the contexts where it is most useful (its *Show in*
setting). In menus they are typically grouped under an `AI Actions` entry.
The AI entries appear only after AI has been configured.

**Verse selection**
:   When you tap a verse and the verse action dialog appears, a single `AI`
    button (robot icon; *AI Action* when the dialog is shown vertically) opens
    the *Select AI Action* sheet with the prompts for verses. If the button is
    not visible, it may be under `More`.

**Text selection**
:   When you select text by long-pressing and dragging, the selection menu
    has an `AI Actions` entry. It opens the same *Select AI Action* sheet, listing
    the prompts that are shown for *Verse selection*. A custom prompt that is
    marked *Text selection* only is not shown by any of the menus yet, so also
    tick *Verse selection* for it.

**Window menu**
:   Long-press the window button at the bottom of the screen (or tap the button
    at the top right of the window) to open the window menu. AI prompts are
    listed under `AI Actions`.

**Workspace menu**
:   The three-dot menu (⋮, *More options*) of the reading view has an
    `AI Actions` entry (robot icon). It lists the prompts marked *Workspace
    menu*, such as *Open study layout* and *Workspace assistant*.

**Note editor**
:   When editing a bookmark note, AI prompts can assist with writing, for
    example *Enhance note*.

### Running a Prompt

**Example: Explaining a verse**

1. Tap a verse to open the verse action dialog.
2. Tap `AI`, then choose `Explain verses`.
3. The AI processes your request and displays the result.

**Example: Word study on selected text**

1. Long-press a word in the Bible text to start a text selection.
2. Extend the selection if needed.
3. Tap `AI Actions` in the selection menu, then choose `Word study`.

**The result.** A prompt that produces a document saves it as a page of the
*AI Documents* document (a [My Document](my_documents.md)) and opens it, normally in
the links window. If you run the same prompt again on the same verses, the saved page
opens instead of a new run. The page has a **Regenerate** action, which lets you add
*Additional instructions (optional)* and choose **Keep previous version** or **Fresh run
(ignore previous result)**.

**Before the run.** *Ask a question*, *Custom prompt* and *Workspace assistant* first
ask you to *Specify task* (any prompt can do so with the *Specify before run*
setting). With the **Ask model before run** setting in the connection settings, a *Select model*
dialog appears before every run, where you can also tick *Set as default model for this
prompt*; it is skipped when the prompt has its own model.

### The Agent Log

When an AI prompt runs, a log panel (labelled *AI Assistant* while idle) shows the
progress in real time. You can see:

- Which tools the AI is calling (e.g. reading verses, looking up dictionaries)
- Permission requests for write operations
- Token usage and estimated cost
- The model being used, with *Change default model (currently: …)* to switch the default

You can cancel a running prompt at any time with `Stop AI`, and close the panel with
`Close agent log`. The *Hide AI panel when done* setting in the connection settings hides the
panel automatically after a task finishes or is cancelled; on an error it stays open.
Rate limits are shown as a log line (*Rate limited. Retrying in …*), and when a run reaches the
*Max agent iterations* limit a *Continue?* dialog asks whether to go on.

### The Raw LLM Log

Under a finished step in the agent log, tap **View raw LLM log** to see exactly
what was exchanged with the AI provider. The **Raw LLM Log** screen lists the
system prompt, your message, the tool definitions, each tool call and its
result, and each API response, with token counts. Tap an entry to expand or
collapse it. Use the copy and share buttons in the top bar to take the whole log
elsewhere. If there is nothing to show, the screen says *No raw log data
available*.

The three-dot menu may contain **Delete** (for a saved log, after a confirmation)
and **Report AI bug**.

### AI Log History

*AndBible* saves the raw logs of your AI conversations. To browse them, open
`AI Settings`, then the connection settings (the gear icon, *Connection settings*;
`Configure Connection` while AI is not yet configured) and, under **Usage**, tap
**AI Log History** (*View and manage saved AI conversation logs*). The list
shows each log's prompt name, model, token counts and cost, and time, and marks
logs that ended in an error with **Error**. Tap a log to open it in the Raw LLM
Log screen. The logs stay on your device and are not synchronized.

- **Delete selected:** long-press a log to start selecting, tick more logs, and
    tap the delete icon in the top bar.
- **Delete old logs…:** in the three-dot menu, choose *Older than 1 week*,
    *Older than 1 month*, *Older than 3 months* or *Delete all*.
- **Auto-delete old logs:** the setting just below **AI Log History** in
    `Connection settings` deletes logs automatically after the number of days you
    enter (30 by default), or keeps everything when you tick *disable* (*Disabled (keep all)*).

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
- **Search by Strong's number** – Find the verses that use a Strong’s number.
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
- **List My Documents** and **List My Document pages** – See your
    [My Documents](my_documents.md) and their pages.
- **List general book keys** and **Read general book content** – Browse and
    read general books such as EPUBs.
- **List windows** – See the windows of the current workspace.

Four more tools only finish a task and need no permission: **Set document title**,
**Open study pad**, **Open My Document page** and **Finish without document** (for
action-only prompts that create bookmarks, etc.). The *Available tools* screen lists
them with the read tools, and they cannot be switched off.

### Write Tools

Write tools can create or modify data in your app. They are subject to the
[Permissions](#permissions) system: each write operation either runs after asking
you (the default), follows a saved permission decision, or is blocked
entirely by a per-tool override.

- **Create bookmark** – Create a new bookmark at a verse, optionally with a
    note.
- **Add bookmark note** – Add a note to an existing bookmark.
- **Update bookmark note** – Modify an existing bookmark’s note.
- **Delete bookmark** – Delete a bookmark.
- **Create label** – Create a new label for organizing bookmarks and study pads.
- **Add label to bookmark** and **Remove label from bookmark** – Change the labels
    of a bookmark.
- **Delete label** – Delete a label.
- **Add study pad entry** – Add a text entry to a study pad.
- **Update study pad text entry** – Change a text entry of a study pad.
- **Create study pad** – Create a study pad with its entries.
- **Create My Document**, **Add My Document page**, **Edit My Document page** and
    **Delete My Document page** – Create and change [My Documents](my_documents.md).
- **Create window**, **Manage window** and **Set window document** – Open, close,
    rearrange and change the documents of windows.

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
- **My Documents** – List your My Documents and their pages.
- **General books** – Browse and read installed general books, such as EPUBs.
- **Windows** – See which windows are open in the workspace.

### Document access

By default the AI can read all installed modules. To limit this, open the connection settings
and, under **Behavior**, tap **Document access** (*Choose which documents the AI can read*). The
*AI document access* screen lists your documents; switch off the ones the AI should not see and
save. The row then tells how many documents are excluded. Fewer documents mean less noise and
lower costs.

A tool you have switched off or denied is not offered to the model at all, so
it cannot try to use it.

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
    `Default tool settings` (`Tool permissions` in the connection settings) or for one prompt in the prompt editor. Write
    tools have a separate runtime permission system, described below.

### Permission Modes

There are four permission modes:

**Always ask** (default)
:   The AI asks for your permission every time it wants to use a write tool.
    The *Agent Permission* dialog shows what the tool will do and offers
    *Allow once*, *Allow for this session*, *Allow all tools this session*,
    *Always allow "<tool>"* (after a confirmation) and *Deny*.

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

1. **Global default** – The `Permission mode` row under **Behavior** in the
    connection settings (the gear icon in `AI Settings`) sets the default
    permission mode for all prompts.
2. **Per-prompt** – In the prompt editor’s `Permissions` tab you can set a
    permission mode that overrides the global default for that prompt only (the
    first choice, *Use default*, keeps the global mode), and allow
    individual write tools for it without asking. Only your own custom prompts
    can allow write tools this way: built-in and add-on prompts, and prompts
    you import from a CSV file, never run write tools without asking unless
    you allowed them yourself. The mode of a built-in or add-on prompt can
    only make your global mode stricter, never looser: if your global mode is
    *Always ask*, a built-in prompt set to *Ask once per run* asks every time
    too.
3. **Per-tool override** – In the connection settings, `Tool permissions`
    opens `Default tool settings`. There you can force individual write tools
    to **Always allow** or **Always deny** (the default is *Ask (default)*)
    regardless of the current permission mode, and switch read tools **Enabled**
    or **Disabled**. Per-tool overrides are useful when you trust certain
    operations (e.g. creating bookmarks) but want to keep approving others
    manually. In a prompt’s `Permissions` tab each tool offers the same choices
    plus *Default (…)*, which follows the global setting.

## Custom Prompts

### Creating a Custom Prompt

You can create your own prompts for specialized tasks:

1. Open the top left main menu (☰).
2. Tap `AI Settings`.
3. Tap the `+` button.
4. Fill in the prompt details on the `Prompt` tab:

    - **Name** – A short name shown in menus.
    - **Description** – What the prompt does (shown in the prompt list).
    - **Prompt template** – The instructions sent to the AI. This is the core of your
        prompt.
    - **Category** – The group the prompt belongs to.
    - **Show in** – Where this prompt should appear: *Verse selection*, *Text selection*,
        *Window menu*, *Workspace menu* and *Note editor*.
    - **Bible documents only** – Hide the prompt in other document types.
    - **Text transformation** – Use the simplified system prompt that keeps formatting
        and uses no tools (see [Special Prompt Behaviors](#special-prompt-behaviors)).
5. On the `Advanced` tab you can optionally choose the **Model** for this prompt
    (the model carries its provider; there is no separate provider choice), and set
    options such as *Max iterations override* and *Specify before run*. The `Permissions`
    tab sets the permission mode and tools for the prompt.
6. Tap the check mark in the top bar to save.

The `+` button on the `AI Settings` screen is labelled *New prompt*.

### Editing Built-in Prompts

Built-in prompts cannot be modified directly; their editor is titled
*Built-in* and shows a notice. To customize one:

1. Open the built-in prompt you want to modify.
2. Use the `Copy to customize` item in the editor’s ⋮ menu. The copy’s name ends in
    “ (copy)”.
3. Edit the copy as needed and save.

Built-in prompts you do not need can be hidden (see [Prompt Categories](#prompt-categories)).

You can also choose `Copy` from any prompt's menu in the prompt list. The
copy keeps the original's favorite mark. The list scrolls to the new copy
and briefly emphasizes its row, including when animations are disabled.
Your search and filters stay active if the copy matches them. If they would
hide the copy, they are cleared so you can see it.

A copy is a custom prompt like any other: it can use the same tools as the
original, but its write tools ask for your permission (following the
[permission mode](#permissions)) until you allow them in the copy's tool list
or set the copy's permission mode. An add-on prompt's *Allow all* mode is not
copied either. A copy of your own custom prompt keeps the permissions you gave
the original.

Prompts you copied or imported before this version keep the permissions they
were saved with. Open such a prompt in the prompt editor to check its tool
list and permission mode, and change them if needed.

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

- **Export** – In the ⋮ menu of `AI Settings`, choose *Export prompts to CSV* to save your
    own custom prompts to a CSV file. Built-in prompts are not exported.
- **Import** – Choose *Import prompts from CSV* in the same menu. Before you pick the file you
    will be asked whether to *Import as editable prompts* (the prompts become regular, editable
    custom prompts) or to *Install as add-on (read-only)* (see below).
- **Permissions are not imported** – A CSV file may come from someone else, so
    importing it never lets its write tools run without asking: write tools the
    file allows become "ask" and an *Allow all* mode becomes the default mode.
    Everything else (text, contexts, read tools, denied tools) is imported as
    is. When the file updates a prompt you already have (same id), that prompt
    keeps the write tools and *Allow all* mode you allowed for it, unless the
    file makes them stricter: a write tool the file denies stays denied, and a
    permission mode set in the file replaces *Allow all*. A row with the id of
    an installed add-on prompt is imported as a new custom prompt with a new id.

### Add-on Prompt Packs

In addition to importing prompts directly into your prompt list, you can
install prompt packs as *add-on modules*. When a CSV is imported as an
add-on:

- Prompts from the pack appear in your prompt list with an **add-on badge**
    (*Add-on: <module>*) showing where they came from. The module is named
    *Prompts_* followed by the name of the CSV file.
- The prompts are **read-only** – you cannot edit them, but you can copy
    them to customize.
- Write tools used by add-on prompts always follow your
    [permission](#permissions) settings; a pack cannot allow them on your
    behalf, not even with the *Allow all* mode. The prompt editor shows them
    the way they run: asking, not allowed.
- Removing the add-on module (delete it like any other document, see
    [Deleting Modules](documents.md#deleting-modules)) removes all its prompts at once.
- Add-on prompts are not synchronized via cloud sync; the underlying CSV
    file lives with the add-on module.

This is useful for distributing curated prompt collections (for example
study packages produced by a third party) without polluting your personal
prompt list with prompts you cannot easily remove later.

## Cost Tracking

*AndBible* tracks token usage and provides estimated costs:

- **Token counts** – Input tokens (text sent to the AI) and output tokens
    (text received) are shown in the usage total.
- **Estimated cost** – Calculated based on each model’s published pricing.
- **Total and per-model figures** – The connection settings show one total
    (*Token usage*, with the estimated cost) under **Usage**; the `Models` screen
    shows the cumulative cost of each model.
- **Reset** – You can reset the usage counters at any time (*Reset usage data*).

View cost tracking in the connection settings (the gear icon in `AI Settings`)
under the Usage section.

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
    - The raw AI logs (*AI Log History*)

The sync category is called **AI Settings** in *Device synchronization*.

API keys are intentionally left out of sync. You need to enter your API key
separately on each device. Add-on prompt packs are also not synced through
this mechanism – they come with the add-on module they live in.

## Tips and Troubleshooting

### Recommended Setup for Beginners

1. Start with **Google Gemini** – it has a free tier that is sufficient for
    trying out the AI features.
2. Try the **Explain verses** prompt first – tap a verse, tap `AI` in the verse
    action dialog and choose it.
3. Keep the permission mode set to **Always ask** until you are comfortable
    with how the AI uses tools.

### Reducing Costs

- Use a smaller, cheaper model for simple tasks like translation.
- Use a more capable (and expensive) model only for complex tasks like word
    studies.
- Configure several models (from one or more providers) and give specific prompts their own *Model*.
- Turn on **Ask model before run** to pick the model each time.
- Limit what the AI reads with **Document access**, the **Commentary response size limit** and
    **Max agent iterations** in the connection settings.

### Common Issues

**“API key invalid” or authentication errors**
:   Double-check that your API key is entered correctly. Visit your provider’s
    console to verify the key is active.

**“Continue?” appears during a run**
:   The run reached the *Max agent iterations* limit. Choose to continue, or stop and
    refine the prompt. A line *Rate limited. Retrying in …* in the log only means that
    the provider asked *AndBible* to wait.

**Something looks like a bug**
:   Open the raw log of the run (see [The Raw LLM Log](#the-raw-llm-log)) and choose *Report AI bug*
    in its ⋮ menu.

**Slow responses or timeouts**
:   AI responses can take several seconds, especially for longer passages or
    complex prompts. Check your internet connection if responses consistently
    fail.

**Unexpected or low-quality results**
:   Try a more capable model, or refine your prompt to be more specific about
    what you want. Different models have different strengths.

**Higher costs than expected**
:   Check which model you are using – larger models cost significantly more.
    Review the cost tracking in the connection settings and the *AI Log History*
    to identify which prompts consume the most tokens. Consider switching to a smaller model for routine tasks.
