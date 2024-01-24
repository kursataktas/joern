package io.shiftleft.semanticcpg.language.operatorextension

import flatgraph.help.{Doc, Traversal}
import io.shiftleft.codepropertygraph.generated.nodes
import io.shiftleft.codepropertygraph.generated.nodes.{Call, Expression}
import io.shiftleft.semanticcpg.language.*

@Traversal(elementType = classOf[Call])
class AssignmentTraversal(val traversal: Iterator[OpNodes.Assignment]) extends AnyVal {

  @Doc(info = "Left-hand sides of assignments")
  def target: Iterator[nodes.Expression] = traversal.map(_.target)

  @Doc(info = "Right-hand sides of assignments")
  def source: Iterator[nodes.Expression] = traversal.map(_.source)
}
