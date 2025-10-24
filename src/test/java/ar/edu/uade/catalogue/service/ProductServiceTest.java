package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.*;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandService brandService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private KafkaMockService kafkaMockService;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private ProductDTO dto;
    private Brand brand;
    private Category category;

    @BeforeEach
    void setup() {
        product = new Product();
        product.setProductCode(1001);
        product.setName("Test Product");
        product.setDescription("Desc");
        product.setUnitPrice(100f);
        product.setDiscount(10f);
        product.setStock(5);
        product.setCalification(4.5f);
        product.setImages(List.of("img.png"));
        product.setActive(true);

        dto = new ProductDTO();
        dto.setProductCode(1001);
        dto.setName("Test Product");
        dto.setDescription("Desc");
        dto.setUnitPrice(100f);
        dto.setDiscount(10f);
        dto.setStock(5);
        dto.setCalification(4.5f);
        dto.setImages(List.of("img.png"));
        dto.setCategories(List.of(1));
        dto.setBrand(1);
        dto.setActive(true);

        brand = new Brand();
        brand.setId(1);
        brand.setName("BrandTest");
        brand.setProducts(List.of(1001));
        brand.setActive(true);

        category = new Category();
        category.setId(1);
        category.setName("CategoryTest");
        category.setProducts(List.of(1001));
        category.setActive(true);
    }

    @Test
    void getProducts_ShouldReturnList() {
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<Product> result = productService.getProducts();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getProductByProductCode_ShouldReturnProduct() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        Product result = productService.getProductByProductCode(1001);

        assertNotNull(result);
        assertEquals("Test Product", result.getName());
    }

    @Test
    void createProduct_ShouldSaveAndReturnProduct() throws Exception {
        when(categoryService.geCategoriesForProductByID(anyList())).thenReturn(List.of(category));
        when(brandService.getBrandByID(anyInt())).thenReturn(brand);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("POST", "{}"));

        Product result = productService.createProduct(dto);

        assertNotNull(result);
        assertEquals("Test Product", result.getName());
    }

    @Test
    void updateProduct_ShouldModifyAndReturnUpdated() throws IOException {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(categoryService.geCategoriesForProductByID(anyList())).thenReturn(List.of(category));
        when(brandService.getBrandByID(anyInt())).thenReturn(brand);
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PUT", "{}"));

        dto.setName("Updated Product");

        Product updated = productService.updateProduct(dto);

        assertNotNull(updated);
        assertEquals("Updated Product", updated.getName());
    }

    @Test
    void updateStockPostSale_ShouldDecreaseStock() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        Product result = productService.updateStockPostSale(1001, 2);

        assertEquals(3, result.getStock());
    }

    @Test
    void updateStockPostCancelation_ShouldIncreaseStock() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        Product result = productService.updateStockPostCancelation(1001, 2);

        assertEquals(7, result.getStock());
    }

    @Test
    void updateStock_ShouldSetNewStock() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        Product result = productService.updateStock(1001, 50);

        assertEquals(50, result.getStock());
    }

    @Test
    void updateUnitPrice_ShouldUpdatePriceWithDiscount() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        Product result = productService.updateUnitPrice(1001, 200f);

        assertNotNull(result);
        assertEquals(200f, result.getUnitPrice());
        assertEquals(20f, result.getPrice()); // 200 * 10%
    }

    @Test
    void updateDiscount_ShouldUpdateAndRecalculatePrice() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        Product result = productService.updateDiscount(1001, 20f);

        assertNotNull(result);
        assertEquals(20f, result.getDiscount());
        assertEquals(20f, result.getPrice()); // 100 * 20%
    }

    @Test
    void deleteProduct_ShouldDeactivate() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        boolean deleted = productService.deleteProduct(1001);

        assertTrue(deleted);
        assertFalse(product.isActive());
    }
}
