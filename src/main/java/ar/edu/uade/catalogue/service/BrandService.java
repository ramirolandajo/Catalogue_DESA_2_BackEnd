package ar.edu.uade.catalogue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.repository.BrandRepository;
import ar.edu.uade.catalogue.repository.ProductRepository;

@Service
public class BrandService {

    @Autowired
    BrandRepository brandRepository;

    @Autowired
    ProductRepository productRepository;

    public List<Brand>getBrands(){
        return brandRepository.findAll().stream().toList();
    }

    public List<Product>getProductsFromBrand(Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        Brand brand = brandOptional.get();

        List<Integer>products = brand.getProducts();
        
        List<Product>productsFound = new ArrayList<>();

        for (Integer productCode : products) {
            productsFound.add(productRepository.findByProductCode(productCode).get());
        }

        return productsFound;
    }

    public Brand getBrandByID(Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        return brandOptional.orElse(null);
    }

    public void addProductToBrand(Integer productCode, Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        Brand brandToUpdate = brandOptional.get();

        List<Integer>productsFromBrand = brandToUpdate.getProducts();
        productsFromBrand.add(productCode);

        brandToUpdate.setProducts(productsFromBrand);

        brandRepository.save(brandToUpdate);        
    }
    
    public Brand createBrand(BrandDTO brandDTO){
        Brand brandToSave = new Brand();
        
        brandToSave.setName(brandDTO.getName());
        brandToSave.setProducts(new ArrayList<>());
        brandToSave.setActive(brandDTO.isActive());
        
        return brandRepository.save(brandToSave);
    }

    public boolean deleteBrand(Integer id){
        //Borrado logico
        try{
            Optional<Brand> brandOptional = brandRepository.findById(id);
            Brand brandToDeactivate = brandOptional.get();
           
            brandToDeactivate.setActive(false);
            brandRepository.save(brandToDeactivate);

            return true; 
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
