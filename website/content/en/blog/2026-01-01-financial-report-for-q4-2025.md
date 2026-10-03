---
title: Financial report for Q4 / 2025
date: '2026-01-01'
slug: financial-report-for-q4-2025
summary: 'Tuomas Airaksinen Consulting, company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via the webshop. See also: financial…'
categories:
- Sponsoring AndBible
tags:
- financial support
- transparency
image: blog/2025/10/screenshot-from-2025-10-02-16-51-45.webp
image_alt: Financial report for Q4 / 2025
---

https://www.youtube.com/watch?v=sKjNBfiU5V4

# Background

[Tuomas Airaksinen Consulting](https://tuomasairaksinen.fi/consulting/), company of the lead developer, provides a way to sponsor AndBible development. You can support the ongoing development of AndBible financially via [the webshop](https://shop.andbible.org).

See also: financial report for [Q4/2023](/2024/01/04/financial-report-for-q4-2024/), [Q1/2024](/2024/04/04/financial-report-for-q1-2024/), [Q2/2024](/2024/07/09/financial-report-for-q2-2024/), [Q3/2024](/2024/10/03/financial-report-for-q3-2024/), [Q4/2024](/2025/01/06/financial-report-for-q4-2024-2/), [Q1/2025](/2025/04/03/financial-report-for-q1-2025/), [Q2/2025](/2025/07/08/financial-report-for-q2-2025/) and [Q3/2025](/2025/10/02/financial-report-for-q3-2025/).

I have again revised reporting to some extent, trying to make it clearer.

## "Free work" correction notice for Q1-Q3/2025 reports

First I want to let you know about a small bug that I noticed when creating report for Q4 in my report generating script. When feature development work exceeded its sponsorship (shown as negative values in the "From Features" column), this unsponsored feature work was not being counted as "Free Work".

The "Free Work" column in the Generic Development tables was underreported in previous quarters. The corrected values are:

| Quarter | Month | From Features | Old Free Work | Corrected Free Work |
| --- | --- | --- | --- | --- |
| Q1 | January | -732.50 € | 293.00 € | 1025.50 € |
| Q1 | March | -19.00 € | 0.00 € | 19.00 € |
| Q2 | April | -339.00 € | 0.00 € | 339.00 € |
| Q2 | May | -239.00 € | 808.65 € | 1047.65 € |
| Q3 | July | -1.00 € | 73.77 € | 74.77 € |
| Q3 | August | -103.00 € | 317.80 € | 420.80 € |

The "Free Work" column from now on correctly includes both:

1. Generic development work not covered by available funding (as before)
2. Feature development work that exceeded its sponsorship (previously missing)

This provides a more accurate picture of the total volunteer/unsponsored development effort. All other figures in the reports (revenue, hours, costs, etc.) remain unchanged. Only the "Free Work" calculation was affected.

## Sponsorship Flow

The sponsorship flow tables show how funding for AndBible development works. When users sponsor specific features, their money is allocated to pay for the development work on those features. If a feature receives more sponsorship than needed, the excess helps fund general development. If a feature needs more work than its sponsorship covers, the shortfall is covered by general development funding when possible, or becomes volunteer work when no funding is available.

Each feature table shows month-by-month how sponsorship money was used: **Revenue** is money received from sponsors, **Cost** is what the development work actually cost, and **Remaining** shows leftover sponsorship money that carries forward to next month or gets transferred to general development when the feature is completed.

### Sponsorship Flow - Feature [#3570 (LLM features)](https://github.com/AndBible/and-bible/issues/3570)

| Month | Carryover | Revenue | Available | Cost | Remaining | To Generic |
| --- | --- | --- | --- | --- | --- | --- |
| December | 0.00 € | 0.00 € | 0.00 € | 1558.00 € | 0.00 € | 1558.00 € (25.97 h work) |

**Rate:** 60.00 €/h

### Sponsorship Flow - Feature [#1982 (In search result window add button 'Change displayed language')](https://github.com/AndBible/and-bible/issues/1982)

| Month | Carryover | Revenue | Available | Cost | Remaining | To Generic |
| --- | --- | --- | --- | --- | --- | --- |
| November | 0.00 € | 105.00 € | 105.00 € | 49.00 € | 56.00 € | 56.00 € (surplus) |

**Rate:** 70.00 €/h

### Sponsorship Flow - Feature [#3551 (Study pad cursor editing position and Studypad search)](https://github.com/AndBible/and-bible/issues/3551)

| Month | Carryover | Revenue | Available | Cost | Remaining | To Generic |
| --- | --- | --- | --- | --- | --- | --- |
| November | 0.00 € | 0.00 € | 0.00 € | 672.00 € | 0.00 € | 672.00 € (11.20 h work) |

**Rate:** 60.00 €/h

**Column Explanations:**

- **Carryover**: Funding balance carried over from the previous month
- **Revenue**: Sponsorship money received this month
- **Available**: Total funding available (Carryover + Revenue)
- **Cost**: Actual cost of work done
- **Remaining**: Funding balance after costs, carried to next month
- **To Generic**: Work exceeding feature budget (shown as € with hours) or surplus funds transferred when feature is completed

### Sponsorship Flow - Generic Development

| Month | Carryover | Revenue | From Completed | Available | Feature Cost | Generic Cost | Remaining | Free Work | Sponsored % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| October | 0.00 € | 1032.00 € | 0.00 € | 1032.00 € | 0.00 € | 495.00 € | 537.00 € | 0.00 € | 100 % |
| November | 537.00 € | 60.00 € | 56.00 € | 653.00 € | 672.00 € | 472.00 € | 0.00 € | 491.00 € | 57 % |
| December | 0.00 € | 138.00 € | 0.00 € | 138.00 € | 1558.00 € | 55.00 € | 0.00 € | 1475.00 € | 9 % |

**Rate:** 60.00 €/h

**Column Explanations:**

- **Month**: Calendar month name
- **Carryover**: Funding balance carried over from the previous month
- **Revenue**: Generic development sponsorship received this month
- **From Completed**: Funding transferred from completed features
- **Available**: Total funding available (Carryover + Revenue + From Completed)
- **Feature Cost**: Cost of unsponsored feature work (feature overflow)
- **Generic Cost**: Cost of generic development work
- **Remaining**: Funding balance after all costs, carried to next month
- **Free Work**: Work not covered by available funding
- **Sponsored %**: Percentage of total costs covered by sponsorship funding

<div class="gallery" markdown>
![](/media/blog/2026/01/2025-q4-hours-comparison.webp)
</div>

## Time Tracking Summary

This section shows how development time was actually spent during the reporting period. **By Category** groups work into broad areas like feature development, bug fixes, and maintenance. **By Task** provides detailed breakdown of specific tasks, showing exactly what was worked on and for how long. This transparency helps sponsors and users understand how their contributions are being used to improve AndBible.

AndBible development is much more than just writing code implementing new features. Below is a description of how my time is distributed across different tasks:

**Code Review**: This task involves reviewing contributions and changes submitted by other developers. It's a crucial process that ensures high-quality code, consistent coding standards, and reduces potential issues before they reach production.

**Maintenance**: Maintenance includes tasks necessary for keeping the AndBible application stable and up-to-date. This covers bug fixes, dependency updates, refactoring code, and optimizing performance to ensure the app continues to function smoothly.

**Support**: Support tasks involve actively engaging with users and developers through email and GitHub. This includes reading and responding to user inquiries, addressing bug reports, participating in discussions, and providing guidance to help resolve issues quickly.

**Feature Development**: This involves developing new functionalities and improvements based on user needs and sponsor requests. Feature development is essential for the ongoing enhancement and expansion of AndBible capabilities.

### By Category

| Category | Total Hours | Tasks |
| --- | --- | --- |
| Feature | 37.87 h | ab ai project (ID=32), ab 1982 language (ID=31), ab feature development (ID=2) |
| Generic | 17.03 h | ab maintenance (ID=11), ab support (ID=10), ab feature development (ID=2) |

![](/media/blog/2026/01/2025-q4-category-distribution.webp)

### By Task

| Task | Hours | Category | Ticket |
| --- | --- | --- | --- |
| ab 1982 language (ID=31) | 0.70 h | Feature | [#1982](https://github.com/AndBible/and-bible/issues/1982) |
| ab ai project (ID=32) | 25.97 h | Feature | [#3570](https://github.com/AndBible/and-bible/issues/3570) |
| ab feature development (ID=2) | 14.93 h | Feature | [#3551](https://github.com/AndBible/and-bible/issues/3551) |
| ab maintenance (ID=11) | 8.45 h | Generic | N/A |
| ab support (ID=10) | 4.85 h | Generic | N/A |

![](/media/blog/2026/01/2025-q4-task-distribution-2.webp)

## Summary

The fourth quarter was dominated by work on the [new LLM/AI features](/2026/01/05/my-ai-plans-to-andbible/), which consumed roughly half of all development time during this period. I started this project because I saw enormous potential in it, and personally it excited and inspired me significantly. Even though there was no sponsorship for it, I wanted to sponsor the work myself through my own volunteer contribution. This is a substantial project and is expected (Lord willing) to be completed during spring 2026.

Two other features were also completed during this quarter: improvements to [Study Pad cursor editing](/2025/11/27/new-feature-studypad-edit-position/) and [search functionality](/2025/11/27/new-feature-search-studypads-that-have-certain-content/), and [a language selection button](https://github.com/AndBible/and-bible/issues/1982) for the search results window. The latter was the only fully sponsored feature work this quarter.

Another highlight of the quarter was welcoming a new team member: Tobe Iheme joined the project as a [part-time developer trainee](/2025/11/13/developer-trainee-for-andbible-project/). He assists with bug fixes, user support, and other project tasks, freeing up more of my time for core development work.

Overall, Q4 saw a noticeable drop in sponsorship coverage compared to earlier in the year, with December being particularly lean. In the long term, a continued decline in sponsorship would naturally reduce my ability to participate in the project at the level I have before. However, I remain positive about the future in this regard. Development continued at a steady pace throughout the quarter, reflecting a commitment to moving AndBible forward regardless of funding levels. Maintenance and user support work continued as usual alongside the feature development.

## Closing words

I want to express my heartfelt gratitude to every AndBible sponsor for making it possible to maintain, support, and continually develop the app. It brings me great joy and encouragement to see others sharing the financial burden and joining me in supporting the ongoing development of AndBible.

Best regards & blessings,  
Tuomas Airaksinen  
Lead developer of AndBible Open Source Project
