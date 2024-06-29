import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.example.Yaml2Kt
import java.io.File

class Test : DescribeSpec( {
    describe("simple") {
        it("works") {
            val targetFile = File("Simple.kt")
            Yaml2Kt(File("simple.yaml"), targetFile).convert()
            targetFile.readText() shouldBe """
                ...
            """.trimIndent()
        }
    }
})