import com.xcq1.Yaml2Kt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

class GenerateTests : DescribeSpec({
    val resources = Paths.get("src/test/resources")
    val testModule = File("test/src/main/kotlin")

    context("Test YAML generates output") {
        withData<Path>(Path::name, Files.list(resources).toList()) {
            Yaml2Kt(it.toFile(), testModule).convert()
        }
    }
})