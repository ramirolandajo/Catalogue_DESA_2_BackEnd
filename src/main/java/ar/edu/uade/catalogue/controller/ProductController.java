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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.DTO.ProductPatchDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.service.ProductService;

@RestController
@RequestMapping(value="/products")
public class ProductController {

    @Autowired
    ProductService productService;

    @GetMapping(value="/getAll",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Product>>getProducts(){
        try {
            List<Product>products = productService.getProducts();
            return new ResponseEntity<>(products,HttpStatus.OK);
    
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }

    @GetMapping(value="/getProductByCode/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>getProductByID(@PathVariable("id")Integer productCode){
        try {
            Product product = productService.getProductByProductCode(productCode);
            return new ResponseEntity<>(product,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value="/create",consumes={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?>createProduct(@RequestBody ProductDTO productDTO){
        try {
            Product productSaved = productService.createProduct(productDTO);
            return new ResponseEntity<>(productSaved,HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(),HttpStatus.CONFLICT);
        }
    }

    @PostMapping(value="/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?>loadBatch(@RequestParam("file") MultipartFile csvFile) throws Exception{
        boolean succeeded = productService.loadBatchFromCSV(csvFile);
        if(succeeded){
            return new ResponseEntity<>("Batch cargado",HttpStatus.CREATED);
        }else{
            return new ResponseEntity<>("Ocurrio un error", HttpStatus.CONFLICT);
        }
    }

    @PatchMapping(value="/update", consumes={MediaType.APPLICATION_JSON_VALUE}, produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>patchProduct(@RequestBody ProductPatchDTO patch){
        try {
            Product productUpdated = productService.patchProduct(patch);
            return new ResponseEntity<>(productUpdated, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/updateStockPostSale/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>updateStockPostSale(@PathVariable("id")Integer id, @RequestParam int amountBought){
        try {
            Product productUpdated = productService.updateStockPostSale(id, amountBought);
            return new ResponseEntity<>(productUpdated,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/updateStockPostCancelation/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>updateStockPostCancelation(@PathVariable("id") Integer id, @RequestParam int amountReturned){
        try {
            Product productUpdated = productService.updateStockPostCancelation(id, amountReturned);
            return new ResponseEntity<>(productUpdated,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/updateStock/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>updateStock(@PathVariable("id") Integer id, @RequestParam int newStock){
        try {
            Product productUpdated = productService.updateStock(id, newStock);
            return new ResponseEntity<>(productUpdated,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/updateUnitPrice/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>updatePrice(@PathVariable("id") Integer productCode, @RequestParam float newPrice){
        try {
            Product productUpdated = productService.updateUnitPrice(productCode, newPrice);
            return new ResponseEntity<>(productUpdated,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/updateDiscount/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>updateDiscount(@PathVariable("id") Integer productCode, @RequestParam float newDiscount){
        try {
            Product productUpdated = productService.updateDiscount(productCode, newDiscount);
            return new ResponseEntity<>(productUpdated,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PatchMapping(value="/activate/{id}", produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> activateProduct(@PathVariable("id") Integer productCode){
        try {
            Product activated = productService.activateProduct(productCode);
            return new ResponseEntity<>(activated, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);
        }
    }

    @DeleteMapping(value="/delete/{id}")
    public ResponseEntity<Void>deleteProduct(@PathVariable("id") Integer id){
        boolean deleted = productService.deleteProduct(id);
        
        if(deleted){
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        }
    }
}
