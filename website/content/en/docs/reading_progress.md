# Reading Progress

*AndBible* can track your Bible reading and memorization progress, showing visual indicators
in the book and chapter selection grids and a dedicated statistics view.

<span id="on-this-page"></span><span id="id1"></span><span id="id2"></span><span id="id3"></span><span id="id4"></span><span id="id5"></span><span id="id6"></span><span id="id7"></span><span id="id8"></span>

## Reading Tracking

There are two ways to mark chapters as read: manually with a button, or automatically
as you scroll through the text.

### Mark as Read Button

A check mark icon is shown at the bottom of each Bible chapter. Tap it to
record the chapter as read. The icon changes appearance once marked, and a count such as `×2` shows
how many times the chapter has been recorded. Each tap adds a new record. To see or remove the records,
press and hold the icon (or right-click it) or tap the count; this opens the read history.

The button is a [text display setting](look_and_feel.md), so it can be turned
off or on per workspace or per window:

1. Open a Bible text window.
2. Tap the three-dot menu (top right of the window) and choose **Text options**.
3. Toggle **Mark as read button**.

### Automatic Reading Tracking

*AndBible* can automatically mark chapters as read when you scroll through them. When at
least 90% of a chapter’s verses have been visible on screen, the chapter is recorded
as read.

To enable automatic tracking:

1. Open the main menu (☰) and go to **Application preferences**.
2. Under **Features**, open **Progress & memorization**. (The same page opens from the ⋮ menu of the **Read/Memory Progress** screen.)
3. Toggle **Auto-track reading**.

Auto-track reading is off by default. This sets the application-wide default, the same switch as **Auto-track reading** under
**Global Text Options** > **Reading & Memorization**. A workspace or an individual window
can override it in its own text options.

## Memorization Tracking

When you successfully complete a **Word Scramble**, **Word Type** or
**Word Order** exercise in the [Memorize](memorize.md) view, the verses
in the exercise are automatically marked as memorized. The marking is also
saved when you tap **Mark as memorized** in the Memorize view’s menu, even
if you have not run an exercise.

You can also disable the automatic marking, for example if you prefer to
mark verses manually after reviewing them yourself:

1. Open the main menu (☰) and go to **Application preferences**.
2. Under **Features**, open **Progress & memorization**.
3. Toggle **Auto-mark as memorized**.

The same screen contains other memorization options described under
[Memorize](memorize.md).

## Progress Indicators

The book and chapter selection grids show visual progress indicators:

- **Book grid:** Each book button shows a thin progress bar at the bottom. The bar
    fills based on how many chapters you have read. Green indicates reading progress;
    gold indicates memorization progress (whichever is higher is shown).
- **Chapter grid:** Individual chapter buttons show a full progress bar when the
    chapter has been marked as read (green) or has memorized verses (gold).

<a id="reading-progress-view"></a>
## Read/Memory Progress View

A dedicated statistics view is available from the main menu (☰) under
**Read/Memory Progress**. It has two tabs, **Reading** and **Memorization**. The ⋮ menu has
**Progress & memorization** (the settings page) and **Help**.

### Reading tab

- **Summary cards:** Chapters read and active days.
- **Overall progress bar:** Percentage of the entire Bible read.
- **Bible Overview heatmap:** The books displayed as colored tiles, grouped by
    Old and New Testament. The color intensity indicates how much of each book you
    have read. Tap a book to expand it and see which individual chapters are marked
    as read. Tap a chapter to navigate directly to that passage. Press and hold a book or chapter
    to see its read history.
- **Reading Activity:** A GitHub-style heatmap showing your daily reading
    activity over the past year. Darker squares indicate more chapters read on that day. Tap a day to see what you read.

### Memorization tab

The tab shows how many verses you have memorized and, when you have set
memorization goals, the progress towards them. Choose **Overview** to see a book heatmap and a **Memorization Activity** calendar,
or **List** to see your **Memorized passages** and **Memorization goals**. Tap a passage or a goal to open it in [Memorize](memorize.md), and
use the X button to remove it from the list.

## Reading Cycles

You can start a new reading cycle to track reading the Bible multiple times. Starting
a new cycle preserves your previous cycle’s data but begins tracking from scratch.

On the **Reading** tab, the current cycle number (for example *Cycle 2*) is shown at the bottom, with arrows at its sides
that take you to the previous and next cycle. Tap **New Cycle** next to it and confirm to start a new cycle.

## Cloud Sync

Reading progress data (both reading records and memorized verses) can be synchronized
between devices using [cloud sync](cloud_sync.md). Enable the **Reading Progress**
toggle in the cloud sync settings.
