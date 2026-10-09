---
title: Financial report for Q3/2026
date: '2026-10-09 10:00'
slug: financial-report-for-q3-2026
summary: 'The AndBible Q3 2026 financial report is published. Most of the quarter went into the Compose port, which is now functional and nearly finished; AndBible for iOS reached the App Store; and the general pool was used up to fund the shared-codebase work. Full transparency on where every sponsored hour went.'
tags:
- financial support
- transparency
image: blog/2026/07/quarterly-report.webp
image_alt: Financial report for Q3/2026
---

## Background

[Sykerö Software](https://sykero.fi) (Tuomas Airaksinen Software Oy), company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via the [webshop](/shop). Recurring sponsorships via bank transfer (EUR, USD, GBP) are also available — reach out for details. You can also make a recurring donation through [GitHub Sponsors](https://github.com/sponsors/AndBible). The previous quarter's report is available [on the AndBible blog](/2026/07/18/financial-report-for-q2-2026/).

## Quarter Overview

I logged **75.85 hours** of development this quarter, almost exactly the same as in Q2. About two thirds of it (**48.52 hours**) went into the shared Android/iOS codebase, more precisely its first phase, the Compose UI port, which the [Q3/Q4 roadmap](/2026/08/01/the-road-ahead-q3-q4-2026/) moved to the front of the queue. A [new roadmap post](/2026/10/09/the-road-ahead-q4-2026-q1-2027/), published alongside this report, covers that work and the plans ahead in more detail. This report focuses on the hours and how they were funded.

**Compose port.** During the quarter, AndBible was ported to Compose and Kotlin Multiplatform: 45.35 hours of UI modernization plus 3.17 hours of common-codebase groundwork. The Compose version is now **functional and nearly finished**. The goal for Q4 is to release it to Android users, with a refreshed Material 3 interface, most likely as AndBible 6.0. The project has no budget of its own. Its funding has come from the 13.03 hours left over from the completed reading tracker, which the sponsor allowed me to redirect here, and those hours were used up in July. From then on, all available hours in the generic pool were transferred to this project. Even so, **6.39 hours** remained unsponsored (see the "Free Work" column below).

**iOS.** The native iOS app, the work of [Jared Murrell (Primetheus)](https://github.com/primetheus), left beta and is [available on the App Store](/2026/09/26/andbible-for-ios-now-available-on-the-app-store/). My part (7.68 hours) was the release pipeline, taking the app through App Store review, and the releases themselves.

**Maintenance.** General maintenance came to 13.33 hours. That is far less than Q2's 53.58 hours of post-release hardening, as expected now that 5.1 is stable. The stable 5.1 branch still received **eight production releases** during the quarter (5.1.1107–5.1.1117). They brought:
- Crash fixes, many of them in EPUB handling, document synchronisation and dropped Nextcloud connections.
- Fixes to verse tracking and scrolling ([#3865](https://github.com/AndBible/and-bible/issues/3865), [#3866](https://github.com/AndBible/and-bible/issues/3866)).
- A document selector on the search results screen.
- An "In cloud only" filter in the synced documents view.
- Page numbers shown as "x/y".
- An update to target Android 16.

**Sponsored features.** The custom background image / wallpaper commission ([#3617](https://github.com/AndBible/and-bible/issues/3617)) was **completed** with 1.33 hours of final work, and its small shortfall was covered from the generic pool. A new commission for [WebDAV synchronisation](https://github.com/AndBible/and-bible/issues/3837) (#3837, 4 hours sponsored) arrived in September. It will be built after the Compose release, so its budget carries over.

**GitHub Sponsors.** The [GitHub Sponsors](https://github.com/sponsors/AndBible) button now has two monthly donors, bringing in a total of **$30/month**. As noted in the Q2 report, these are donations without consideration: they do not purchase development hours and are not part of the hour pools in this report. Thank you to both donors.

**Planning, reporting and support.** Planning took 4.97 hours. This category covers roadmap work, such as the [Q3/Q4 roadmap](/2026/08/01/the-road-ahead-q3-q4-2026/), and other dedicated planning. Part of the planning, reporting and support work is also included in the maintenance hours above, since many of the bug fixes respond directly to reports that come in through the support desk. For user support itself, I have received welcome help, and special thanks go to [Timmy Brown (timbze)](https://github.com/timbze).

## Sponsorship Flow

The sponsorship flow tables show how development hours are funded. Sponsors purchase development hours through orders. Each pool tracks available hours vs. hours worked. Transfers between pools are configured explicitly and may use a conversion ratio (e.g., feature hours are worth more generic hours due to different rates).

**Sponsored** shows hours purchased by orders (amount / hourly rate). **Hours** shows actual development work done. **Remaining** shows the hour balance — negative values indicate unsponsored work.

## Sponsorship Flow - Feature [#3837 (WebDAV for Sync)](https://github.com/AndBible/and-bible/issues/3837)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
|-------|-----------|-----------|-----------|-------|-----------|-----------|-----------|
| September | 0.00 h | 4.00 h | 4.00 h | 0.02 h | - | - | 3.98 h |

## Sponsorship Flow - Feature ui-modernization (UI Modernization / Multiplatform preparation)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
|-------|-----------|-----------|-----------|-------|-----------|-----------|-----------|
| July | 13.03 h | 0.00 h | 13.94 h | 13.93 h | 0.91 h (← generic) | - | 0.00 h |
| August | 0.00 h | 0.00 h | 13.47 h | 19.63 h | 13.47 h (← generic) | 6.16 h | 0.00 h |
| September | 0.00 h | 0.00 h | 14.72 h | 14.95 h | 14.72 h (← generic) | 0.23 h | 0.00 h |

## Sponsorship Flow - Feature [#3617 (Custom background image / wallpaper support)](https://github.com/AndBible/and-bible/issues/3617)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
|-------|-----------|-----------|-----------|-------|-----------|-----------|-----------|
| July | 0.50 h | 0.00 h | 1.33 h | 1.33 h | 0.83 h (← generic) | - | 0.00 h |

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
|-------|-----------|-----------|-----------|-------|-----------|-----------|-----------|
| July | 2.05 h | 20.29 h | 20.47 h | 8.95 h | -1.87 h (→ ui-modernization, → #3617) | - | 11.52 h |
| August | 11.52 h | 16.20 h | 14.25 h | 14.23 h | -13.47 h (→ ui-modernization) | - | 0.02 h |
| September | 0.02 h | 17.50 h | 2.80 h | 2.80 h | -14.72 h (→ ui-modernization) | - | 0.00 h |

## Transfers

| Date | From | To | Hours (from) | Hours (to) | Note |
|------|------|----|--------------|------------|------|
| 2026-07 | Generic | ui-modernization | 0.91 h | 0.91 h | Cover UI modernization shortfall from generic pool (pool is in 65 €/h units, 1:1) |
| 2026-07 | Generic | [#3617](https://github.com/AndBible/and-bible/issues/3617) | 0.96 h | 0.83 h | Cover #3617 shortfall from generic pool (converted at 65/75) |
| 2026-08 | Generic | ui-modernization | 13.47 h | 13.47 h | Cover UI modernization shortfall from generic pool (all available generic hours) |
| 2026-09 | Generic | ui-modernization | 14.72 h | 14.72 h | Cover UI modernization shortfall from generic pool (all available generic hours) |

## Time Tracking Summary

This section shows how development time was actually spent during the reporting period. **By Category** groups work into broad areas like feature development, bug fixes, and maintenance. **By Task** provides detailed breakdown of specific tasks, showing exactly what was worked on and for how long. This transparency helps sponsors and users understand how their contributions are being used to improve AndBible.

### By Category

| Category | Total Hours | Tasks |
|----------|-------------|-------|
| Generic | 25.98 h | ab ios (ID=42), ab maintenance (ID=11), ab planning (ID=33) |
| Feature | 49.87 h | ab ui modernization (ID=48), ab webdav (ID=53), ab custom background 3617 (ID=40), ab ios common cod |

### By Task

| Task | Hours | Category | Ticket |
|------|-------|----------|--------|
| ab custom background 3617 (ID=40) | 1.33 h | Feature | [#3617](https://github.com/AndBible/and-bible/issues/3617) |
| ab ios common codebase (ID=47) | 3.17 h | Feature | ui-modernization |
| ab ui modernization (ID=48) | 45.35 h | Feature | ui-modernization |
| ab webdav (ID=53) | 0.02 h | Feature | [#3837](https://github.com/AndBible/and-bible/issues/3837) |
| ab ios (ID=42) | 7.68 h | Generic | N/A |
| ab maintenance (ID=11) | 13.33 h | Generic | N/A |
| ab planning (ID=33) | 4.97 h | Generic | N/A |

## Charts

The following charts provide visual insights into the development work and funding distribution:

### Hours Worked vs Sponsored Hours

![Hours Worked vs Sponsored Hours](/media/blog/2026/10/2026-q3-hours-comparison.webp)

### Time Distribution by Category

![Time Distribution by Category](/media/blog/2026/10/2026-q3-category-distribution.webp)

### Time Distribution by Task

![Time Distribution by Task](/media/blog/2026/10/2026-q3-task-distribution.webp)

## Summary

In total, 75.85 hours of development work were done this quarter. Roughly 69.5 hours were covered by sponsorship, and 6.39 hours (8%) were unsponsored. This is the same level as in Q2, so the gap between sponsored and actual hours stays small. The recurring monthly sponsorship remains the stable core of the funding.

The balance in this quarter's sponsored hours came from moving all available hours in the generic pool to UI modernization. The generic pool therefore ends the quarter at zero, and there is no buffer going into Q4. The shared-codebase project runs at least until the end of the year, and the current level of sponsorship does not cover it in full. Dedicated sponsorship for this work would make a real difference. Overall funding is still below the target of roughly one full working day per week (around 90 hours per quarter), which would let me commit to a predictable development pace.

Looking ahead, the [new roadmap](/2026/10/09/the-road-ahead-q4-2026-q1-2027/) is simple. In Q4, the Compose version will be released to Android users, most likely as AndBible 6.0. In Q1–Q2 2027, the same shared codebase will come to iOS as a unified release.

Best regards & blessings,  
Tuomas Airaksinen  
Lead developer of AndBible Open Source Project
