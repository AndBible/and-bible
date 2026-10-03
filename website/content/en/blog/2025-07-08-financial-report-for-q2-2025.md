---
title: Financial report for Q2 / 2025
date: '2025-07-08'
slug: financial-report-for-q2-2025
summary: 'Tuomas Airaksinen Consulting, company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via the webshop. See also: financial…'
categories:
- Sponsoring AndBible
tags:
- financial support
- transparency
image: blog/2025/07/financial2.webp
image_alt: Financial report Q2/2025
---

https://www.youtube.com/watch?v=TY6prA2XaxU

#### Background

[Tuomas Airaksinen Consulting](https://tuomasairaksinen.fi/consulting/), company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via [the webshop](https://shop.andbible.org).

See also: financial report for [Q4/2023](/2024/01/04/financial-report-for-q4-2024/), [Q1/2024](/2024/04/04/financial-report-for-q1-2024/), [Q2/2024](/2024/07/09/financial-report-for-q2-2024/), [Q3/2024](/2024/10/03/financial-report-for-q3-2024/), [Q4/2024](/2025/01/06/financial-report-for-q4-2024-2/) and [Q1/2025](/2025/04/03/financial-report-for-q1-2025/).

#### Changes in reporting

Recently, I've made significant changes to the way I handle reporting. Previously, managing reports manually on Google Sheets each month had become cumbersome, particularly when tracking sponsored development tasks specifically dedicated to various features within the AndBible project. To address this challenge, I wrote a small program that automatically generates precise and high-quality reports. This new automated system greatly streamlines the reporting process, enhances accuracy, and simplifies ongoing monitoring. Moving forward, reporting will be conducted only quarterly rather than monthly (i.e I'm stopping updating Google Sheets), and communicated directly through these financial reports.

#### Sponsorship Flow

The sponsorship flow tables show how funding for AndBible development works. When users sponsor specific features, their money is allocated to pay for the development work on those features. If a feature receives more sponsorship than needed, the excess helps fund general development. If a feature needs more work than its sponsorship covers, the shortfall is covered by general development funding when possible, or becomes volunteer work when no funding is available.

Each feature table shows month-by-month how sponsorship money was used: **Revenue** is money received from sponsors, **Cost** is what the development work actually cost, and **Remaining** shows leftover sponsorship money that carries forward to next month or gets transferred to general development when the feature is completed.

## Sponsorship Flow - Feature [#2547 (Bible Memory Feature)](https://github.com/AndBible/and-bible/issues/2547)

| Month | Orders | Revenue | Hours Spent | Rate | Cost | Remaining | To Generic |
| --- | --- | --- | --- | --- | --- | --- | --- |
| April | 2 | 240.00 € | 9.65 h | 60.00 €/h | 579.00 € | 0.00 € | 5.65 h |
| May | 0 | 0.00 € | 3.98 h | 60.00 €/h | 239.00 € | 0.00 € | 3.98 h |
| June | 0 | 0.00 € | 0.00 h | - | 0.00 € | 0.00 € | - |

**Column Explanations:**

- **Month**: Calendar month name
- **Orders**: Count of sponsorship orders for this feature
- **Revenue**: Total sponsorship money received
- **Hours Spent**: Total hours worked on this feature
- **Rate**: Average hourly rate used
- **Cost**: Actual cost of work done (uses actual hourly rates)
- **Remaining**: Sponsorship balance after paying for work
- **To Generic**: Hours (insufficient payment) or € (completed feature transfer) moved to generic funding

**Note:** When a feature is completed, remaining sponsorship funds are transferred to generic development (shown as EUR in 'To Generic' column).

\*) Rate is 60€/hour for feature development work is an exception. Normally this is 70€/hour.

## Sponsorship Flow - Generic Development

