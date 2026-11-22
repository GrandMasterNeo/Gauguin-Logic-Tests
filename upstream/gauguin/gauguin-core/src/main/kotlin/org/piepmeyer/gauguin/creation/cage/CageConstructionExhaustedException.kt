package org.piepmeyer.gauguin.creation.cage

/**
 * A candidate's cage layout cannot be completed within its local restart budget.
 * The caller may discard this candidate and generate a new Latin grid.
 */
class CageConstructionExhaustedException(message: String) : IllegalStateException(message)
