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
        productToSave.setImage(productDTO.getImages());
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());
        productToSave.setActive(productDTO.isActive());

        // Agregamos el producto a las categorias a las que pertenece
        categoryService.addProductToCategorys(productToSave, productDTO.getCategories());

        // Agregamos el producto a la marca a la que pertenece
        brandService.addProductToBrand(productToSave, productDTO.getBrand());

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
                    String categoryString = data[7].trim();
                    List<Integer> categories = Arrays.stream(categoryString.split(";"))
                                                .map(String::trim)
                                                .filter(s -> !s.isEmpty())
                                                .map(Integer::parseInt)
                                                .toList();
                    
                    String imageString = data[10].trim();
                    List<String> images = Arrays.stream(imageString.split(";"))
                                            .map(String::trim)
                                            .filter(s-> !s.isEmpty())
                                            .toList();
                    
                    Integer productCode = Integer.valueOf(data[0].trim());
                    String name = data[1].trim();
                    String description = data[2].trim();
                    Float price = Float.valueOf(data[3].trim());
                    Float unitPrice = Float.valueOf(data[4].trim());
                    Float discount = Float.valueOf(data[5].trim());
                    int stock = Integer.parseInt(data[6].trim());
                    int brandID = Integer.parseInt(data[8].trim());
                    Float calification = Float.valueOf(data[9].trim());
                    boolean isNew = Boolean.parseBoolean(data[11].trim());
                    boolean isBestSeller = Boolean.parseBoolean(data[12].trim());
                    boolean isFeatured = Boolean.parseBoolean(data[13].trim());
                    boolean hero = Boolean.parseBoolean(data[14].trim());
                    boolean active = Boolean.parseBoolean(data[15].trim());
                
                    ProductDTO pDTO = new ProductDTO();
                    pDTO.setProductCode(productCode);
                    pDTO.setName(name);
                    pDTO.setDescription(description);
                    pDTO.setPrice(price);
                    pDTO.setUnitPrice(unitPrice);
                    pDTO.setDiscount(discount);
                    pDTO.setStock(stock);
                    pDTO.setCategories(categories);
                    pDTO.setBrand(brandID);
                    pDTO.setCalification(calification);
                    pDTO.setImages(images);
                    pDTO.setNew(isNew);
                    pDTO.setBestSeller(isBestSeller);
                    pDTO.setFeatured(isFeatured);
                    pDTO.setHero(hero);
                    pDTO.setActive(active);
                    
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

    public Product updateProduct(ProductDTO productUpdateDTO){
        List<Category>categoriesToUpdate = categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = brandService.getBrandByID(productUpdateDTO.getBrand());

        Optional<Product> productOptional = productRepository.findByProductCode(productUpdateDTO.getProductCode());
        Product productToUpdate = productOptional.get();

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setPrice(productUpdateDTO.getPrice());
        productToUpdate.setUnitPrice(productUpdateDTO.getUnitPrice());
        productToUpdate.setDiscount(productUpdateDTO.getDiscount());
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setCategories(categoriesToUpdate);
        productToUpdate.setBrand(brandToUpdate);
        productToUpdate.setCalification(productUpdateDTO.getCalification());
        productToUpdate.setImage(productUpdateDTO.getImages());
        productToUpdate.setNew(productUpdateDTO.isNew());
        productToUpdate.setBestSeller(productUpdateDTO.isBestSeller());
        productToUpdate.setFeatured(productUpdateDTO.isFeatured());
        productToUpdate.setHero(productUpdateDTO.isHero());
        productToUpdate.setActive(productUpdateDTO.isActive());
        
        return productRepository.save(productToUpdate);
    }

    public Product updateStockPostSale(Integer productCode, int amountBought){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() - amountBought;
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        return productToUpdate;
    }
    
    public Product updateStockPostCancelation(Integer productCode, int amountReturned){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        int newStock = productToUpdate.getStock() + amountReturned;

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        return  productToUpdate;
    }
    public Product updateStock (Integer productCode, int newStock){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();
        
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);
        
        return productToUpdate;
    }

    public Product updateUnitPrice (Integer productCode, float newPrice){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        productToUpdate.setUnitPrice(newPrice);
        
        float priceWithDiscount = newPrice * (productToUpdate.getDiscount() / 100);

        productToUpdate.setPrice(priceWithDiscount);

        return productRepository.save(productToUpdate);
    }
    
    public Product updateDiscount(Integer productCode, float newDiscount){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        Product productToUpdate = productOptional.get();

        productToUpdate.setDiscount(newDiscount);

        float newPriceWithDiscount = productToUpdate.getUnitPrice() * (newDiscount / 100);

        productToUpdate.setPrice(newPriceWithDiscount);

       return productRepository.save(productToUpdate);
    }

    public boolean deleteProduct(Integer productCode){
        try{
            Optional<Product> productOptional = productRepository.findByProductCode(productCode);
            Product productToDiactivate = productOptional.get();

            productToDiactivate.setActive(false);
            productRepository.save(productToDiactivate);

            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
