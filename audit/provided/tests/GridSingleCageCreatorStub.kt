package org.piepmeyer.gauguin.creation.cage

import org.piepmeyer.gauguin.grid.*
import kotlin.math.abs

/** Independent arithmetic enumerator used only to supply raw combinations to the developer's real cache class. */
class GridSingleCageCreator(variant: GridVariant, val cage: GridCage) {
  val possibleCombinations: Set<IntArray> by lazy {
    val out=mutableListOf<IntArray>(); val a=IntArray(cage.cells.size)
    fun valid(): Boolean = when(cage.action){
      GridCageAction.ACTION_NONE -> a.size==1 && a[0]==cage.result
      GridCageAction.ACTION_ADD -> a.sum()==cage.result
      GridCageAction.ACTION_MULTIPLY -> a.fold(1){p,x->p*x}==cage.result
      GridCageAction.ACTION_SUBTRACT -> a.size==2 && abs(a[0]-a[1])==cage.result
      GridCageAction.ACTION_DIVIDE -> a.size==2 && minOf(a[0],a[1])!=0 && maxOf(a[0],a[1])%minOf(a[0],a[1])==0 && maxOf(a[0],a[1])/minOf(a[0],a[1])==cage.result
    }
    fun rec(i:Int){
      if(i==a.size){if(valid())out.add(a.clone());return}
      for(v in variant.possibleDigits){
        if((0 until i).any{j -> a[j]==v && (cage.cells[i].row==cage.cells[j].row || cage.cells[i].column==cage.cells[j].column)})continue
        a[i]=v;rec(i+1)
      }
    }
    rec(0)
    out.toSet()
  }
}
