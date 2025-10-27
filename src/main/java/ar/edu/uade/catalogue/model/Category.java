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
import jakarta.persistence.ManyToMany;
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
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    @Column(name = "category_id")
    private Integer id;

    @Column(name = "category_code", unique = true, nullable = true)
    private Integer categoryCode; // identificador unificado

    @Column(name = "name")
    private String name;
    
    //@Column(name = "products")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_products", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "product_id")
    List<Integer>products;

    @Column(name="active", nullable=false)
    private boolean active;
}