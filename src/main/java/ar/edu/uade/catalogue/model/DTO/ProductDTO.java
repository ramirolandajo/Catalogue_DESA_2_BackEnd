package ar.edu.uade.catalogue.model.DTO;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductDTO {

    private Integer id;
    private String name;
    private String description;
    private float price;
    private int stock;
    private float calification;
    private Set<String>categories;
    private String brand;
}
