package com.piplineData.loadService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class LoadServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoadServiceApplication.class, args);
	}

}
