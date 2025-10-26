package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;
import ar.edu.uade.catalogue.model.*;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.repository.BrandRepository;
import ar.edu.uade.catalogue.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BrandServiceTest {

    @Mock private BrandRepository brandRepository;
    @Mock private ProductRepository productRepository;
    @Mock private KafkaMockService kafkaMockService;
    @Mock private InventoryEventPublisher inventoryEventPublisher;

    @InjectMocks
    private BrandService brandService;

    private Brand brand;
    private Product product;

    @BeforeEach
    void setUp() {
        brand = new Brand();
        brand.setId(1);
        brand.setBrandCode(101);
        brand.setName("Samsung");
        brand.setActive(true);
        brand.setProducts(new ArrayList<>());

        product = new Product();
        product.setId(1);
        product.setProductCode(1001);
        product.setName("Smart TV");
    }

    // ================
    // MÉTODOS PRINCIPALES
    // ================

    @Test
    @DisplayName("shouldReturnAllBrandsWhenRepositoryHasData")
    void shouldReturnAllBrandsWhenRepositoryHasData() {
        when(brandRepository.findAll()).thenReturn(List.of(brand));

        List<Brand> result = brandService.getBrands();

        assertEquals(1, result.size());
        assertEquals("Samsung", result.get(0).getName());
        verify(brandRepository).findAll();
    }

    @Test
    @DisplayName("shouldReturnProductsFromBrandWhenBrandExists")
    void shouldReturnProductsFromBrandWhenBrandExists() {
        brand.setProducts(List.of(1001));
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        List<Product> products = brandService.getProductsFromBrand(1);

        assertEquals(1, products.size());
        assertEquals("Smart TV", products.get(0).getName());
        verify(productRepository).findByProductCode(1001);
    }

    @Test
    @DisplayName("shouldThrowWhenBrandNotFoundOnGetProductsFromBrand")
    void shouldThrowWhenBrandNotFoundOnGetProductsFromBrand() {
        when(brandRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(EmptyResultDataAccessException.class, () -> brandService.getProductsFromBrand(1));
    }

    @Test
    @DisplayName("shouldGetBrandByIdWhenExists")
    void shouldGetBrandByIdWhenExists() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        Brand result = brandService.getBrandByID(1);
        assertNotNull(result);
        assertEquals("Samsung", result.getName());
    }

    @Test
    @DisplayName("shouldGetBrandByCodeWhenExists")
    void shouldGetBrandByCodeWhenExists() {
        when(brandRepository.findByBrandCode(101)).thenReturn(Optional.of(brand));
        Brand result = brandService.getBrandByCode(101);
        assertEquals("Samsung", result.getName());
    }

    @Test
    @DisplayName("shouldAddProductToBrandWhenBrandExistsAndProductNotPresent")
    void shouldAddProductToBrandWhenBrandExistsAndProductNotPresent() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        brandService.addProductToBrand(1001, 1);

        assertTrue(brand.getProducts().contains(1001));
        verify(kafkaMockService).sendEvent(contains("PATCH: producto 1001"), any(Brand.class));
        verify(brandRepository).save(brand);
    }

    @Test
    @DisplayName("shouldThrowWhenAddingProductToNonExistingBrand")
    void shouldThrowWhenAddingProductToNonExistingBrand() {
        when(brandRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(EmptyResultDataAccessException.class, () -> brandService.addProductToBrand(1001, 1));
    }

    @Test
    @DisplayName("shouldCreateBrandWhenValidDataProvided")
    void shouldCreateBrandWhenValidDataProvided() {
        BrandDTO dto = new BrandDTO();
        dto.setBrandCode(200);
        dto.setName("LG");
        dto.setActive(true);

        when(brandRepository.existsByBrandCode(200)).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        Brand saved = brandService.createBrand(dto);

        assertEquals("LG", saved.getName());
        assertTrue(saved.isActive());
        verify(inventoryEventPublisher).emitMarcaCreada(saved);
        verify(kafkaMockService).sendEvent(eq("POST: Marca creada"), any(Brand.class));
    }

    @Test
    @DisplayName("shouldThrowWhenBrandCodeIsMissingOnCreate")
    void shouldThrowWhenBrandCodeIsMissingOnCreate() {
        BrandDTO dto = new BrandDTO();
        dto.setName("LG");
        assertThrows(IllegalArgumentException.class, () -> brandService.createBrand(dto));
    }

    @Test
    @DisplayName("shouldThrowWhenBrandCodeAlreadyExistsOnCreate")
    void shouldThrowWhenBrandCodeAlreadyExistsOnCreate() {
        BrandDTO dto = new BrandDTO();
        dto.setBrandCode(101);
        when(brandRepository.existsByBrandCode(101)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> brandService.createBrand(dto));
    }

    @Test
    @DisplayName("shouldActivateBrandWhenInactive")
    void shouldActivateBrandWhenInactive() {
        brand.setActive(false);
        when(brandRepository.findByBrandCode(101)).thenReturn(Optional.of(brand));
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        Brand result = brandService.activateBrandByCode(101);

        assertTrue(result.isActive());
        verify(inventoryEventPublisher).emitMarcaActivada(result);
        verify(kafkaMockService).sendEvent(eq("PATCH: Marca activada"), any());
    }

    @Test
    @DisplayName("shouldThrowWhenActivatingAlreadyActiveBrand")
    void shouldThrowWhenActivatingAlreadyActiveBrand() {
        brand.setActive(true);
        when(brandRepository.findByBrandCode(101)).thenReturn(Optional.of(brand));
        assertThrows(IllegalStateException.class, () -> brandService.activateBrandByCode(101));
    }

    @Test
    @DisplayName("shouldDeactivateBrandByCodeWhenExists")
    void shouldDeactivateBrandByCodeWhenExists() {
        when(brandRepository.findByBrandCode(101)).thenReturn(Optional.of(brand));
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        boolean result = brandService.deleteBrandByCode(101);

        assertTrue(result);
        assertFalse(brand.isActive());
        verify(inventoryEventPublisher).emitMarcaDesactivada(any(Brand.class));
    }

    @Test
    @DisplayName("shouldReturnFalseWhenDeletingBrandByCodeNotFound")
    void shouldReturnFalseWhenDeletingBrandByCodeNotFound() {
        when(brandRepository.findByBrandCode(101)).thenReturn(Optional.empty());
        boolean result = brandService.deleteBrandByCode(101);
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldDeactivateBrandByIdWhenExists")
    void shouldDeactivateBrandByIdWhenExists() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        boolean result = brandService.deleteBrand(1);

        assertTrue(result);
        assertFalse(brand.isActive());
        verify(inventoryEventPublisher).emitMarcaDesactivada(any(Brand.class));
    }

    @Test
    @DisplayName("shouldReturnFalseWhenDeletingBrandByIdNotFound")
    void shouldReturnFalseWhenDeletingBrandByIdNotFound() {
        when(brandRepository.findById(1)).thenReturn(Optional.empty());
        boolean result = brandService.deleteBrand(1);
        assertFalse(result);
    }
}
