package io.joern.kotlin2cpg.querying

import io.joern.kotlin2cpg.testfixtures.KotlinCode2CpgFixture
import io.joern.x2cpg.Defines
import io.shiftleft.codepropertygraph.generated.DispatchTypes
import io.shiftleft.codepropertygraph.generated.EdgeTypes
import io.shiftleft.codepropertygraph.generated.EvaluationStrategies
import io.shiftleft.codepropertygraph.generated.ModifierTypes
import io.shiftleft.codepropertygraph.generated.edges.Capture
import io.shiftleft.codepropertygraph.generated.nodes.Binding
import io.shiftleft.codepropertygraph.generated.nodes.Block
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.shiftleft.codepropertygraph.generated.nodes.ClosureBinding
import io.shiftleft.codepropertygraph.generated.nodes.Local
import io.shiftleft.codepropertygraph.generated.nodes.MethodRef
import io.shiftleft.codepropertygraph.generated.nodes.Return
import io.shiftleft.codepropertygraph.generated.nodes.TypeDecl
import io.shiftleft.semanticcpg.language.*

class LambdaTests extends KotlinCode2CpgFixture(withOssDataflow = false, withDefaultJars = true) {
  "CPG for code with a simple lambda which captures a method parameter" should {
    val cpg = code("fun f1(p: String) { 1.let { println(p) } }")

    "should contain a single METHOD_REF node with a single CAPTURE edge" in {
      cpg.methodRef.size shouldBe 1
      cpg.methodRef.outE.collectAll[Capture].size shouldBe 1
    }

    "should contain a LOCAL node for the captured method parameter" in {
      val List(l) = cpg.local.nameExact("p").l
      l.typeFullName shouldBe "java.lang.String"
    }

    "should contain a CLOSURE_BINDING node for the captured parameter with the correct props set" in {
      val List(cb) = cpg.all.collectAll[ClosureBinding].l
      cb.closureOriginalName shouldBe Some("p")
      cb.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      cb.closureBindingId should not be None

      cb._refOut.size shouldBe 1
    }

    "should contain a CALL node for the `let` invocation" in {
      val List(c) = cpg.call.code("1.let.*").l
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
      c.methodFullName shouldBe "kotlin.let:java.lang.Object(java.lang.Object,kotlin.Function1)"
      c.signature shouldBe "java.lang.Object(java.lang.Object,kotlin.Function1)"
    }
  }

  "CPG for code with a simple lambda which captures a local" should {
    val cpg = code("""
        |fun foo(x: String) {
        |    val baz: String = "BAZ"
        |    1.let { println(baz) }
        |}
        |""".stripMargin)

    "should contain a METHOD_REF node with a non-null CODE prop set" in {
      cpg.methodRef.size shouldBe 1
      val List(mr) = cpg.methodRef.l
      mr.code should not be null
    }

    "should contain two CAPTURE edges for the METHOD_REF" in {
      cpg.methodRef.outE.collectAll[Capture].size shouldBe 2
    }

    "should contain a LOCAL node for the captured `baz`" in {
      cpg.local.code("baz").size shouldBe 2
    }

    "should contain a CLOSURE_BINDING node for captured `baz` with the correct props set" in {
      val List(cb) = cpg.all.collectAll[ClosureBinding].filter(_.closureOriginalName.getOrElse("") == "baz").l
      cb.closureOriginalName shouldBe Some("baz")
      cb.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      cb.closureBindingId should not be None

      cb._refOut.size shouldBe 1
    }
  }

