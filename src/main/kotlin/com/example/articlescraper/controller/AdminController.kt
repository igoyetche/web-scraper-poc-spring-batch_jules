package com.example.articlescraper.controller

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for administrative operations, such as triggering batch jobs.
 *
 * Endpoints under `/api/admin` are typically secured.
 *
 * @property jobLauncher The Spring Batch [JobLauncher] used to run jobs.
 * @property articleScrapingJob The main batch job bean to be triggered.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val jobLauncher: JobLauncher,
    private val articleScrapingJob: Job // Assuming the job bean is named "articleScrapingJob"
) {

    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    /**
     * Manually triggers the main article scraping batch job.
     *
     * A new set of [JobParameters] with the current timestamp is used for each run
     * to ensure job instance uniqueness.
     *
     * @return A [ResponseEntity] indicating the outcome of the job trigger attempt.
     *         Returns HTTP 200 with the execution ID if the job is launched successfully.
     *         Returns HTTP 500 if an error occurs during job launching.
     */
    @PostMapping("/refresh")
    fun triggerJobManually(): ResponseEntity<String> {
        logger.info("POST /api/admin/refresh - Received request to trigger the article scraping job.")
        try {
            val jobParameters = JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters()

            val jobExecution = jobLauncher.run(articleScrapingJob, jobParameters)
            val message = "Job triggered successfully. Execution ID: ${jobExecution.id}, Status: ${jobExecution.status}"
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            logger.error("Error triggering job: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to trigger job: ${e.message}")
        }
    }
}
