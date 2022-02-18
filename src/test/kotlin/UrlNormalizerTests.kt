import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.integration.UrlNormalizer
import de.maxr1998.diskord.integration.resolver.sources.TwitterApi
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.codepoints
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

@Suppress("unused")
class UrlNormalizerTests : StringSpec({
    "Random property tests" {
        checkAll(iterations = 20, Arb.string(codepoints = Arb.codepoints())) { input ->
            // No changes (link in random content is unlikely)
            UrlNormalizer.normalizeUrls(input) shouldBe input
        }
    }
    "Twitter tests" {
        forAll(
            row("$TW_TEST_URL?format=jpg&name=large"),
            row("$TW_TEST_URL?format=jpg&name=small"),
            row("$TW_TEST_URL?format=jpg&name=500x500"),
            row("$TW_TEST_URL?format=jpg&name=orig"),
            row("$TW_TEST_URL.jpg?name=large"),
            row("$TW_TEST_URL.jpg:large"),
            row("$TW_TEST_URL.jpg"),
        ) { input ->
            UrlNormalizer.normalizeUrls(input) shouldBe "$TW_TEST_URL?format=jpg&name=orig"
        }
    }
    "Pinterest tests" {
        val input = "${Constants.PINTEREST_IMAGE_BASE_URL}/123x/ab/cd/ef/abcdef00000000000000000000000000.jpg"
        val result = "${Constants.PINTEREST_IMAGE_BASE_URL}/originals/ab/cd/ef/abcdef00000000000000000000000000.jpg"

        UrlNormalizer.normalizeUrls(input) shouldBe result
    }
}) {
    companion object {
        const val TW_TEST_URL = "${TwitterApi.TWITTER_IMAGE_BASE_URL}EXXXXXXXXXXXXXX"
    }
}