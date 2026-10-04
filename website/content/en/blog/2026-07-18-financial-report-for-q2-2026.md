---
title: Financial report for Q2/2026
date: '2026-07-18'
slug: financial-report-for-q2-2026
summary: 'The AndBible Q1 2026 financial report is published. Highlights: AI features heading to production in Q2, a new reading & memorization tracker, company restructuring, and full transparency on where every sponsored hour went. Your support makes this possible!'
tags:
- financial support
- transparency
image: blog/2026/07/quarterly-report.webp
image_alt: Financial report for Q2/2026
---

## Background

[Sykerö Software](https://sykero.fi) (Tuomas Airaksinen Software Oy), company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via the [webshop](/shop). Recurring sponsorships via bank transfer (EUR, USD, GBP) are also available — reach out for details. The previous quarter’s report is available [on the AndBible blog](/2026/04/10/financial-report-for-q1-2026/).

**A note on GitHub Sponsors.** In response to a user request, a [**GitHub Sponsors**](https://github.com/sponsors/AndBible) button has also been added this quarter for easy recurring monthly support, currently bringing in $20/month. Unlike the webshop and bank-transfer sponsorships, which purchase development hours in return, a GitHub sponsorship is a **donation without consideration** — a gift supporting my company, with no promise of work attached, nor can there be one. In spirit it is closest to the **“Free Work” column** in the reports below: a simple, voluntary way to help narrow that gap.

## Quarter Overview

Where Q1 2026 was defined by *building* the AI feature set, Q2 was about **shipping it and stabilising the app around it**. After a beta cycle running through April and May, **AndBible 5.1 reached stable release on 13 June 2026** — the largest release in some time. It brought the optional AI assistant (Gemini, Claude, OpenRouter and others), the reading & memorisation progress tracker, and the new editable “My Documents” pages to all users. Getting a release of this size out cleanly is a substantial undertaking in itself, and a large share of the quarter went into the testing, bug fixing and polish needed to make it happen.

This shows plainly in the time data: **maintenance was by far the largest category at 53.58 hours**, reflecting the crash resolution, bug fixing and general hardening that a major release demands — from EPUB and font-pack fixes to synchronisation crash handling and improved commentary browsing. In total I logged just over **75 hours** of development this quarter — a lower and more sustainable figure than Q1’s 140-hour AI push, and a welcome return to a healthier pace.

**Document synchronisation.** The headline new development of Q2 was a system for **synchronising your entire installed document library across devices** — Bibles, commentaries, dictionaries, maps and sideloaded modules alike. It copies the actual document files (not just references to re-download them) through your own personal Google Drive or Nextcloud storage, so nothing passes through third-party servers; transfers run automatically over Wi-Fi in the background, with a preview of what will be uploaded and downloaded and per-device control over which documents to include. This was a major piece of engineering — an incremental cloud-listing cache, a “Sync now” transfer preview, cloud-storage accounting, and a good deal of lifecycle hardening — and it was released as a full, stable feature, [announced on the blog in early July](/2026/07/04/new-feature-document-sync/).

**Reading tracker completed.** The reading & memorisation progress tracker ([#3586](https://github.com/AndBible/and-bible/issues/3586)), which was nearly finished at the end of Q1, was **completed early this quarter** (7.78 hours of final work). With the feature done, the **11.29 hours remaining** in its budget have been redirected into a new pool — more on that below.

**Smaller improvements.** A number of quality-of-life refinements also landed, including a new colour e-ink mode for colour e-ink devices, an unobtrusive [reading-progress indicator for EPUB documents](/2026/07/05/new-feature-reading-progress-indicator-for-epub-documents/), refreshed list views, a “hide status bar” option, and generic volume-key page scrolling.

**iOS — a major new front.** The most significant strategic development of the quarter was bringing AndBible to **iOS**. The iOS port itself is overwhelmingly the work of contributor [Primetheus (Jared Murrell)](https://github.com/Primetheus), whose efforts over an extended period are what made it possible. My own contribution this quarter was to turn it into a real, maintainable platform: I acquired an Apple computer, set up an Apple Developer account, and built out the automation for iOS builds and releases. This led to a **public TestFlight beta** for iPhone, iPad and Apple Silicon Macs, [announced in mid-July](/2026/07/17/andbible-for-ios-the-public-beta-is-here/). It is an early MVP with gaps and rough edges, but it is a genuine milestone — AndBible now runs on Apple devices, fulfilling years of user requests.

**Towards a unified, modern codebase.** In the near term, the current iOS beta will mature, as it stands, into a full production release. The larger goal — God willing, perhaps six months to a year away — is a single AndBible in which the Android and iOS versions share **one common codebase** at full feature parity, together with a **modernised UI/UX** (a Material 3 redesign). To fund the groundwork, the hours freed up by the completed reading tracker have been transferred into a new **“UI Modernization / Multiplatform preparation”** pool as a starting budget (13.03 hours). This is the main focus for the quarters ahead, and — much as I did for the AI plans earlier this year — I intend to publish a **separate blog post in the coming weeks** setting out the vision and roadmap in more detail.

**Support and issue triage.** User support remained the weakest area, at just 0.82 hours for the whole quarter — the major release and the synchronisation work absorbed most of my available time, and catching up on support and issue triage carries over as an ongoing priority.

## Sponsorship Flow

The sponsorship flow tables show how development hours are funded. Sponsors purchase development hours through orders. Each pool tracks available hours vs. hours worked. Transfers between pools are configured explicitly and may use a conversion ratio (e.g., feature hours are worth more generic hours due to different rates).

**Sponsored** shows hours purchased by orders (amount / hourly rate). **Hours** shows actual development work done. **Remaining** shows the hour balance — negative values indicate unsponsored work.

## Sponsorship Flow - Feature [#3617 (Custom background image / wallpaper support)](https://github.com/AndBible/and-bible/issues/3617)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| May | 0.00 h | 0.50 h | 0.50 h | 0.00 h | - | - | 0.50 h |

## Sponsorship Flow - Feature finark (Avoin Raamatunkäännös module)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| May | 0.00 h | 1.00 h | 1.00 h | 1.15 h | - | 0.15 h | 0.00 h |

## Sponsorship Flow - Feature [#3586 (Reading & Memorization tracker)](https://github.com/AndBible/and-bible/issues/3586)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| April | 19.07 h | 0.00 h | 19.07 h | 1.43 h | - | - | 17.63 h |
| May | 17.63 h | 0.00 h | 17.63 h | 6.35 h | - | - | 11.28 h |
| June | 11.28 h | 0.00 h | -0.01 h | 0.00 h | -11.29 h (→ ui-modernization) | 0.01 h | 0.00 h |

## Sponsorship Flow - Feature ui-modernization (UI Modernization / Multiplatform preparation)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| June | 0.00 h | 0.00 h | 13.03 h | 0.00 h | 13.03 h (← #3586) | - | 13.03 h |

**Column Explanations:**

- **Carryover**: Hours carried over from the previous month
- **Sponsored**: Hours purchased by sponsorship orders this month
- **Available**: Total hours available (Carryover + Sponsored ± Transfers)
- **Hours**: Hours of development work done
- **Transfers**: Net hours transferred in/out (already included in Available)
- **Free Work**: Unsponsored hours (negative balance absorbed, does not carry forward)
- **Remaining**: Available − Hours − Free Work, carried to next month (always ≥ 0)

## Sponsorship Flow - Generic Development

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| April | 0.00 h | 16.40 h | 16.40 h | 15.80 h | - | - | 0.60 h |
| May | 0.60 h | 16.50 h | 17.10 h | 23.22 h | - | 6.12 h | 0.00 h |
| June | 0.00 h | 29.30 h | 29.30 h | 27.25 h | - | - | 2.05 h |

## Transfers

| Date | From | To | Hours (from) | Hours (to) | Note |
| --- | --- | --- | --- | --- | --- |
| 2026-06 | [#3586](https://github.com/AndBible/and-bible/issues/3586) | ui-modernization | 11.29 h | 13.03 h | Reading & Memorization tracker remaining → UI modernization (new project, converted at 75/65) |

## Time Tracking Summary

This section shows how development time was actually spent during the reporting period. **By Category** groups work into broad areas like feature development, bug fixes, and maintenance. **By Task** provides detailed breakdown of specific tasks, showing exactly what was worked on and for how long. This transparency helps sponsors and users understand how their contributions are being used to improve AndBible.

### By Category

| Category | Total Hours | Tasks |
| --- | --- | --- |
| Generic | 65.30 h | ab ios (ID=42), ab maintenance (ID=11), ab planning (ID=33), ab support (ID=10) |
| Feature | 9.90 h | ab finark (ID=39), ab tracker feat (ID=37), ab ai project (ID=32) |

### By Task

| Task | Hours | Category | Ticket |
| --- | --- | --- | --- |
| ab finark (ID=39) | 1.15 h | Feature | finark |
| ab tracker feat (ID=37) | 7.78 h | Feature | [#3586](https://github.com/AndBible/and-bible/issues/3586) |
| ab ai project (ID=32) | 0.97 h | Feature (generic-funded) | N/A |
| ab ios (ID=42) | 5.97 h | Generic | N/A |
| ab maintenance (ID=11) | 53.58 h | Generic | N/A |
| ab planning (ID=33) | 4.93 h | Generic | N/A |
| ab support (ID=10) | 0.82 h | Generic | N/A |

## Charts

The following charts provide visual insights into the development work and funding distribution:

### Hours Worked vs Sponsored Hours

![](/media/blog/2026/07/2026-q2-hours-comparison-1.webp)

### Time Distribution by Category

![](/media/blog/2026/07/2026-q2-category-distribution.webp)

### Time Distribution by Task

![](/media/blog/2026/07/2026-q2-task-distribution.webp)

## Summary

In total, 75.20 hours of development work were done this quarter, of which roughly 69 hours were covered by sponsorship and about 6 hours (8%) were unsponsored. This is a marked improvement on Q1, where unsponsored work reached 31% — the gap between sponsored and actual hours has essentially closed. In euro terms, revenue was lower than in Q1 (whose total had been boosted by one-off feature commissions), but the recurring monthly sponsorship remains the stable core of the funding; the full figures are in the revenue table below.

The funding balance was therefore much healthier than in Q1, even though total revenue was lower. The quarter’s work was almost entirely sponsor-funded, which reflects both a more sustainable pace and the maintenance-heavy nature of stabilising a major release. Sponsorship still falls short of the target of roughly one full working day per week (around 90 hours per quarter), which would let me commit to a predictable development pace with confidence — but the shortfall this quarter was in overall capacity rather than in unpaid work.

Looking ahead to Q3, the priorities follow directly from the iOS beta: unifying the Android and iOS codebases, modernising the UI/UX (a Material 3 redesign), and growing the iOS version towards feature parity — while catching up at last on user support and issue triage, which remained the weakest area this quarter. As noted above, I intend to publish a separate roadmap blog post in the coming weeks that will set out this multiplatform vision in more detail.

## Closing Words

I want to express my heartfelt gratitude to every AndBible sponsor for making it possible to maintain, support and continually develop the app. A great deal happened this quarter — a big release brought to stable, a powerful new document-synchronisation feature, the first steps of AndBible onto iOS, and a great deal of quieter bug fixing that kept everything running smoothly — and it is your support that makes all of it possible.

Above all, I remain deeply grateful to God for how the project continues to develop. It is a privilege to work on software that helps people engage with Scripture, and I’m genuinely excited about the road ahead: bringing AndBible to more platforms, and giving the app the modern, polished interface its users deserve.

Best regards & blessings,  
Tuomas Airaksinen  
Lead developer of AndBible Open Source Project
