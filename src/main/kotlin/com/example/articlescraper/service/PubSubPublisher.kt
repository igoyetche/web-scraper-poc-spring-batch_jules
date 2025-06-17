package com.example.articlescraper.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * A stub client for publishing messages to Google Cloud Pub/Sub.
 *
 * This service simulates network calls to Pub/Sub.
 * It includes delays and retry mechanisms to mimic real-world behavior.
 */
@Service
class PubSubPublisher(
    @Value("\${gcp.pubsub.topic.name:your-default-topic}") private val topicName: String
) {
    private val logger = LoggerFactory.getLogger(PubSubPublisher::class.java)

    /**
     * Publishes a list of article IDs to the configured Pub/Sub topic (simulated).
     *
     * @param articleIds A list of article IDs to publish.
     * @return `true` if the "publication" was successful, `false` otherwise.
     * @throws InterruptedException if the simulated delay is interrupted.
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000)
    )
    fun publishArticleIds(articleIds: List<String>): Boolean {
        if (articleIds.isEmpty()) {
            logger.info("No article IDs provided to publish. Skipping.")
            return true
        }
        logger.info("Attempting to publish ${articleIds.size} article IDs to Pub/Sub topic [$topicName]: $articleIds")

        // Simulate network latency
        try {
            Thread.sleep(50)
        } catch (e: InterruptedException) {
            logger.warn("Pub/Sub publishing delay interrupted for topic [$topicName]. IDs: $articleIds", e)
            Thread.currentThread().interrupt()
            throw e // Re-throw for retry
        }

        // Simulate occasional failure for retry demonstration
        // if (System.currentTimeMillis() % 8 == 0 && articleIds.isNotEmpty()) {
        //     logger.warn("Simulating Pub/Sub publish failure for topic [$topicName]. IDs: $articleIds")
        //     throw RuntimeException("Simulated Pub/Sub publishing error")
        // }

        logger.info("Successfully 'published' ${articleIds.size} article IDs to topic [$topicName].")
        return true
    }

    /**
     * Publishes a single article ID to the configured Pub/Sub topic (simulated).
     *
     * @param articleId The article ID to publish.
     * @return `true` if the "publication" was successful, `false` otherwise.
     * @throws InterruptedException if the simulated delay is interrupted.
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, multiplier = 2.0, maxDelay = 3000) // Shorter delay for single ID
    )
    fun publishArticleId(articleId: String): Boolean {
        logger.info("Attempting to publish article ID [$articleId] to Pub/Sub topic [$topicName]")

        // Simulate network latency
        try {
            Thread.sleep(30) // Shorter sleep for single ID
        } catch (e: InterruptedException) {
            logger.warn("Pub/Sub publishing delay interrupted for topic [$topicName], ID: $articleId", e)
            Thread.currentThread().interrupt()
            throw e // Re-throw for retry
        }
        logger.info("Successfully 'published' article ID [$articleId] to topic [$topicName].")
        return true
    }
}
