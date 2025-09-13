package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Event;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaMockService kafkaMockService;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private CategoryDTO categoryDTO;
    private Product product;

    @BeforeEach
    void setup() {
        category = new Category();
        category.setId(1);
        category.setName("Laptops");
        category.setProducts(new ArrayList<>(List.of(1001)));
        category.setActive(true);

        categoryDTO = new CategoryDTO();
        categoryDTO.setName("Tablets");
        categoryDTO.setActive(true);

        product = new Product();
        product.setProductCode(1001);
        product.setName("iPad");
    }

    @Test
    void getCategories_ShouldReturnList() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<Category> result = categoryService.getCategories();

        assertEquals(1, result.size());
    }

    @Test
    void getAllProductsFromCategory_ShouldReturnProducts() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));

        List<Product> result = categoryService.getAllProductsFromCategory(1);

        assertEquals(1, result.size());
        assertEquals("iPad", result.get(0).getName());
    }

    @Test
    void getCategoryByID_ShouldReturnCategory() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryByID(1);

        assertNotNull(result);
        assertEquals("Laptops", result.getName());
    }

    @Test
    void geCategoriesForProductByID_ShouldReturnCategories() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        List<Category> result = categoryService.geCategoriesForProductByID(List.of(1));

        assertEquals(1, result.size());
    }

    @Test
    void createCategory_ShouldSaveAndReturnCategory() {
        when(categoryRepository.save(any(Category.class))).thenReturn(category);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("POST", "{}"));

        Category saved = categoryService.createCategory(categoryDTO);

        assertNotNull(saved);
        assertEquals("Laptops", saved.getName());
    }

    @Test
    void addProductToCategories_ShouldUpdateCategories() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
       // when(productRepository.findByProductCode(1001)).thenReturn(Optional.of(product));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        categoryService.addProductToCategories(1001, List.of(1));

        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void deleteCategory_ShouldDeactivate() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenReturn(category);
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event("PATCH", "{}"));

        boolean deleted = categoryService.deleteCategory(1);

        assertTrue(deleted);
        assertFalse(category.isActive());
    }
}
