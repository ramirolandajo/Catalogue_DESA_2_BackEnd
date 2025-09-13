package ar.edu.uade.catalogue.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.repository.ProductRepository;

@Service
public class ProductService {

    @Autowired
    ProductRepository productRepository;
    
    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    KafkaMockService kafkaMockService;

    public List<Product>getProducts(){
        return productRepository.findAll();
    }

    public Product getProductByProductCode(Integer productCode){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        return productOptional.orElse(null);
    }

    public Product createProduct(ProductDTO productDTO) throws Exception{

        List<Category>categoriesToSave = categoryService.geCategoriesForProductByID(productDTO.getCategories());
        Brand brandToSave = brandService.getBrandByID(productDTO.getBrand());
        
        // "Price" es con descuento. "unitPrice" precio por unidad sin descuento aplicado
        float priceWithDiscount = productDTO.getUnitPrice() * (productDTO.getDiscount() / 100);

        Product productToSave = new Product();
        
        productToSave.setProductCode(productDTO.getProductCode());
        productToSave.setName(productDTO.getName());
        productToSave.setDescription(productDTO.getDescription());
        productToSave.setPrice(priceWithDiscount);
        productToSave.setUnitPrice(productDTO.getUnitPrice());
        productToSave.setDiscount(productDTO.getDiscount());
        productToSave.setStock(productDTO.getStock());
        productToSave.setCalification(productDTO.getCalification());
        productToSave.setCategories(categoriesToSave);
        productToSave.setBrand(brandToSave);
        productToSave.setImages(productDTO.getImages());
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());
        productToSave.setActive(productDTO.isActive());

        productRepository.save(productToSave);

        // Agregamos el producto a las categorias a las que pertenece
        categoryService.addProductToCategories(productDTO.getProductCode(), productDTO.getCategories());

        // Agregamos el producto a la marca a la que pertenece
        brandService.addProductToBrand(productDTO.getProductCode(), productDTO.getBrand());

        Event eventSent = kafkaMockService.sendEvent("POST: Producto creado", productToSave);
        System.out.println(eventSent.toString());

        return productToSave;
    }

    public boolean loadBatchFromCSV(MultipartFile csvFile) throws Exception {
        List<ProductDTO> items = new ArrayList<>();

        try (CSVReader r = new CSVReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {
            String[] row;
            int line = 0;

            while ((row = r.readNext()) != null) {
                line++;
                // Saltar cabecera
                if (line == 1 && String.join(",", row).toLowerCase().contains("productcode")) continue;
                if (row.length < 15) continue; // formato inválido

                try {
                    ProductDTO dto = new ProductDTO();
                    dto.setProductCode(Integer.parseInt(row[0].trim()));
                    dto.setName(row[1].trim());
                    dto.setDescription(row[2].trim());
                    dto.setUnitPrice(Float.parseFloat(row[3].trim()));
                    dto.setDiscount(Float.parseFloat(row[4].trim()));
                    dto.setStock(Integer.parseInt(row[5].trim()));

                    dto.setCategories(Arrays.stream(row[6].split(";"))
                            .filter(s -> !s.isBlank())
                            .map(Integer::parseInt)
                            .toList());

                    dto.setBrand(Integer.parseInt(row[7].trim()));
                    dto.setCalification(Float.parseFloat(row[8].trim()));

                    dto.setImages(Arrays.stream(row[9].split(";"))
                            .filter(s -> !s.isBlank())
                            .toList());

                    dto.setNew(Boolean.parseBoolean(row[10].trim()));
                    dto.setBestSeller(Boolean.parseBoolean(row[11].trim()));
                    dto.setFeatured(Boolean.parseBoolean(row[12].trim()));
                    dto.setHero(Boolean.parseBoolean(row[13].trim()));
                    dto.setActive(Boolean.parseBoolean(row[14].trim()));

                    items.add(dto);
                } catch (Exception e) {
                    // si falla una fila, la salteamos
                    System.out.println("Error en línea " + line + ": " + Arrays.toString(row));
                }
            }
        }
        // Aca los Event que manda al core salen desde el createProduct()
        for (ProductDTO p : items) {
            createProduct(p);
        }
        return !items.isEmpty();
    }


    public Product updateProduct(ProductDTO productUpdateDTO){
        List<Category>categoriesToUpdate = categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = brandService.getBrandByID(productUpdateDTO.getBrand());

        Optional<Product> productOptional = productRepository.findByProductCode(productUpdateDTO.getProductCode());
        Product productToUpdate = productOptional.get();

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setUnitPrice(productUpdateDTO.getUnitPrice());
        productToUpdate.setDiscount(productUpdateDTO.getDiscount());
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setCategories(categoriesToUpdate);
        productToUpdate.setBrand(brandToUpdate);
        productToUpdate.setCalification(productUpdateDTO.getCalification());
        productToUpdate.setImages(productUpdateDTO.getImages());
        productToUpdate.setNew(productUpdateDTO.isNew());
        productToUpdate.setBestSeller(productUpdateDTO.isBestSeller());
        productToUpdate.setFeatured(productUpdateDTO.isFeatured());
        productToUpdate.setHero(productUpdateDTO.isHero());
        productToUpdate.setActive(productUpdateDTO.isActive());
        
        Event eventSent = kafkaMockService.sendEvent("PUT: Producto Actualizado", productToUpdate);
        System.out.println(eventSent.toString());

        return productRepository.save(productToUpdate);
    }

    public Product updateStockPostSale(Integer productCode, int amountBought){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() - amountBought;
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Stock disminuido por venta", productToUpdate);
        System.out.println(eventSent.toString());

        return productToUpdate;
    }
    
    public Product updateStockPostCancelation(Integer productCode, int amountReturned){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() + amountReturned;

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Stock aumentado por cancelacion", productToUpdate);
        System.out.println(eventSent.toString());

        return  productToUpdate;
    }
    public Product updateStock (Integer productCode, int newStock){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();
        
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Stock actualizado", productToUpdate);
        System.out.println(eventSent.toString());
        
        return productToUpdate;
    }

    public Product updateUnitPrice (Integer productCode, float newPrice){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        productToUpdate.setUnitPrice(newPrice);
        
        float priceWithDiscount = newPrice * (productToUpdate.getDiscount() / 100);

        productToUpdate.setPrice(priceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Precio unitario actualizado", productToUpdate);
        System.out.println(eventSent.toString());

        return productRepository.save(productToUpdate);
    }
    
    public Product updateDiscount(Integer productCode, float newDiscount){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        productToUpdate.setDiscount(newDiscount);

        float newPriceWithDiscount = productToUpdate.getUnitPrice() * (newDiscount / 100);

        productToUpdate.setPrice(newPriceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Descuento actualizado", productToUpdate);
        System.out.println(eventSent.toString());

       return productRepository.save(productToUpdate);
    }

    public boolean deleteProduct(Integer productCode){
        try{
            Optional<Product> productOptional = productRepository.findByProductCode(productCode);
            Product productToDiactivate = productOptional.get();

            productToDiactivate.setActive(false);
            productRepository.save(productToDiactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Producto desactivado", productToDiactivate);
            System.out.println(eventSent.toString());
            
            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
