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

    private Long id; //Long pra el ID
    private Integer productCode;
    private String name;
    private String description;
    private float price;
    private float unitPrice;
    private float discount;
    private int stock;
    private List<CategoryDTO> categories;
    private BrandDTO brand;
    private List<String> images;
    private boolean isNew;
    private boolean isBestSeller;
    private boolean isFeatured;
    private boolean hero; 

}
