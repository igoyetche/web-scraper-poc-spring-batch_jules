package com.example.articlescraper.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Represents an article scraped from a website.
 *
 * @property id The unique identifier of the article in MongoDB.
 * @property url The original URL of the article.
 * @property title The title of the article.
 * @property rawHtml The raw HTML content of the article page.
 * @property heroImageUrl Optional URL of the article's hero image.
 * @property heroImage Optional byte array of the downloaded hero image.
 * @property classification Optional classification result for the article content.
 * @property translations Optional map of language codes to translated article text.
 * @property gcsUrl Optional URL of the uploaded JSON DTO in Google Cloud Storage.
 * @property publishedToPubSub Flag indicating if the article has been published to Pub/Sub. Defaults to false.
 * @property createdAt Timestamp of when the article record was created.
 * @property updatedAt Timestamp of when the article record was last updated.
 */
@Document(collection = "articles")
data class Article(
    @Id
    val id: String,
    val url: String,
    val title: String,
    val rawHtml: String,
    val heroImageUrl: String? = null,
    val heroImage: ByteArray? = null,
    val classification: String? = null,
    val translations: Map<String, String>? = null,
    val gcsUrl: String? = null,
    val publishedToPubSub: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    // Override equals and hashCode for ByteArray properties as default implementations are not suitable for them.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Article

        if (id != other.id) return false
        if (url != other.url) return false
        if (title != other.title) return false
        if (rawHtml != other.rawHtml) return false
        if (heroImageUrl != other.heroImageUrl) return false
        if (heroImage != null) {
            if (other.heroImage == null) return false
            if (!heroImage.contentEquals(other.heroImage)) return false
        } else if (other.heroImage != null) return false
        if (classification != other.classification) return false
        if (translations != other.translations) return false
        if (gcsUrl != other.gcsUrl) return false
        if (publishedToPubSub != other.publishedToPubSub) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + rawHtml.hashCode()
        result = 31 * result + (heroImageUrl?.hashCode() ?: 0)
        result = 31 * result + (heroImage?.contentHashCode() ?: 0)
        result = 31 * result + (classification?.hashCode() ?: 0)
        result = 31 * result + (translations?.hashCode() ?: 0)
        result = 31 * result + (gcsUrl?.hashCode() ?: 0)
        result = 31 * result + publishedToPubSub.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
