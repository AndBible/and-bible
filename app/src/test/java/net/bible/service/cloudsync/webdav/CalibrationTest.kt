package net.bible.service.cloudsync.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationTest {
    @Test fun folderMovedWithChild_isYes() = assertEquals(Propagation.YES, decidePropagation(m0 = 1000, m1 = 5000, f = 5000))
    @Test fun folderAfterChild_isYes() = assertEquals(Propagation.YES, decidePropagation(1000, 6000, 5000))
    @Test fun folderStayedBehind_isNo() = assertEquals(Propagation.NO, decidePropagation(1000, 1000, 5000))
    @Test fun sameSecondFolder_isInconclusive() = assertNull(decidePropagation(5000, 5000, 5000))
    @Test fun folderNewerThanFileBefore_isInconclusive() = assertNull(decidePropagation(6000, 6000, 5000))
    @Test fun missingTimes_areInconclusive() {
        assertNull(decidePropagation(null, 5000, 5000))
        assertNull(decidePropagation(1000, null, 5000))
        assertNull(decidePropagation(1000, 5000, null))
    }
}
