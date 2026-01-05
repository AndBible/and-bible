#set document(
  title: "AndBible AI Features Roadmap",
  author: "AndBible Team",
)

#set page(
  paper: "a4",
  margin: (x: 2.5cm, y: 2.5cm),
  header: context {
    if counter(page).get().first() > 1 [
      #set text(size: 9pt, fill: gray)
      AndBible AI Features Roadmap
      #h(1fr)
      #counter(page).display()
    ]
  },
)

#set text(
  font: "Libertinus Serif",
  size: 11pt,
  lang: "en",
)

#set par(
  justify: true,
  leading: 0.65em,
)

#set heading(numbering: none)

#show heading.where(level: 1): it => {
  set text(size: 18pt, weight: "bold")
  v(0.5em)
  it
  v(0.3em)
}

#show heading.where(level: 2): it => {
  set text(size: 14pt, weight: "bold")
  v(0.8em)
  it
  v(0.3em)
}

#show heading.where(level: 3): it => {
  set text(size: 12pt, weight: "bold")
  v(0.6em)
  it
  v(0.2em)
}

#show link: it => {
  set text(fill: rgb("#1a5490"))
  it
}

// Title page
#align(center)[
  #v(3cm)

  #image("../svg/logo.svg", width: 4cm)

  #v(1cm)

  #text(size: 28pt, weight: "bold")[AndBible]

  #v(0.3cm)

  #text(size: 20pt)[AI Features Roadmap]

  #v(1cm)

  #text(size: 11pt, fill: gray)[
    Status: Planned / In Development \
    January 2025
  ]

  #v(2cm)

  #text(size: 10pt, style: "italic")[
    Powerful AI-assisted Bible study — completely opt-in, \
    fully transparent, and under your control.
  ]
]

#pagebreak()

// Content starts
= Introduction

AndBible is expanding its capabilities with AI-powered features designed to enhance Bible study. This roadmap outlines our vision for integrating Large Language Model (LLM) technology into AndBible, giving users powerful new tools while maintaining full control over their data and AI interactions.

*Goal:* make AI a helpful companion for Bible study — one that saves time on routine tasks, helps discover connections you might miss, and assists in organizing your study materials.

= Guiding Principles

#block(
  fill: rgb("#f5f5f5"),
  inset: 1em,
  radius: 4pt,
  width: 100%,
)[
  + *Completely Opt-In* — AI features are hidden and inactive by default. Nothing AI-related appears in the interface until you choose to enable it by configuring your own API connection. If you prefer AndBible without AI, you'll never see these features.

  + *User Control* — When enabled, you decide when and how AI is used. All features are customizable, and write operations require explicit permission.

  + *Transparency* — See exactly what the AI is doing through a real-time status log. No hidden operations.

  + *Flexibility* — Use any OpenAI-compatible API endpoint. This includes commercial services (OpenAI, xAI Grok, Mistral, Google Gemini, Groq, OpenRouter, and others) or your own self-hosted server (Ollama, vLLM, etc.).
]

= Core Features

== LLM Mode: Automatic Document Processing

Process Bible text automatically as you read, with results displayed inline.

*Use cases:*
- Translate commentaries and study resources into your native language (many excellent resources exist only in English or other major languages)
- Get AI-assisted analysis of original language words in Strong's-enabled Bible modules
- Apply custom transformations (summaries, simplifications) to any document

*How it works:* Enable LLM Mode in Text Display Settings, select a prompt (e.g., "Translate to Finnish"), and the text is processed automatically as you read. Results are cached locally — the same content won't be processed twice.

== Prompt Manager: Your AI Instructions

A central place to create, edit, and organize AI prompts that define what the AI should do.

*Built-in prompts (examples, subject to change):*
- *Translate to UI language* — Automatic translation to your app's language
- *Summarize* — Concise summaries of passages or chapters
- *Explain verses* — Synthesize insights from installed commentaries
- *Word study* — Analyze original language words with Strong's definitions
- *Create thematic bookmarks* — Identify and bookmark key passages

*Custom prompts:* Create your own for specific study tasks. Configure where each prompt appears:
- Text Display Settings (automatic processing)
- Verse selection menu (One Tap Actions)
- Text selection context menu
- Window menu (document-level actions)

== AI Actions: On-Demand Assistance

Manually triggered AI operations on selected text or documents.

#table(
  columns: (auto, 1fr),
  inset: 8pt,
  stroke: 0.5pt + gray,
  fill: (col, row) => if row == 0 { rgb("#e8e8e8") } else { none },
  [*Action*], [*What happens*],
  [Commentary summary], [AI reads all installed commentaries for selected verses and creates a unified summary],
  [Cross-reference analysis], [AI explains connections between related passages],
  [Study notes generation], [AI creates structured notes for a chapter, saved to your documents],
  [Thematic bookmarking], [AI identifies important themes and creates labeled bookmarks],
)

Results appear in different places depending on the action: summaries and analyses go to *My Documents*, bookmarks appear in your bookmark list, StudyPad entries in your StudyPads. All AI-created content is tracked in the status log, making it easy to review what the AI has done and remove unwanted items.

== AI-Powered Search

Traditional Bible search finds exact word matches. AI search understands meaning and context.

*How it works:* Describe what you're looking for in natural language, and the AI returns relevant Bible references — even when your search terms don't appear in the text.

