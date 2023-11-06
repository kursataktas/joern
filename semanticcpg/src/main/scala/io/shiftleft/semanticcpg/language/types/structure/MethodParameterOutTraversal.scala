package io.shiftleft.semanticcpg.language.types.structure

import io.shiftleft.codepropertygraph.generated.v2.nodes.*
import io.shiftleft.codepropertygraph.generated.v2.Language.*
import io.shiftleft.semanticcpg.language.*

class MethodParameterOutTraversal(val traversal: Iterator[MethodParameterOut]) extends AnyVal {

  def paramIn: Iterator[MethodParameterIn] = {
    // TODO define a named step in schema
    traversal.flatMap(_._parameterLinkIn.collectAll[MethodParameterIn])
  }

  /* method parameter indexes are  based, i.e. first parameter has index  (that's how java2cpg generates it) */
  def index(num: Int): Iterator[MethodParameterOut] =
    traversal.filter { _.index == num }

    /* get all parameters from (and including)
   * method parameter indexes are  based, i.e. first parameter has index  (that's how java2cpg generates it) */
  def indexFrom(num: Int): Iterator[MethodParameterOut] =
    traversal.filter(_.index >= num)

  /* get all parameters up to (and including)
   * method parameter indexes are  based, i.e. first parameter has index  (that's how java2cpg generates it) */
  def indexTo(num: Int): Iterator[MethodParameterOut] =
    traversal.filter(_.index <= num)

  def argument: Iterator[Expression] =
    for {
      paramOut <- traversal
      method = paramOut.method
      call <- method._callIn
      arg  <- call._argumentOut.collectAll[Expression]
      // TODO define 'parameterLinkIn' as named step in schema
      if paramOut._parameterLinkIn.collectAll[MethodParameterIn].index.headOption.contains(arg.argumentIndex)
    } yield arg

}
