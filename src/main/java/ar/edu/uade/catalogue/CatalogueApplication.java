package ar.edu.uade.catalogue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import jakarta.persistence.EntityManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

@SpringBootApplication
public class CatalogueApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogueApplication.class, args);
    }

    @Bean
    CommandLineRunner dbInfoLogger(DataSource dataSource, Environment env, EntityManager em) {
        return args -> {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData md = conn.getMetaData();
                String url = md.getURL();
                String user = md.getUserName();
                String catalog = conn.getCatalog();
                String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "<no-config>");
                System.out.println("[DB] URL=" + url + " user=" + user + " catalog=" + catalog + " ddl-auto=" + ddlAuto);
                // Mostrar database() para MySQL
                var dbName = (String) em.createNativeQuery("select database()").getSingleResult();
                System.out.println("[DB] database()=" + dbName);
            } catch (Exception e) {
                System.err.println("[DB] Error obteniendo metadata: " + e.getMessage());
            }
        };
    }
}
