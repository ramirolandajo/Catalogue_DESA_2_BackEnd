package ar.edu.uade.catalogue.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter

@Entity
@Table(name = "brand")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "brand_id")
    private Integer id;

    @Column(name = "brand_code", unique = true, nullable = true)
    private Integer brandCode; // identificador unificado

    @Column(name = "name")
    private String name;

    //@Column(name = "products")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "brand_products", joinColumns = @JoinColumn(name = "brand_id"))
    @Column(name = "product_id")
    private List<Integer>products;

    @Column(name="active", nullable=false)
    private boolean active;
}
