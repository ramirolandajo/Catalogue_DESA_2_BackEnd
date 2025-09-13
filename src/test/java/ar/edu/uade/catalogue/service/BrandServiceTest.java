package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.repository.BrandRepository;
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
class BrandServiceTest {

    @Mock
    BrandRepository brandRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    KafkaMockService kafkaMockService;

    @InjectMocks
    BrandService brandService;

    private Brand brand;
    private Product product;

    @BeforeEach
    void setUp() {
        brand = new Brand();
        brand.setId(1);
        brand.setName("Sony");
        brand.setProducts(List.of(1001));
        brand.setActive(true);

        product = new Product();
        product.setProductCode(1001);
        product.setName("PlayStation");
    }

    @Test
    void createBrand_ShouldSaveAndReturn() {
        BrandDTO dto = new BrandDTO();
        dto.setName("Apple");
        dto.setActive(true);

        when(brandRepository.save(any(Brand.class))).thenReturn(brand);

        Brand saved = brandService.createBrand(dto);

        assertNotNull(saved);
        assertEquals("Sony", saved.getName());
        verify(brandRepository, times(1)).save(any(Brand.class));
        verify(kafkaMockService, times(1)).sendEvent(eq("POST: Marca creada"), any(Brand.class));
    }

    @Test
    void getBrands_ShouldReturnList() {
        when(brandRepository.findAll()).thenReturn(List.of(brand));

        List<Brand> result = brandService.getBrands();

        assertEquals(1, result.size());
        assertEquals("Sony", result.get(0).getName());
    }

    @Test
    void getProductsFromBrand_ShouldReturnProducts() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        List<Product> result = brandService.getProductsFromBrand(1);

        assertEquals(1, result.size());
        assertEquals("PlayStation", result.get(0).getName());
    }

    @Test
    void deleteBrand_ShouldDeactivate() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));

        boolean result = brandService.deleteBrand(1);

        assertTrue(result);
        assertFalse(brand.isActive());
        verify(brandRepository, times(1)).save(brand);
        verify(kafkaMockService, times(1)).sendEvent(eq("PATCH: Marca desactivada"), any(Brand.class));
    }
}
