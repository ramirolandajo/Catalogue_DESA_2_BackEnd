package ar.edu.uade.catalogue.controller;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.service.BrandService;
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
@WebMvcTest(BrandController.class)
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrandService brandService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getBrands_ShouldReturnList() throws Exception {
        Brand brand = new Brand();
        brand.setId(1);
        brand.setName("Sony");
        brand.setActive(true);

        Mockito.when(brandService.getBrands()).thenReturn(List.of(brand));

        mockMvc.perform(get("/brand/getAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sony"));
    }

    @Test
    void getBrandByID_ShouldReturnBrand() throws Exception {
        Brand brand = new Brand();
        brand.setId(2);
        brand.setName("Apple");

        Mockito.when(brandService.getBrandByID(2)).thenReturn(brand);

        mockMvc.perform(get("/brand/getBrandByID/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apple"));
    }

    @Test
    void getProductsFromBrand_ShouldReturnProducts() throws Exception {
        Product p = new Product();
        p.setProductCode(1001);
        p.setName("MacBook");

        Mockito.when(brandService.getProductsFromBrand(2)).thenReturn(List.of(p));

        mockMvc.perform(get("/brand/getProducts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MacBook"));
    }

    @Test
    void createBrand_ShouldReturnCreated() throws Exception {
        BrandDTO dto = new BrandDTO();
        dto.setName("Samsung");
        dto.setActive(true);

        Brand brand = new Brand();
        brand.setId(3);
        brand.setName("Samsung");
        brand.setActive(true);

        Mockito.when(brandService.createBrand(any(BrandDTO.class))).thenReturn(brand);

        mockMvc.perform(post("/brand/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Samsung"));
    }

    @Test
    void deleteBrand_ShouldReturnOk() throws Exception {
        Mockito.when(brandService.deleteBrand(4)).thenReturn(true);

        mockMvc.perform(delete("/brand/delete/4"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBrand_NotFound_ShouldReturn404() throws Exception {
        Mockito.when(brandService.deleteBrand(9999)).thenReturn(false);

        mockMvc.perform(delete("/brand/delete/9999"))
                .andExpect(status().isNotFound());
    }
}
