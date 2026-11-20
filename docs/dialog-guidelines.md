# Dialog guidelines

Developer documentation for AndBible's confirm, message, options, info and notice dialogs. Every one of
them is drawn by `AbDialogShell` (`sharedUi/.../components/dialog/`), so they share one look: an 18sp lead
line, an optional grey detail line, role-coloured verb buttons, answer cards and (planned) a full-screen
text page for long text. Design record: `docs/superpowers/specs/2026-11-20-dialog-redesign-design.md`.

## Copy rules

These apply to every string you write for a dialog.

1. **Lead line = the question or headline**, ideally ≤ 60 characters. It is never "Are you sure?". It
   names the action: "Delete the label “Sermon notes”?", "Discard changes?", "Restore this backup?".
2. **User-given names** (label, document, provider) are quoted with typographic quotes inside the lead
   line. The lead line can wrap.
3. **Detail line = consequence**, only when it adds information. "Its Study Pad notes are deleted too.",
   "This cannot be undone.", "Your edits to this prompt will be lost." It never repeats the lead line.
4. **No title-plus-repeat.** One lead line, not a topic title and then the same question again.
5. **Buttons are verbs:** "Delete", "Remove", "Reset", "Overwrite", "Install", "Restore". "OK" is allowed
   only to acknowledge a message with no action. A destructive confirm sets `destructive = true`.
   Cancel stays "Cancel" except where a clearer label exists ("Keep editing" for discard-changes).
6. **Answers** (3+ choices) each say what happens in their consequence line when the label alone is
   not enough ("The bookmarks stay, without a label.").
7. **Errors** say what went wrong and what the user can do. The bare "An error has occurred" (13 sites)
   gets a detail line where the caller knows the cause. Where it does not, the text stays but renders
   as a lead line.
8. **New strings get new keys.** A rewritten English text never reuses the old resource key. The old
   key is deleted when its last use is gone, so stale translations cannot show the old meaning.
   Strings follow the usual path: `strings.xml`, `Strings.kt`, `AndroidStrings.kt`. Check the
   generator's "N interface members, N mapped overrides" line.

## Which component fits which case

| Case | Component | Notes |
|---|---|---|
| Message or error, blocking notice | `AbMessageDialog`, `AbErrorDialog` | HTML body goes through `AbMessageDialog`; an untitled message has no lead line |
| Confirmation (an answer to a question) | `AbConfirmDialog` | `detail` for the consequence, `destructive = true` for a harmful confirm |
| Help text, disclaimer, tool description | `AbInfoDialog` | Title is the lead; body, slot and "read more" link scroll together |
| Release or donation notice with logo, QR, icon lines | `AbNoticeDialog` | Logo in the icon slot, blocks in body style |
| 3+ answers to a question | `AbOptionsDialog` | Answer cards with consequence lines; with a checkbox the answers become buttons |
| 3+ actions (not answers) | `AbActionSheet` | A sheet, not a dialog |
| Single choice from a list | `AbChoiceSheet` | Sheet |
| Multi-select from a list | `AbMultiSelectSheet` (sheet) or `AbMultiSelectDialog` | Sheet by default; the dialog form scrolls in the shell, with no inner height cap |
| Single text field | `AbTextInputDialog` | Dialog (IME) |
| Custom form or picker | `AbAlertDialog` (adapter on the shell) | A sheet when the body scrolls |
| Modal wait | `AbProgressDialog` | Dialog |
| Long static text (over about 600 English characters), certificates, licences | `AbTextPage` (planned, B4) | Full-screen page, footer with Close or Cancel + primary |

The dialog-versus-sheet rule is §7 of `docs/superpowers/specs/2026-09-11-compose-platform-dialog-removal-design.md`:
actions go to a sheet, answers to a dialog.

## Tokens

The sizes of the lead, body, detail, card and shell (padding, corner, gaps, width, the 40% lead fraction) live in `AbDialogTokens` (`AbDialogTokens.kt`). The shell's height-safety constants are private to `AbDialogShell.kt`: the 16dp side and 24dp top/bottom screen margins, the 56dp middle reserve, and the compact frame (windows shorter than 420dp: 8dp margin, 12dp top and 8dp bottom padding). `AbAlertDialog` bounds its text at 10000dp. The shell applies three height rules:

1. the button row is never capped and never scrolls (an empty row takes no height and no gap above it);
2. the middle area (detail + content) keeps at least 56dp and scrolls; a caller's own height cap or scroll inside it nests a second scroll, so do not add one;
3. the lead line yields first: its cap is the smaller of 40% of the available height and what remains after the button row and the middle area, and it scrolls inside the cap. Both scroll areas show a divider at an edge that can scroll further, and a middle area that needs less than its reserve hands the rest back to the lead.

- **Lead line:** 18sp / 25sp, medium, `onSurface`.
- **Body:** 16sp / 23sp `onSurface`. **Detail:** 14sp / 20sp `onSurfaceVariant`.
- **Shell:** corner 20dp, padding 24dp sides / 22dp top / 14dp bottom, 10dp lead to body, 18dp above the button row, width 280-560dp.
- **Buttons:** `Cancel`, `Neutral`, `Confirm` use `primary`; `Destructive` uses `error`. In pure monochrome every
  role is ink and Destructive is bold. Test tags: `dialog-dismiss`, `dialog-neutral`, `dialog-confirm` (Destructive too).
- **Answer card:** 1dp `outline` border (`monoInk` in monochrome), 14dp radius, 11dp / 14dp padding, label 15sp medium,
  consequence 13sp `onSurfaceVariant`; at least 48dp tall; tag `dialog-answer-<value>`.
- **Height safety:** the three rules above. Check every dialog at 320dp wide and AX5 (`fontScale = 3f`).
- **Monochrome:** `monoBorder` on the shell, paper surface, no tonal elevation, no dim. No hue anywhere.

## Example: deleting a label

Before (M3 `AlertDialog` defaults: the whole sentence is a 24sp title, no body, a vague button):

```
Do you want to remove the label “Sermon notes”?          <- 24sp title
                                       [Cancel]  [Yes]
```

After (lead line, consequence, verb button, destructive role):

```
Delete the label “Sermon notes”?                         <- 18sp lead
Its Study Pad notes are deleted too. This cannot be undone.   <- 14sp grey detail
                                [Cancel]  [Delete]       <- Delete in the error colour
```

```kotlin
AbConfirmDialog(
    title = null,
    message = "Delete the label “${label.name}”?",
    detail = "Its Study Pad notes are deleted too. This cannot be undone.",
    confirmText = "Delete", dismissText = "Cancel",
    destructive = true,
    onConfirm = ::delete, onDismiss = ::close,
)
```

When a label has bookmarks that would be left without a label, ask with `AbOptionsDialog` instead: "Delete label only"
("The bookmarks stay, without a label.") and "Delete label and bookmarks" (destructive).
