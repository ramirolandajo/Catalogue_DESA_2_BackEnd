package ar.edu.uade.catalogue.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BrandDTO {
    private Long id;
    private String name;
    private Boolean active;    
}
