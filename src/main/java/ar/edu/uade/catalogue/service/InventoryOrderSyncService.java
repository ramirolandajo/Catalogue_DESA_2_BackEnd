package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryOrderSyncService {
    private static final Logger log = LoggerFactory.getLogger(InventoryOrderSyncService.class);

    private final ProductRepository productRepository;

    public InventoryOrderSyncService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * POST: Compra pendiente -> descontar stock inmediato (modelo simple sin reservas persistidas).
     * Tolerante a productos inexistentes o stock insuficiente (loggear y continuar).
     */
    public void reserveStock(JsonNode payload) {
        for (Item it : iterateItems(payload)) {
            Optional<Product> opt = productRepository.findByProductCode(it.productCode());
            if (opt.isEmpty()) {
                log.warn("[Inventario][CompraPendiente] Producto inexistente productCode={}, se omite.", it.productCode());
                continue;
            }
            Product p = opt.get();
            int newStock = p.getStock() - it.quantity();
            if (newStock < 0) {
                log.warn("[Inventario][CompraPendiente] Stock insuficiente productCode={} actual={} pedido={}, se omite.", p.getProductCode(), p.getStock(), it.quantity());
                continue;
            }
            p.setStock(newStock);
            productRepository.save(p);
            log.info("[Inventario][CompraPendiente] productCode={} qty={} newStock={}", it.productCode(), it.quantity(), newStock);
        }
    }

    /**
     * POST: Compra confirmada -> no-op (ya se descontó en pendiente)
     */
    public void confirmStock(JsonNode payload) {
        log.info("[Inventario][CompraConfirmada] Sin cambios de stock (ya descontado en pendiente)");
    }

    /**
     * DELETE: Compra cancelada -> reponer stock (compat)
     */
    public void cancelReservation(JsonNode payload) {
        for (Item it : iterateItems(payload)) {
            Optional<Product> opt = productRepository.findByProductCode(it.productCode());
            if (opt.isEmpty()) {
                log.warn("[Inventario][CompraCancelada] Producto inexistente productCode={}, se omite.", it.productCode());
                continue;
            }
            Product p = opt.get();
            int newStock = p.getStock() + it.quantity();
            p.setStock(newStock);
            productRepository.save(p);
            log.info("[Inventario][CompraCancelada] productCode={} qty={} newStock={}", it.productCode(), it.quantity(), newStock);
        }
    }

    /**
     * POST: Stock rollback - compra cancelada -> reponer stock
     */
    public void applyRollback(JsonNode payload) {
        for (Item it : iterateItems(payload)) {
            Optional<Product> opt = productRepository.findByProductCode(it.productCode());
            if (opt.isEmpty()) {
                log.warn("[Inventario][Rollback] Producto inexistente productCode={}, se omite.", it.productCode());
                continue;
            }
            Product p = opt.get();
            int newStock = p.getStock() + it.quantity();
            p.setStock(newStock);
            productRepository.save(p);
            log.info("[Inventario][Rollback] productCode={} qty={} newStock={}", it.productCode(), it.quantity(), newStock);
        }
    }

    // Helpers
    private record Item(int productCode, int quantity) {}

    private Iterable<Item> iterateItems(JsonNode payload) {
        java.util.List<Item> out = new java.util.ArrayList<>();
        if (payload == null) return out;
        JsonNode cart = payload.get("cart");
        if (cart == null || cart.isNull()) return out;
        JsonNode items = cart.has("cartItems") ? cart.get("cartItems") : cart.get("items");
        if (items == null || !items.isArray()) return out;
        for (JsonNode n : items) {
            int code = 0;
            if (n.has("productCode")) code = n.get("productCode").asInt();
            else if (n.has("productId")) code = n.get("productId").asInt();
            int qty = n.has("quantity") ? n.get("quantity").asInt() : 0;
            if (code > 0 && qty > 0) out.add(new Item(code, qty));
        }
        return out;
    }
}
