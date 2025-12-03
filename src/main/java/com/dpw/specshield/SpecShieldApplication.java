package com.dpw.specshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.dpw.specshield"})
public class SpecShieldApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpecShieldApplication.class, args);
	}
}
