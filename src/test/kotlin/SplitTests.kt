import de.maxr1998.diskord.util.extension.splitWhitespaceNonEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

@Suppress("unused")
class SplitTests : StringSpec({
    "Generic split should return correct output" {
        with("%cmd arg1 arg2".splitWhitespaceNonEmpty()) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }
    }

    "Inputs without whitespace shouldn't split" {
        with("whatever".splitWhitespaceNonEmpty()) {
            size shouldBe 1
            get(0) shouldBe "whatever"
        }
    }

    "Inputs with repeated whitespaces should return correct output" {
        with("%cmd  arg1 arg2".splitWhitespaceNonEmpty()) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }

        with("%cmd   arg1 arg2".splitWhitespaceNonEmpty()) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }

        with("%cmd    arg1    arg2".splitWhitespaceNonEmpty()) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }
    }

    "Inputs with exotic whitespaces should return correct output" {
        with("%cmd\u3000arg1\u3000arg2".splitWhitespaceNonEmpty()) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }
    }

    "Limit higher than arg count should have no influence" {
        with("%cmd arg1 arg2".splitWhitespaceNonEmpty(10)) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }
    }

    "Limit matching arg count should have no influence" {
        with("%cmd arg1 arg2".splitWhitespaceNonEmpty(3)) {
            size shouldBe 3
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1"
            get(2) shouldBe "arg2"
        }
    }

    "Limit lower than arg count should properly cap output" {
        with("%cmd arg1 arg2 arg3".splitWhitespaceNonEmpty(2)) {
            size shouldBe 2
            get(0) shouldBe "%cmd"
            get(1) shouldBe "arg1 arg2 arg3"
        }
    }

    "Negative or zero limit should fail" {
        shouldThrow<IllegalArgumentException> {
            "whatever".splitWhitespaceNonEmpty(-1)
        }
        shouldThrow<IllegalArgumentException> {
            "whatever".splitWhitespaceNonEmpty(0)
        }
    }
})