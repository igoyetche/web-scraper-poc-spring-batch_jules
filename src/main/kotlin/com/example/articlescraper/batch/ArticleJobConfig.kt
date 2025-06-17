package com.example.articlescraper.batch

import com.example.articlescraper.domain.Article
import com.example.articlescraper.repository.ArticleRepository
import com.example.articlescraper.service.ArticleDownloader
import com.example.articlescraper.service.ClassificationClient
import com.example.articlescraper.service.JsonUploader
import com.example.articlescraper.service.ListFetcher
import com.example.articlescraper.service.PubSubPublisher
import com.example.articlescraper.service.TranslationClient
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.support.IteratorItemReader
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant

/**
 * Configuration class for Spring Batch jobs related to article scraping.
 *
 * This class defines the beans for jobs and steps involved in the article scraping process.
 *
 * @property jobRepository The repository for storing job metadata.
 * @property transactionManager The transaction manager to be used by steps.
 * @property listFetcher Service for fetching article URLs.
 * @property articleDownloader Service for downloading individual article content.
 * @property classificationClient Client for classifying article content.
 * @property translationClient Client for translating article content.
 * @property jsonUploader Client for uploading JSON data to GCS.
 * @property pubSubPublisher Client for publishing messages to Pub/Sub.
 * @property articleRepository Repository for saving and retrieving article data.
 */
