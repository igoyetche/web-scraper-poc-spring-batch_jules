package com.example.articlescraper.repository

import com.example.articlescraper.domain.Article
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.stream.Stream

/**
 * Spring Data MongoDB repository for [Article] entities.
 */
interface ArticleRepository : MongoRepository<Article, String> {

    /**
     * Finds an article by its original URL.
     *
     * @param url The URL of the article to find.
     * @return The [Article] if found, otherwise null.
     */
    fun findByUrl(url: String): Article?

    /**
     * Finds all articles where the 'classification' field is null.
     *
     * This method is suitable for fetching a manageable number of articles that
     * need classification. For very large datasets, consider a streaming approach.
     *
     * @return A list of [Article] objects that have not yet been classified.
     */
    fun findByClassificationIsNull(): List<Article>

    /**
     * Finds all articles where the 'classification' field is not null.
     *
     * This method is used to fetch articles that are potentially ready for
     * subsequent processing steps like translation, as classification is a prerequisite.
     *
     * @return A list of [Article] objects that have a non-null classification.
     */
    fun findByClassificationIsNotNull(): List<Article>

    /**
     * Finds all articles that have been classified but do not yet have a GCS URL.
     *
     * This method is used to fetch articles that are ready to be uploaded to GCS.
     * It assumes that if `classification` is not null, preceding steps like translation
     * (if applicable to all classified items) have been attempted or completed.
     *
     * @return A list of [Article] objects that are classified and have a null `gcsUrl`.
     */
    fun findByClassificationIsNotNullAndGcsUrlIsNull(): List<Article>

    /**
     * Finds all articles that have a GCS URL but are not yet marked as published to Pub/Sub.
     *
     * This method is used to fetch articles that are ready to be announced via Pub/Sub.
     *
     * @return A list of [Article] objects that have a `gcsUrl` and `publishedToPubSub` is false.
     */
    fun findByGcsUrlIsNotNullAndPublishedToPubSubFalse(): List<Article>

    /**
     * Streams all articles where the 'classification' field is null.
     *
     * This method is preferable for processing large numbers of unclassified articles
     * as it avoids loading all of them into memory at once.
     * Note: Requires a transaction or careful handling of the stream if used in a
     * context that might close the underlying MongoDB cursor prematurely.
     *
     * @return A [Stream] of [Article] objects that have not yet been classified.
     */
    // fun streamByClassificationIsNull(): Stream<Article> // Example of streaming alternative
}
