package ar.edu.uade.catalogue.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.DTO.ProductUpdateDTO;
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

    public Product createProduct(ProductDTO productDTO) throws Exception{

        List<Category>categoriesToSave = categoryService.geCategoriesForProductByID(productDTO.getCategories());
        Brand brandToSave = brandService.getBrandByID(productDTO.getBrand());
        //Review reviewsToSave = reviewService.getReviews.... desarrollar

        Product productToSave = new Product();
        
        productToSave.setId(productDTO.getId());
        productToSave.setName(productDTO.getName());
        productToSave.setDescription(productDTO.getDescription());
        productToSave.setPrice(productDTO.getPrice());
        productToSave.setStock(productDTO.getStock());
        //productToSave.setReviews(reviewsToSave);
        productToSave.setCategories(categoriesToSave);
        productToSave.setBrand(brandToSave);
        productToSave.setImage(productDTO.getImages());
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());

        categoryService.addProductToCategorys(productToSave, productDTO.getCategories());
        return productRepository.save(productToSave);
    }

    public boolean loadBactchFromCSV(MultipartFile csvFile) throws  IOException{
        //Agregar validaciones y modificar para los nuevos atributos
        try(BufferedReader br = new BufferedReader(new InputStreamReader(csvFile.getInputStream()))){
            String line;
            while((line = br.readLine()) != null){
                String[] data = line.split(",");
                ProductDTO p = new ProductDTO();
                p.setName(data[0]);
                p.setDescription(data[1]);
                p.setPrice(Float.parseFloat(data[2]));
                p.setStock(Integer.parseInt(data[3]));
                //p.setR(Float.parseFloat(data[4]));
                p.setCategories(List.of(Integer.valueOf(data[5])));//chequear que funcione
                p.setBrand(Integer.valueOf(data[6]));
                
                createProduct(p);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    } 

    public Product updateProduct(ProductUpdateDTO productUpdateDTO){
        List<Category>categoriesToUpdate = categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = brandService.getBrandByID(productUpdateDTO.getBrand());

        Product productToUpdate = productRepository.getById(productUpdateDTO.getId());

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setPrice(productUpdateDTO.getPrice());
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setCategories(categoriesToUpdate);
        productToUpdate.setBrand(brandToUpdate);
        productToUpdate.setImage(productUpdateDTO.getImages());
        productToUpdate.setNew(productUpdateDTO.isNew());
        productToUpdate.setBestSeller(productUpdateDTO.isBestSeller());
        productToUpdate.setFeatured(productUpdateDTO.isFeatured());
        productToUpdate.setHero(productUpdateDTO.isHero());
        
        return productRepository.save(productToUpdate);
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