@Configuration
class ArticleJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val listFetcher: ListFetcher,
    private val articleDownloader: ArticleDownloader,
    private val classificationClient: ClassificationClient,
    private val translationClient: TranslationClient,
    private val jsonUploader: JsonUploader,
    private val pubSubPublisher: PubSubPublisher,
    private val articleRepository: ArticleRepository
) {

    private val logger = LoggerFactory.getLogger(ArticleJobConfig::class.java)

    companion object {
        const val ARTICLE_URLS_EXECUTION_KEY = "articleUrls"
    }

    /**
     * Defines the first step: fetching the list of article URLs.
     * Stores URLs in [JobExecutionContext].
     * @return A [Step] instance.
     */
    @Bean
    fun fetchListStep(): Step {
        return StepBuilder("fetchListStep", jobRepository)
            .tasklet(Tasklet { _, chunkContext ->
                logger.info("Starting fetchListStep...")
                val articleUrls = listFetcher.fetchArticleUrls()
                if (articleUrls.isNotEmpty()) {
                    chunkContext.stepContext.stepExecution.jobExecution.executionContext.put(
                        ARTICLE_URLS_EXECUTION_KEY,
                        articleUrls
                    )
                    logger.info("Successfully fetched ${articleUrls.size} article URLs.")
                } else {
                    logger.warn("No article URLs fetched. Subsequent steps might be skipped.")
                }
                logger.info("fetchListStep completed.")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()
    }

    /**
     * [ItemReader] for article URLs from [JobExecutionContext].
     * @param jobExecution Current [JobExecution].
     * @return An [ItemReader] for URLs.
     */
    @Bean
    @StepScope
    fun articleUrlReader(
        @Value("#{jobExecution}") jobExecution: JobExecution
    ): ItemReader<String> {
        val articleUrls = jobExecution.executionContext.get(ARTICLE_URLS_EXECUTION_KEY) as? List<String>
        if (articleUrls.isNullOrEmpty()) {
            logger.warn("articleUrlReader: No URLs found in JobExecutionContext. Reader will be empty.")
            return IteratorItemReader(emptyList<String>().iterator())
        }
        logger.info("articleUrlReader initialized with ${articleUrls.size} URLs.")
        return IteratorItemReader(articleUrls.iterator())
    }

    /**
     * [ItemProcessor] to download article content for a given URL.
     * @return An [ItemProcessor] transforming URL to [Article].
     */
    @Bean
    @StepScope
    fun articleProcessor(): ItemProcessor<String, Article> {
        return ItemProcessor { url ->
            logger.debug("articleProcessor: Processing URL: $url")
            try {
                articleDownloader.downloadArticleContent(url)
            } catch (e: Exception) {
                logger.error("articleProcessor: Failed for URL $url: ${e.message}", e)
                null // Filter out on error
            }
        }
    }

    /**
     * [ItemWriter] to save downloaded [Article] objects to MongoDB.
     * @return An [ItemWriter] for [Article]s.
     */
    @Bean
    @StepScope
    fun articleMongoWriter(): ItemWriter<Article> {
        return ItemWriter { items ->
            logger.info("articleMongoWriter: Writing ${items.size} articles.")
            articleRepository.saveAll(items.toList())
            logger.debug("articleMongoWriter: ${items.size} articles written.")
        }
    }

    /**
     * Defines the second step: downloading and persisting articles.
     * Chunk-oriented step using [articleUrlReader], [articleProcessor], and [articleMongoWriter].
     * @return A [Step] instance.
     */
    @Bean
    fun downloadArticlesStep(
        articleUrlReader: ItemReader<String>,
        articleProcessor: ItemProcessor<String, Article>,
        articleMongoWriter: ItemWriter<Article>
    ): Step {
        logger.info("Configuring downloadArticlesStep...")
        return StepBuilder("downloadArticlesStep", jobRepository)
            .chunk<String, Article>(10, transactionManager)
            .reader(articleUrlReader)
            .processor(articleProcessor)
            .writer(articleMongoWriter)
            .faultTolerant()
            .build()
    }

    /**
     * [ItemReader] for articles needing classification.
     * Reads articles where `classification` is null from MongoDB.
     * @return An [ItemReader] for unclassified [Article]s.
     */
    @Bean
    @StepScope
    fun unclassifiedArticleReader(): ItemReader<Article> {
        logger.info("unclassifiedArticleReader: Fetching articles with null classification.")
        val unclassifiedArticles = articleRepository.findByClassificationIsNull()
        if (unclassifiedArticles.isEmpty()) {
            logger.info("unclassifiedArticleReader: No articles found needing classification.")
        } else {
            logger.info("unclassifiedArticleReader: Found ${unclassifiedArticles.size} articles to classify.")
        }
        return IteratorItemReader(unclassifiedArticles.iterator())
    }

    /**
     * [ItemProcessor] to classify an [Article].
     * Uses [ClassificationClient] and updates the article.
     * @return An [ItemProcessor] that classifies an [Article].
     */
    @Bean
    @StepScope
    fun classificationProcessor(): ItemProcessor<Article, Article> {
        return ItemProcessor { article ->
            logger.debug("classificationProcessor: Classifying article ID: ${article.id}")
            try {
                val classificationResult = classificationClient.classifyArticle(article)
                article.copy(
                    classification = classificationResult,
                    updatedAt = Instant.now()
                )
            } catch (e: Exception) {
                logger.error("classificationProcessor: Failed for article ID ${article.id}: ${e.message}", e)
                // Optionally re-throw or return null to filter if classification is critical
                // For now, returning the original article if classification fails to avoid data loss for this item in the writer.
                // Consider a dead-letter queue or specific error handling for failed classifications.
                null // Filter out items that fail classification
            }
        }
    }

    /**
     * [ItemWriter] to save classified [Article] objects (with updated classification) to MongoDB.
     * This writer is essentially the same as articleMongoWriter but named for clarity in the step definition.
     * It could be reused, but defining it separately allows for potential different logic in the future.
     * @return An [ItemWriter] for [Article]s.
     */
    @Bean
    @StepScope
    fun classifiedArticleMongoWriter(): ItemWriter<Article> {
        return ItemWriter { items ->
            logger.info("classifiedArticleMongoWriter: Writing ${items.size} classified articles.")
            articleRepository.saveAll(items.toList()) // Assuming Article state is managed by processor
            logger.debug("classifiedArticleMongoWriter: ${items.size} classified articles written.")
        }
    }

    /**
     * Defines the third step: classifying articles.
     * Chunk-oriented step using [unclassifiedArticleReader], [classificationProcessor], and [classifiedArticleMongoWriter].
     * @return A [Step] instance.
     */
    @Bean
    fun classifyArticlesStep(
        unclassifiedArticleReader: ItemReader<Article>,
        classificationProcessor: ItemProcessor<Article, Article>,
        classifiedArticleMongoWriter: ItemWriter<Article>
    ): Step {
        logger.info("Configuring classifyArticlesStep...")
        return StepBuilder("classifyArticlesStep", jobRepository)
            .chunk<Article, Article>(10, transactionManager)
            .reader(unclassifiedArticleReader)
            .processor(classificationProcessor)
            .writer(classifiedArticleMongoWriter)
            .faultTolerant()
            // .skipLimit(...)
            // .skip(...)
            .build()
    }

    /**
     * [ItemReader] for articles that are classified and may need translation.
     * Reads articles where `classification` is not null.
     * @return An [ItemReader] for classified [Article]s.
     */
    @Bean
    @StepScope
    fun translatableArticleReader(): ItemReader<Article> {
        logger.info("translatableArticleReader: Fetching classified articles.")
        val translatableArticles = articleRepository.findByClassificationIsNotNull()
        if (translatableArticles.isEmpty()) {
            logger.info("translatableArticleReader: No classified articles found for translation.")
        } else {
            logger.info("translatableArticleReader: Found ${translatableArticles.size} classified articles.")
        }
        return IteratorItemReader(translatableArticles.iterator())
    }

    /**
     * [ItemProcessor] to translate an [Article] into multiple languages.
     * Uses [TranslationClient] and updates the article's translations map.
     * Returns the article if translations were added, otherwise null to filter.
     * @return An [ItemProcessor] that translates an [Article].
     */
    @Bean
    @StepScope
    fun translationProcessor(): ItemProcessor<Article, Article?> {
        return ItemProcessor { article ->
            logger.debug("translationProcessor: Checking article ID: ${article.id} for required translations.")
            val requiredTranslations = translationClient.getRequiredTranslations(article)

            if (requiredTranslations.isEmpty()) {
                logger.debug("translationProcessor: Article ID: ${article.id} needs no new translations. Filtering out.")
                return@ItemProcessor null // Filter out if no new translations were made
            }

            val updatedTranslations = (article.translations ?: emptyMap()).toMutableMap()
            var translationOccurred = false

            for (language in requiredTranslations) {
                try {
                    val translatedText = translationClient.translateArticle(article, language)
                    updatedTranslations[language] = translatedText
                    translationOccurred = true
                } catch (e: Exception) {
                    logger.error("translationProcessor: Failed to translate article ID ${article.id} to $language: ${e.message}", e)
                    // Decide if one failed translation should stop others or filter the item
                    // For now, we continue with other languages but the item will be "dirty"
                    // This item will be saved with partial translations if any succeeded.
                }
            }

            if (translationOccurred) {
                logger.info("translationProcessor: Article ID: ${article.id} updated with new translations for languages: ${updatedTranslations.keys.joinToString()}.")
                article.copy(
                    translations = updatedTranslations,
                    updatedAt = Instant.now()
                )
            } else {
                logger.warn("translationProcessor: No new translations were successfully applied for article ID: ${article.id} despite being identified as needing them. Original required: $requiredTranslations")
                null // Filter out if no translations were actually added (e.g. all attempts failed)
            }
        }
    }

    /**
     * [ItemWriter] to save translated [Article] objects to MongoDB.
     * This writer is identical to classifiedArticleMongoWriter and articleMongoWriter in function
     * but named for step clarity. It saves articles with updated translations.
     * @return An [ItemWriter] for [Article]s.
     */
    @Bean
    @StepScope
    fun translatedArticleMongoWriter(): ItemWriter<Article> {
        return ItemWriter { items ->
            logger.info("translatedArticleMongoWriter: Writing ${items.size} translated articles.")
            articleRepository.saveAll(items.filterNotNull().toList()) // Filter out nulls from processor
            logger.debug("translatedArticleMongoWriter: ${items.size} translated articles processed for writing.")
        }
    }

    /**
     * Defines the fourth step: translating articles.
     * Chunk-oriented step using [translatableArticleReader], [translationProcessor], and [translatedArticleMongoWriter].
     * @return A [Step] instance.
     */
    @Bean
    fun translateArticlesStep(
        translatableArticleReader: ItemReader<Article>,
        translationProcessor: ItemProcessor<Article, Article?>, // Processor can return null
        translatedArticleMongoWriter: ItemWriter<Article>
    ): Step {
        logger.info("Configuring translateArticlesStep...")
        return StepBuilder("translateArticlesStep", jobRepository)
            .chunk<Article, Article>(5, transactionManager) // Smaller chunk size for potentially slower operations
            .reader(translatableArticleReader)
            .processor(translationProcessor)
            .writer(translatedArticleMongoWriter)
            .faultTolerant()
            // .skipLimit(...)
            // .skip(...)
            .build()
    }

    /**
     * [ItemReader] for articles ready for JSON upload to GCS.
     * Reads articles that are classified but have no `gcsUrl`.
     * @return An [ItemReader] for such [Article]s.
     */
    @Bean
    @StepScope
    fun uploadableArticleReader(): ItemReader<Article> {
        logger.info("uploadableArticleReader: Fetching classified articles with null gcsUrl.")
        val uploadableArticles = articleRepository.findByClassificationIsNotNullAndGcsUrlIsNull()
        if (uploadableArticles.isEmpty()) {
            logger.info("uploadableArticleReader: No articles found requiring GCS JSON upload.")
        } else {
            logger.info("uploadableArticleReader: Found ${uploadableArticles.size} articles for GCS JSON upload.")
        }
        return IteratorItemReader(uploadableArticles.iterator())
    }

    /**
     * [ItemProcessor] to upload an [Article]'s JSON representation to GCS.
     * Uses [JsonUploader] and updates the article's `gcsUrl`.
     * @return An [ItemProcessor] that processes an [Article] for GCS upload.
     */
    @Bean
    @StepScope
    fun jsonUploadProcessor(): ItemProcessor<Article, Article> {
        return ItemProcessor { article ->
            logger.debug("jsonUploadProcessor: Processing article ID: ${article.id} for GCS JSON upload.")
            try {
                val gcsUrlResult = jsonUploader.uploadArticleJson(article)
                article.copy(
                    gcsUrl = gcsUrlResult,
                    updatedAt = Instant.now()
                )
            } catch (e: Exception) {
                logger.error("jsonUploadProcessor: Failed to upload JSON for article ID ${article.id}: ${e.message}", e)
                // Decide on error handling: skip, retry (already handled by JsonUploader), or fail step
                // Returning null would filter it from writer. For now, let it propagate if JsonUploader retries fail.
                throw e // Or return null to filter from writer if partial success is okay
            }
        }
    }

    /**
     * [ItemWriter] to save [Article] objects with updated `gcsUrl` to MongoDB.
     * Similar to other writers, named for clarity.
     * @return An [ItemWriter] for [Article]s.
     */
    @Bean
    @StepScope
    fun gcsUrlArticleMongoWriter(): ItemWriter<Article> {
        return ItemWriter { items ->
            logger.info("gcsUrlArticleMongoWriter: Writing ${items.size} articles with updated GCS URLs.")
            articleRepository.saveAll(items.toList())
            logger.debug("gcsUrlArticleMongoWriter: ${items.size} articles with GCS URLs written.")
        }
    }

    /**
     * Defines the fifth step: uploading article JSON to GCS.
     * Chunk-oriented step using [uploadableArticleReader], [jsonUploadProcessor], and [gcsUrlArticleMongoWriter].
     * @return A [Step] instance.
     */
    @Bean
    fun uploadJsonStep(
        uploadableArticleReader: ItemReader<Article>,
        jsonUploadProcessor: ItemProcessor<Article, Article>,
        gcsUrlArticleMongoWriter: ItemWriter<Article>
    ): Step {
        logger.info("Configuring uploadJsonStep...")
        return StepBuilder("uploadJsonStep", jobRepository)
            .chunk<Article, Article>(10, transactionManager)
            .reader(uploadableArticleReader)
            .processor(jsonUploadProcessor)
            .writer(gcsUrlArticleMongoWriter)
            .faultTolerant()
            // .skip(...) for specific non-retryable exceptions from processor
            .build()
    }


    /**
     * [ItemReader] for articles ready for Pub/Sub notification.
     * Reads articles that have a `gcsUrl` but `publishedToPubSub` is false.
     * @return An [ItemReader] for such [Article]s.
     */
    @Bean
    @StepScope
    fun publishableArticleReader(): ItemReader<Article> {
        logger.info("publishableArticleReader: Fetching articles with GCS URL, not yet published to Pub/Sub.")
        val publishableArticles = articleRepository.findByGcsUrlIsNotNullAndPublishedToPubSubFalse()
        if (publishableArticles.isEmpty()) {
            logger.info("publishableArticleReader: No articles found requiring Pub/Sub notification.")
        } else {
            logger.info("publishableArticleReader: Found ${publishableArticles.size} articles for Pub/Sub notification.")
        }
        return IteratorItemReader(publishableArticles.iterator())
    }

    /**
     * [ItemProcessor] to publish an [Article]'s ID to Pub/Sub.
     * Uses [PubSubPublisher] and updates the article's `publishedToPubSub` flag.
     * @return An [ItemProcessor] that processes an [Article] for Pub/Sub publishing.
     */
    @Bean
    @StepScope
    fun pubSubPublishProcessor(): ItemProcessor<Article, Article> {
        return ItemProcessor { article ->
            logger.debug("pubSubPublishProcessor: Processing article ID: ${article.id} for Pub/Sub notification.")
            try {
                val success = pubSubPublisher.publishArticleId(article.id)
                if (success) {
                    logger.info("pubSubPublishProcessor: Successfully published article ID ${article.id} to Pub/Sub.")
                    article.copy(
                        publishedToPubSub = true,
                        updatedAt = Instant.now()
                    )
                } else {
                    // This case might not be hit if publishArticleId throws exception on failure after retries
                    logger.warn("pubSubPublishProcessor: Failed to publish article ID ${article.id} to Pub/Sub (publisher returned false). Article will not be updated.")
                    null // Filter out if not successful
                }
            } catch (e: Exception) {
                logger.error("pubSubPublishProcessor: Failed to publish article ID ${article.id} to Pub/Sub: ${e.message}", e)
                // Let exception propagate if retry in PubSubPublisher fails, or return null to filter
                throw e // Or return null
            }
        }
    }

    /**
     * [ItemWriter] to save [Article] objects with updated `publishedToPubSub` flag to MongoDB.
     * @return An [ItemWriter] for [Article]s.
     */
    @Bean
    @StepScope
    fun publishedArticleMongoWriter(): ItemWriter<Article> {
        return ItemWriter { items ->
            logger.info("publishedArticleMongoWriter: Writing ${items.size} articles with updated Pub/Sub status.")
            articleRepository.saveAll(items.filterNotNull().toList()) // Filter nulls if processor returns them
            logger.debug("publishedArticleMongoWriter: ${items.size} articles with Pub/Sub status written.")
        }
    }

    /**
     * Defines the sixth and final step: publishing article IDs to Pub/Sub.
     * Chunk-oriented step using [publishableArticleReader], [pubSubPublishProcessor], and [publishedArticleMongoWriter].
     * @return A [Step] instance.
     */
    @Bean
    fun publishPubSubStep(
        publishableArticleReader: ItemReader<Article>,
        pubSubPublishProcessor: ItemProcessor<Article, Article>,
        publishedArticleMongoWriter: ItemWriter<Article>
    ): Step {
        logger.info("Configuring publishPubSubStep...")
        return StepBuilder("publishPubSubStep", jobRepository)
            .chunk<Article, Article>(10, transactionManager)
            .reader(publishableArticleReader)
            .processor(pubSubPublishProcessor)
            .writer(publishedArticleMongoWriter)
            .faultTolerant()
            .build()
    }

    /**
     * Defines the main article scraping job.
     *
     * Orchestrates the full flow:
     * 1. Fetch list of article URLs.
     * 2. Download and persist each article.
     * 3. Classify downloaded articles.
     * 4. Translate classified articles.
     * 5. Upload article JSON to GCS.
     * 6. Publish article ID to Pub/Sub.
     *
     * @param fetchListStep Step to fetch URLs.
     * @param downloadArticlesStep Step to download articles.
     * @param classifyArticlesStep Step to classify articles.
     * @param translateArticlesStep Step to translate articles.
     * @param uploadJsonStep Step to upload JSON to GCS.
     * @param publishPubSubStep Step to publish to Pub/Sub.
     * @return A [Job] instance for article scraping.
     */
    @Bean
    fun articleScrapingJob(
        fetchListStep: Step,
        downloadArticlesStep: Step,
        classifyArticlesStep: Step,
        translateArticlesStep: Step,
        uploadJsonStep: Step,
        publishPubSubStep: Step
    ): Job {
        return JobBuilder("articleScrapingJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(fetchListStep)
            .next(downloadArticlesStep)
            .next(classifyArticlesStep)
            .next(translateArticlesStep)
            .next(uploadJsonStep)
            .next(publishPubSubStep)
            .build()
    }
}
