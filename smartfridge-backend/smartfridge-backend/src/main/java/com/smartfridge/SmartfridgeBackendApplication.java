package com.smartfridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend. Sustituye al despliegue manual de un
 * .war sobre Apache Tomcat: `mvn spring-boot:run` (o el JAR ejecutable
 * generado por spring-boot-maven-plugin) levanta un Tomcat embebido.
 */
@SpringBootApplication
public class SmartfridgeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartfridgeBackendApplication.class, args);
    }
}
