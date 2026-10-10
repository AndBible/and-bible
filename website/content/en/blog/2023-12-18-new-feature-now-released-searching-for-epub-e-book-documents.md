---
title: 'New feature in today''s release (5.0.792): search inside ePub e-book documents'
date: '2023-12-18'
slug: new-feature-now-released-searching-for-epub-e-book-documents
summary: Since version 5.0 was released in 2nd November, I have used most of my development time to fix bugs and crashes that have arised since the release. There have not…
categories:
- New features
tags:
- tips & tricks
- epub
- search
- new features
- video
image: blog/2023/12/newfeature.webp
image_alt: 'New feature in today''s release (5.0.792): search inside ePub e-book documents'
---

Since version 5.0 was released in 2nd November, I have used most of my development time to fix bugs and crashes that have arised since the release. There have not been any severe issues that would affect many users, but there have been big amount of smaller issues that affect small amount of users. Nevertheless, fixing crashes and bugs after a big release is a priority.

However, recently I have finally had a little time to develop one new feature too. Version 5.0.792 (released on 18th Dec) introduces a new *search feature for epub e-books*. From users point of view, it works similar way as search for Bible documents: you use search dialog, input your search keyword(s) and get list of results, search keyword(s) highlighted. To be able to do searchs to your epub documents, you need to let AndBible first create search index.

![](/media/blog/2023/12/screenshot-20231216-202342-bible-study.webp)

![](/media/blog/2023/12/screenshot-20231216-202422-bible-study.webp)

![](/media/blog/2023/12/screenshot-20231216-202438-bible-study.webp)

From technical point of view, searching inside ePub document works differently than for other document types. For Bible documents, AndBible uses Apache Lucene to perform searches (via JSword library). EPub search, however, is implemented using SQLite3's FTS5 (Full Text Search) engine to index document and perform searches. Thus, I have enabled also possibility to enter raw [FTS5 query syntax](https://www.sqlite.org/fts5.html#full_text_query_syntax) through the Search view UI.

For anyone interested in code, here's [link to pull request](https://github.com/AndBible/and-bible/pull/3080). To learn how convenient it was to use FTS5, see especially code in [EpubSearch.kt](https://github.com/AndBible/and-bible/blob/current-stable/app/src/main/java/net/bible/service/sword/epub/EpubSearch.kt).

And by the way: even if you can't do programming yourself, you too can [support AndBible development financially by purchasing development hours](https://shop.andbible.org) and so contribute to the project.

https://www.youtube.com/watch?v=PNAeNm6P8VM
