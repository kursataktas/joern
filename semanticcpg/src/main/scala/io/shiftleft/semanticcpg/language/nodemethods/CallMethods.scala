package io.shiftleft.semanticcpg.language.nodemethods

import io.shiftleft.codepropertygraph.generated.v2.accessors.Lang.*
import io.shiftleft.codepropertygraph.generated.v2.nodes.*
import io.shiftleft.semanticcpg.language.*

class CallMethods(val node: Call) extends AnyVal {

  def isStatic: Boolean =
    node.dispatchType == "STATIC_DISPATCH"

  def isDynamic: Boolean =
    node.dispatchType == "DYNAMIC_DISPATCH"

  def receiver: Iterator[Expression] =
    node._receiverOut.collectAll[Expression]

  def arguments(index: Int): Iterator[Expression] =
    node._argumentOut.collect {
      case expr: Expression if expr.argumentIndex == index => expr
    }

  // TODO define as named step in the schema
  def argument: Iterator[Expression] =
    node._argumentOut.collectAll[Expression]

  def argument(index: Int): Expression =
    arguments(index).next

  def argumentOption(index: Int): Option[Expression] =
    node._argumentOut.collectFirst {
      case expr: Expression if expr.argumentIndex == index => expr
    }

  // TODO reimplement
//  override def location: NewLocation = {
//    LocationCreator(node, node.code, node.label, node.lineNumber, node.method)
//  }
}