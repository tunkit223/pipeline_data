package com.piplineData.loadService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

import java.util.TimeZone;

@SpringBootApplication
@EnableRetry
public class LoadServiceApplication {

	public static void main(String[] args) {
		// Set default timezone to UTC to avoid PostgreSQL timezone issues
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(LoadServiceApplication.class, args);
	}

}
