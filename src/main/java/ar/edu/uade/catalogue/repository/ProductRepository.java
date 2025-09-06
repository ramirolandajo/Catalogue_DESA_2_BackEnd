package ar.edu.uade.catalogue.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.uade.catalogue.model.Product;

@Repository
public interface ProductRepository  extends JpaRepository<Product,Integer>{

    public Optional<Product> findByProductCode(Integer id);
}