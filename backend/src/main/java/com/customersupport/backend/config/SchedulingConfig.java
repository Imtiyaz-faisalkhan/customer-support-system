package com.customersupport.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (the IMAP mailbox poller). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
