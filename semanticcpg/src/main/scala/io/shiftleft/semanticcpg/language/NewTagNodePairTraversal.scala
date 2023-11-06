package io.shiftleft.semanticcpg.language

import io.shiftleft.codepropertygraph.generated.v2.EdgeKinds
import io.shiftleft.codepropertygraph.generated.v2.nodes.{NewNode, NewTagNodePair, StoredNode}
import io.joern.odb2.DiffGraphBuilder

class NewTagNodePairTraversal(traversal: Iterator[NewTagNodePair]) extends HasStoreMethod {
  override def store()(implicit diffGraph: DiffGraphBuilder): Unit = {
    traversal.foreach { tagNodePair =>
      val tag      = tagNodePair.tag
      val tagValue = tagNodePair.node
      diffGraph.addNode(tag.asInstanceOf[NewNode])
      tagValue match {
        case tagValue: StoredNode =>
          diffGraph.addEdge(tagValue, tag.asInstanceOf[NewNode], EdgeKinds.TAGGED_BY)
        case tagValue: NewNode =>
          diffGraph.addEdge(tagValue, tag.asInstanceOf[NewNode], EdgeKinds.TAGGED_BY, Nil)
      }
    }
  }
}