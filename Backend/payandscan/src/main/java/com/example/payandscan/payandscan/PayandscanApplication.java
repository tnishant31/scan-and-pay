package com.example.payandscan.payandscan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayandscanApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayandscanApplication.class, args);
	}

}
