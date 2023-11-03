package io.shiftleft.semanticcpg.language.android

import io.shiftleft.codepropertygraph.generated.v2.nodes.Local
import io.shiftleft.semanticcpg.language.*
import io.shiftleft.codepropertygraph.generated.v2.Language.*

class LocalTraversal(val traversal: Iterator[Local]) extends AnyVal {
  def callsEnableJS =
    traversal
      .where(
        _.referencingIdentifiers.inCall
          .nameExact("getSettings")
          .where(
            _.inCall
              .nameExact("setJavaScriptEnabled")
              .argument
              .isLiteral
              .codeExact("true")
          )
      )

  def loadUrlCalls =
    traversal.referencingIdentifiers.inCall.nameExact("loadUrl")

  def addJavascriptInterfaceCalls =
    traversal.referencingIdentifiers.inCall.nameExact("addJavascriptInterface")
}
