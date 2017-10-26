package org.retest.rebazer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfig {
	
	@Bean
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }
	
}
