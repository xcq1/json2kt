import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.memberProperties

class EdgecasesTest : ShouldSpec({
    context("nulls") {
        should("be handled correctly") {
            Edgecases._null shouldBe null
            Edgecases.nullList shouldContainExactly listOf(null)
            Edgecases.nullMapping._null shouldBe null
        }
    }
    context("empty collections") {
        should("be handled correctly") {
            Edgecases.emptyList.shouldBeEmpty()
            Edgecases.emptyMapping.id shouldBe "empty-mapping"
            (Edgecases.emptyMapping::class).memberProperties.map { it.name } shouldContainExactly listOf("id")
        }
    }

    context("mappings on numbers") {
        should("contain the correct contents") {
            Edgecases.mappingOnNumbers._3 shouldBe "three"
            Edgecases.mappingOnNumbers._2 shouldBe "two"
            Edgecases.mappingOnNumbers._1 shouldBe "one"
            Edgecases.mappingOnNumbers._0 shouldBe "we have lift-off"
        }
    }
})