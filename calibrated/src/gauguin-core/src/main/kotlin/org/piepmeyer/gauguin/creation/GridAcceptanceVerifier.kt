package org.piepmeyer.gauguin.creation

import org.piepmeyer.gauguin.grid.Grid

/**
 * Optional generation-only safety gate. A missing implementation leaves the
 * upstream generator unchanged, keeping core tests and normal app startup safe.
 */
fun interface GridAcceptanceVerifier {
    fun accepts(grid: Grid): Boolean
}
