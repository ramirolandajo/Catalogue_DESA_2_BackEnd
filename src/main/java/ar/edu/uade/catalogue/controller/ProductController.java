package ar.edu.uade.catalogue.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.DTO.ProductPatchDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.service.ProductService;
import ar.edu.uade.catalogue.service.ProductService.BatchResult;

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

    @PostMapping(value="/create",consumes={MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?>createProduct(@RequestPart ProductDTO productDTO, @RequestPart List<MultipartFile> images){
        try {
            Product productSaved = productService.createProduct(productDTO,images);
            return new ResponseEntity<>(productSaved,HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String,Object> body = new HashMap<>();
            body.put("error", e.getMessage());
            return new ResponseEntity<>(body,HttpStatus.CONFLICT);
        }
    }

    // Unificado: acepta multipart/form-data y text/csv|text/plain|octet-stream en el mismo endpoint
    @PostMapping(value="/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, "text/csv", "text/plain", MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/octet-stream" })
    public ResponseEntity<?> uploadFlexible(
            @RequestParam(value = "file", required = false) MultipartFile csvFile,
            @RequestBody(required = false) byte[] csvBytes) throws Exception {
        if (csvFile == null && (csvBytes == null || csvBytes.length == 0)) {
            return new ResponseEntity<>(Map.of("error","No se recibió archivo ni cuerpo CSV"), HttpStatus.BAD_REQUEST);
        }
        BatchResult result;
        if (csvFile != null && !csvFile.isEmpty()) {
            result = productService.loadBatchFromCSVDetailed(csvFile);
        } else {
            String content = new String(csvBytes, StandardCharsets.UTF_8);
            result = productService.loadBatchFromStringDetailed(content);
        }
        if (result.success()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Batch cargado",
                "totalRows", result.totalRows(),
                "created", result.created()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Batch rechazado por errores",
                "totalRows", result.totalRows(),
                "created", result.created(),
                "errors", result.errors()
            ));
        }
    }

    // Alternativa: subir CSV como texto/raw (Insomnia/Postman) sin multipart (ruta dedicada)
    @PostMapping(value = "/uploadRaw", consumes = {"text/csv", "text/plain", MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/octet-stream"})
    public ResponseEntity<?> loadBatchRaw(@RequestBody byte[] csvBytes) throws Exception {
        String content = new String(csvBytes, StandardCharsets.UTF_8);
        BatchResult result = productService.loadBatchFromStringDetailed(content);
        if (result.success()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Batch cargado",
                "totalRows", result.totalRows(),
                "created", result.created()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Batch rechazado por errores",
                "totalRows", result.totalRows(),
                "created", result.created(),
                "errors", result.errors()
            ));
        }
    }

    // Nuevo endpoint: subir Excel (.xlsx/.xls) con all-or-nothing y reporte de errores
    @PostMapping(value = "/uploadExcel", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile excel) throws Exception {
        var result = productService.loadBatchFromExcel(excel);
        if (result.success()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Batch cargado",
                "totalRows", result.totalRows(),
                "created", result.created()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Batch rechazado por errores",
                "totalRows", result.totalRows(),
                "created", result.created(),
                "errors", result.errors()
            ));
        }
    }

    // Nuevo: exportar todos los productos en CSV (mismo formato que import) y forzar descarga
    @GetMapping(value = "/export", produces = { "text/csv" })
    public ResponseEntity<byte[]> exportProductsCsv() {
        byte[] csv = productService.exportProductsCsv();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "products-" + ts + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"{}");
        headers.setContentLength(csv.length);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @PatchMapping(value="/update", consumes={MediaType.MULTIPART_FORM_DATA_VALUE}, produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Product>patchProduct(@RequestPart ProductPatchDTO patch, @RequestPart List<MultipartFile> patchImages){
        try {
            Product productUpdated = productService.patchProduct(patch, patchImages);
            return new ResponseEntity<>(productUpdated, HttpStatus.OK);
        } catch (EmptyResultDataAccessException | IOException e) {
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
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.CONFLICT);
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
