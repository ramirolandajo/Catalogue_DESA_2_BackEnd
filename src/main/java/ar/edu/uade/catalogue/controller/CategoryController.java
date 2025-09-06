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

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.service.CategoryService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping(value="/category")
public class CategoryController {

    @Autowired
    CategoryService categoryService;

    @GetMapping(value="/getAll",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Category>>getBrands(){
        try {
            List<Category> categories = categoryService.getCategories();
            return new ResponseEntity<>(categories, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }

   @GetMapping(value="/getProducts/{id}", produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Product>>getProductsFromCategory(@PathVariable("id") Integer id){
        try {
            List<Product> productsFromCategory = categoryService.getAllProductsFromCategory(id);
            return new ResponseEntity<>(productsFromCategory, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(value="/getCategoryByID/{id}", produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Category>getCategoryByID(@PathVariable("id") Integer categoryID){
        try {
            Category category = categoryService.getCategoryByID(categoryID);
            return new ResponseEntity<>(category, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value="/create", consumes={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Category>createCategory(@RequestBody Category category){
        Category categorySaved = categoryService.createCategory(category);
        return new ResponseEntity<>(categorySaved, HttpStatus.CREATED);
    }

    @DeleteMapping(value="/delete/{id}")
    public ResponseEntity<Void>deleteCategory(@PathVariable("id") Integer id){
        boolean deletedCategory = categoryService.deleteCategory(id);

        if(deletedCategory){
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


}