| Month | Orders | Revenue | From Features | Available | Hours Spent | Rate | Cost | Remaining | Free Work |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Leftover from Previous Period** | - | 690.46 € | - | - | - | - | - | 690.46 € | - |
| April | 4 | 258.00 € | -339.00 € | 609.46 € | 9.58 h | 60.00 €/h | 575.00 € | 34.46 € | 0.00 € |
| May | 7 | 232.89 € | -239.00 € | 28.35 € | 13.95 h | 60.00 €/h | 837.00 € | 0.00 € | 808.65 € |
| June | 11 | 1837.71 € | 0.00 € | 1837.71 € | 12.93 h | 60.00 €/h | 776.00 € | 1061.71 € | 0.00 € |

**Column Explanations:**

- **Month**: Calendar month name
- **Orders**: Count of generic development orders
- **Revenue**: Total generic development funding received
- **From Features**: Net funding flow from features (positive: transfers from completed features, negative: feature overflow costs)
- **Available**: Total funding available after feature flows
- **Hours Spent**: Total hours worked on generic development
- **Rate**: Average hourly rate used for generic work
- **Cost**: Actual cost of generic work done
- **Remaining**: Generic funding balance after paying for generic work
- **Free Work**: Generic work not covered by available funding

![](/media/blog/2025/07/2025-q2-hours-comparison-3.webp)

Note: the new reporting system is lacking data from 2024. Please see data in previous financial report ([Q1/2025](/2025/04/03/financial-report-for-q1-2025/)) if you want to compare.

#### Time Tracking Summary

This section shows how development time was actually spent during the reporting period. **By Category** groups work into broad areas like feature development, bug fixes, and maintenance. **By Task** provides detailed breakdown of specific tasks, showing exactly what was worked on and for how long. This transparency helps sponsors and users understand how their contributions are being used to improve AndBible.

AndBible development is much more than just writing code implementing new features. Below is a description of how my time is distributed across different tasks:

**Code Review**: This task involves reviewing contributions and changes submitted by other developers. It's a crucial process that ensures high-quality code, consistent coding standards, and reduces potential issues before they reach production.

**Maintenance**: Maintenance includes tasks necessary for keeping the AndBible application stable and up-to-date. This covers bug fixes, dependency updates, refactoring code, and optimizing performance to ensure the app continues to function smoothly.

**Support**: Support tasks involve actively engaging with users and developers through email and GitHub. This includes reading and responding to user inquiries, addressing bug reports, participating in discussions, and providing guidance to help resolve issues quickly.

**Feature Development**: This involves developing new functionalities and improvements based on user needs and sponsor requests. Feature development is essential for the ongoing enhancement and expansion of AndBible capabilities.

## By Category

| Category | Total Hours | Tasks |
| --- | --- | --- |
| Generic | 36.47 h | ab maintenance (ID=11), ab support (ID=10), ab code review (ID=16) |
| Feature | 13.63 h | ab feat bible memory (ID=23) |

## By Task

| Task | Hours | Category | Ticket |
| --- | --- | --- | --- |
| ab feat bible memory (ID=23) | 13.63 h | Feature | [#2547](https://github.com/AndBible/and-bible/issues/2547) |
| ab code review (ID=16) | 0.65 h | Generic | N/A |
| ab maintenance (ID=11) | 19.73 h | Generic | N/A |
| ab support (ID=10) | 16.08 h | Generic | N/A |

![](/media/blog/2025/07/2025-q2-category-distribution-3.webp)

#### Final observations

- 👍Support has stayed at similarly good level as earlier quartal. I feel blessed about this development, and very happy to see that there are other people sharing the (financial) burden of AndBible development with me!
- 👍New cool features that sponsorship has enabled me to do during the quartal: [Bible Memory feature](https://github.com/AndBible/and-bible/issues/2547).
- 👍As of writing this, I have approximately 23 hours of excess funding, which I believe I will be able to fulfill during July because of my summer holiday.

#### Closing words

I want to express my heartfelt gratitude to every AndBible sponsor for making it possible to maintain, support, and continually develop the app. It brings me great joy and encouragement to see others sharing the financial burden and joining me in supporting the ongoing development of AndBible.

Best regards & blessings,  
Tuomas Airaksinen  
Lead developer of AndBible Open Source Project
