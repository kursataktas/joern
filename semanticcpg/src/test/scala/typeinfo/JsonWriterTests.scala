package typeinfo

import io.shiftleft.semanticcpg.typeinfo.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.Try

class JsonWriterTests extends AnyWordSpec with Matchers {
  private val test1: String =
    """
      |{
      | "FULL_NAME":"com.amazon.ion.IonFloat",
      | "NAME":"IonFloat",
      | "TYPE_PARAMETERS":[
      | ],
      | "INHERITS":[
      |   "java.lang.Cloneable"
      | ],
      | "METHODS":[
      |   {
      |     "NAME":"bigIntegerValue",
      |     "FULL_NAME":"com.amazon.ion.IonFloat.bigIntegerValue:java.math.BigInteger()",
      |     "SIGNATURE":"java.math.BigInteger()"
      |   }
      | ],
      | "MEMBERS":[
      |   {
      |     "NAME":"EMPTY_ARRAY",
      |     "TYPE_FULL_NAME":"com.amazon.ion.IonValue"
      |   }
      | ],
      | "DEPENDS":[
      |   {
      |     "FULL_NAME": "java.lang",
      |     "VERSION": "4.1.2"
      |   }
      | ]
      |}""".stripMargin

  "simple struct writer" should {
    "write same output as input from loader" in {
      val typ: Try[TypeDecl] = JsonLoader.parse(test1)
      typ.isSuccess shouldEqual true

      val output: Try[String] = JsonWriter.writeToString(typ.get)
      output.isSuccess shouldEqual true
      output.get shouldEqual test1
    }
  }
}
