package ar.edu.uade.catalogue.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
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
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id; 

    @Column(name="product_code", unique = true, nullable = false)
    private Integer productCode;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price")
    private float price; //con descuento

    @Column(name="unit_price")
    private float unitPrice; 
    
    @Column(name="discount")
    private float discount;

    @Column(name = "stock")
    private int stock;

    @Column(name = "calification")
    //@OneToMany(mappedBy="product_id", cascade= CascadeType.ALL)
    private float calification;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @JoinTable(
        name = "product_category",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "brand_id")
    private Brand brand;
   
    @Column(name = "images")
    private List<String> images; //Solo links a las img

    @Column(name="isNew")
    private boolean isNew;

    @Column(name="isBestSeller")
    private boolean isBestSeller;

    @Column(name="isFeatured")
    private boolean isFeatured;

    @Column(name="hero")
    private boolean hero;

    @Column(name="active")
    private boolean active;

@Override
public String toString(){
    return "Product {" +
                        "id: " + id +
                        "nombre: " + name +
                        "descripcion: " + description +
                        "precio: " + price + 
                        "stock: " + stock + 
                        "calificacion: "  + 
                        "marca: " + brand + 
                        "isNew: " + isNew + 
                        "isBestSeller: " + isBestSeller +
                        "isFeatured: " + isFeatured +
                        "hero: " + hero;
}

}