*Examples:*
- _"Passages about God's faithfulness during trials"_ → Returns relevant verses from Psalms, Job, Romans, etc.
- _"Jesus teaching about money"_ → Finds parables and teachings even without the word "money"
- _"Old Testament prophecies about the Messiah's suffering"_ → Returns Isaiah 53, Psalm 22, etc.

AI search is integrated into the existing search interface as an additional search mode.

== AI-Enhanced Cross-References

Some Bible modules include cross-references, but coverage varies. AI can suggest additional connections based on:

- Thematic similarities
- Parallel narratives
- Prophetic fulfillments
- Linguistic patterns in original languages
- Theological concepts

*How it works:* Like LLM Mode, this is enabled in Text Display Settings and configured via Prompt Manager. When active, AI-generated cross-references appear as footnotes in the Bible text alongside regular cross-references — seamlessly integrated into your reading experience.

== My Documents & AI Links

Create and manage personal documents within AndBible, with special support for AI-generated content.

*Features:*
- Create study documents with multiple pages
- Supports Markdown formatting with Scripture links
- Documents appear in the document selector alongside installed books
- Full bookmark and StudyPad integration

*AI Links — Interactive AI Content:*

AI-generated documents can include special links that trigger further AI actions. For example:

#block(
  fill: rgb("#f8f8f8"),
  inset: 1em,
  radius: 4pt,
  width: 100%,
)[
  #set text(font: "DejaVu Sans Mono", size: 9pt)
  ```
  ## The Beatitudes (Matthew 5:3-12)

  Jesus opens the Sermon on the Mount with eight blessings...

  **Explore further:**
  - [Compare with Luke's version](ai://compare?refs=Matt.5.3-12,Luke.6.20-26)
  - [Word study: "Blessed" (μακάριος)](ai://word-study?strongs=G3107)
  - [Find similar teachings](ai://search?query=Jesus+teachings+on+humility)
  ```
]

Clicking these links triggers the corresponding AI action with pre-configured parameters. This creates *interactive study documents* that enable deeper exploration without manual prompt configuration.

== What the AI Can Access

The AI can read from and write to various parts of AndBible, with appropriate permissions.

*Read capabilities (automatic):*
- Bible text from installed translations
- Commentary entries
- Cross-references
- Dictionary and Strong's definitions
- Your bookmarks, labels, and StudyPads

*Write capabilities (requires permission):*
- Create bookmarks with notes
- Add entries to StudyPads
- Create new labels

*Note:* A dedicated "AI Documents" document is available where AI can save its output without requiring separate permission. This keeps AI-generated content organized in one place by default.

All AI-created content is marked with its source, so you always know what was generated by AI.

= Control & Management

== Permissions System

*You stay in control:*
- Write operations require explicit approval
- Permission dialog shows exactly what the AI wants to do
- Options per operation type: "Always ask," "Allow for session," "Always allow," or "Deny"

*Status visibility:*
- Real-time log shows AI operations as they happen
- See what data is being read and what changes are being made
- Cancel operations at any time

== Cache Manager

Manage your locally cached AI-processed content:

- *View cache contents* — See what's been processed and when
- *Selective deletion* — Remove specific entries or clear by date range
- *Invalidation* — Mark old entries for re-processing (e.g., after switching to a better model)
- *Storage info* — Monitor cache size and manage disk usage

Useful when you want to re-process content with improved prompts or a different LLM provider.

== Cost Tracking

Monitor how much your AI usage costs:

- *Configure pricing* — Set per-token prices for your LLM provider (input and output tokens)
- *Per-operation costs* — See how much each AI action costs before and after execution
- *Usage history* — Track cumulative costs over time
- *Budget awareness* — Make informed decisions about which operations to run

Since different providers have vastly different pricing, this helps you stay in control of your spending.

== Privacy & Sync

- *Your API, your data* — Configure your own API endpoint (commercial providers or self-hosted servers)
- *No third-party data sharing* — Data only goes to your chosen API provider
- *Local caching* — Processed content stored on your device
- *Device sync* — When enabled, AI-generated documents, cached content, and custom prompts sync between your devices via Google Drive or Nextcloud (using AndBible's existing sync feature), just like bookmarks and notes

= Technical Notes

- *API Compatibility:* Works with any OpenAI-compatible endpoint
- *Caching:* Processed content cached locally to avoid redundant API calls
- *Offline Access:* Cached content available offline; new processing requires internet
- *Sync Support:* AI content and custom prompts sync between devices

= Summary

AndBible's AI features are designed to enhance your Bible study:

#table(
  columns: (auto, 1fr),
  inset: 8pt,
  stroke: 0.5pt + gray,
  fill: (col, row) => if row == 0 { rgb("#e8e8e8") } else { none },
  [*Benefit*], [*How*],
  [Remove language barriers], [On-the-fly translation of any text],
  [Discover connections], [AI-powered search and cross-references],
  [Save time], [Automated summaries and commentary synthesis],
  [Organize insights], [AI-assisted bookmarking and note generation],
  [Enable exploration], [Interactive AI links for deeper study],
  [Maintain control], [Transparent permissions and customizable prompts],
)

#v(1em)

These tools make deep Bible study more accessible while keeping you firmly in charge of your study journey.
