package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;
import ar.edu.uade.catalogue.model.*;
import ar.edu.uade.catalogue.model.DTO.CategoryDTO;
import ar.edu.uade.catalogue.repository.CategoryRepository;
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
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private KafkaMockService kafkaMockService;
    @Mock private InventoryEventPublisher inventoryEventPublisher;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1);
        category.setCategoryCode(200);
        category.setName("Electrónica");
        category.setActive(true);
        category.setProducts(new ArrayList<>());

        product = new Product();
        product.setId(1);
        product.setProductCode(999);
        product.setName("Smartphone");
    }

    // ========================
    // MÉTODOS PRINCIPALES
    // ========================

    @Test
    @DisplayName("shouldReturnAllCategoriesWhenRepositoryHasData")
    void shouldReturnAllCategoriesWhenRepositoryHasData() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<Category> result = categoryService.getCategories();

        assertEquals(1, result.size());
        assertEquals("Electrónica", result.get(0).getName());
        verify(categoryRepository).findAll();
    }

    @Test
    @DisplayName("shouldReturnAllProductsFromCategoryWhenExists")
    void shouldReturnAllProductsFromCategoryWhenExists() {
        category.setProducts(List.of(999));
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(productRepository.findByProductCode(999)).thenReturn(Optional.of(product));

        List<Product> result = categoryService.getAllProductsFromCategory(1);

        assertEquals(1, result.size());
        assertEquals("Smartphone", result.get(0).getName());
        verify(productRepository).findByProductCode(999);
    }

    @Test
    @DisplayName("shouldReturnCategoryByIdWhenExists")
    void shouldReturnCategoryByIdWhenExists() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryByID(1);

        assertNotNull(result);
        assertEquals("Electrónica", result.getName());
    }

    @Test
    @DisplayName("shouldReturnCategoryByCodeWhenExists")
    void shouldReturnCategoryByCodeWhenExists() {
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryByCode(200);

        assertEquals("Electrónica", result.getName());
    }

    @Test
    @DisplayName("shouldReturnEmptyListWhenNoCategoriesFoundByIDs")
    void shouldReturnEmptyListWhenNoCategoriesFoundByIDs() {
        List<Category> result = categoryService.geCategoriesForProductByID(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("shouldReturnCategoriesWhenFoundByIDs")
    void shouldReturnCategoriesWhenFoundByIDs() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        List<Category> result = categoryService.geCategoriesForProductByID(List.of(1));

        assertEquals(1, result.size());
        verify(categoryRepository).findById(1);
    }

    @Test
    @DisplayName("shouldReturnCategoriesWhenFoundByCodes")
    void shouldReturnCategoriesWhenFoundByCodes() {
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));

        List<Category> result = categoryService.geCategoriesForProductByCodes(List.of(200));

        assertEquals(1, result.size());
        verify(categoryRepository).findByCategoryCode(200);
    }

    @Test
    @DisplayName("shouldCreateCategoryWhenValidDataProvided")
    void shouldCreateCategoryWhenValidDataProvided() {
        CategoryDTO dto = new CategoryDTO();
        dto.setCategoryCode(201);
        dto.setName("Hogar");
        dto.setActive(true);

        when(categoryRepository.existsByCategoryCode(201)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        Category saved = categoryService.createCategory(dto);

        assertEquals("Hogar", saved.getName());
        assertTrue(saved.isActive());
        verify(inventoryEventPublisher).emitCategoriaCreada(saved);
        verify(kafkaMockService).sendEvent(eq("POST: Categoría creada"), any(Category.class));
    }

    @Test
    @DisplayName("shouldThrowWhenCategoryCodeMissingOnCreate")
    void shouldThrowWhenCategoryCodeMissingOnCreate() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Hogar");

        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(dto));
    }

    @Test
    @DisplayName("shouldThrowWhenCategoryCodeAlreadyExistsOnCreate")
    void shouldThrowWhenCategoryCodeAlreadyExistsOnCreate() {
        CategoryDTO dto = new CategoryDTO();
        dto.setCategoryCode(200);
        when(categoryRepository.existsByCategoryCode(200)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(dto));
    }

    @Test
    @DisplayName("shouldAddProductToCategoriesByIDsWhenExists")
    void shouldAddProductToCategoriesByIDsWhenExists() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        categoryService.addProductToCategories(999, List.of(1));

        assertTrue(category.getProducts().contains(999));
        verify(categoryRepository).save(category);
    }

    @Test
    @DisplayName("shouldAddProductToCategoriesByCodesWhenExists")
    void shouldAddProductToCategoriesByCodesWhenExists() {
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));

        categoryService.addProductToCategoriesByCodes(999, List.of(200));

        assertTrue(category.getProducts().contains(999));
        verify(categoryRepository).save(category);
    }

    @Test
    @DisplayName("shouldDeactivateCategoryByIdWhenExists")
    void shouldDeactivateCategoryByIdWhenExists() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        boolean result = categoryService.deleteCategory(1);

        assertTrue(result);
        assertFalse(category.isActive());
        verify(inventoryEventPublisher).emitCategoriaDesactivada(any(Category.class));
    }

    @Test
    @DisplayName("shouldReturnFalseWhenCategoryNotFoundOnDeleteById")
    void shouldReturnFalseWhenCategoryNotFoundOnDeleteById() {
        when(categoryRepository.findById(1)).thenReturn(Optional.empty());

        boolean result = categoryService.deleteCategory(1);

        assertFalse(result);
    }

    @Test
    @DisplayName("shouldDeactivateCategoryByCodeWhenExists")
    void shouldDeactivateCategoryByCodeWhenExists() {
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaMockService.sendEvent(anyString(), any())).thenReturn(new Event());

        boolean result = categoryService.deleteCategoryByCode(200);

        assertTrue(result);
        assertFalse(category.isActive());
        verify(inventoryEventPublisher).emitCategoriaDesactivada(any(Category.class));
    }

    @Test
    @DisplayName("shouldReturnFalseWhenCategoryNotFoundOnDeleteByCode")
    void shouldReturnFalseWhenCategoryNotFoundOnDeleteByCode() {
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.empty());

        boolean result = categoryService.deleteCategoryByCode(200);

        assertFalse(result);
    }

    @Test
    @DisplayName("shouldActivateCategoryWhenInactive")
    void shouldActivateCategoryWhenInactive() {
        category.setActive(false);
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.activateCategoryByCode(200);

        assertTrue(result.isActive());
        verify(inventoryEventPublisher).emitCategoriaActivada(result);
        verify(kafkaMockService).sendEvent(eq("PATCH: Categoria activada"), any());
    }

    @Test
    @DisplayName("shouldThrowWhenActivatingAlreadyActiveCategory")
    void shouldThrowWhenActivatingAlreadyActiveCategory() {
        category.setActive(true);
        when(categoryRepository.findByCategoryCode(200)).thenReturn(Optional.of(category));

        assertThrows(IllegalStateException.class, () -> categoryService.activateCategoryByCode(200));
    }
}
