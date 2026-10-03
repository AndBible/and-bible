---
title: Financial report for Q1/2026
date: '2026-04-10'
slug: financial-report-for-q1-2026
summary: 'The AndBible Q1 2026 financial report is published. Highlights: AI features heading to production in Q2, a new reading & memorization tracker, company restructuring, and full transparency on where every sponsored hour went. Your support makes this possible!'
tags:
- financial support
- transparency
image: blog/2025/10/screenshot-from-2025-10-02-16-51-45.webp
image_alt: Financial report for Q1/2026
---

https://www.youtube.com/watch?v=6pEhqwxWJ30

## Background

[Sykerö Software](https://sykero.fi) (Tuomas Airaksinen Software Oy), company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via the [webshop](https://shop.andbible.org). Recurring sponsorships via bank transfer (EUR, USD, GBP) are also available — reach out for details.

## Quarter Overview

The defining theme of Q1 2026 was developing **AI-powered features for AndBible** — bringing LLM capabilities directly into the app for users. I outlined the vision and roadmap in a [blog post in early January](/2026/01/05/my-ai-plans-to-andbible/), and I’m pleased to report that the entire feature set is now essentially implemented as planned. What remains is testing, bug fixing, and prompt refinement, with the goal of shipping the AI features to production during Q2. The AI integration — including a multi-provider LLM agent system with Bible-aware tools, an AI document system, a prompt editor, and deep integration points throughout the app — is arguably the most ambitious feature addition to AndBible in the past couple of years, and I’m genuinely thrilled with how all the pieces have come together.

This work came at a cost: the AI project consumed 75.72 hours this quarter (101.69 hours total since Q4 2025), and since there is no dedicated feature sponsorship for it, all of that was drawn from the generic development pool. As a result, the generic pool accumulated **43.23 hours of free (unsponsored) work** over the quarter, meaning roughly 43 hours (~2,810 €) of the AI work was effectively self-sponsored. The detailed sponsorship data below tells the full story, but in short: community sponsorship doesn’t come close to covering this implementation in full. That said, I consider this a worthwhile investment — the feature is genuinely exciting and I believe it will be a valuable addition for users.

**Business restructuring and rate adjustment.** At the start of the year, I gained a significant new sponsor whose recurring monthly commitment provides a more stable funding base for ongoing development. This prompted a restructuring from a sole proprietorship to a limited liability company (Tuomas Airaksinen Software Oy, trading as [Sykerö Software](https://sykero.fi)), which is considerably more tax-efficient since, as a side business alongside near-full-time employment (4 days/week), the sole proprietorship income was taxed at a high marginal rate, whereas a limited company benefits from corporate tax and preferential dividend taxation. At the same time, I made the first-ever hourly rate adjustment, raising both rates by 5 €/h (generic: 60 → 65 €/h, feature: 70 → 75 €/h) based on the [Finnish consumer price index](https://www.stat.fi/til/khi/index_en.html). Going forward, I intend to make a similar index-based adjustment each January; since this was the first increase since the project started, the step was larger than future adjustments will be.

Additionally, the new company’s Wise business account now enables recurring sponsors to pay via direct bank transfer in EUR, USD, and GBP (and possibly others). Note that bank transfer sponsorships require a separate invoice and agreement, so the [webshop](/shop) remains the recommended option for smaller or one-time contributions.

**Quarterly planning and reporting.** The new sponsor requires quarterly roadmap planning and progress reporting (tracked as “ab planning” in the time data, 6.98 hours this quarter). I carry out this work within sponsored hours, and I also publish these planning reports as articles on the [AndBible blog](/2026/04/11/the-road-ahead-q2-q3-2026/).

**Sponsored feature work.** In addition to the AI work, I worked on three sponsored feature commissions during Q1. The first was a [collection of smaller improvements](https://github.com/AndBible/and-bible/issues/3388) (8.00 hours sponsored, 4.90 hours worked), which was completed and its remaining 3.10 hours transferred to the tracker commission below. The second was a [maintenance work](https://github.com/AndBible/and-bible/issues/3588) pool (7.50 hours sponsored, 14.15 hours worked) — this is essentially generic development, but the sponsor specifically wanted to earmark their contribution for non-AI work such as bug fixes and general maintenance. The shortfall was covered by a transfer from the generic pool. The third was a comprehensive **reading and memorization progress tracking** system ([#3586](https://github.com/AndBible/and-bible/issues/3586)) with automatic reading detection, progress statistics, calendar heatmaps, and reading cycle support. This work is nearly complete — 19.07 hours remain in the budget, which will be used for final bug fixes and polish, with any remaining hours allocated as directed by the sponsor.

**Support, issues, and pull requests.** One area that suffered this quarter was user support and issue triage. After January, support work dropped to just 1.97 hours for the entire quarter, and I have not been reading through open issues except those directly related to the AI project and the sponsored tracker feature. This is something I need to address going forward. On the pull request side, we’ve seen an increase in community-contributed pull requests — likely driven by the growing use of AI coding assistants. I have not had time to properly review most of them. A refined contribution policy is under development to ensure that high-quality contributions make it into the codebase efficiently, while avoiding spending disproportionate review time on lower-quality AI-generated submissions.

## Report Format Changes

Starting with this quarter, the sponsorship flow tables have switched back to an hour-based format. The motivation is clarity: orders purchase hours, tracked work consumes hours, and transfers between pools are straightforward hour-to-hour operations (with a conversion ratio when rates differ). The previous euro-based format made pool transfers and carryovers harder to follow. A “Free Work” column has been added to clearly show unsponsored hours — when a pool’s balance goes negative, the deficit is absorbed as free work and the carryover resets to zero instead of propagating a negative balance to the next month. The old “Sponsored %” column has been removed, and inter-pool transfers are now shown in a dedicated Transfers table. The hours comparison chart now also includes a “Free Work” bar alongside the existing “Total Hours Worked” and “Sponsored Hours” bars, making the unsponsored portion visible at a glance.

# Sponsorship Flow

The sponsorship flow tables show how development hours are funded. Sponsors purchase development hours through orders. Each pool tracks available hours vs. hours worked. Transfers between pools are configured explicitly and may use a conversion ratio (e.g., feature hours are worth more generic hours due to different rates).

**Sponsored** shows hours purchased by orders (amount / hourly rate). **Hours** shows actual development work done. **Remaining** shows the hour balance — negative values indicate unsponsored work.

## Sponsorship Flow - Feature [#3388 (Small features collection)](https://github.com/AndBible/and-bible/issues/3388)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| January | 0.00 h | 8.00 h | 8.00 h | 3.88 h | - | - | 4.12 h |
| February | 4.12 h | 0.00 h | 4.12 h | 1.02 h | - | - | 3.10 h |
| March | 3.10 h | 0.00 h | 0.00 h | 0.00 h | -3.10 h (→ #3586) | - | 0.00 h |

## Sponsorship Flow - Feature [#3588 (Maintenance work)](https://github.com/AndBible/and-bible/issues/3588)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| January | 0.00 h | 7.50 h | 7.50 h | 6.50 h | - | - | 1.00 h |
| February | 1.00 h | 0.00 h | 7.65 h | 7.65 h | 6.65 h (← generic) | - | 0.00 h |

## Sponsorship Flow - Feature [#3586 (Reading & Memorization tracker)](https://github.com/AndBible/and-bible/issues/3586)

| Month | Carryover | Sponsored | Available | Hours | Transfers | Free Work | Remaining |
| --- | --- | --- | --- | --- | --- | --- | --- |
| March | 0.00 h | 37.50 h | 40.60 h | 21.53 h | 3.10 h (← #3388) | - | 19.07 h |

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
| January | 0.00 h | 23.80 h | 23.80 h | 17.40 h | - | - | 6.40 h |
| February | 6.40 h | 20.05 h | 19.80 h | 36.18 h | -6.65 h (→ #3588) | 16.38 h | 0.00 h |
| March | 0.00 h | 18.80 h | 18.80 h | 45.65 h | - | 26.85 h | 0.00 h |

## Transfers

| Date | From | To | Hours (from) | Hours (to) | Note |
| --- | --- | --- | --- | --- | --- |
| 2026-03 | [#3388](https://github.com/AndBible/and-bible/issues/3388) | [#3586](https://github.com/AndBible/and-bible/issues/3586) | 3.10 h | 3.10 h | Small features remaining → Reading tracker |
| 2026-02 | Generic | [#3588](https://github.com/AndBible/and-bible/issues/3588) | 6.65 h | 6.65 h | Cover #3588 shortfall from generic pool |

## Time Tracking Summary

This section shows how development time was actually spent during the reporting period. **By Category** groups work into broad areas like feature development, bug fixes, and maintenance. **By Task** provides detailed breakdown of specific tasks, showing exactly what was worked on and for how long. This transparency helps sponsors and users understand how their contributions are being used to improve AndBible.

### By Category

| Category | Total Hours | Tasks |
| --- | --- | --- |
| Feature | 102.15 h | ab ai project (ID=32), ab tracker feat (ID=37), ab small feats #3587 (ID=34) |
| Generic | 37.67 h | ab maintenance (ID=11), ab maintenance #3588 (ID=35), ab planning (ID=33), ab support (ID=10) |

### By Task

| Task | Hours | Category | Ticket |
| --- | --- | --- | --- |
| ab small feats #3587 (ID=34) | 4.90 h | Feature | [#3388](https://github.com/AndBible/and-bible/issues/3388) |
| ab tracker feat (ID=37) | 21.53 h | Feature | [#3586](https://github.com/AndBible/and-bible/issues/3586) |
| ab ai project (ID=32) | 75.72 h | Feature (generic-funded) | N/A |
| ab maintenance (ID=11) | 14.57 h | Generic | N/A |
| ab planning (ID=33) | 6.98 h | Generic | N/A |
| ab support (ID=10) | 1.97 h | Generic | N/A |
| ab maintenance #3588 (ID=35) | 14.15 h | Generic (earmarked) | [#3588](https://github.com/AndBible/and-bible/issues/3588) |

## Charts

The following charts provide visual insights into the development work and funding distribution:

### Hours Worked vs Sponsored Hours

![](/media/blog/2026/04/2026-q1-hours-comparison-1.webp)

### Time Distribution by Category

![](/media/blog/2026/04/2026-q1-category-distribution-1.webp)

### Time Distribution by Task

![](/media/blog/2026/04/2026-q1-task-distribution-1.webp)

## Summary

In total, 139.82 hours of development work were done this quarter, of which roughly 97 hours were covered by sponsorship and 43 hours (31%) were unsponsored work — primarily due to the AI feature effort. Revenue for the quarter was 7,618 €, a significant increase from Q4 2025 (1,335 €), largely thanks to the new recurring sponsor and the feature commissions.

While the funding situation has improved significantly compared to late 2025, sponsorship still falls short of the target: roughly one full working day per week (around 90 hours per quarter). Reaching that level consistently would allow me to commit to a predictable development pace with confidence. This quarter’s high free work percentage was largely driven by the AI feature, which put me in a particular creative flow that was hard to step away from — but that level of unsponsored contribution is not sustainable in the long run.

Looking ahead to Q2, the priorities are: shipping the AI features to production, releasing an MVP of the iOS version, finishing the reading tracker, catching up on user support and issue triage, and establishing a clearer contribution policy for community pull requests. I intend to publish a separate roadmap blog post soon that will cover the forward-looking plans in more detail.

## Closing Words

I want to express my heartfelt gratitude to every AndBible sponsor for making it possible to maintain, support, and continually develop the app. This quarter in particular, the gap between sponsored and actual work hours was significant, but the enthusiasm from sponsors — both new and returning — gives me confidence that the project is heading in a good direction.

Above all, I’m deeply grateful to God for how things have developed. My personal motivation for the project has grown enormously — knowing how many people are walking alongside me in this work makes all the difference. It is a privilege to work on software that helps people engage with Scripture, and I look forward to shipping the AI features and returning to a more balanced workload in Q2.

Best regards & blessings,  
Tuomas Airaksinen  
Lead developer of AndBible Open Source Project

P.S. Roadmap post ["The Road Ahead - Q2/Q3 2026"](/2026/04/11/the-road-ahead-q2-q3-2026/) has been published!
