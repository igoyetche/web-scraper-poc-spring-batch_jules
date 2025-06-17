package com.example.articlescraper

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.task.configuration.EnableTask
import org.springframework.retry.annotation.EnableRetry

/**
 * Main entry point for the Article Scraper application.
 *
 * This application is responsible for scraping articles, processing them,
 * and potentially storing them in a database and publishing messages.
 *
 * It enables Spring Boot auto-configuration, Spring Cloud Task, and Spring Retry.
 */
@SpringBootApplication
@EnableTask // Enable Spring Cloud Task
@EnableRetry // Enable Spring Retry for @Retryable annotation
class ArticleScraperApplication

/**
 * Main function to run the Spring Boot application.
 * @param args Command line arguments.
 */
fun main(args: Array<String>) {
    runApplication<ArticleScraperApplication>(*args)
}
