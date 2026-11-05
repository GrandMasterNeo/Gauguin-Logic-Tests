package audit
import org.piepmeyer.gauguin.grid.*
import org.piepmeyer.gauguin.difficulty.human.*

fun makeCase(broken: Boolean): Grid {
    val sol=IntArray(16){i->((i/4)+(i%4))%4+1}
    val cells=(0 until 16).map{ GridCell(it,it/4,it%4,sol[it]).also{c->c.possibles=(1..4).toSet()} }
    val group = if(broken) listOf(0,1) else listOf<Int>()
    val groupCages= mutableListOf<GridCage>()
    if(broken) groupCages += GridCage(0,GridCageAction.ACTION_ADD,3,group.map{cells[it]})
    for(i in 0 until 16) if(!group.contains(i)) groupCages+=GridCage(groupCages.size,GridCageAction.ACTION_NONE,sol[i],listOf(cells[i]))
    groupCages.forEach { c -> c.cells.forEach{it.cage=c} }
    if(broken){cells[0].possibles=setOf(3);cells[1].possibles=setOf(4)}
    return Grid(cells,groupCages,GridVariant((1..4).toList()),GridSize(4))
}
fun main(){
    val bad=makeCase(true)
    val result=HumanSolver(bad,validate=false).solveAndCalculateDifficulty(avoidReveal=true)
    check(!result.success) {"Contradictory cache should force unsuccessful result"}
    check(!bad.isSolved()) {"Broken grid was somehow solved"}
    val good=makeCase(false)
    val valid=HumanSolver(good,validate=true).solveAndCalculateDifficulty(avoidReveal=true)
    check(valid.success && good.isSolved() && good.numberOfMistakes()==0) {"Valid isolated single-cage puzzle must solve"}
    println("GUARD INTEGRATION: contradictory arithmetic-cage inputs rejected, solved valid grid accepted; both passed with actual HumanSolver and actual cache code")
}
