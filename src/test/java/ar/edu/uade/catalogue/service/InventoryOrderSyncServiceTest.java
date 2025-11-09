package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class InventoryOrderSyncServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryOrderSyncService inventoryOrderSyncService;

    private ObjectMapper objectMapper;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        existingProduct = new Product();
        existingProduct.setProductCode(111);
        existingProduct.setStock(10);
    }

    // ----------------------------------------------------
    // RESERVE STOCK
    // ----------------------------------------------------

    @Test
    @DisplayName("shouldDecreaseStockWhenProductExistsAndStockSufficient")
    void shouldDecreaseStockWhenProductExistsAndStockSufficient() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": {
                "cartItems": [
                  { "productCode": 111, "quantity": 3 }
                ]
              }
            }
        """);

        when(productRepository.findByProductCode(111)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryOrderSyncService.reserveStock(payload);

        assertEquals(7, existingProduct.getStock());
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("shouldSkipWhenProductDoesNotExistInReserve")
    void shouldSkipWhenProductDoesNotExistInReserve() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 999, "quantity": 5 } ] }
            }
        """);

        when(productRepository.findByProductCode(999)).thenReturn(Optional.empty());

        inventoryOrderSyncService.reserveStock(payload);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("shouldSkipWhenStockInsufficientInReserve")
    void shouldSkipWhenStockInsufficientInReserve() throws Exception {
        existingProduct.setStock(2);

        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 111, "quantity": 5 } ] }
            }
        """);

        when(productRepository.findByProductCode(111)).thenReturn(Optional.of(existingProduct));

        inventoryOrderSyncService.reserveStock(payload);

        // no cambios porque stock insuficiente
        assertEquals(2, existingProduct.getStock());
        verify(productRepository, never()).save(any());
    }

    // ----------------------------------------------------
    // CANCEL RESERVATION
    // ----------------------------------------------------

    @Test
    @DisplayName("shouldIncreaseStockOnCancelWhenProductExists")
    void shouldIncreaseStockOnCancelWhenProductExists() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 111, "quantity": 4 } ] }
            }
        """);

        when(productRepository.findByProductCode(111)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryOrderSyncService.cancelReservation(payload);

        assertEquals(14, existingProduct.getStock());
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("shouldSkipCancelWhenProductDoesNotExist")
    void shouldSkipCancelWhenProductDoesNotExist() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 999, "quantity": 2 } ] }
            }
        """);

        when(productRepository.findByProductCode(999)).thenReturn(Optional.empty());

        inventoryOrderSyncService.cancelReservation(payload);

        verify(productRepository, never()).save(any());
    }

    // ----------------------------------------------------
    // APPLY ROLLBACK
    // ----------------------------------------------------

    @Test
    @DisplayName("shouldReplenishStockOnRollback")
    void shouldReplenishStockOnRollback() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 111, "quantity": 3 } ] }
            }
        """);

        when(productRepository.findByProductCode(111)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryOrderSyncService.applyRollback(payload);

        assertEquals(13, existingProduct.getStock());
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("shouldSkipRollbackWhenProductDoesNotExist")
    void shouldSkipRollbackWhenProductDoesNotExist() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            {
              "cart": { "cartItems": [ { "productCode": 999, "quantity": 5 } ] }
            }
        """);

        when(productRepository.findByProductCode(999)).thenReturn(Optional.empty());

        inventoryOrderSyncService.applyRollback(payload);

        verify(productRepository, never()).save(any());
    }

    // ----------------------------------------------------
    // CONFIRM STOCK
    // ----------------------------------------------------

    @Test
    @DisplayName("shouldJustLogOnConfirmWithoutRepositoryInteractions")
    void shouldJustLogOnConfirmWithoutRepositoryInteractions() throws Exception {
        JsonNode payload = objectMapper.readTree("""
            { "cart": { "cartItems": [ { "productCode": 111, "quantity": 2 } ] } }
        """);

        inventoryOrderSyncService.confirmStock(payload);

        // confirmStock no toca la base de datos
        verifyNoInteractions(productRepository);
    }

    // ----------------------------------------------------
    // EDGE CASES - NULL / EMPTY
    // ----------------------------------------------------

    @Test
    @DisplayName("shouldHandleNullPayloadGracefully")
    void shouldHandleNullPayloadGracefully() {
        inventoryOrderSyncService.reserveStock(null);
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("shouldHandleEmptyCartGracefully")
    void shouldHandleEmptyCartGracefully() throws Exception {
        JsonNode payload = objectMapper.readTree("""
        { "cart": {} }
    """);
        inventoryOrderSyncService.reserveStock(payload);
        verifyNoInteractions(productRepository);
    }

}
