# AndBible AI Features Roadmap

**Status:** Planned / In Development
**Last Updated:** January 2025

## Introduction

AndBible is expanding its capabilities with AI-powered features designed to enhance Bible study. This roadmap outlines our vision for integrating Large Language Model (LLM) technology into AndBible, giving users powerful new tools while maintaining full control over their data and AI interactions.

Our goal: **make AI a helpful companion for Bible study** — one that saves time on routine tasks, helps discover connections you might miss, and assists in organizing your study materials.

## Guiding Principles

1. **Completely Opt-In** — AI features are hidden and inactive by default. Nothing AI-related appears in the interface until you choose to enable it by configuring your own API connection. If you prefer AndBible without AI, you'll never see these features.

2. **User Control** — When enabled, you decide when and how AI is used. All features are customizable, and write operations require explicit permission.

3. **Transparency** — See exactly what the AI is doing through a real-time status log. No hidden operations.

4. **Flexibility** — Use any OpenAI-compatible API endpoint. This includes commercial services (OpenAI, xAI Grok, Mistral, Google Gemini, Groq, OpenRouter, and others) or your own self-hosted server (Ollama, vLLM, etc.). Create custom prompts tailored to your study needs.

---

## Core Features

### LLM Mode: Automatic Document Processing

Process Bible text automatically as you read, with results displayed inline.

**Use cases:**
- Translate commentaries and study resources into your native language (many excellent resources exist only in English or other major languages)
- Get AI-assisted analysis of original language words in Strong's-enabled Bible modules
- Apply custom transformations (summaries, simplifications) to any document

**How it works:** Enable LLM Mode in Text Display Settings, select a prompt (e.g., "Translate to Finnish"), and the text is processed verse-by-verse. Results are cached locally — the same document won't be processed twice.

---

### Prompt Manager: Your AI Instructions

A central place to create, edit, and organize AI prompts that define what the AI should do.

**Built-in prompts (examples, subject to change):**
- **Translate to UI language** — Automatic translation to your app's language
- **Summarize** — Concise summaries of passages or chapters
- **Explain verses** — Synthesize insights from installed commentaries
- **Word study** — Analyze original language words with Strong's definitions
- **Create thematic bookmarks** — Identify and bookmark key passages

**Custom prompts:** Create your own for specific study tasks. Configure where each prompt appears:
- Text Display Settings (automatic processing)
- Verse selection menu (One Tap Actions)
- Text selection context menu
- Window menu (document-level actions)

---

### AI Actions: On-Demand Assistance

Manually triggered AI operations on selected text or documents.

**Example workflows:**

| Action | What happens |
|--------|--------------|
| **Commentary summary** | AI reads all installed commentaries for selected verses and creates a unified summary |
| **Cross-reference analysis** | AI explains connections between related passages |
| **Study notes generation** | AI creates structured notes for a chapter, saved to your documents |
| **Thematic bookmarking** | AI identifies important themes and creates labeled bookmarks |

Results appear in different places depending on the action: summaries and analyses go to **My Documents**, bookmarks appear in your bookmark list, StudyPad entries in your StudyPads. All AI-created content is tracked in the status log, making it easy to review what the AI has done and remove unwanted items.

---

### AI-Powered Search

Traditional Bible search finds exact word matches. AI search understands meaning and context.

**How it works:** Describe what you're looking for in natural language, and the AI returns relevant Bible references — even when your search terms don't appear in the text.

**Examples:**
- *"Passages about God's faithfulness during trials"* → Returns relevant verses from Psalms, Job, Romans, etc.
- *"Jesus teaching about money"* → Finds parables and teachings even without the word "money"
- *"Old Testament prophecies about the Messiah's suffering"* → Returns Isaiah 53, Psalm 22, etc.

AI search is integrated into the existing search interface as an additional search mode.

---

### AI-Enhanced Cross-References

Standard cross-reference databases are valuable but limited. AI can suggest additional connections based on:

- Thematic similarities
- Parallel narratives
- Prophetic fulfillments
- Linguistic patterns in original languages
- Theological concepts

