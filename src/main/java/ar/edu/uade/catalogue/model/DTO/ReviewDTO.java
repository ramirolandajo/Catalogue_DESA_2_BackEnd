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
public class ReviewDTO {

    private String user;
    private String title;
    private String description;
    private float score;
    private Integer ProductID;
    private List<String> images;
}
