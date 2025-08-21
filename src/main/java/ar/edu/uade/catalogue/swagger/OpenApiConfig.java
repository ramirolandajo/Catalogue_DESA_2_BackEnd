package ar.edu.uade.catalogue.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                //Info general
                .info(new Info()
                        .title("CompuMundoHiperMegaRed | Catalogue | Desarrollo de Apps 2")
                        .version("1.0.0")
                        .description("Es una app que cuenta con varios modulos" +
                        " desarrollados con un back-end en Java, con el framework" +
                        " Springboot y utilizando MySql para la persistencia." +
                        " Finalmente se utilizo Swagger para la documentacion de la API ")
                        .contact(new Contact()
                                .name("Sebastian Bernasconi | Ramiro Landajo | Enzo Asplanatti | "
                                + "Nikolas Berntsen | Santino Casalini | Francisco Fabrello | " 
                                + "Elisheba Tawil")
                                .email("sebernasconi@uade.edu.ar | poner el resto")

                        )
                );
    }
}