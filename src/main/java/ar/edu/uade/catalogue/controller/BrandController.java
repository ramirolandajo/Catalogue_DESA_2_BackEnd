package ar.edu.uade.catalogue.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.service.BrandService;


@RestController
@RequestMapping(value="/brand")
public class BrandController {

    @Autowired
    BrandService brandService;

    @GetMapping(value="/getAll", produces= {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Brand>>getBrands(){
        try {
            List<Brand> brands = brandService.getBrands();
            return new ResponseEntity<>(brands,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(value="/getProducts/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Product>>getProductsFromBrand(@PathVariable("id")Integer id){
        try {
            List<Product>productsFromBrand = brandService.getProductsFromBrand(id);
            return new ResponseEntity<>(productsFromBrand, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(value="/getBrandByID/{id}", produces= {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Brand>getBrandByID(@PathVariable("id") Integer brandID){
        try {
            Brand brand = brandService.getBrandByID(brandID);
            return new ResponseEntity<>(brand,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value="/create", consumes={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Brand>createBrand(@RequestBody BrandDTO brandDTO){
      Brand brandSaved = brandService.createBrand(brandDTO);
      return  new ResponseEntity<>(brandSaved,HttpStatus.CREATED);
    }

    // Nuevo: activar marca por brandCode
    @PatchMapping(value="/activateByCode/{brandCode}", produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> activateBrandByCode(@PathVariable("brandCode") Integer brandCode) {
        try {
            Brand activated = brandService.activateBrandByCode(brandCode);
            return new ResponseEntity<>(activated, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        }
    }

    // Cambiado: desactivar por brandCode (mantenemos la ruta pero ahora interpreta brandCode)
    @DeleteMapping(value="/delete/{id}")
    public ResponseEntity<Void>deleteBrand(@PathVariable("id") Integer brandCode){
        boolean brandDeleted = brandService.deleteBrandByCode(brandCode);

        if(brandDeleted){
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
