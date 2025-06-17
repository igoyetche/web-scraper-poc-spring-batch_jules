package com.example.articlescraper.service

import com.example.articlescraper.domain.Article
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * A stub client for translating articles into different languages.
 *
 * This service simulates network calls to an external translation engine.
 * It includes delays and retry mechanisms to mimic real-world behavior.
 */
@Service
class TranslationClient {

    private val logger = LoggerFactory.getLogger(TranslationClient::class.java)

    companion object {
        /** List of target languages for translation. */
        val TARGET_LANGUAGES = listOf("es", "pt", "fr") // Spanish, Portuguese, French
    }

    /**
     * Translates the given article content into the specified target language.
     *
     * This is a stub implementation that simulates a network call. It returns dummy
     * translated text and includes a short delay.
     *
     * @param article The [Article] to translate (ID and title are used for dummy text).
     * @param language The target language code (e.g., "es", "fr").
     * @return A dummy translated string.
     * @throws InterruptedException if the simulated delay is interrupted.
     * @throws IllegalArgumentException if the language is not supported (for demo).
     */
    @Retryable(
        value = [Exception::class], // Retry on any exception
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 6000)
    )
    fun translateArticle(article: Article, language: String): String {
        logger.info("Attempting to translate article ID: ${article.id} ('${article.title}') to language: $language")

        if (!TARGET_LANGUAGES.contains(language)) {
            throw IllegalArgumentException("Unsupported language: $language")
        }

        // Simulate network latency
        try {
            Thread.sleep(150) // Slightly longer delay for translation
        } catch (e: InterruptedException) {
            logger.warn("Translation delay interrupted for article ID: ${article.id}, language: $language", e)
            Thread.currentThread().interrupt() // Restore interruption status
            throw e // Re-throw to allow retry or failure
        }

        // Simulate a failure occasionally for retry demonstration
        // if (System.currentTimeMillis() % 7 == 0) {
        // logger.warn("Simulating translation failure for article ID: ${article.id}, language: $language")
        // throw RuntimeException("Simulated translation service error for $language")
        // }

        val translatedText = "Translated content for '${article.title}' to $language. Original text hash: ${article.rawHtml.take(20).hashCode()}"
        logger.info("Article ID: ${article.id} successfully translated to $language.")
        return translatedText
    }

    /**
     * Determines which target languages require translation for the given article.
     *
     * It compares the [TARGET_LANGUAGES] with the keys already present in the
     * `article.translations` map.
     *
     * @param article The [Article] to check.
     * @return A list of language codes (e.g., "es", "fr") for which translations are still needed.
     *         Returns an empty list if all target languages are already translated or if the article
     *         has no translations map initialized (treated as needing all translations).
     */
    fun getRequiredTranslations(article: Article): List<String> {
        val existingTranslations = article.translations?.keys ?: emptySet()
        val needed = TARGET_LANGUAGES.filterNot { existingTranslations.contains(it) }
        if (needed.isNotEmpty()) {
            logger.debug("Article ID: ${article.id} requires translation for: $needed. Existing: $existingTranslations")
        } else {
            logger.debug("Article ID: ${article.id} has all required translations ($TARGET_LANGUAGES). Existing: $existingTranslations")
        }
        return needed
    }
}
