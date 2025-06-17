package com.example.articlescraper.service

import com.example.articlescraper.domain.Article
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

/**
 * A stub client for uploading article JSON data to a GCS bucket.
 *
 * This service simulates network calls to Google Cloud Storage.
 * It includes delays and retry mechanisms to mimic real-world behavior.
 * It uses Jackson ObjectMapper for serializing the Article object to JSON.
 */
@Service
class JsonUploader(
    @Value("\${gcs.bucket.name:your-default-bucket-name}") private val bucketName: String
) {

    private val logger = LoggerFactory.getLogger(JsonUploader::class.java)
    private val objectMapper: ObjectMapper = ObjectMapper().registerModules(KotlinModule.Builder().build(), JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)


    /**
     * Uploads the given [Article] object as a JSON string to a GCS bucket (simulated).
     *
     * This is a stub implementation. It serializes the article to JSON, logs the action,
     * simulates a network delay, and returns a dummy GCS URL.
     *
     * @param article The [Article] to upload.
     * @return A dummy GCS URL string for the "uploaded" file.
     * @throws InterruptedException if the simulated delay is interrupted.
     * @throws com.fasterxml.jackson.core.JsonProcessingException if JSON serialization fails.
     */
    @Retryable(
        value = [Exception::class], // Retry on any exception, including serialization or simulated network issues
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 6000)
    )
    fun uploadArticleJson(article: Article): String {
        val filename = "${article.id}.json"
        logger.info("Attempting to upload JSON for article ID: ${article.id} to GCS bucket '$bucketName' as '$filename'")

        // Serialize Article to JSON
        val articleJson = try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(article)
        } catch (e: Exception) {
            logger.error("Failed to serialize Article ID: ${article.id} to JSON: ${e.message}", e)
            throw e // Re-throw to trigger retry or fail the step item
        }

        logger.debug("Serialized JSON for article ID ${article.id}: \n$articleJson") // Log JSON for debugging

        // Simulate network latency for upload
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            logger.warn("GCS upload delay interrupted for article ID: ${article.id}", e)
            Thread.currentThread().interrupt() // Restore interruption status
            throw e // Re-throw to allow retry or failure
        }

        // Simulate an upload failure occasionally for retry demonstration
        // if (System.currentTimeMillis() % 10 == 0) {
        //     logger.warn("Simulating GCS upload failure for article ID: ${article.id}")
        //     throw RuntimeException("Simulated GCS upload error")
        // }

        val gcsUrl = "gs://$bucketName/articles/$filename"
        logger.info("Successfully 'uploaded' article ID: ${article.id} to $gcsUrl")
        return gcsUrl
    }
}
