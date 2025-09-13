package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    private ProductDTO dto;
    private Product product;
    private Brand brand;
    private List<Category> categories;

    @BeforeEach
    void setUp() {
        dto = new ProductDTO();
        dto.setProductCode(1001);
        dto.setName("Test Product");
        dto.setDescription("Description");
        dto.setUnitPrice(100f);
        dto.setDiscount(10f);
        dto.setStock(5);
        dto.setBrand(1);
        dto.setCategories(List.of(1, 2));
        dto.setCalification(4.5f);
        dto.setImages(List.of("image1.jpg", "image2.jpg"));
        dto.setNew(true);
        dto.setBestSeller(false);
        dto.setFeatured(true);
        dto.setHero(true);
        dto.setActive(true);

        product = new Product();
        product.setProductCode(1001);
        product.setName("Test Product");

        brand = new Brand();
        brand.setId(1);
        brand.setName("Test Brand");

        categories = List.of(new Category(1, "Cat1", null, true),
                             new Category(2, "Cat2", null, true));
    }

    @Test
    void createProduct_ShouldSaveAndReturnProduct() throws Exception {
        when(brandService.getBrandByID(1)).thenReturn(brand);
        when(categoryService.geCategoriesForProductByID(dto.getCategories())).thenReturn(categories);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product saved = productService.createProduct(dto);

        assertNotNull(saved);
        verify(productRepository, times(1)).save(any(Product.class));
        verify(brandService, times(1)).addProductToBrand(dto.getProductCode(), dto.getBrand());
        verify(categoryService, times(1)).addProductToCategories(dto.getProductCode(), dto.getCategories());
        verify(kafkaMockService, times(1)).sendEvent(eq("POST: Producto creado"), any(Product.class));
    }

    @Test
    void getProductByCode_ShouldReturnProduct() {
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        Product found = productService.getProductByProductCode(1001);

        assertNotNull(found);
        assertEquals(1001, found.getProductCode());
    }

    @Test
    void getProductByCode_NotFound_ShouldReturnNull() {
        when(productRepository.findByProductCode(9999)).thenReturn(Optional.empty());

        Product found = productService.getProductByProductCode(9999);

        assertNull(found);
    }

    @Test
    void updateStock_ShouldDecreaseStock() {
        product.setStock(10);
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        Product updated = productService.updateStockPostSale(1001, 3);

        assertEquals(7, updated.getStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void updateUnitPrice_ShouldChangePrice() {
        product.setDiscount(10f);
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product updated = productService.updateUnitPrice(1001, 200f);

        assertEquals(200f, updated.getUnitPrice());
        assertEquals(20f, updated.getPrice()); // 200 * 10% de descuento
    }

    @Test
    void deleteProduct_ShouldDeactivate() {
        product.setActive(true);
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        boolean result = productService.deleteProduct(1001);

        assertTrue(result);
        assertFalse(product.isActive());
        verify(productRepository, times(1)).save(product);
    }
}
