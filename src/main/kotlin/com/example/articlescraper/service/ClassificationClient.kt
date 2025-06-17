package com.example.articlescraper.service

import com.example.articlescraper.domain.Article
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * A stub client for classifying articles.
 *
 * This service simulates network calls to an external classification engine.
 * It includes a delay and retry mechanism to mimic real-world behavior.
 */
@Service
class ClassificationClient {

    private val logger = LoggerFactory.getLogger(ClassificationClient::class.java)
    private val classifications = listOf("Technology", "Software Development", "Cloud Computing", "AI/ML", "DevOps")
    private var lastUsedIndex = -1 // Simple way to cycle through classifications

    /**
     * Classifies the given article.
     *
     * This is a stub implementation that simulates a network call. It returns a dummy
     * classification and includes a short delay.
     *
     * @param article The [Article] to classify (ID and title are logged).
     * @return A dummy classification string.
     * @throws InterruptedException if the simulated delay is interrupted.
     */
    @Retryable(
        value = [Exception::class], // Retry on any exception
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000)
    )
    fun classifyArticle(article: Article): String {
        logger.info("Attempting to classify article ID: ${article.id}, Title: '${article.title}'")

        // Simulate network latency
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            logger.warn("Classification delay interrupted for article ID: ${article.id}", e)
            Thread.currentThread().interrupt() // Restore interruption status
            throw e // Re-throw to allow retry or failure
        }

        // Simulate a failure occasionally for retry demonstration (e.g., 1 in 5 times)
        // if (System.currentTimeMillis() % 5 == 0) {
        //     logger.warn("Simulating classification failure for article ID: ${article.id}")
        //     throw RuntimeException("Simulated classification service error")
        // }

        // Return a cycling dummy classification
        lastUsedIndex = (lastUsedIndex + 1) % classifications.size
        val classification = classifications[lastUsedIndex]
        logger.info("Article ID: ${article.id} classified as '$classification'")
        return classification
    }
}
