package org.piepmeyer.gauguin.grid

import org.piepmeyer.gauguin.creation.cage.GridCageType

class GridCell(val cellNumber: Int, val row: Int, val column: Int, val value: Int) {
  var userValue: Int? = null
  val isUserValueSet: Boolean get() = userValue != null
  var possibles: Set<Int> = setOf()
  var cage: GridCage? = null
  fun removePossible(n: Int) { possibles = possibles - n }
  fun cage(): GridCage = cage!!
}

enum class GridCageAction { ACTION_NONE, ACTION_ADD, ACTION_MULTIPLY, ACTION_SUBTRACT, ACTION_DIVIDE }
class GridCage(val id: Int, val action: GridCageAction, val result: Int, val cells: List<GridCell>) {
  val cageType: GridCageType get() = if(cells.size==1) GridCageType.SINGLE else GridCageType.MULTIPLE
  fun getCell(i: Int) = cells[i]
  fun isUserMathCorrect(): Boolean = cells.any { !it.isUserValueSet } || when(action) {
    GridCageAction.ACTION_NONE -> cells.first().userValue == result
    GridCageAction.ACTION_ADD -> cells.sumOf { it.userValue!! } == result
    GridCageAction.ACTION_MULTIPLY -> cells.fold(1) { a,b -> a*b.userValue!! } == result
    GridCageAction.ACTION_SUBTRACT -> kotlin.math.abs(cells[0].userValue!! - cells[1].userValue!!) == result
    GridCageAction.ACTION_DIVIDE -> { val v1=cells[0].userValue!!; val v2=cells[1].userValue!!; val hi=maxOf(v1,v2); val lo=minOf(v1,v2); lo!=0 && hi%lo==0 && hi/lo==result }
  }
}
class GridVariant(digits: Collection<Int>) { val possibleDigits: Set<Int> = digits.toSet(); val width get() = possibleDigits.size; val height get() = possibleDigits.size; val gridSize get() = GridSize(possibleDigits.size) }
class GridSize(val size: Int) { val width get()=size;val height get()=size;fun smallestSide(): Int = size; fun largestSide():Int=size }
class Grid(val cells: List<GridCell>, val cages: List<GridCage>, val variant: GridVariant, val gridSize: GridSize) {
  fun isSolved(): Boolean = cells.all{it.isUserValueSet} && cages.all{it.isUserMathCorrect()} && (0 until gridSize.size).all { i -> cells.filter{it.row==i}.map{it.userValue}.toSet().size==gridSize.size && cells.filter{it.column==i}.map{it.userValue}.toSet().size==gridSize.size }
  fun numberOfMistakes(): Int = cells.count { it.userValue != null && it.userValue != it.value }
  fun detailedToString(changedCells: Collection<GridCell>) = "changed=${changedCells.map { it.cellNumber }}"
  fun eachColumnContainsEachPossibleValue()=true
  fun eachRowContainsEachPossibleValue()=true
  fun getCell(i:Int)=cells[i]
  fun getValidCellAt(y:Int,x:Int)=cells[y*gridSize.width+x]
  fun getCellsAtSameRow(c:GridCell)=cells.filter { it.row==c.row && it!==c }
  fun getCellsAtSameColumn(c:GridCell)=cells.filter { it.column==c.column && it!==c }
  fun setUserValueAndRemovePossibles(cell:GridCell, value:Int?):List<GridCell>{
    cell.userValue=value;cell.possibles=emptySet()
    val affected= mutableListOf(cell)
    if(value!=null)for(peer in cells){if(peer!==cell&&(peer.row==cell.row||peer.column==cell.column)&&value in peer.possibles){peer.removePossible(value);affected.add(peer)}}
    return affected
  }
  fun copyWithEmptyUserValues():Grid {
    val cs=cells.map{GridCell(it.cellNumber,it.row,it.column,it.value)}
    val cg=cages.map { old-> GridCage(old.id,old.action,old.result,old.cells.map{cs[it.cellNumber]}).also { cage->cage.cells.forEach{it.cage=cage} } }
    return Grid(cs,cg,variant,gridSize)
  }
}
