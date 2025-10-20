package ar.edu.uade.catalogue.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter

public class CategoryDTO {
    private Integer categoryCode; // nuevo identificador unificado
    private String name;
    private boolean active;
}