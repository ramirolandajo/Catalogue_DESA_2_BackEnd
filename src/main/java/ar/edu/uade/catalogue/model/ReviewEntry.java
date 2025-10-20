package ar.edu.uade.catalogue.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEntry {
    private Integer productCode; // redundante para auditoría del origen
    private String reviewText;   // mensaje de la review
}

