package net.bible.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.sharedcore.platform.OrderedLauncher

/** An [OrderedLauncher] on its own real-dispatcher scope, for tests that only need a constructor argument (e.g. `BookmarkControl`). */
fun testOrderedLauncher() = OrderedLauncher(CoroutineScope(SupervisorJob() + Dispatchers.Default))