  "CPG for code with a call with a call to `map` on a list of string" should {
    val cpg = code("""
        |package mypkg
        |fun f2(p: String) {
        |    val m = listOf(p, "ls", "ps")
        |    val o = m.map { i -> i + "_cmd" }
        |    o.forEach { i  -> println(i) }
        |}
        |""".stripMargin)

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*0.*").l
      m.fullName shouldBe s"mypkg.f2.${Defines.ClosurePrefix}0:java.lang.String(java.lang.String)"
      m.signature shouldBe "java.lang.String(java.lang.String)"
    }
  }

  "CPG for code with a list iterator lambda" should {
    val cpg = code("""
        |package mypkg
        |
        |fun foo(x: String): Int {
        |    val l = listOf("one", x)
        |    l.forEach { arg ->
        |        println(arg)
        |    }
        |    return 1
        |}
        |""".stripMargin)

    "should contain a CALL node for `println`" in {
      val callsWithPrintln = cpg.call.code(".*println.*").code.toSet
      callsWithPrintln should contain("println(arg)")
      callsWithPrintln.exists(_.trim.startsWith("l.forEach")) shouldBe true
    }

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.fullName shouldBe s"mypkg.foo.${Defines.ClosurePrefix}0:void(java.lang.String)"
      m.signature shouldBe "void(java.lang.String)"
      m.lineNumber shouldBe Some(6)
      m.columnNumber shouldBe Some(14)
    }

    "should contain a METHOD node for the lambda with a corresponding METHOD_RETURN which has the correct props set" in {
      val List(mr) = cpg.method.fullName(".*lambda.*").methodReturn.l
      mr.evaluationStrategy shouldBe EvaluationStrategies.BY_VALUE
      mr.typeFullName shouldBe "java.lang.Object"
      mr.lineNumber shouldBe Some(6)
      mr.columnNumber shouldBe Some(14)
    }

    "should contain a METHOD node for the lambda with a corresponding MODIFIER which has the correct props set" in {
      val List(mod1, mod2) = cpg.method.fullName(".*lambda.*").modifier.l
      mod1.modifierType shouldBe ModifierTypes.VIRTUAL
      mod2.modifierType shouldBe ModifierTypes.LAMBDA
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with the correct properties set" in {
      val List(p) = cpg.method.fullName(".*lambda.*").parameter.l
      p.code shouldBe "arg"
      p.lineNumber shouldBe Some(6)
      p.columnNumber shouldBe Some(16)
      p.typeFullName shouldBe "java.lang.String"
    }

    "should contain a CALL node for `forEach` with the correct properties set" in {
      val List(c) = cpg.call.methodFullName(".*forEach.*").l
      c.methodFullName shouldBe "kotlin.collections.forEach:void(java.lang.Iterable,kotlin.Function1)"
      c.signature shouldBe "void(java.lang.Iterable,kotlin.Function1)"
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
      c.lineNumber shouldBe Some(6)
      c.columnNumber shouldBe Some(4)

      val List(firstArg, secondArg) = cpg.call.methodFullName(".*forEach.*").argument.l
      firstArg.argumentIndex shouldBe 1
      secondArg.argumentIndex shouldBe 2
    }

    "should contain a TYPE_DECL node for the lambda with the correct props set" in {
      val List(td) = cpg.typeDecl.fullName(".*lambda.*").l
      td.isExternal shouldBe false
      td.code shouldBe "LAMBDA_TYPE_DECL"
      td.inheritsFromTypeFullName shouldBe Seq("kotlin.Function1")
      Option(td.astParent).isDefined shouldBe true

      val List(bm) = cpg.typeDecl.fullName(".*lambda.*").boundMethod.dedup.l
      bm.fullName shouldBe s"mypkg.foo.${Defines.ClosurePrefix}0:void(java.lang.String)"
      bm.name shouldBe s"${Defines.ClosurePrefix}0"

      val List(b1, b2) = bm.referencingBinding.l
      b1.signature shouldBe "void(java.lang.String)"
      b1.name shouldBe "invoke"
      b2.signature shouldBe "java.lang.Object(java.lang.Object)"
      b2.name shouldBe "invoke"
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with referencing identifiers" in {
      cpg.method.fullName(".*lambda.*").parameter.referencingIdentifiers.size shouldBe 1
    }
  }

  "CPG for code with a scope function lambda with implicit parameter" should {
    val cpg = code("""
        |package mypkg
        |fun f3(p: String): String {
        |    val out = p.apply { println(this) }
        |    return out
        |}
        ||""".stripMargin)

    "should contain a METHOD_PARAMETER_IN for the lambda with the correct properties set" in {
      val List(p) = cpg.method.fullName(".*lambda.*").parameter.l
      p.code shouldBe "this"
      p.typeFullName shouldBe "ANY"
      p.index shouldBe 1
    }
  }

  "CPG for code containing a lambda with parameter destructuring" should {
    val cpg = code("""|package mypkg
        |
        |fun f1(p: String) {
        |    val m = mapOf(p to 1, "two" to 2, "three" to 3)
        |    m.forEach { (k, v) ->
        |        println(k)
        |    }
        |}
        |""".stripMargin)

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.fullName shouldBe s"mypkg.f1.${Defines.ClosurePrefix}0:void(java.util.Map$$Entry)"
      m.signature shouldBe "void(java.util.Map$Entry)"
    }

    "should contain METHOD_PARAMETER_IN nodes for the lambda with the correct properties set" in {
      val List(p1, p2) = cpg.method.fullName(".*lambda.*").parameter.l
      p1.code shouldBe "k"
      p1.index shouldBe 1
      p1.typeFullName shouldBe "java.lang.String"
      p2.code shouldBe "v"
      p2.index shouldBe 2
      p2.typeFullName shouldBe "int"
    }
  }

  "CPG for code containing a lambda with parameter destructuring and an `_` entry" should {
    val cpg = code("""
      |package mypkg
      |
      |fun f1(p: String) {
      |    val m = mapOf(p to 1, "two" to 2, "three" to 3)
      |    m.forEach { (k, _) ->
      |        println(k)
      |    }
      |}
      |""".stripMargin)

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.fullName shouldBe s"mypkg.f1.${Defines.ClosurePrefix}0:void(java.util.Map$$Entry)"
      m.signature shouldBe "void(java.util.Map$Entry)"
    }

    "should contain one METHOD_PARAMETER_IN node for the lambda with the correct properties set" in {
      val List(p1) = cpg.method.fullName(".*lambda.*").parameter.l
      p1.code shouldBe "k"
      p1.index shouldBe 1
      p1.typeFullName shouldBe "java.lang.String"
    }
  }

  "CPG for code with a scope function lambda" should {
    val cpg = code("""
        |package mypkg
        |
        |fun throughTakeIf(x: String) {
        |  x.takeIf { arg -> arg.length > 1 }
        |}
        |""".stripMargin)

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.fullName shouldBe s"mypkg.throughTakeIf.${Defines.ClosurePrefix}0:boolean(java.lang.String)"
      m.signature shouldBe "boolean(java.lang.String)"
    }

    "should contain a METHOD node for the lambda with a corresponding METHOD_RETURN which has the correct props set" in {
      val List(mr) = cpg.method.fullName(".*lambda.*").methodReturn.l
      mr.evaluationStrategy shouldBe EvaluationStrategies.BY_VALUE
      mr.typeFullName shouldBe "java.lang.Object"
    }

    "should contain a METHOD node for the lambda with a corresponding MODIFIER which has the correct props set" in {
      val List(mod1, mod2) = cpg.method.fullName(".*lambda.*").modifier.l
      mod1.modifierType shouldBe ModifierTypes.VIRTUAL
      mod2.modifierType shouldBe ModifierTypes.LAMBDA
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with the correct properties set" in {
      val List(p) = cpg.method.fullName(".*lambda.*").parameter.l
      p.code shouldBe "arg"
      p.typeFullName shouldBe "java.lang.String"
      p.index shouldBe 1
    }

    "should contain a CALL node for `takeIf` with the correct properties set" in {
      val List(c) = cpg.call.code("x.takeIf.*").l
      c.methodFullName shouldBe "kotlin.takeIf:java.lang.Object(java.lang.Object,kotlin.Function1)"
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
      c.typeFullName shouldBe "java.lang.String"
      c.signature shouldBe "java.lang.Object(java.lang.Object,kotlin.Function1)"
    }

    "should contain a RETURN node around as the last child of the lambda's BLOCK" in {
      val List(b: Block) = cpg.method.fullName(".*lambda.*").block.l
      val hasReturnAsLastChild = b.astChildren.last match {
        case _: Return => true
        case _         => false
      }
      hasReturnAsLastChild shouldBe true
    }

    "should contain a TYPE_DECL node for the lambda with the correct props set" in {
      val List(td) = cpg.typeDecl.fullName(".*lambda.*").l
      td.isExternal shouldBe false
      td.code shouldBe "LAMBDA_TYPE_DECL"
      Option(td.astParent).isDefined shouldBe true

      val List(bm) = cpg.typeDecl.fullName(".*lambda.*").boundMethod.dedup.l
      bm.fullName shouldBe s"mypkg.throughTakeIf.${Defines.ClosurePrefix}0:boolean(java.lang.String)"
      bm.name shouldBe s"${Defines.ClosurePrefix}0"

      val List(b1, b2) = bm.referencingBinding.l
      b1.signature shouldBe "boolean(java.lang.String)"
      b1.name shouldBe "invoke"
      b2.signature shouldBe "java.lang.Object(java.lang.Object)"
      b2.name shouldBe "invoke"
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with referencing identifiers" in {
      cpg.method.fullName(".*lambda.*").parameter.referencingIdentifiers.size shouldBe 1
    }
  }

  "CPG for code with a lambda mapping values of a collection" should {
    val cpg = code("""
        |package mypkg
        |
        |fun mappedListWith(p: String): List<String> {
        |    val col = listOf("one", p)
        |    val mappedCol = col.map { arg ->
        |          arg + "MAPPED"
        |      }
        |    return mappedCol
        |}
        |""".stripMargin)

    "should contain a METHOD node for the lambda the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*0.*").l
      m.fullName shouldBe s"mypkg.mappedListWith.${Defines.ClosurePrefix}0:java.lang.String(java.lang.String)"
      m.signature shouldBe "java.lang.String(java.lang.String)"
      m.lineNumber shouldBe Some(6)
      m.columnNumber shouldBe Some(28)
    }

    "should contain a METHOD node for the lambda with a corresponding METHOD_RETURN which has the correct props set" in {
      val List(mr) = cpg.method.fullName(".*lambda.*").methodReturn.l
      mr.evaluationStrategy shouldBe EvaluationStrategies.BY_VALUE
      mr.typeFullName shouldBe "java.lang.Object"
      mr.lineNumber shouldBe Some(6)
      mr.columnNumber shouldBe Some(28)
    }

    "should contain a METHOD node for the lambda with a corresponding MODIFIER which has the correct props set" in {
      val List(mod1, mod2) = cpg.method.fullName(".*lambda.*").modifier.l
      mod1.modifierType shouldBe ModifierTypes.VIRTUAL
      mod2.modifierType shouldBe ModifierTypes.LAMBDA
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with the correct properties set" in {
      val List(p) = cpg.method.fullName(".*lambda.*").parameter.l
      p.code shouldBe "arg"
      p.typeFullName shouldBe "java.lang.String"
    }

    "should contain a CALL node for `map` with the correct properties set" in {
      val List(c) = cpg.call.methodFullName(".*map.*").take(1).l
      c.methodFullName shouldBe "kotlin.collections.map:java.util.List(java.lang.Iterable,kotlin.Function1)"
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
      c.lineNumber shouldBe Some(6)
      c.columnNumber shouldBe Some(20)
    }

    "should contain a TYPE_DECL node for the lambda with the correct props set" in {
      val List(td) = cpg.typeDecl.fullName(".*lambda.*").l
      td.isExternal shouldBe false
      td.code shouldBe "LAMBDA_TYPE_DECL"

      val List(bm) = cpg.typeDecl.fullName(".*lambda.*").boundMethod.dedup.l
      bm.fullName shouldBe s"mypkg.mappedListWith.${Defines.ClosurePrefix}0:java.lang.String(java.lang.String)"
      bm.name shouldBe s"${Defines.ClosurePrefix}0"

      val List(b1, b2) = bm.referencingBinding.l
      b1.signature shouldBe "java.lang.String(java.lang.String)"
      b1.name shouldBe "invoke"
      b2.signature shouldBe "java.lang.Object(java.lang.Object)"
      b2.name shouldBe "invoke"
    }

    "should contain a METHOD_PARAMETER_IN for the lambda with referencing identifiers" in {
      cpg.method.fullName(".*lambda.*").parameter.referencingIdentifiers.size shouldBe 1
    }
  }

  "CPG for code with destructuring inside lambda" should {
    val cpg = code("""
       |package mypkg
       |
       |fun main(args: Array<String>) {
       |    val map = mapOf("one" to 1, "two" to 2, "three" to 3)
       |    map.mapValues { (key, value) -> "$value!" }
       |}
       |""".stripMargin)

    "should not contain any method parameters with an empty name" in {
      cpg.method.parameter.filter { p => p.name == null }.method.fullName.l shouldBe Seq()
    }
  }

  "CPG for code with a simple lambda which captures a method parameter inside method" should {
    val cpg = code("""
        |package mypkg
        |
        |class AClass {
        |    fun doSomething(x: String) {
        |        1.let {
        |            println(x)
        |        }
        |    }
        |}
        |
        |""".stripMargin)

    "should contain CALL node for println" in {
      cpg.call.code("print.*").size shouldBe 1
    }

    "should contain a METHOD node for the lambda with the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.signature shouldBe "void(int)"
    }
  }

  "CPG for code with a simple lambda which captures a method parameter, nested twice" should {
    val cpg = code("""
      |package mypkg
      |
      |fun foo(x: String): Int {
      |    1.let {
      |      2.let {
      |        println(x)
      |      }
      |    }
      |   return 0
      |}
      |""".stripMargin)

    "should contain two METHOD nodes representing the lambdas" in {
      cpg.method.fullName(".*lambda.*").size shouldBe 2
    }

    "should contain a METHOD node for the second lambda with the correct props set" in {
      val List(m) = cpg.method.fullName(".*lambda.*1.*").l
      m.signature shouldBe "void(int)"
    }

    "should contain METHOD_REF nodes with the correct props set" in {
      val List(firstMethodRef: MethodRef, secondMethodRef: MethodRef) = cpg.methodRef.l
      firstMethodRef.out(EdgeTypes.CAPTURE).size shouldBe 1
      secondMethodRef.out(EdgeTypes.CAPTURE).size shouldBe 2
    }

    "should contain three CLOSURE_BINDING nodes" in {
      cpg.all.collectAll[ClosureBinding].size shouldBe 3
    }
  }

  "CPG for code with call with lambda inside method declaration" should {
    val cpg = code("""
        |package mypkg
        |
        |import kotlin.random.Random
        |
        |class AClass() {
        |    fun check(col: List<Int?>) {
        |        val rand = Random.nextInt(0, 100)
        |        when (rand) {
        |            1 -> println("1!")
        |            2 -> println("2!")
        |            else -> {
        |                val filtered = col.all { entry -> entry != null }
        |                println(filtered)
        |            }
        |        }
        |    }
        |}
        |
        |fun main() {
        |    val list = listOf(1, 2, 3)
        |    val a = AClass()
        |    a.check(list)
        |    println("SCOPE")
        |}
        |""".stripMargin)

    "should a METHOD node for the lambda" in {
      cpg.method.fullName(".*lambda.*").size shouldBe 1
    }
  }

  "CPG for code with lambda with no statements in its block" should {
    val cpg = code("""
        |package mypkg
        |fun nopnopnopnopnopnopnopnop() {
        |    1.let { _ -> }
        |}
        |""".stripMargin)
    "contain a METHOD node for the lambda" in {
      cpg.method.fullName(".*lambda.*").l should not be empty
    }
  }

  "CPG for code with `lazy` lambda call" should {
    val cpg = code("""
        |package mypkg
        |fun f1(p: String) {
        |    val l1 = lazy { p }
        |    println(l1.value)
        |}
        |""".stripMargin)

    "contain a METHOD node for the lambda with the correct signature" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.signature shouldBe "java.lang.String()"
    }

    "contain a BINDING node for the lambda with the correct signature" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      val List(b1, b2) = m.referencingBinding.l
      b1.signature shouldBe "java.lang.String()"
      b1.name shouldBe "invoke"
      b2.signature shouldBe "java.lang.Object()"
      b2.name shouldBe "invoke"
    }
  }

  "CPG for code with non-scope-function single-parameter lambda call" should {
    val cpg = code("""
        |package mypkg
        |fun f1(p: String) {
        |    val r = Result.success(p)
        |    r.onSuccess { println(it) }
        |}
        |""".stripMargin)

    "contain a METHOD node for the lambda with a PARAMETER with implicit parameter name" in {
      val List(m) = cpg.method.fullName(".*lambda.*").l
      m.signature shouldBe "void(java.lang.String)"
      val List(p) = m.parameter.l
      p.name shouldBe "it"
    }
  }

  "test nested lambda full names" in {
    val cpg = code("""
                     |package mypkg
                     |val x = { i: Int ->
                     |  val y = { j: Int ->
                     |    j
                     |  }
                     |}
                     |""".stripMargin)
    val List(m1, m2) = cpg.method.fullName(".*lambda..*").l
    m1.fullName shouldBe s"mypkg.x.${Defines.ClosurePrefix}0:void(int)"
    m2.fullName shouldBe s"mypkg.x.${Defines.ClosurePrefix}0.${Defines.ClosurePrefix}1:int(int)"
  }

  "CPG for code with lambda directly used as argument for interface parameter" should {
    val cpg = code("""
                     |package mypkg
                     |open class AAA
                     |class BBB: AAA()
                     |fun interface SomeInterface<T: AAA> {
                     |  fun method(param: T): T
                     |}
                     |fun interfaceUser(someInterface: SomeInterface<BBB>) {}
                     |fun invoke() {
                     |  interfaceUser { obj -> obj }
                     |}
                     |""".stripMargin)

    "contain correct lambda, bindings and type decl nodes" in {
      val List(lambdaMethod) = cpg.method.fullName(".*lambda.*").l
      lambdaMethod.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      lambdaMethod.signature shouldBe "mypkg.BBB(mypkg.BBB)"

      val List(lambdaTypeDecl) = lambdaMethod.bindingTypeDecl.dedup.l
      lambdaTypeDecl.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0"
      lambdaTypeDecl.inheritsFromTypeFullName should contain theSameElementsAs(List("mypkg.SomeInterface"))

      val List(binding1, binding2) = lambdaMethod.referencingBinding.l
      binding1.name shouldBe "method"
      binding1.signature shouldBe "mypkg.BBB(mypkg.BBB)"
      binding1.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding1.bindingTypeDecl shouldBe lambdaTypeDecl
      binding2.name shouldBe "method"
      binding2.signature shouldBe "mypkg.AAA(mypkg.AAA)"
      binding2.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding2.bindingTypeDecl shouldBe lambdaTypeDecl
    }
  }

  "CPG for code with wrapped lambda used as argument for interface parameter" should {
    val cpg = code("""
                     |package mypkg
                     |open class AAA
                     |class BBB: AAA()
                     |fun interface SomeInterface<T: AAA> {
                     |  fun method(param: T): T
                     |}
                     |fun interfaceUser(someInterface: SomeInterface<BBB>) {}
                     |fun invoke() {
                     |  interfaceUser(SomeInterface{ obj -> obj })
                     |}
                     |""".stripMargin)

    "contain correct lambda, bindings and type decl nodes" in {
      val List(lambdaMethod) = cpg.method.fullName(".*lambda.*").l
      lambdaMethod.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      lambdaMethod.signature shouldBe "mypkg.BBB(mypkg.BBB)"

      val List(lambdaTypeDecl) = lambdaMethod.bindingTypeDecl.dedup.l
      lambdaTypeDecl.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0"
      lambdaTypeDecl.inheritsFromTypeFullName should contain theSameElementsAs(List("mypkg.SomeInterface"))

      val List(binding1, binding2) = lambdaMethod.referencingBinding.l
      binding1.name shouldBe "method"
      binding1.signature shouldBe "mypkg.BBB(mypkg.BBB)"
      binding1.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding1.bindingTypeDecl shouldBe lambdaTypeDecl
      binding2.name shouldBe "method"
      binding2.signature shouldBe "mypkg.AAA(mypkg.AAA)"
      binding2.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding2.bindingTypeDecl shouldBe lambdaTypeDecl
    }
  }

  "CPG for code with wrapped lambda assigned to local variable" should {
    val cpg = code("""
                     |package mypkg
                     |open class AAA
                     |class BBB: AAA()
                     |fun interface SomeInterface<T: AAA> {
                     |  fun method(param: T): T
                     |}
                     |fun invoke() {
                     |  val aaa: SomeInterface<BBB> = SomeInterface{ obj -> obj }
                     |}
                     |""".stripMargin)

    "contain correct lambda, bindings and type decl nodes" in {
      val List(lambdaMethod) = cpg.method.fullName(".*lambda.*").l
      lambdaMethod.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      lambdaMethod.signature shouldBe "mypkg.BBB(mypkg.BBB)"

      val List(lambdaTypeDecl) = lambdaMethod.bindingTypeDecl.dedup.l
      lambdaTypeDecl.fullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0"
      lambdaTypeDecl.inheritsFromTypeFullName should contain theSameElementsAs(List("mypkg.SomeInterface"))

      val List(binding1, binding2) = lambdaMethod.referencingBinding.l
      binding1.name shouldBe "method"
      binding1.signature shouldBe "mypkg.BBB(mypkg.BBB)"
      binding1.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding1.bindingTypeDecl shouldBe lambdaTypeDecl
      binding2.name shouldBe "method"
      binding2.signature shouldBe "mypkg.AAA(mypkg.AAA)"
      binding2.methodFullName shouldBe s"mypkg.invoke.${Defines.ClosurePrefix}0:mypkg.BBB(mypkg.BBB)"
      binding2.bindingTypeDecl shouldBe lambdaTypeDecl
    }
  }
}
