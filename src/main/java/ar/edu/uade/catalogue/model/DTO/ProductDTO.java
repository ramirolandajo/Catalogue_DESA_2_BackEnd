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

    private Integer productCode;
    private String name;
    private String description;
    private float unitPrice;
    private float discount;
    private int stock;

    // Nuevo: priorizar códigos unificados
    private List<Integer> categoryCodes;
    private Integer brandCode;

    // Compatibilidad hacia atrás (IDs internos); si se proveen ambos, se priorizan los códigos
    private List<Integer> categories;
    private Integer brand;

    private float calification;
    private List<String> images;
    private boolean isNew;
    private boolean isBestSeller;
    private boolean isFeatured;
    private boolean hero; 
    private boolean active;
}