package ar.edu.uade.catalogue.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.service.BrandService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

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

    @GetMapping(value="/getbrandByID/{id}", produces= {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Brand>getBrandByID(@PathVariable("id") Integer brandID){
        try {
            Brand brand = brandService.getBrandByID(brandID);
            return new ResponseEntity<>(brand,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(value="/getBrandByName/{name}", produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Brand>getBrandByName(@PathVariable("name") String name){
        try {
            Brand brand = brandService.getBrandByName(name);
            return  new ResponseEntity<>(brand, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return  new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value="/create", consumes={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Brand>createBrand(@RequestBody Brand brand){
      Brand brandSaved = brandService.createBrand(brand);
      return  new ResponseEntity<>(brandSaved,HttpStatus.CREATED);
    }

    @DeleteMapping(value="/delete/{id}")
    public ResponseEntity<Void>deleteBrand(@PathVariable("id") Integer id){
        boolean brandDeleted = brandService.deleteBrand(id);

        if(brandDeleted){
            return new ResponseEntity<>(HttpStatus.OK);//NO_CONTENT(?)
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

}
