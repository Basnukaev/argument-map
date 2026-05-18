package ru.basnukaev.argumentmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArgumentMapApplication {

	static {
		// Разрешить Basic auth на HTTPS-туннеле через прокси (для
		// SHAMELA_PROXY, см. ShamelaHttpClientConfig). JDK по умолчанию
		// блокирует Basic для CONNECT-tunnel - это защита от MITM прокси,
		// потому что CONNECT-запрос идёт plaintext ДО TLS handshake.
		//
		// Property читается JDK один раз при ПЕРВОМ auth challenge на JVM
		// и кешируется. Если ставить через System.setProperty() внутри
		// @Bean метода - может быть уже поздно: другой HttpClient в Spring
		// контексте мог сделать auth challenge раньше. Поэтому ставим
		// здесь, в static-блоке main-класса - выполняется при загрузке
		// класса, до SpringApplication.run(), до создания любого bean.
		//
		// Безопасно для self-hosted доверенного прокси: TLS encrypts target
		// traffic после CONNECT, риск утечки credential возможен только
		// если сам прокси скомпрометирован (тогда у атакующего и так уже
		// доступ к target).
		System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
	}

	public static void main(String[] args) {
		SpringApplication.run(ArgumentMapApplication.class, args);
	}

}
