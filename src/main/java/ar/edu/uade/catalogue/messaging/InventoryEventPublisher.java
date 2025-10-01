package ar.edu.uade.catalogue.messaging;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final CoreApiClient coreApiClient;

    @Value("${communication.enabled:true}")
    private boolean commEnabled;

    public InventoryEventPublisher(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    private boolean shouldEmit(String type) {
        if (!commEnabled) {
            log.info("[CoreApi][Skip] Emision deshabilitada (communication.enabled=false). type='{}'", type);
            return false;
        }
        return true;
    }

    public void emitActualizarStock(Product p) {
        String type = "PUT: Actualizar stock";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("productCode", p.getProductCode());
        payload.put("id", p.getId());
        payload.put("nombre", p.getName());
        payload.put("stock", p.getStock());
        payload.put("description", p.getDescription());
        payload.put("price", p.getPrice());
        payload.put("unit_price", p.getUnitPrice());
        payload.put("unitPrice", p.getUnitPrice());
        payload.put("calification", p.getCalification());
        payload.put("image", Optional.ofNullable(p.getImages()).filter(l->!l.isEmpty()).map(l->l.get(0)).orElse(null));
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitAgregarProducto(Product p) {
        String type = "POST: Agregar un producto";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = buildProductPayload(p);
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitProductoActualizado(Product p) {
        String type = "PATCH: modificar un producto";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = buildProductPayload(p);
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitProductoDesactivado(Product p) {
        String type = "PATCH: Producto desactivado";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = buildProductPayload(p);
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitProductoActivado(Product p) {
        String type = "PATCH: activar producto"; // cambio solicitado
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("productCode", p.getProductCode());
        payload.put("id", p.getId());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitMarcaCreada(Brand b) {
        String type = "POST: Marca creada";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", b.getId());
        payload.put("brandCode", b.getBrandCode());
        payload.put("name", b.getName());
        payload.put("products", b.getProducts());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitCategoriaCreada(Category c) {
        String type = "POST: Categoría creada";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", c.getId());
        payload.put("categoryCode", c.getCategoryCode());
        payload.put("name", c.getName());
        payload.put("products", c.getProducts());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitMarcaDesactivada(Brand b) {
        String type = "PATCH: Marca desactivada";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", b.getId());
        payload.put("brandCode", b.getBrandCode());
        payload.put("name", b.getName());
        payload.put("products", b.getProducts());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitCategoriaDesactivada(Category c) {
        String type = "PATCH: Categoria desactivada";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", c.getId());
        payload.put("categoryCode", c.getCategoryCode());
        payload.put("name", c.getName());
        payload.put("products", c.getProducts());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    public void emitMarcaActivada(Brand b) {
        String type = "PATCH: Marca activada";
        if (!shouldEmit(type)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", b.getId());
        payload.put("brandCode", b.getBrandCode());
        payload.put("name", b.getName());
        payload.put("products", b.getProducts());
        coreApiClient.postEvent(type, payload, OffsetDateTime.now());
    }

    private Map<String, Object> buildProductPayload(Product p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", p.getId());
        payload.put("productCode", p.getProductCode());
        payload.put("name", p.getName());
        payload.put("description", p.getDescription());
        payload.put("unit_price", p.getUnitPrice());
        payload.put("unitPrice", p.getUnitPrice());
        payload.put("price", p.getPrice());
        payload.put("discount", p.getDiscount());
        payload.put("stock", p.getStock());
        // Códigos de categorías prioritarios + compat
        List<Integer> categoryCodes = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getCategoryCode).collect(Collectors.toList());
        List<Integer> categoryIds = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getId).collect(Collectors.toList());
        payload.put("categories", categoryCodes);
        payload.put("categoryIds", categoryIds);
        // Marca por código prioritario + compat
        Integer brandCode = p.getBrand() == null ? null : p.getBrand().getBrandCode();
        Integer brandId = p.getBrand() == null ? null : p.getBrand().getId();
        payload.put("brand", brandCode);
        payload.put("brandId", brandId);
        payload.put("calification", p.getCalification());
        payload.put("images", p.getImages());
        payload.put("new", p.isNew());
        payload.put("bestSeller", p.isBestSeller());
        payload.put("featured", p.isFeatured());
        payload.put("hero", p.isHero());
        payload.put("active", p.isActive());
        return payload;
    }
}
