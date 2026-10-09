package net.bible.service.cloudsync.webdav

/**
 * Decides from one upload whether the server bumps a collection's getlastmodified when a child is
 * written. [m0] = parent mtime before the PUT, [m1] = after, [f] = the new file's mtime (all
 * server clock, whole seconds). Evidence only counts when the folder was demonstrably older than
 * the file before the upload (`m0 < f`); otherwise (folder created in the same second) the result
 * is inconclusive, which prevents a false YES on a just-created folder.
 */
fun decidePropagation(m0: Long?, m1: Long?, f: Long?): Propagation? {
    if (m0 == null || m1 == null || f == null) return null
    if (m0 >= f) return null
    return if (m1 >= f) Propagation.YES else Propagation.NO
}
