package audit

import kotlin.random.Random
import org.piepmeyer.gauguin.grid.*
import org.piepmeyer.gauguin.difficulty.human.*

/** Actual 37 original strategy classes, in registry order; true game logic / isolated grid model. */
class RefreshedCache(private val grid: Grid): HumanSolverCache {
    private val lines = GridLinesProvider(grid)
    private val allCombinations = comboCache(grid)
    override fun possibles(cage: GridCage): Set<IntArray> = allCombinations.getValue(cage.id).toSet()
    override fun adjacentlinesWithEachPossibleValue(numberOfLines: Int): Set<GridLines> = lines.adjacentlinesWithEachPossibleValue(numberOfLines)
    override fun linesWithEachPossibleValue(): Set<GridLine> = lines.linesWithEachPossibleValue()
    override fun adjacentlines(numberOfLines: Int): Set<GridLines> = lines.adjacentlines(numberOfLines)
    override fun allLines(): Set<GridLine> = lines.allLines()
}
fun main() {
    val rnd = Random(2026110511)
    val all = latinSquares4()
    val statCalls = IntArray(HumanSolverStrategies.entries.size)
    val statChanges = IntArray(statCalls.size)
    val statRemovals = IntArray(statCalls.size)
    val statAssignments = IntArray(statCalls.size)
    val exceptions = linkedMapOf<String,Int>()
    val failures = mutableListOf<String>()
    var totalSteps=0; var tested=0; var ambiguous=0; var nonemptyCages=0; var halted=0
    repeat(360){ case ->
        val original = makeGrid(rnd, all.random(rnd))
        val allowed = compatibleSquares(original, all)
        require(allowed.isNotEmpty())
        if(allowed.size>1) ambiguous++
        tested++
        var steps=0
        while(steps<65){
            val currentCache = RefreshedCache(original)
            if(original.cages.any{currentCache.possibles(it).isEmpty()} || original.cells.any{!it.isUserValueSet && it.possibles.isEmpty()}){
                failures += "Unexpected inconsistent state before step case=$case steps=$steps"
                break
            }
            nonemptyCages++
            var changed = false
            val idxList = HumanSolverStrategies.entries.withIndex().shuffled(rnd)
            for((idx,registered) in idxList){
                val before = original.cells.map{it.userValue to it.possibles.toSet()}
                statCalls[idx]++
                val failure = runCatching {registered.solver.fillCells(original, currentCache)}
                if(failure.isFailure){
                    val err=failure.exceptionOrNull()!!
                    exceptions["${registered.name}:${err.javaClass.simpleName}"]=(exceptions["${registered.name}:${err.javaClass.simpleName}"]?:0)+1
                    failures+="EXCEPTION case=$case step=$steps strategy=${registered.name}: ${err.message}"
                    break
                }
                val now = original.cells.map{it.userValue to it.possibles.toSet()}
                val diffs=now.zip(before).count {it.first!=it.second}
                if(diffs>0){
                    statChanges[idx]++
                    for(ci in original.cells.indices){
                        val (oldUser,oldPoss)=before[ci]
                        val (newUser,newPoss)=now[ci]
                        if (newUser!=oldUser && newUser!=null){
                            statAssignments[idx]++
                            if(allowed.any{it[ci]!=newUser}) failures += "INVALID PLACEMENT case=$case step=$steps strategy=${registered.name} cell=$ci value=$newUser allowed=${allowed.map{it[ci]}.distinct()}"
                        }
                        if(newUser==oldUser){
                            for(v in oldPoss-newPoss){
                                statRemovals[idx]++
                                if(allowed.any{it[ci]==v}) failures += "INVALID REMOVAL case=$case step=$steps strategy=${registered.name} cell=$ci value=$v solutions=${allowed.size}"
                            }
                        }
                        if (oldUser!=newUser && oldUser!=null) failures+="OLD PLACEMENT CHANGED case=$case strategy=${registered.name} cell=$ci"
                        if (!newPoss.subtract(oldPoss).isEmpty()) failures+="UNEXPECTED CANDIDATE ADDITION case=$case strategy=${registered.name} cell=$ci"
                    }
                    if(compatibleSquares(original,all).size!=allowed.size) failures += "SOLUTION SPACE LOST case=$case strategy=${registered.name} before=${allowed.size} after=${compatibleSquares(original,all).size}"
                    changed=true
                    steps++
                    break
                }
            }
            if(failures.isNotEmpty()) break
            if(!changed){halted++;break}
        }
        totalSteps+=steps
        if(failures.isNotEmpty()) return@repeat
    }
    println("SEQUENTIAL results tested=$tested ambiguous=$ambiguous statesAfterChanges=$totalSteps cacheChecks=$nonemptyCages stableHalts=$halted")
    println("Total strategy calls=${statCalls.sum()} changedSteps=${statChanges.sum()} removals=${statRemovals.sum()} placements=${statAssignments.sum()} exceptions=${exceptions.values.sum()} invalid=${failures.size}")
    HumanSolverStrategies.entries.forEachIndexed {i,e -> if(statChanges[i]>0) println("ACTIVE ${e.name} calls=${statCalls[i]} changes=${statChanges[i]} removals=${statRemovals[i]} placements=${statAssignments[i]}") }
    println("Activated strategies=${statChanges.count{it>0}} / ${statChanges.size}")
    exceptions.forEach { println("EXCEPTION ${it.key}: ${it.value}") }
    failures.take(15).forEach{println("FAIL $it")}
    check(failures.isEmpty()){"Sequential cross-strategy audit found invalid behavior"}
}
