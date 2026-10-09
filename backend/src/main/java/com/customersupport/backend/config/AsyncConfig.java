package com.customersupport.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Runs @Async work (AI categorization) on Boot's default task executor, off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
