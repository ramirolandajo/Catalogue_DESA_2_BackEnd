package ar.edu.uade.catalogue.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    
    @Column(name = "products")
    List<Integer>products;

    @Column(name="active", nullable=false)
    private boolean active;
}