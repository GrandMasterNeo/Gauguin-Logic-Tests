package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Registry integrity only. The mathematical correctness of every strategy
 * requires separate soundness and regression testing.
 */
class HumanSolverRegistryAuditTest :
    FunSpec({
        test("all 37 strategies remain registered exactly once") {
            val strategies = HumanSolverStrategies.entries

            strategies.size shouldBe 37
            strategies.map { it.solver.javaClass }.toSet().size shouldBe 37
            strategies.all { it.difficulty > 0 } shouldBe true
        }

        test("only the two expected trial-based strategies are marked Nishio") {
            HumanSolverStrategies.entries
                .filter { it.isNishio }
                .map { it.name } shouldBe
                listOf("ANishioWithPairs", "AAdvancedNishioWithPairs")
        }
    })
