import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SimpleTest : ShouldSpec({
    context("plain string content") {
        should("be correct") {
            Simple.id shouldBe "Simple"
            Simple.doe shouldBe "a deer, a female deer"
            Simple.ray shouldBe "a drop of golden sun"
            Simple.pi shouldBe 3.14159
            Simple.xmas shouldBe true
            Simple.frenchHens shouldBe 3
        }
    }
    context("simple list content") {
        should("be generated as list") {
            Simple.callingBirds.shouldContainExactly("huey", "dewey", "louie", "fred")
        }
    }

    context("complex list content") {
        should("contain the correct contents") {
            Simple.emails.map { "From: ${it.from} To: ${it.to} Subject: ${it.subject}" }.shouldContainExactly(
                "From: a To: b Subject: short letters",
                "From: alice To: bob Subject: physics experiment",
                "From: kotlin-newsletter To: me Subject: kotlin",
            )
        }
    }

    context("object content") {
        should("be correct on first layer") {
            Simple.xmasFifthDay.id shouldBe "xmas-fifth-day"
            Simple.xmasFifthDay.callingBirds shouldBe "four"
            Simple.xmasFifthDay.frenchHens shouldBe 3
            Simple.xmasFifthDay.goldenRings shouldBe 5
            Simple.xmasFifthDay.turtleDoves shouldBe "two"
        }

        should("be correct on second layer") {
            Simple.xmasFifthDay.partridges.id shouldBe "partridges"
            Simple.xmasFifthDay.partridges.count shouldBe 1
            Simple.xmasFifthDay.partridges.location shouldBe "a pear tree"
        }
    }
})