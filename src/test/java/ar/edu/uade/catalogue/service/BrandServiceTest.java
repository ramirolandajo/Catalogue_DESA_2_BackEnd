package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Event;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaMockService kafkaMockService;

    @InjectMocks
    private BrandService brandService;

    private Brand brand;
    private BrandDTO brandDTO;
    private Product product;

    @BeforeEach
    void setup() {
        brand = new Brand();
        brand.setId(1);
        brand.setName("Sony");
        brand.setProducts(new ArrayList<>(List.of(1001)));
        brand.setActive(true);

        brandDTO = new BrandDTO();
        brandDTO.setName("Apple");
        brandDTO.setActive(true);

        product = new Product();
        product.setProductCode(1001);
        product.setName("MacBook");
    }

    @Test
    void getBrands_ShouldReturnList() {
        when(brandRepository.findAll()).thenReturn(List.of(brand));

        List<Brand> result = brandService.getBrands();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getProductsFromBrand_ShouldReturnProducts() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        List<Product> result = brandService.getProductsFromBrand(1);

        assertEquals(1, result.size());
        assertEquals("MacBook", result.get(0).getName());
    }

    @Test
    void getBrandByID_ShouldReturnBrand() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));

        Brand result = brandService.getBrandByID(1);

        assertNotNull(result);
        assertEquals("Sony", result.getName());
    }

    @Test
    void addProductToBrand_ShouldUpdateAndSave() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
       //when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        brandService.addProductToBrand(1001, 1);

        verify(brandRepository, times(1)).save(any(Brand.class));
    }

    @Test
    void createBrand_ShouldSaveAndReturnBrand() {
        when(brandRepository.save(any(Brand.class))).thenReturn(brand);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("POST", "{}"));

        Brand saved = brandService.createBrand(brandDTO);

        assertNotNull(saved);
        assertEquals("Sony", saved.getName());
    }

    @Test
    void deleteBrand_ShouldDeactivate() {
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.save(any(Brand.class))).thenReturn(brand);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        boolean deleted = brandService.deleteBrand(1);

        assertTrue(deleted);
        assertFalse(brand.isActive());
    }
}
