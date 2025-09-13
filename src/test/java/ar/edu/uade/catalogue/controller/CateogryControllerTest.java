package ar.edu.uade.catalogue.controller;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.CategoryDTO;
import ar.edu.uade.catalogue.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getCategories_ShouldReturnList() throws Exception {
        Category category = new Category();
        category.setId(1);
        category.setName("Electrónica");
        category.setActive(true);

        Mockito.when(categoryService.getCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/category/getAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Electrónica"));
    }

    @Test
    void getCategoryByID_ShouldReturnCategory() throws Exception {
        Category category = new Category();
        category.setId(2);
        category.setName("Hogar");

        Mockito.when(categoryService.getCategoryByID(2)).thenReturn(category);

        mockMvc.perform(get("/category/getCategoryByID/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hogar"));
    }

    @Test
    void getProductsFromCategory_ShouldReturnProducts() throws Exception {
        Product p = new Product();
        p.setProductCode(1001);
        p.setName("Heladera");

        Mockito.when(categoryService.getAllProductsFromCategory(2)).thenReturn(List.of(p));

        mockMvc.perform(get("/category/getProducts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Heladera"));
    }

    @Test
    void createCategory_ShouldReturnCreated() throws Exception {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Deportes");
        dto.setActive(true);

        Category category = new Category();
        category.setId(3);
        category.setName("Deportes");
        category.setActive(true);

        Mockito.when(categoryService.createCategory(any(CategoryDTO.class))).thenReturn(category);

        mockMvc.perform(post("/category/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Deportes"));
    }

    @Test
    void deleteCategory_ShouldReturnOk() throws Exception {
        Mockito.when(categoryService.deleteCategory(4)).thenReturn(true);

        mockMvc.perform(delete("/category/delete/4"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCategory_NotFound_ShouldReturn404() throws Exception {
        Mockito.when(categoryService.deleteCategory(9999)).thenReturn(false);

        mockMvc.perform(delete("/category/delete/9999"))
                .andExpect(status().isNotFound());
    }
}
