package audit

import kotlin.random.Random
import org.piepmeyer.gauguin.grid.*
import org.piepmeyer.gauguin.difficulty.human.*
import org.piepmeyer.gauguin.difficulty.human.strategy.*

// Exhaustive 4x4 Latin-square solution enumeration, independent of the Gauguin human solver.
fun latinSquares4(): List<IntArray> {
  val out = mutableListOf<IntArray>()
  val a=IntArray(16)
  val rows=IntArray(4); val cols=IntArray(4)
  fun rec(i:Int) {
    if(i==16){ out.add(a.clone());return }
    val r=i/4; val c=i%4
    for(d in 1..4) {
      val bit=1 shl d
      if(rows[r] and bit != 0 || cols[c] and bit != 0)continue
      rows[r]=rows[r] or bit; cols[c]=cols[c] or bit; a[i]=d
      rec(i+1)
      rows[r]=rows[r] xor bit; cols[c]=cols[c] xor bit
    }
  }
  rec(0);return out
}

fun mathValid(op: GridCageAction, target: Int, values: IntArray): Boolean = when(op) {
  GridCageAction.ACTION_NONE -> values.size==1 && values[0]==target
  GridCageAction.ACTION_ADD -> values.sum()==target
  GridCageAction.ACTION_MULTIPLY -> values.fold(1){a,b->a*b}==target
  GridCageAction.ACTION_SUBTRACT -> values.size==2 && kotlin.math.abs(values[0]-values[1])==target
  GridCageAction.ACTION_DIVIDE -> values.size==2 && (values.max()/values.min()==target) && values.max()%values.min()==0
}

class AuditCache(val grid:Grid, private val combos: Map<Int,List<IntArray>>):HumanSolverCache {
  val lines = (0..3).flatMap { listOf(GridLine(grid,GridLineType.ROW,it),GridLine(grid,GridLineType.COLUMN,it)) }
  override fun possibles(cage:GridCage):Set<IntArray> = combos.getValue(cage.id).toSet()
  override fun allLines():Set<GridLine> = lines.toSet()
  override fun linesWithEachPossibleValue():Set<GridLine> = lines.toSet()
  override fun adjacentlines(n:Int):Set<GridLines> = if (n in 1..4) linkedSetOf<GridLines>().apply {
    for(t in listOf(GridLineType.ROW,GridLineType.COLUMN))for(start in 0..(4-n))add(GridLines((start until start+n).map{GridLine(grid,t,it)}.toSet()))
  } else emptySet()
  override fun adjacentlinesWithEachPossibleValue(numberOfLines:Int)=adjacentlines(numberOfLines)
}

fun randomPartition(r:Random, square:IntArray):List<List<Int>> {
  val remaining = (0..15).toMutableSet(); val out= mutableListOf<List<Int>>()
  while(remaining.isNotEmpty()) {
    val first=remaining.random(r);remaining.remove(first)
    val want = listOf(1,2,2,3,3,3,4,4).random(r)
    val group= mutableListOf(first)
    while(group.size<want){
      val available=group.flatMap{ i->listOf((i/4-1)*4+i%4,(i/4+1)*4+i%4,i-1,i+1).filter{j->j in remaining && (kotlin.math.abs(j/4-i/4)+kotlin.math.abs(j%4-i%4)==1)} }.distinct()
      if(available.isEmpty())break
      val next=available.random(r);remaining.remove(next);group.add(next)
    }
    out.add(group.sorted())
  }
  return out
}
fun makeGrid(r:Random, square:IntArray):Grid{
  val cells=(0..15).map{GridCell(it,it/4,it%4,square[it])}
  val groups=randomPartition(r,square)
  val cages=groups.mapIndexed{id, ids ->
    val vs=ids.map{square[it]}.toIntArray()
    val options= mutableListOf(GridCageAction.ACTION_ADD, GridCageAction.ACTION_ADD,GridCageAction.ACTION_MULTIPLY)
    if(vs.size==2){options.add(GridCageAction.ACTION_SUBTRACT);if(vs.max()%vs.min()==0)options.add(GridCageAction.ACTION_DIVIDE)}
    val op=if(vs.size==1) GridCageAction.ACTION_NONE else options.random(r)
    val target=when(op){
      GridCageAction.ACTION_NONE->vs[0];GridCageAction.ACTION_ADD->vs.sum();GridCageAction.ACTION_MULTIPLY->vs.fold(1){a,b->a*b};GridCageAction.ACTION_SUBTRACT->kotlin.math.abs(vs[0]-vs[1]);GridCageAction.ACTION_DIVIDE->vs.max()/vs.min()
    }
    GridCage(id,op,target,ids.map{cells[it]}).also{c->c.cells.forEach{it.cage=c}}
  }
  cells.forEach{cell ->
    if(cell.cage!!.cells.size==1||r.nextDouble()<0.08)cell.userValue=cell.value
    else cell.possibles=(1..4).filter{it==cell.value || r.nextDouble()<0.52}.toSet()
  }
  // Gauguin removes values of placed cells from their row/column peer candidates.
  for(cell in cells)if(!cell.isUserValueSet){
    val used=cells.filter{it.isUserValueSet && (it.row==cell.row||it.column==cell.column)}.mapNotNull{it.userValue}.toSet()
    cell.possibles = cell.possibles - used
  }
  return Grid(cells,cages,GridVariant(listOf(1,2,3,4)),GridSize(4))
}
fun compatibleSquares(grid:Grid,all:List<IntArray>):List<IntArray> = all.filter{ sq->
  grid.cells.all{it.userValue?.let{v->v==sq[it.cellNumber]}?:it.possibles.contains(sq[it.cellNumber])} &&
    grid.cages.all{c->mathValid(c.action,c.result,c.cells.map{sq[it.cellNumber]}.toIntArray())}
}
fun comboCache(grid:Grid):Map<Int,List<IntArray>> = grid.cages.associate{c->
  val out= mutableListOf<IntArray>();val a=IntArray(c.cells.size)
  fun rec(i:Int){
    if(i==a.size){if(mathValid(c.action,c.result,a))out.add(a.clone());return}
    val cell=c.cells[i]
    for(v in (cell.userValue?.let{listOf(it)}?:cell.possibles.toList())){
      if((0 until i).any{j->(cell.row==c.cells[j].row||cell.column==c.cells[j].column)&&a[j]==v})continue
      a[i]=v;rec(i+1)
    }
  }
  rec(0)
  c.id to out
}

fun copyGrid(grid:Grid):Grid{
  val cs=grid.cells.map{GridCell(it.cellNumber,it.row,it.column,it.value).also{x->x.userValue=it.userValue;x.possibles=it.possibles.toSet()}}
  val cages=grid.cages.map{c->GridCage(c.id,c.action,c.result,c.cells.map{cs[it.cellNumber]}).also{x->x.cells.forEach{it.cage=x}}}
  return Grid(cs,cages,grid.variant,grid.gridSize)
}
