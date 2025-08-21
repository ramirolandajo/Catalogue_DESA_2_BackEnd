package ar.edu.uade.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.uade.catalogue.model.Product;

@Repository
public interface ProductRepository  extends JpaRepository<Product,Integer>{
    
}