package com.example.articlescraper.batch

import com.mongodb.client.MongoClient
import io.github.michaelruocco.spring.batch.explorer.MongoJobExplorer
import io.github.michaelruocco.spring.batch.repository.MongoJobRepository
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * Configures Spring Batch to use MongoDB for storing job metadata.
 *
 * This class implements [BatchConfigurer] to provide custom instances of
 * [JobRepository], [JobExplorer], and [JobLauncher] that are backed by MongoDB.
 *
 * @property mongoClient The MongoDB client instance.
 * @property mongoTemplate The Spring Data MongoDB template for interacting with MongoDB.
 */
@Configuration
class MongoBatchConfigurer @Autowired constructor(
    private val mongoClient: MongoClient,
    private val mongoTemplate: MongoTemplate
) : BatchConfigurer {

    private val transactionManager: PlatformTransactionManager = ResourcelessTransactionManager()
    private val jobRepository: JobRepository by lazy {
        MongoJobRepository(mongoTemplate.converter.mappingContext, mongoClient, mongoTemplate.db.name)
    }
    private val jobExplorer: JobExplorer by lazy {
        MongoJobExplorer(mongoTemplate.converter.mappingContext, mongoClient, mongoTemplate.db.name)
    }
    private val jobLauncher: JobLauncher by lazy {
        val launcher = TaskExecutorJobLauncher()
        launcher.setJobRepository(jobRepository)
        launcher.afterPropertiesSet()
        launcher
    }

    /**
     * Provides a [JobRepository] that uses MongoDB for persistence.
     * @return The configured [MongoJobRepository].
     */
    override fun getJobRepository(): JobRepository = jobRepository

    /**
     * Provides a [PlatformTransactionManager]. For MongoDB batch,
     * a [ResourcelessTransactionManager] is often used as per spring-batch-mongodb documentation.
     * @return The configured [PlatformTransactionManager].
     */
    override fun getTransactionManager(): PlatformTransactionManager = transactionManager

    /**
     * Provides a [JobLauncher].
     * @return The configured [JobLauncher].
     */
    override fun getJobLauncher(): JobLauncher = jobLauncher

    /**
     * Provides a [JobExplorer] that reads job information from MongoDB.
     * @return The configured [MongoJobExplorer].
     */
    override fun getJobExplorer(): JobExplorer = jobExplorer
}
