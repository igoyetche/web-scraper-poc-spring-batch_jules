package com.example.articlescraper.controller

import com.example.articlescraper.domain.Article
import com.example.articlescraper.repository.ArticleRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for accessing article data.
 *
 * Provides endpoints to retrieve all articles or a specific article by its ID.
 *
 * @property articleRepository Repository for accessing article data.
 */
@RestController
@RequestMapping("/api/articles")
class ArticleController(private val articleRepository: ArticleRepository) {

    private val logger = LoggerFactory.getLogger(ArticleController::class.java)

    /**
     * Retrieves all articles currently stored in the database.
     *
     * @return A list of all [Article] objects.
     */
    @GetMapping
    fun getAllArticles(): List<Article> {
        logger.info("GET /api/articles - Retrieving all articles.")
        return articleRepository.findAll()
    }

    /**
     * Retrieves a specific article by its unique ID.
     *
     * @param id The ID of the article to retrieve.
     * @return A [ResponseEntity] containing the [Article] if found (HTTP 200),
     *         or a Not Found response (HTTP 404) if no article with the given ID exists.
     */
    @GetMapping("/{id}")
    fun getArticleById(@PathVariable id: String): ResponseEntity<Article> {
        logger.info("GET /api/articles/$id - Retrieving article by ID.")
        val article = articleRepository.findById(id)
        return if (article.isPresent) {
            logger.info("Article with ID $id found.")
            ResponseEntity.ok(article.get())
        } else {
            logger.warn("Article with ID $id not found.")
            ResponseEntity.notFound().build()
        }
    }
}
