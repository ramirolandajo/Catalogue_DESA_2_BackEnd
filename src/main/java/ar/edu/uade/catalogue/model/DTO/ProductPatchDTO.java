package ar.edu.uade.catalogue.model.DTO;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductPatchDTO {
    // Requerido para identificar el producto a actualizar
    private Integer productCode;

    // Campos opcionales (null => no modificar)
    private String name;
    private Float unitPrice;
    private Float discount;
    private Integer stock;

    // Nuevos: usar códigos unificados
    private List<Integer> categoryCodes;
    private Integer brandCode;

    // Compatibilidad: aún se aceptan IDs internos si no se envían códigos
    private String description;
    private List<Integer> categories;
    private Integer brand;
    private Float calification;
    private List<String> images;
    private List<String> keepImages;
    @JsonProperty("new")
    private Boolean isNew;
    @JsonProperty("bestSeller")
    private Boolean isBestSeller;
    @JsonProperty("featured")
    private Boolean isFeatured;
    private Boolean hero;
    private Boolean active;
}
