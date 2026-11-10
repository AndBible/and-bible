package net.bible.sharedlogic.platform.host

import okio.FileSystem
import okio.Path

/**
 * Atomically recovers a relocated Sword module store without merging module versions.
 *
 * Recovery is skipped if the source tree contains symlinks or the destination contains any
 * non-directory entry. Filesystem failures propagate so startup cannot proceed with a partial store.
 */
fun recoverRelocatedSwordModules(
    source: Path,
    destination: Path,
    files: FileSystem = FileSystem.SYSTEM,
    log: (String) -> Unit = {},
) {
    val sourceMetadata = files.metadataOrNull(source) ?: return
    if (!sourceMetadata.isDirectory || sourceMetadata.symlinkTarget != null) return
    if (hasSymlinkInPath(files, source) || hasSymlinkInPath(files, destination)) {
        log("Skipped Sword module recovery from $source to $destination because a path contains a symlink")
        return
    }
    if (containsSymlink(files, source)) {
        log("Skipped Sword module recovery from $source to $destination because the source tree contains a symlink")
        return
    }
    if (destinationHasPayload(files, destination)) {
        log("Skipped Sword module recovery from $source to $destination because the destination contains data")
        return
    }

    if (files.metadataOrNull(destination) != null) deleteEmptyDirectoryTree(files, destination)
    destination.parent?.let { files.createDirectories(it) }
    files.atomicMove(source, destination)
    log("Recovered Sword module store from $source to $destination")
}

private fun containsSymlink(files: FileSystem, directory: Path): Boolean =
    files.list(directory).any { entry ->
        val metadata = files.metadataOrNull(entry) ?: return@any false
        metadata.symlinkTarget != null || (metadata.isDirectory && containsSymlink(files, entry))
    }

private fun destinationHasPayload(files: FileSystem, path: Path): Boolean {
    val metadata = files.metadataOrNull(path) ?: return false
    if (!metadata.isDirectory || metadata.symlinkTarget != null) return true
    return files.list(path).any { entry ->
        val entryMetadata = files.metadataOrNull(entry) ?: return@any false
        !entryMetadata.isDirectory || entryMetadata.symlinkTarget != null || destinationHasPayload(files, entry)
    }
}

private fun deleteEmptyDirectoryTree(files: FileSystem, directory: Path) {
    for (entry in files.list(directory)) {
        val metadata = files.metadataOrNull(entry)
        if (metadata?.isDirectory == true && metadata.symlinkTarget == null) {
            deleteEmptyDirectoryTree(files, entry)
        } else {
            error("Destination changed during Sword module recovery preflight: $entry")
        }
    }
    files.delete(directory)
}

private fun hasSymlinkInPath(files: FileSystem, path: Path): Boolean {
    var current: Path? = path
    while (current != null) {
        if (files.metadataOrNull(current)?.symlinkTarget != null) return true
        current = current.parent
    }
    return false
}
