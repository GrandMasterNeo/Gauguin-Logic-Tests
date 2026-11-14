package org.piepmeyer.gauguin.difficulty.human

import io.kotest.core.spec.style.FunSpec

/**
 * Test-only guardrail for deduction-only difficulty calibration.
 *
 * Counts and order are intentional assertions about the currently audited
 * strategy registry, not a claim that all strategies are logically sound.
 */
class HumanStrategyRegistryInvariantTest : FunSpec({
    test("the audited registry has 35 deduction strategies and two Nishio strategies") {
        val strategies = HumanSolverStrategies.entries
        val deduction = strategies.filterNot { it.isNishio }
        val trial = strategies.filter { it.isNishio }

        check(strategies.size == 37) { "Unexpected strategy count: ${strategies.size}" }
        check(deduction.size == 35) { "Unexpected deduction-only count: ${deduction.size}" }
        check(trial.size == 2) { "Unexpected Nishio count: ${trial.size}" }
        check(trial.all { it.name.contains("Nishio") })
        check(deduction.none { it.name.contains("Nishio") })
    }

    test("registered strategy weights are nondecreasing, with Nishio last") {
        val strategies = HumanSolverStrategies.entries
        val weights = strategies.map { it.difficulty }
        check(weights == weights.sorted()) { "Strategy priority differs from ascending weights: $weights" }
        check(strategies.take(35).none { it.isNishio })
        check(strategies.takeLast(2).all { it.isNishio })
        println("STRATEGY_REGISTRY total=${strategies.size} deduction=35 nishio=2 minWeight=${weights.first()} maxDeductionWeight=${weights[34]} maxWeight=${weights.last()}")
    }
})