**How it works:** When viewing cross-references, an option to "Find more with AI" queries the LLM for additional relevant passages not in your installed cross-reference modules.

---

### My Documents & AI Links

Create and manage personal documents within AndBible, with special support for AI-generated content.

**Features:**
- Create study documents with multiple pages
- Supports Markdown formatting with Scripture links
- Documents appear in the document selector alongside installed books
- Full bookmark and StudyPad integration

**AI Links — Interactive AI Content:**

AI-generated documents can include special links that trigger further AI actions. For example:

```markdown
## The Beatitudes (Matthew 5:3-12)

Jesus opens the Sermon on the Mount with eight blessings...

**Explore further:**
- [Compare with Luke's version](ai://compare?refs=Matt.5.3-12,Luke.6.20-26)
- [Word study: "Blessed" (μακάριος)](ai://word-study?strongs=G3107)
- [Find similar teachings](ai://search?query=Jesus+teachings+on+humility)
```

Clicking these links triggers the corresponding AI action with pre-configured parameters. This creates **interactive study documents** that enable deeper exploration without manual prompt configuration.

---

### What the AI Can Access

The AI can read from and write to various parts of AndBible, with appropriate permissions.

**Read capabilities (automatic):**
- Bible text from installed translations
- Commentary entries
- Cross-references
- Dictionary and Strong's definitions
- Your bookmarks, labels, and StudyPads

**Write capabilities (requires permission):**
- Create bookmarks with notes
- Add entries to StudyPads
- Create new labels

**Note:** A dedicated "AI Documents" document is available where AI can save its output without requiring separate permission. This keeps AI-generated content organized in one place by default.

All AI-created content is marked with its source, so you always know what was generated by AI.

---

## Control & Management

### Permissions System

**You stay in control:**
- Write operations require explicit approval
- Permission dialog shows exactly what the AI wants to do
- Options per operation type: "Always ask," "Allow for session," "Always allow," or "Deny"

**Status visibility:**
- Real-time log shows AI operations as they happen
- See what data is being read and what changes are being made
- Cancel operations at any time

### Cache Manager

Manage your locally cached AI-processed content:

- **View cache contents** — See what's been processed and when
- **Selective deletion** — Remove specific entries or clear by date range
- **Invalidation** — Mark old entries for re-processing (e.g., after switching to a better model)
- **Storage info** — Monitor cache size and manage disk usage

Useful when you want to re-process content with improved prompts or a different LLM provider.

### Cost Tracking

Monitor how much your AI usage costs:

- **Configure pricing** — Set per-token prices for your LLM provider (input and output tokens)
- **Per-operation costs** — See how much each AI action costs before and after execution
- **Usage history** — Track cumulative costs over time
- **Budget awareness** — Make informed decisions about which operations to run

Since different providers have vastly different pricing, this helps you stay in control of your spending.

### Privacy

- **Your API, your data** — Configure your own API endpoint (commercial providers or self-hosted servers)
- **No third-party data sharing** — Data only goes to your chosen API provider
- **Local caching** — Processed content stored on your device
- **Device sync** — AI-generated documents, cached content, and custom prompts sync between your devices via Google Drive or Nextcloud (using AndBible's existing sync feature), just like bookmarks and notes etc.

---

## Technical Notes

- **API Compatibility:** Works with any OpenAI-compatible endpoint
- **Caching:** Processed content cached locally to avoid redundant API calls
- **Offline Access:** Cached content available offline; new processing requires internet
- **Sync Support:** AI content and custom prompts sync between devices

---

## Summary

AndBible's AI features are designed to enhance your Bible study by:

| Benefit | How |
|---------|-----|
| **Remove language barriers** | On-the-fly translation of any text |
| **Discover connections** | AI-powered search and cross-references |
| **Save time** | Automated summaries and commentary synthesis |
| **Organize insights** | AI-assisted bookmarking and note generation |
| **Enable exploration** | Interactive AI links for deeper study |
| **Maintain control** | Transparent permissions and customizable prompts |

These tools make deep Bible study more accessible while keeping you firmly in charge of your study journey.
