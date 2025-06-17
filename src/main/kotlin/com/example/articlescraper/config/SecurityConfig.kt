package com.example.articlescraper.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Security configuration for the Article Scraper application.
 *
 * This class enables web security and defines access rules for different API endpoints.
 * It configures Basic Authentication for administrative endpoints.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    /**
     * Configures the primary security filter chain for the application.
     *
     * - Allows public access to `/api/articles/**`.
     * - Requires authentication for `/api/admin/**` using HTTP Basic authentication.
     * - Disables CSRF protection (suitable for stateless backend APIs).
     *
     * @param http The [HttpSecurity] to configure.
     * @return The configured [SecurityFilterChain].
     * @throws Exception if an error occurs during configuration.
     */
    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info("Configuring SecurityFilterChain...")
        http
            .authorizeHttpRequests { authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/api/articles/**").permitAll()
                    .requestMatchers("/api/admin/**").authenticated()
                    .anyRequest().permitAll() // Or .anyRequest().authenticated() if everything else should be secure
            }
            .httpBasic(Customizer.withDefaults())
            .csrf { csrf -> csrf.disable() } // Disable CSRF for stateless API

        logger.info("SecurityFilterChain configured: /api/articles/** is public, /api/admin/** requires Basic Auth.")
        return http.build()
    }

    /**
     * Provides an in-memory [UserDetailsService] for Basic Authentication.
     *
     * Defines a single "admin" user with a password.
     * **Note:** This is for demonstration purposes. For production, use a secure,
     * persistent user store and externalize credentials.
     *
     * @return An [UserDetailsService] with a pre-configured admin user.
     */
    @Bean
    fun userDetailsService(): UserDetailsService {
        logger.info("Creating InMemoryUserDetailsManager with 'admin' user.")
        val user = User.withDefaultPasswordEncoder() // For demo only, use proper password encoding in production
            .username("admin")
            .password("password") // Store passwords securely, e.g., hashed and from config
            .roles("ADMIN", "USER")
            .build()
        return InMemoryUserDetailsManager(user)
    }
}
