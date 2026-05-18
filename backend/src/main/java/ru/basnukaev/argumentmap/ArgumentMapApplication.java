package ru.basnukaev.argumentmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArgumentMapApplication {

	static {
		// Разрешить Basic auth на HTTPS-туннеле через прокси - SHAMELA_PROXY.
		// Должен быть установлен ДО любого HttpClient в JVM (JDK кеширует
		// property при первом auth challenge). Подробности и reproducer -
		// в docs/gotchas.md "Java HttpClient + SHAMELA_PROXY".
		System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
	}

	public static void main(String[] args) {
		SpringApplication.run(ArgumentMapApplication.class, args);
	}

}
