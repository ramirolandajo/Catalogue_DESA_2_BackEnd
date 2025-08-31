package ar.edu.uade.catalogue.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import ar.edu.uade.catalogue.model.Review;
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
    ReviewService reviewService;

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

        Product productToSave = new Product();
        
        productToSave.setId(productDTO.getId());
        productToSave.setName(productDTO.getName());
        productToSave.setDescription(productDTO.getDescription());
        productToSave.setPrice(productDTO.getPrice());
        productToSave.setStock(productDTO.getStock());
        productToSave.setReviews(new ArrayList<>());
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

    public boolean loadBactchFromCSV(MultipartFile csvFile) throws  Exception{
       List<ProductDTO> products = new ArrayList<>();
       File log = new File("log.txt");
       FileWriter logWriter = new FileWriter(log);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(csvFile.getInputStream()))) {
            String line;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;

                
                if (line.trim().isEmpty()) {
                    logWriter.write("\nLinea " + lineNumber + " vacia");
                    continue; //Ignora linea vacia
                }

                String[] data = line.split(",");

                
                if (data.length < 15) {
                    //Chequea que tenga el formato y si no loguea
                    logWriter.write(" \nCampos vacios en fila " + lineNumber + ": " + line);
                    continue;
                }

                try {
                    //Manejando lista de categorias e imagenes para setear
                    String categoryString = data[5].trim();
                    List<Integer> categories = Arrays.stream(categoryString.split(";"))
                                                .map(String::trim)
                                                .filter(s -> !s.isEmpty())
                                                .map(Integer::parseInt)
                                                .toList();
                    
                    String imageString = data[7].trim();
                    List<String> images = Arrays.stream(imageString.split(";"))
                                            .map(String::trim)
                                            .filter(s-> !s.isEmpty())
                                            .toList();
                    
                    Integer id = Integer.parseInt(data[0].trim());
                    String name = data[1].trim();
                    String description = data[2].trim();
                    Float price = Float.parseFloat(data[3].trim());
                    int stock = Integer.parseInt(data[4].trim());
                    int brandID = Integer.parseInt(data[6].trim());
                    boolean isNew = Boolean.parseBoolean(data[8].trim());
                    boolean isBestSeller = Boolean.parseBoolean(data[9].trim());
                    boolean isFeatured = Boolean.parseBoolean(data[10].trim());
                    boolean hero = Boolean.parseBoolean(data[11].trim());
                
                    ProductDTO pDTO = new ProductDTO();
                    pDTO.setId(id);
                    pDTO.setName(name);
                    pDTO.setDescription(description);
                    pDTO.setPrice(price);
                    pDTO.setStock(stock);
                    pDTO.setCategories(categories);
                    pDTO.setBrand(brandID);
                    pDTO.setImages(images);
                    pDTO.setNew(isNew);
                    pDTO.setBestSeller(isBestSeller);
                    pDTO.setFeatured(isFeatured);
                    pDTO.setHero(hero);
                    
                    products.add(pDTO);

                } catch (NumberFormatException e) {
                    logWriter.write(" \nError al parsear número en fila " + lineNumber + ": " + line);
                }
            }
        }

        logWriter.close();

        if (!products.isEmpty()) {
            for (ProductDTO p : products) {
                createProduct(p);
            }
            return true;
        } else {
            return false;
        }
    } 

    public Product updateProduct(ProductUpdateDTO productUpdateDTO){
        List<Category>categoriesToUpdate = categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = brandService.getBrandByID(productUpdateDTO.getBrand());
        List<Review> reviews = reviewService.getReviewsByProductID(productUpdateDTO.getId());

        Optional<Product> productOptional = productRepository.findById(productUpdateDTO.getId());
        Product productToUpdate = productOptional.get();

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setPrice(productUpdateDTO.getPrice());
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setReviews(reviews);
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
