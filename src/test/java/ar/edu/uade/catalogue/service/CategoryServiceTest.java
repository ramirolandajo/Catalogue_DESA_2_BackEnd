package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.CategoryDTO;
import ar.edu.uade.catalogue.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    KafkaMockService kafkaMockService;

    @InjectMocks
    CategoryService categoryService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1);
        category.setName("Electrónica");
        category.setProducts(List.of(1001));
        category.setActive(true);

        product = new Product();
        product.setProductCode(1001);
        product.setName("TV 4K");
    }

    @Test
    void createCategory_ShouldSaveAndReturn() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Hogar");
        dto.setActive(true);

        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        Category saved = categoryService.createCategory(dto);

        assertNotNull(saved);
        assertEquals("Electrónica", saved.getName());
        verify(categoryRepository, times(1)).save(any(Category.class));
        verify(kafkaMockService, times(1)).sendEvent(eq("POST: Cateogria creada"), any(Category.class));
    }

    @Test
    void getCategories_ShouldReturnList() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<Category> result = categoryService.getCategories();

        assertEquals(1, result.size());
        assertEquals("Electrónica", result.get(0).getName());
    }

    @Test
    void getProductsFromCategory_ShouldReturnProducts() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        List<Product> result = categoryService.getAllProductsFromCategory(1);

        assertEquals(1, result.size());
        assertEquals("TV 4K", result.get(0).getName());
    }

    @Test
    void deleteCategory_ShouldDeactivate() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        boolean result = categoryService.deleteCategory(1);

        assertTrue(result);
        assertFalse(category.isActive());
        verify(categoryRepository, times(1)).save(category);
        verify(kafkaMockService, times(1)).sendEvent(eq("PATCH: Categoria desactivada"), any(Category.class));
    }
}
