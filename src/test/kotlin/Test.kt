import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.example.Yaml2Kt
import java.io.File

class Test : DescribeSpec( {
    describe("simple") {
        it("works") {
            val targetDir = File("build/test").also {it.mkdirs()}
            Yaml2Kt(File("src/test/resources/simple.yaml"), targetDir).convert()
            targetDir.resolve("Simple.kt").readText() shouldBe """
                import kotlin.Boolean
                import kotlin.Double
                import kotlin.Int
                import kotlin.String
                
                public object Simple {
                  public val id: String = "Simple"
                
                  public val doe: String = "a deer, a female deer"
                
                  public val ray: String = "a drop of golden sun"
                
                  public val pi: Double = 3.14159
                
                  public val xmas: Boolean = true
                
                  public val frenchHens: Int = 3
                
                  public val callingBirds: CallingBirds = CallingBirds("calling-birds", "huey", "dewey", "louie",
                      "fred")
                
                  public val xmasFifthDay: XmasFifthDay = XmasFifthDay("xmas-fifth-day", "four", 3, 5,
                      Partridges("partridges", 1, "a pear tree"), "two")
                
                  public data class CallingBirds private constructor(
                    public val id: String,
                    public val `0`: String,
                    public val `1`: String,
                    public val `2`: String,
                    public val `3`: String,
                  )
                
                  public data class XmasFifthDay private constructor(
                    public val id: String,
                    public val callingBirds: String,
                    public val frenchHens: Int,
                    public val goldenRings: Int,
                    public val partridges: Partridges,
                    public val turtleDoves: String,
                  ) {
                    public data class Partridges private constructor(
                      public val id: String,
                      public val count: Int,
                      public val location: String,
                    )
                  }
                }

            """.trimIndent()
        }
    }
})