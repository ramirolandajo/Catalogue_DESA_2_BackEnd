package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;
import ar.edu.uade.catalogue.model.*;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.dao.EmptyResultDataAccessException;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private BrandService brandService;
    @Mock private CategoryService categoryService;
    @Mock private KafkaMockService kafkaMockService;
    @Mock private InventoryEventPublisher inventoryEventPublisher;
    @Mock private S3ImageService s3ImageService;

    @InjectMocks
    private ProductService productService;

    private ProductDTO dto;
    private Product existing;
    private Brand brand;
    private Category cat1, cat2;

    @BeforeEach
    void setUp() throws IOException {
        brand = new Brand();
        brand.setId(1);
        brand.setBrandCode(100);
        brand.setName("Apple");

        cat1 = new Category();
        cat1.setId(1);
        cat1.setCategoryCode(200);
        cat2 = new Category();
        cat2.setId(2);
        cat2.setCategoryCode(201);

        dto = new ProductDTO();
        dto.setProductCode(999);
        dto.setName("iPhone 15");
        dto.setDescription("Celular premium");
        dto.setUnitPrice(1000f);
        dto.setDiscount(0.1f);
        dto.setStock(10);
        dto.setCalification(4.8f);
        dto.setCategoryCodes(List.of(cat1.getCategoryCode(), cat2.getCategoryCode()));
        dto.setBrandCode(brand.getBrandCode());
        dto.setImages(List.of("https://google.com/image.jpg"));
        dto.setNew(true);
        dto.setBestSeller(true);
        dto.setActive(true);

        existing = new Product();
        existing.setId(1);
        existing.setProductCode(999);
        existing.setName("iPhone 14");
        existing.setDescription("Celular anterior");
        existing.setUnitPrice(900f);
        existing.setDiscount(0.05f);
        existing.setPrice(855f);
        existing.setStock(5);
        existing.setBrand(brand);
        existing.setCategories(List.of(cat1));
        existing.setActive(true);

        // --- Mockeo flexible para servicios reutilizados ---
        lenient().when(s3ImageService.fromUrlToS3(anyString()))
                .thenReturn("https://s3.aws.com/file.jpg");

        lenient().when(categoryService.geCategoriesForProductByCodes(anyList()))
                .thenReturn(List.of(cat1, cat2));

        lenient().when(brandService.getBrandByCode(anyInt()))
                .thenReturn(brand);

        // --- Mockeo general para evitar NullPointer en sendEvent ---
        lenient().when(kafkaMockService.sendEvent(anyString(), any()))
                .thenReturn(new Event("mock", "ok"));
    }

    @Test
    @DisplayName("shouldCreateProductAndEmitEventsWhenDataIsValid")
    void shouldCreateProductAndEmitEventsWhenDataIsValid() throws IOException {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(10);
            return p;
        });

        Product created = productService.createProduct(dto,List.of());

        assertNotNull(created);
        assertEquals("iPhone 15", created.getName());
        assertEquals(900f, created.getPrice()); // 1000 * 0.9
        verify(categoryService).addProductToCategoriesByCodes(999, dto.getCategoryCodes());
        verify(inventoryEventPublisher).emitAgregarProducto(any(Product.class));
        verify(kafkaMockService).sendEvent(eq("POST: Agregar un producto"), any(Product.class));
    }

    @Test
    @DisplayName("shouldUpdateProductWhenExists")
    void shouldUpdateProductWhenExists() throws IOException {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(dto);

        assertEquals("iPhone 15", updated.getName());
        assertEquals(900f, updated.getPrice());
        verify(inventoryEventPublisher).emitProductoActualizado(any(Product.class));
        verify(kafkaMockService, atLeastOnce()).sendEvent(anyString(), any());
    }

    @Test
    @DisplayName("shouldThrowWhenUpdatingNonExistingProduct")
    void shouldThrowWhenUpdatingNonExistingProduct() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.empty());
        assertThrows(EmptyResultDataAccessException.class, () -> productService.updateProduct(dto));
    }

    @Test
    @DisplayName("shouldDecreaseStockAfterSale")
    void shouldDecreaseStockAfterSale() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateStockPostSale(999, 2);

        assertEquals(3, result.getStock());
        verify(inventoryEventPublisher).emitActualizarStock(result);
    }

    @Test
    @DisplayName("shouldIncreaseStockAfterCancelation")
    void shouldIncreaseStockAfterCancelation() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateStockPostCancelation(999, 2);

        assertEquals(7, result.getStock());
        verify(kafkaMockService).sendEvent(eq("PUT: Actualizar stock"), eq(result));
    }

    @Test
    @DisplayName("shouldUpdateUnitPriceAndEmitEvents")
    void shouldUpdateUnitPriceAndEmitEvents() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateUnitPrice(999, 1200f);

        assertEquals(1200f, result.getUnitPrice());
        verify(inventoryEventPublisher).emitProductoActualizado(result);
        verify(kafkaMockService, atLeast(1)).sendEvent(anyString(), eq(result));
    }

    @Test
    @DisplayName("shouldUpdateDiscountAndEmitEvents")
    void shouldUpdateDiscountAndEmitEvents() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateDiscount(999, 0.2f);

        assertEquals(0.2f, result.getDiscount());
        verify(kafkaMockService, atLeast(1)).sendEvent(anyString(), eq(result));
    }

    @Test
    @DisplayName("shouldDeactivateProductWhenExists")
    void shouldDeactivateProductWhenExists() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));

        boolean result = productService.deleteProduct(999);

        assertTrue(result);
        verify(inventoryEventPublisher).emitProductoDesactivado(any(Product.class));
    }

    @Test
    @DisplayName("shouldReturnFalseWhenProductNotFoundOnDelete")
    void shouldReturnFalseWhenProductNotFoundOnDelete() {
        when(productRepository.findByProductCode(999)).thenThrow(new EmptyResultDataAccessException(1));
        boolean result = productService.deleteProduct(999);
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldActivateProductWhenInactive")
    void shouldActivateProductWhenInactive() {
        existing.setActive(false);
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.activateProduct(999);

        assertTrue(result.isActive());
        verify(inventoryEventPublisher).emitProductoActivado(result);
        verify(kafkaMockService).sendEvent(eq("PATCH: activar producto"), any());
    }

    @Test
    @DisplayName("shouldAddReviewAndUpdateRating")
    void shouldAddReviewAndUpdateRating() {
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.addReview(999, "Excelente calidad", 4.9f);

        assertEquals(1, result.getReviews().size());
        assertEquals(4.9f, result.getCalification());
        verify(productRepository).save(result);
    }
}
