package ar.edu.uade.catalogue.controller;

import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.service.ProductService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getProducts_ShouldReturnList() throws Exception {
        Product product = new Product();
        product.setProductCode(1001);
        product.setName("Phone");

        Mockito.when(productService.getProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/products/getAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value(1001))
                .andExpect(jsonPath("$[0].name").value("Phone"));
    }

    @Test
    void getProductByCode_ShouldReturnProduct() throws Exception {
        Product product = new Product();
        product.setProductCode(1002);
        product.setName("Laptop");

        Mockito.when(productService.getProductByProductCode(1002)).thenReturn(product);

        mockMvc.perform(get("/products/getProductByCode/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    void createProduct_ShouldReturnCreated() throws Exception {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode(1003);
        dto.setName("Tablet");
        dto.setDescription("Test tablet");

        Product product = new Product();
        product.setProductCode(1003);
        product.setName("Tablet");

        Mockito.when(productService.createProduct(any(ProductDTO.class))).thenReturn(product);

        mockMvc.perform(post("/products/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tablet"));
    }

    @Test
    void updateProduct_ShouldReturnOk() throws Exception {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode(1004);
        dto.setName("Updated Phone");

        Product updated = new Product();
        updated.setProductCode(1004);
        updated.setName("Updated Phone");

        Mockito.when(productService.updateProduct(any(ProductDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/products/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Phone"));
    }

    @Test
    void updateStock_ShouldReturnOk() throws Exception {
        Product updated = new Product();
        updated.setProductCode(1005);
        updated.setStock(50);

        Mockito.when(productService.updateStock(eq(1005), eq(50))).thenReturn(updated);

        mockMvc.perform(patch("/products/updateStock/1005")
                        .param("newStock", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    void deleteProduct_ShouldReturnOk() throws Exception {
        Mockito.when(productService.deleteProduct(1006)).thenReturn(true);

        mockMvc.perform(delete("/products/delete/1006"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteProduct_NotFound_ShouldReturn404() throws Exception {
        Mockito.when(productService.deleteProduct(9999)).thenReturn(false);

        mockMvc.perform(delete("/products/delete/9999"))
                .andExpect(status().isNotFound());
    }
}
