package com.example.articlescraper.service

import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * Service responsible for fetching a list of article URLs from a target website.
 *
 * It iterates through paginated content, extracts article links, and handles
 * potential network issues with retries.
 */
@Service
class ListFetcher(
    @Value("\${scraper.delayMs}") private val delayMs: Long
) {
    private val logger = LoggerFactory.getLogger(ListFetcher::class.java)
    private val baseUrl = "https://thenewstack.io/page/"

    companion object {
        // This selector might need adjustment if the website structure changes.
        // It targets <a> tags within <article> elements that have a class containing "story".
        const val ARTICLE_LINK_SELECTOR = "article[class*='story'] h2.entry-title a[href]"
    }

    /**
     * Fetches a list of unique article URLs from "https://thenewstack.io".
     *
     * It paginates through the site, extracting article links from each page.
     * The process stops if a page returns a 404 error or if no articles are found on a page.
     * Includes a delay between page requests to be polite to the server.
     *
     * @return A list of unique article URLs.
     */
    fun fetchArticleUrls(): List<String> {
        val articleUrls = mutableSetOf<String>()
        var pageNum = 1
        var continueFetching = true

        logger.info("Starting to fetch article URLs from $baseUrl...")

        while (continueFetching) {
            val pageUrl = "$baseUrl$pageNum/"
            logger.info("Fetching URLs from page: $pageUrl")
            try {
                val newUrls = fetchUrlsFromPage(pageUrl)
                if (newUrls.isNotEmpty()) {
                    val addedCount = newUrls.count { articleUrls.add(it) }
                    logger.info("Found ${newUrls.size} URLs on page $pageNum. Added $addedCount new unique URLs.")
                    pageNum++
                } else {
                    logger.info("No article URLs found on page $pageNum. Stopping.")
                    continueFetching = false
                }
            } catch (e: HttpStatusException) {
                if (e.statusCode == 404) {
                    logger.warn("Page $pageUrl returned 404. Assuming end of pages. Stopping.")
                } else {
                    logger.error("HTTP error fetching page $pageUrl: ${e.statusCode}. Stopping.", e)
                }
                continueFetching = false
            } catch (e: Exception) { // Catching other exceptions that might occur during Jsoup.connect or parsing
                logger.error("Error fetching or parsing page $pageUrl. Stopping.", e)
                continueFetching = false
            }

            if (continueFetching && delayMs > 0) {
                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    logger.warn("Delay interrupted. Continuing...")
                    Thread.currentThread().interrupt() // Restore interruption status
                }
            }
        }

        logger.info("Finished fetching article URLs. Total unique URLs found: ${articleUrls.size}")
        return articleUrls.toList()
    }

    /**
     * Fetches and parses a single page to extract article URLs using Jsoup.
     * This method is marked as `@Retryable` to handle transient network issues.
     *
     * @param pageUrl The URL of the page to fetch.
     * @return A list of article URLs found on the page.
     * @throws HttpStatusException if the server returns an HTTP error status (e.g., 404).
     * @throws Exception for other network or parsing errors.
     */
    @Retryable(
        value = [Exception::class], // Retry on any exception during HTTP call/parsing
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    internal fun fetchUrlsFromPage(pageUrl: String): List<String> {
        logger.debug("Attempting to connect to: $pageUrl")
        val document = Jsoup.connect(pageUrl).get()
        val links = document.select(ARTICLE_LINK_SELECTOR)
        return links.mapNotNull { it.attr("abs:href") }.filter { it.isNotBlank() }
    }
}
