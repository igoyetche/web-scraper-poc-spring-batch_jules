package com.example.articlescraper.service

import com.example.articlescraper.domain.Article
import com.example.articlescraper.repository.ArticleRepository
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/**
 * Service responsible for downloading individual article content, including HTML and hero images.
 *
 * It uses Jsoup for web scraping and includes retry mechanisms for network operations.
 * Interacts with [ArticleRepository] to check for existing articles.
 */
@Service
class ArticleDownloader(
    @Value("\${scraper.delayMs}") private val delayMs: Long,
    private val articleRepository: ArticleRepository
) {
    private val logger = LoggerFactory.getLogger(ArticleDownloader::class.java)

    companion object {
        private const val HERO_IMAGE_OG_SELECTOR = "meta[property=og:image]"
        private const val TITLE_OG_SELECTOR = "meta[property=og:title]"
        private const val TITLE_TAG_SELECTOR = "title"
    }

    /**
     * Downloads the content of an article from the given URL.
     *
     * If an article with the same URL already exists in the database, its `updatedAt` timestamp
     * is updated, and the existing article is returned. Otherwise, the article content
     * (HTML, title, hero image) is downloaded, a new [Article] object is created.
     * The ID for new articles is generated from a SHA-256 hash of the URL.
     *
     * @param url The URL of the article to download.
     * @return An [Article] object, either newly downloaded or existing and updated.
     * @throws Exception if downloading or processing fails despite retries.
     */
    fun downloadArticleContent(url: String): Article {
        logger.info("Processing article URL: $url")

        val existingArticle = articleRepository.findByUrl(url)
        if (existingArticle != null) {
            logger.info("Article with URL '$url' already exists. Updating timestamp.")
            return existingArticle.copy(updatedAt = Instant.now())
            // Not saving here, the batch writer will handle it.
        }

        logger.debug("Article with URL '$url' not found in DB. Proceeding to download.")
        Thread.sleep(delayMs) // Politeness delay before hitting the server

        val response = fetchHtmlContent(url)
        val rawHtml = response.body()
        val document = response.parse() // Use the parsed document from the response

        val title = document.selectFirst(TITLE_OG_SELECTOR)?.attr("content")
            ?: document.selectFirst(TITLE_TAG_SELECTOR)?.text()
            ?: "Title Not Found"
        logger.debug("Extracted title for $url: '$title'")

        var heroImageUrl: String? = null
        var heroImage: ByteArray? = null

        document.selectFirst(HERO_IMAGE_OG_SELECTOR)?.attr("content")?.let { imageUrl ->
            if (imageUrl.isNotBlank()) {
                heroImageUrl = imageUrl
                logger.info("Found hero image URL for $url: $heroImageUrl. Attempting to download.")
                try {
                    Thread.sleep(delayMs) // Politeness delay
                    heroImage = downloadHeroImage(heroImageUrl!!)
                    logger.info("Successfully downloaded hero image for $url. Size: ${heroImage?.size ?: 0} bytes.")
                } catch (e: Exception) {
                    logger.warn("Failed to download hero image from $heroImageUrl for article $url: ${e.message}", e)
                    // Continue without the image if download fails
                }
            }
        }

        val articleId = generateIdFromUrl(url)
        val now = Instant.now()

        return Article(
            id = articleId,
            url = url,
            title = title,
            rawHtml = rawHtml,
            heroImageUrl = heroImageUrl,
            heroImage = heroImage,
            createdAt = now,
            updatedAt = now
            // classification, translations, gcsUrl, publishedToPubSub will be handled by later steps/processes
        )
    }

    /**
     * Fetches the HTML content and connection response for a given URL using Jsoup.
     * Marked as `@Retryable` for resilience.
     *
     * @param url The URL to fetch.
     * @return A Jsoup [Connection.Response] object.
     * @throws Exception on network or parsing errors after retries.
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    internal fun fetchHtmlContent(url: String): Connection.Response {
        logger.debug("Fetching HTML content from: $url")
        return Jsoup.connect(url)
            .userAgent("ArticleScraperBot/1.0 (+https://example.com/bot-info)") // Be a good bot citizen
            .timeout(15000) // 15 seconds timeout
            .execute()
    }

    /**
     * Downloads the hero image bytes from the given URL.
     * Marked as `@Retryable` for resilience.
     *
     * @param imageUrl The URL of the image to download.
     * @return A [ByteArray] containing the image data.
     * @throws Exception if the download fails, the content type is not an image, or after retries.
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    internal fun downloadHeroImage(imageUrl: String): ByteArray {
        logger.debug("Downloading hero image from: $imageUrl")
        val response = Jsoup.connect(imageUrl)
            .ignoreContentType(true) // We'll check content type manually
            .userAgent("ArticleScraperBot/1.0 (Image Fetcher)")
            .timeout(20000) // 20 seconds timeout for images
            .execute()

        if (!response.contentType().startsWith("image/")) {
            throw Exception("Content type for $imageUrl is not an image: ${response.contentType()}")
        }
        return response.bodyAsBytes()
    }

    /**
     * Generates a SHA-256 hash of the URL to be used as a document ID.
     * @param url The URL string.
     * @return A hexadecimal string representation of the SHA-256 hash.
     */
    private fun generateIdFromUrl(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
