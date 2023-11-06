package io.shiftleft.semanticcpg

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.v2.nodes.*
import io.shiftleft.codepropertygraph.generated.v2.{EdgeKinds, Languages, ModifierTypes}
import io.shiftleft.codepropertygraph.generated.v2.Language.*
import io.shiftleft.passes.CpgPass
import io.shiftleft.semanticcpg.language.*
import io.joern.odb2.DiffGraphBuilder

package object testing {

  object MockCpg {

    def apply(): MockCpg = new MockCpg

    def apply(f: (DiffGraphBuilder, Cpg) => Unit): MockCpg = new MockCpg().withCustom(f)
  }

  case class MockCpg(cpg: Cpg = Cpg.emptyCpg) {

    def withMetaData(language: String = Languages.C): MockCpg = withMetaData(language, Nil)

    def withMetaData(language: String, overlays: List[String]): MockCpg = {
      withCustom { (diffGraph, _) =>
        diffGraph.addNode(NewMetaData().language(language).overlays(overlays))
      }
    }

    def withFile(filename: String): MockCpg =
      withCustom { (graph, _) =>
        graph.addNode(NewFile().name(filename))
      }

    def withNamespace(name: String, inFile: Option[String] = None): MockCpg =
      withCustom { (graph, _) =>
        {
          val namespaceBlock = NewNamespaceBlock().name(name)
          val namespace      = NewNamespace().name(name)
          graph.addNode(namespaceBlock)
          graph.addNode(namespace)
          graph.addEdge(namespaceBlock, namespace, EdgeKinds.REF)
          if (inFile.isDefined) {
            val fileNode = cpg.file(inFile.get).head
            graph.addEdge(namespaceBlock, fileNode, EdgeKinds.SOURCE_FILE)
          }
        }
      }

    def withTypeDecl(
      name: String,
      isExternal: Boolean = false,
      inNamespace: Option[String] = None,
      inFile: Option[String] = None
    ): MockCpg =
      withCustom { (graph, _) =>
        {
          val typeNode = NewType().name(name)
          val typeDeclNode = NewTypeDecl()
            .name(name)
            .fullName(name)
            .isExternal(isExternal)

          val member   = NewMember().name("amember")
          val modifier = NewModifier().modifierType(ModifierTypes.STATIC)

          graph.addNode(typeDeclNode)
          graph.addNode(typeNode)
          graph.addNode(member)
          graph.addNode(modifier)
          graph.addEdge(typeNode, typeDeclNode, EdgeKinds.REF)
          graph.addEdge(typeDeclNode, member, EdgeKinds.AST)
          graph.addEdge(member, modifier, EdgeKinds.AST)

          if (inNamespace.isDefined) {
            val namespaceBlock = cpg.namespaceBlock(inNamespace.get).head
            graph.addEdge(namespaceBlock, typeDeclNode, EdgeKinds.AST)
          }
          if (inFile.isDefined) {
            val fileNode = cpg.file(inFile.get).head
            graph.addEdge(typeDeclNode, fileNode, EdgeKinds.SOURCE_FILE)
          }
        }
      }

    def withMethod(
      name: String,
      external: Boolean = false,
      inTypeDecl: Option[String] = None,
      fileName: String = ""
    ): MockCpg =
      withCustom { (graph, _) =>
        val retParam  = NewMethodReturn().typeFullName("int").order(10)
        val param     = NewMethodParameterIn().order(1).index(1).name("param1")
        val paramType = NewType().name("paramtype")
        val paramOut  = NewMethodParameterOut().name("param1").order(1)
        val method =
          NewMethod().isExternal(external).name(name).fullName(name).signature("asignature").filename(fileName)
        val block    = NewBlock().typeFullName("int")
        val modifier = NewModifier().modifierType("modifiertype")

        graph.addNode(method)
        graph.addNode(retParam)
        graph.addNode(param)
        graph.addNode(paramType)
        graph.addNode(paramOut)
        graph.addNode(block)
        graph.addNode(modifier)
        graph.addEdge(method, retParam, EdgeKinds.AST)
        graph.addEdge(method, param, EdgeKinds.AST)
        graph.addEdge(param, paramOut, EdgeKinds.PARAMETER_LINK)
        graph.addEdge(method, block, EdgeKinds.AST)
        graph.addEdge(param, paramType, EdgeKinds.EVAL_TYPE)
        graph.addEdge(paramOut, paramType, EdgeKinds.EVAL_TYPE)
        graph.addEdge(method, modifier, EdgeKinds.AST)

        if (inTypeDecl.isDefined) {
          val typeDeclNode = cpg.typeDecl(inTypeDecl.get).head
          graph.addEdge(typeDeclNode, method, EdgeKinds.AST)
        }
      }

    def withTagsOnMethod(
      methodName: String,
      methodTags: List[(String, String)] = List(),
      paramTags: List[(String, String)] = List()
    ): MockCpg =
      withCustom { (graph, cpg) =>
        implicit val diffGraph: DiffGraphBuilder = graph
        methodTags.foreach { case (k, v) =>
          cpg.method(methodName).newTagNodePair(k, v).store()(diffGraph)
        }
        paramTags.foreach { case (k, v) =>
          cpg.method(methodName).parameter.newTagNodePair(k, v).store()(diffGraph)
        }
      }

    def withCallInMethod(methodName: String, callName: String, code: Option[String] = None): MockCpg =
      withCustom { (graph, cpg) =>
        val methodNode = cpg.method(methodName).head
        val blockNode  = methodNode.block
        val callNode   = NewCall().name(callName).code(code.getOrElse(callName))
        graph.addNode(callNode)
        graph.addEdge(blockNode, callNode, EdgeKinds.AST)
        graph.addEdge(methodNode, callNode, EdgeKinds.CONTAINS)
      }

    def withMethodCall(calledMethod: String, callingMethod: String, code: Option[String] = None): MockCpg =
      withCustom { (graph, cpg) =>
        val callingMethodNode = cpg.method(callingMethod).head
        val calledMethodNode  = cpg.method(calledMethod).head
        val callNode          = NewCall().name(calledMethod).code(code.getOrElse(calledMethod))
        graph.addEdge(callNode, calledMethodNode, EdgeKinds.CALL)
        graph.addEdge(callingMethodNode, callNode, EdgeKinds.CONTAINS)
      }

    def withLocalInMethod(methodName: String, localName: String): MockCpg =
      withCustom { (graph, cpg) =>
        val methodNode = cpg.method(methodName).head
        val blockNode  = methodNode.block
        val typeNode   = NewType().name("alocaltype")
        val localNode  = NewLocal().name(localName).typeFullName("alocaltype")
        graph.addNode(localNode)
        graph.addNode(typeNode)
        graph.addEdge(blockNode, localNode, EdgeKinds.AST)
        graph.addEdge(localNode, typeNode, EdgeKinds.EVAL_TYPE)
      }

    def withLiteralArgument(callName: String, literalCode: String): MockCpg = {
      withCustom { (graph, cpg) =>
        val callNode    = cpg.call(callName).head
        val methodNode  = callNode.method
        val literalNode = NewLiteral().code(literalCode)
        val typeDecl = NewTypeDecl()
          .name("ATypeDecl")
          .fullName("ATypeDecl")

        graph.addNode(typeDecl)
        graph.addNode(literalNode)
        graph.addEdge(callNode, literalNode, EdgeKinds.AST)
        graph.addEdge(methodNode, literalNode, EdgeKinds.CONTAINS)
      }
    }

    def withIdentifierArgument(callName: String, name: String, index: Int = 1): MockCpg =
      withArgument(callName, NewIdentifier().name(name).argumentIndex(index))

    def withCallArgument(callName: String, callArgName: String, code: String = "", index: Int = 1): MockCpg =
      withArgument(callName, NewCall().name(callArgName).code(code).argumentIndex(index))

    def withArgument(callName: String, newNode: NewNode): MockCpg = withCustom { (graph, cpg) =>
      val callNode   = cpg.call(callName).head
      val methodNode = callNode.method
      val typeDecl   = NewTypeDecl().name("abc")
      graph.addEdge(callNode, newNode, EdgeKinds.AST)
      graph.addEdge(callNode, newNode, EdgeKinds.ARGUMENT)
      graph.addEdge(methodNode, newNode, EdgeKinds.CONTAINS)
      graph.addEdge(newNode, typeDecl, EdgeKinds.REF)
      graph.addNode(newNode)
    }

    def withCustom(f: (DiffGraphBuilder, Cpg) => Unit): MockCpg = {
      val diffGraph = new DiffGraphBuilder
      f(diffGraph, cpg)
      class MyPass extends CpgPass(cpg) {
        override def run(builder: DiffGraphBuilder): Unit = {
          builder.absorb(diffGraph)
        }
      }
      new MyPass().createAndApply()
      this
    }
  }

}