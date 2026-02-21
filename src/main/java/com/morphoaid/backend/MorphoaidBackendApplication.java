package com.morphoaid.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MorphoaidBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MorphoaidBackendApplication.class, args);
	}

}
