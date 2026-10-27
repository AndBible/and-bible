# Calvin commentary regression fixture

`calvin-psalms-85-86.zip` contains the exact compressed Psalm 85 and 86
blocks and verse offsets from the public-domain `CalvinCommentaries` 1.0
module attached to AndBible issue #3844:

https://github.com/user-attachments/files/30218388/andBible.Calvin.Com.alt.zip

The attachment's `DistributionLicense` is `Public Domain`; its text source is
http://www.ccel.org/ (John Calvin's collected commentaries, converted by Luke
Plant). The fixture retains the original XML, including chapter-level
`annotateRef` attributes in the first verse, unbalanced div boundaries across
entries, and empty entries. It contains no executable code.

To keep the fixture small and independent of installed modules, only the two
chapter blocks are kept. Their block numbers are remapped from 527/528 to 1/2;
the KJV verse indices and offsets within the decompressed blocks are unchanged.
Block 0 is an empty zlib block, all other verse indices are zero, and the New
Testament files are empty. A separate fixture module name prevents collisions
with an installed Calvin module.

Psalm 85 has notes for verses 1, 2, 4, 5, 6, 7, 8, 9, 10, 12 and 13; verses 3
and 11 have no entries. Psalm 86 has notes for verses 1, 2, 3, 5, 6, 8, 9, 10,
11, 12, 13, 14, 15, 16 and 17; verses 4 and 7 have no entries.
