package ar.edu.uade.catalogue.model.DTO;

import java.util.List;

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
    private List<Integer> categories;
    private Integer brand;
    private List<String> images;
    private boolean isNew;
    private boolean isBestSeller;
    private boolean isFeatured;
    private boolean hero;
}
