package io.joern.swiftsrc2cpg.io

import better.files.File
import io.joern.x2cpg.utils.server.FrontendHTTPClient
import io.shiftleft.codepropertygraph.cpgloading.CpgLoader
import io.shiftleft.semanticcpg.language.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.parallel.CollectionConverters.RangeIsParallelizable
import scala.util.Failure
import scala.util.Success

class SwiftSrc2CpgHTTPServerTests extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private var port: Int = -1

  private def newProjectUnderTest(index: Option[Int] = None): File = {
    val dir  = File.newTemporaryDirectory("swiftsrc2cpgTestsHttpTest")
    val file = dir / "main.swift"
    file.createIfNotExists(createParents = true)
    val indexStr = index.map(_.toString).getOrElse("")
    file.writeText(s"""
      |func main() {
      |  println($indexStr)
      |}""".stripMargin)
    file.deleteOnExit()
    dir.deleteOnExit()
  }

  override def beforeAll(): Unit = {
    // Start server
    port = io.joern.swiftsrc2cpg.Main.startup()
  }

  override def afterAll(): Unit = {
    // Stop server
    io.joern.swiftsrc2cpg.Main.stop()
  }

  "Using swiftsrc2cpg in server mode" should {
    "build CPGs correctly (single test)" in {
      val cpgOutFile = File.newTemporaryFile("swiftsrc2cpg.bin")
      cpgOutFile.deleteOnExit()
      val projectUnderTest = newProjectUnderTest()
      val input            = projectUnderTest.path.toAbsolutePath.toString
      val output           = cpgOutFile.toString
      val client           = FrontendHTTPClient(port)
      val req              = client.buildRequest(Array(s"input=$input", s"output=$output"))
      client.sendRequest(req) match {
        case Failure(exception) => fail(exception.getMessage)
        case Success(out) =>
          out shouldBe output
          val cpg = CpgLoader.load(output)
          cpg.method.name.l should contain("main")
          cpg.call.code.l should contain("println()")
      }
    }

    "build CPGs correctly (multi-threaded test)" in {
      (0 until 10).par.foreach { index =>
        val cpgOutFile = File.newTemporaryFile("swiftsrc2cpg.bin")
        cpgOutFile.deleteOnExit()
        val projectUnderTest = newProjectUnderTest(Some(index))
        val input            = projectUnderTest.path.toAbsolutePath.toString
        val output           = cpgOutFile.toString
        val client           = FrontendHTTPClient(port)
        val req              = client.buildRequest(Array(s"input=$input", s"output=$output"))
        client.sendRequest(req) match {
          case Failure(exception) => fail(exception.getMessage)
          case Success(out) =>
            out shouldBe output
            val cpg = CpgLoader.load(output)
            cpg.method.name.l should contain("main")
            cpg.call.code.l should contain(s"println($index)")
        }
      }
    }
  }

}
