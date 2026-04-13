package ru.basnukaev.argumentmap;

import org.springframework.boot.SpringApplication;

public class TestArgumentMapApplication {

	public static void main(String[] args) {
		SpringApplication.from(ArgumentMapApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
