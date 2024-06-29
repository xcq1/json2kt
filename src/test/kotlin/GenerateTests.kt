import io.kotest.core.spec.style.DescribeSpec
import org.example.Yaml2Kt
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class GenerateTests : DescribeSpec({
    describe("all test YAMLs") {
        it("generate output") {
            val resources = Paths.get("src/test/resources")
            val testModule = File("test/src/main/kotlin")
            Files.list(resources).forEach {
                Yaml2Kt(it.toFile(), testModule).convert()
            }
            println("You can now run the tests of the test module!")
        }
    }
})