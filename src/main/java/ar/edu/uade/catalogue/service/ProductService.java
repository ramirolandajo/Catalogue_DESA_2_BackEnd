package ar.edu.uade.catalogue.service;

<<<<<<< HEAD

=======
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
>>>>>>> 88ea793dac3751de51a2b7496dee22bc52e36903
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
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

    public List<Product>getProducts(){
        return productRepository.findAll();
    }

    public Product getProductByID(Integer id){
        Optional<Product> productOptional = productRepository.findById(id);
        return productOptional.orElse(null);
    }

    public Product createProduct(ProductDTO productDTO, MultipartFile file) throws Exception{

        if (file.isEmpty()) {
            throw new Exception("No cargo una imagen para el producto");
        }
        List<Category>categories = categoryService.geCategoriesForProductByID(productDTO.getCategories());
        Brand brand = brandService.getBrandByID(productDTO.getBrand());
        byte[] image = file.getBytes();

        Product productToSave = new Product();
        
        productToSave.setName(productDTO.getName());
        productToSave.setDescription(productDTO.getDescription());
        productToSave.setPrice(productDTO.getPrice());
        productToSave.setStock(productDTO.getStock());
        productToSave.setCalification(productDTO.getCalification());
        productToSave.setCategories(categories);
        productToSave.setBrand(brand);
        productToSave.setImage(image);

        categoryService.addProductToCategorys(productToSave, productDTO.getCategories());
        return productRepository.save(productToSave);
    }

    public boolean loadBactchFromCSV(MultipartFile csvFile) throws  IOException{
        try(BufferedReader br = new BufferedReader(new InputStreamReader(csvFile.getInputStream()))){
            String line;
            while((line = br.readLine()) != null){
                String[] data = line.split(",");
                ProductDTO p = new ProductDTO();
                p.setName(data[0]);
                p.setDescription(data[1]);
                p.setPrice(Float.parseFloat(data[2]));
                p.setStock(Integer.parseInt(data[3]));
                p.setCalification(Float.parseFloat(data[4]));
                p.setCategories(List.of(Integer.valueOf(data[5])));//chequear que funcione
                p.setBrand(Integer.valueOf(data[6]));
                
                createProduct(p, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    } 

    public Product updateProduct(Integer id, String name, String description){
            //DESARROLLAR
            return  new Product();
    }

    public Product updateStockPostSale(Integer id, int amountBought){
        Optional<Product> productOptional = productRepository.findById(id);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() - amountBought;
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        return productToUpdate;
    }
    
    public Product updateStockPostCancelation(Integer id, int amountReturned){
        Optional<Product> productOptional = productRepository.findById(id);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() + amountReturned;

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        return  productToUpdate;
    }
    public Product updateStock (Integer id, int newStock){
        Optional<Product> productOptional = productRepository.findById(id);
        Product productToUpdate = productOptional.get();
        
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);
        
        return productToUpdate;
    }

    public Product updatePrice (Integer id, float newPrice){
        Optional<Product> productOptional = productRepository.findById(id);
        Product productToUpdate = productOptional.get();

        productToUpdate.setPrice(newPrice); 

        productRepository.save(productToUpdate);

        return productToUpdate;
    }

    public boolean deleteProduct(Integer id){
        try{
            productRepository.deleteById(id);
            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
