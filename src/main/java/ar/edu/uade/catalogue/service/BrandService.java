package ar.edu.uade.catalogue.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.repository.BrandRepository;

@Service
public class BrandService {

    @Autowired
    BrandRepository brandRepository;

    public List<Brand>getBrands(){
        return brandRepository.findAll().stream().toList();
    }

    public Brand getBrandByName(String name){
        Optional<Brand> brandOptional = brandRepository.findByName(name);
        return brandOptional.orElse(null);
    }

    public Brand getBrandByID(Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        return brandOptional.orElse(null);
    }

    public Brand createBrand(Brand brand){
        return brandRepository.save(brand);
    }

    public boolean deleteBrand(Integer id){
        try{
            brandRepository.deleteById(id);
            return true; 
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
