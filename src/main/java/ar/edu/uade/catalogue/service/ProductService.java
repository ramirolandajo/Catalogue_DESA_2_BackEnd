package ar.edu.uade.catalogue.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.DTO.ProductPatchDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.repository.ProductRepository;
import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;

@Service
public class ProductService {

    @Autowired
    ProductRepository productRepository;
    
    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    KafkaMockService kafkaMockService;

    @Autowired
    InventoryEventPublisher inventoryEventPublisher;

    public List<Product>getProducts(){
        return productRepository.findAll();
    }

    public Product getProductByProductCode(Integer productCode){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        return productOptional.orElse(null);
    }

    public Product createProduct(ProductDTO productDTO){
        // Resolver categorías por códigos (prioritario) o por IDs legacy
        List<Category> categoriesToSave = (productDTO.getCategoryCodes() != null && !productDTO.getCategoryCodes().isEmpty())
                ? categoryService.geCategoriesForProductByCodes(productDTO.getCategoryCodes())
                : categoryService.geCategoriesForProductByID(productDTO.getCategories());
        // Resolver marca por brandCode (prioritario) o por ID legacy
        Brand brandToSave = (productDTO.getBrandCode() != null)
                ? brandService.getBrandByCode(productDTO.getBrandCode())
                : brandService.getBrandByID(productDTO.getBrand());

        // Calcular precio con descuento correctamente
        float priceWithDiscount = computePrice(productDTO.getUnitPrice(), productDTO.getDiscount());

        Product productToSave = new Product();
        
        productToSave.setProductCode(productDTO.getProductCode());
        productToSave.setName(productDTO.getName());
        productToSave.setDescription(productDTO.getDescription());
        productToSave.setPrice(priceWithDiscount);
        productToSave.setUnitPrice(productDTO.getUnitPrice());
        productToSave.setDiscount(productDTO.getDiscount());
        productToSave.setStock(productDTO.getStock());
        productToSave.setCalification(productDTO.getCalification());
        productToSave.setCategories(categoriesToSave);
        productToSave.setBrand(brandToSave);
        productToSave.setImages(productDTO.getImages());
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());
        productToSave.setActive(productDTO.isActive());

        productRepository.save(productToSave);

        // Agregamos el producto a las categorias a las que pertenece usando códigos si se enviaron
        if (productDTO.getCategoryCodes() != null && !productDTO.getCategoryCodes().isEmpty()) {
            categoryService.addProductToCategoriesByCodes(productDTO.getProductCode(), productDTO.getCategoryCodes());
        } else {
            categoryService.addProductToCategories(productDTO.getProductCode(), productDTO.getCategories());
        }

        // Agregamos el producto a la marca a la que pertenece (legacy por ID; mantengo para no romper)
        if (productDTO.getBrand() != null) {
            brandService.addProductToBrand(productDTO.getProductCode(), productDTO.getBrand());
        }

        // Emitimos hacia middleware SOLO "POST: Agregar un producto" y persistimos ese mismo evento
        inventoryEventPublisher.emitAgregarProducto(productToSave);
        kafkaMockService.sendEvent("POST: Agregar un producto", productToSave);

        return productToSave;
    }

    public boolean loadBatchFromCSV(MultipartFile csvFile) throws Exception {
        List<ProductDTO> items = new ArrayList<>();

        try (CSVReader r = new CSVReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = r.readNext();
            if (header == null) return false;

            // Detección de encabezados
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                if (header[i] == null) continue;
                idx.put(header[i].trim().toLowerCase(), i);
            }

            boolean hasHeader = idx.containsKey("productcode") || idx.containsKey("product_code");

            if (!hasHeader) {
                // No hay encabezado, tratar la primera fila como datos legacy y seguir parsing posicional
                parseLegacyRow(items, header, 1);
            }

            String[] row;
            int line = hasHeader ? 1 : 2; // ya consumimos una línea como header o como primera data

            while ((row = r.readNext()) != null) {
                line++;
                try {
                    if (hasHeader) {
                        ProductDTO dto = parseByHeader(idx, row);
                        if (dto != null) items.add(dto);
                    } else {
                        parseLegacyRow(items, row, line);
                    }
                } catch (Exception e) {
                    System.out.println("Error en línea " + line + ": " + java.util.Arrays.toString(row) + " -> " + e.getMessage());
                }
            }
        }
        // Crear productos (esto emite eventos en createProduct)
        for (ProductDTO p : items) {
            try {
                createProduct(p);
            } catch (Exception ex) {
                System.out.println("Error creando productoCode=" + p.getProductCode() + ": " + ex.getMessage());
            }
        }
        return !items.isEmpty();
    }

    // Helpers de CSV
    private ProductDTO parseByHeader(Map<String, Integer> idx, String[] row) {
        ProductDTO dto = new ProductDTO();
        // Requeridos mínimos
        dto.setProductCode(getInt(idx, row, "productcode", "product_code"));
        dto.setName(getStr(idx, row, "name"));
        dto.setDescription(getStr(idx, row, "description"));
        dto.setUnitPrice(getFloat(idx, row, "unitprice", "unit_price"));
        dto.setDiscount(getFloat(idx, row, "discount"));
        dto.setStock(getInt(idx, row, "stock"));

        // Códigos prioritarios
        dto.setCategoryCodes(getIntList(idx, row, ";", "categorycodes", "category_codes"));
        Integer brandCode = getIntNullable(idx, row, "brandcode", "brand_code");
        if (brandCode != null) dto.setBrandCode(brandCode);

        // Compatibilidad legacy
        dto.setCategories(getIntList(idx, row, ";", "categories"));
        Integer brandId = getIntNullable(idx, row, "brand");
        if (brandId != null) dto.setBrand(brandId);

        // Resto
        dto.setCalification(getFloat(idx, row, "calification", "rating"));
        dto.setImages(getStrList(idx, row, ";", "images"));
        dto.setNew(getBool(idx, row, "new", "isnew"));
        dto.setBestSeller(getBool(idx, row, "bestseller", "isBestseller", "is_bestseller"));
        dto.setFeatured(getBool(idx, row, "featured", "isfeatured", "is_featured"));
        dto.setHero(getBool(idx, row, "hero"));
        dto.setActive(getBool(idx, row, "active", "enabled"));

        // Validación básica
        if (dto.getProductCode() == null || dto.getName() == null) return null;
        return dto;
    }

    private void parseLegacyRow(List<ProductDTO> items, String[] row, int line) {
        // Formato anterior de 15 columnas
        if (row.length < 15) {
            System.out.println("Fila legacy inválida en línea " + line + ": columnas=" + row.length);
            return;
        }
        try {
            ProductDTO dto = new ProductDTO();
            dto.setProductCode(Integer.parseInt(row[0].trim()));
            dto.setName(row[1].trim());
            dto.setDescription(row[2].trim());
            dto.setUnitPrice(Float.parseFloat(row[3].trim()));
            dto.setDiscount(Float.parseFloat(row[4].trim()));
            dto.setStock(Integer.parseInt(row[5].trim()));

            dto.setCategories(Arrays.stream(row[6].split(";"))
                    .filter(s -> !s.isBlank())
                    .map(Integer::parseInt)
                    .toList());

            dto.setBrand(Integer.parseInt(row[7].trim()));
            dto.setCalification(Float.parseFloat(row[8].trim()));

            dto.setImages(Arrays.stream(row[9].split(";"))
                    .filter(s -> !s.isBlank())
                    .toList());

            dto.setNew(Boolean.parseBoolean(row[10].trim()));
            dto.setBestSeller(Boolean.parseBoolean(row[11].trim()));
            dto.setFeatured(Boolean.parseBoolean(row[12].trim()));
            dto.setHero(Boolean.parseBoolean(row[13].trim()));
            dto.setActive(Boolean.parseBoolean(row[14].trim()));
            items.add(dto);
        } catch (Exception e) {
            System.out.println("Error legacy en línea " + line + ": " + java.util.Arrays.toString(row));
        }
    }

    // Utilidades de parseo por header
    private Integer col(Map<String, Integer> idx, String... keys) {
        for (String k : keys) {
            Integer i = idx.get(k.toLowerCase());
            if (i != null) return i;
        }
        return null;
    }
    private String getStr(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        return (i == null || i >= row.length) ? null : safeStr(row[i]);
    }
    private Integer getInt(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return null;
        try { return Integer.parseInt(row[i].trim()); } catch (Exception e) { return null; }
    }
    private Integer getIntNullable(Map<String, Integer> idx, String[] row, String... keys) { return getInt(idx, row, keys); }
    private Float getFloat(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return 0f;
        try { return Float.parseFloat(row[i].trim()); } catch (Exception e) { return 0f; }
    }
    private Boolean getBool(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return false;
        String v = safeStr(row[i]).toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y");
    }
    private List<Integer> getIntList(Map<String, Integer> idx, String[] row, String sep, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return java.util.List.of();
        String v = safeStr(row[i]);
        if (v.isBlank()) return java.util.List.of();
        return Arrays.stream(v.split(sep)).map(String::trim).filter(s->!s.isBlank()).map(Integer::parseInt).toList();
    }
    private List<String> getStrList(Map<String, Integer> idx, String[] row, String sep, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return java.util.List.of();
        String v = safeStr(row[i]);
        if (v.isBlank()) return java.util.List.of();
        return Arrays.stream(v.split(sep)).map(String::trim).filter(s->!s.isBlank()).toList();
    }
    private String safeStr(String s) { return s == null ? "" : s.trim(); }

    public Product updateProduct(ProductDTO productUpdateDTO){
        List<Category>categoriesToUpdate = (productUpdateDTO.getCategoryCodes() != null && !productUpdateDTO.getCategoryCodes().isEmpty())
                ? categoryService.geCategoriesForProductByCodes(productUpdateDTO.getCategoryCodes())
                : categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = (productUpdateDTO.getBrandCode() != null)
                ? brandService.getBrandByCode(productUpdateDTO.getBrandCode())
                : brandService.getBrandByID(productUpdateDTO.getBrand());

        Product productToUpdate = productRepository.findByProductCode(productUpdateDTO.getProductCode())
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productUpdateDTO.getProductCode(), 1));

        boolean wasActive = productToUpdate.isActive();

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setUnitPrice(productUpdateDTO.getUnitPrice());
        productToUpdate.setDiscount(productUpdateDTO.getDiscount());
        // Recalcular price derivado
        productToUpdate.setPrice(computePrice(productToUpdate.getUnitPrice(), productToUpdate.getDiscount()));
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setCategories(categoriesToUpdate);
        productToUpdate.setBrand(brandToUpdate);
        productToUpdate.setCalification(productUpdateDTO.getCalification());
        productToUpdate.setImages(productUpdateDTO.getImages());
        productToUpdate.setNew(productUpdateDTO.isNew());
        productToUpdate.setBestSeller(productUpdateDTO.isBestSeller());
        productToUpdate.setFeatured(productUpdateDTO.isFeatured());
        productToUpdate.setHero(productUpdateDTO.isHero());
        productToUpdate.setActive(productUpdateDTO.isActive());
        
        Product saved = productRepository.save(productToUpdate);

        if (!wasActive && saved.isActive()) {
            inventoryEventPublisher.emitProductoActivado(saved);
            kafkaMockService.sendEvent("PATCH: Producto activado", saved);
        }
        inventoryEventPublisher.emitProductoActualizado(saved);

        // Evento requerido: payload con códigos
        var payload = buildProductModificationPayload(saved);
        kafkaMockService.sendEvent("PATCH: modificar un producto", payload);

        return saved;
    }

    private java.util.Map<String, Object> buildProductModificationPayload(Product p) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("productCode", p.getProductCode());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("discount", p.getDiscount());
        map.put("stock", p.getStock());
        // Códigos de categorías prioritarios
        List<Integer> categoryCodes = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getCategoryCode).toList();
        List<Integer> categoryIds = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getId).toList();
        map.put("categories", categoryCodes); // nuevo contrato
        map.put("categoryIds", categoryIds);  // compat
        // Marca por código prioritario
        Integer brandCode = p.getBrand() == null ? null : p.getBrand().getBrandCode();
        Integer brandId = p.getBrand() == null ? null : p.getBrand().getId();
        map.put("brand", brandCode);    // nuevo contrato
        map.put("brandId", brandId);    // compat
        map.put("calification", p.getCalification());
        map.put("images", p.getImages() == null ? List.of() : new java.util.ArrayList<>(p.getImages()));
        map.put("new", p.isNew());
        map.put("bestSeller", p.isBestSeller());
        map.put("featured", p.isFeatured());
        map.put("hero", p.isHero());
        map.put("active", p.isActive());
        // precios
        map.put("unitPrice", p.getUnitPrice());
        map.put("unit_price", p.getUnitPrice());
        map.put("price", p.getPrice());
        return map;
    }

    public Product updateStockPostSale(Integer productCode, int amountBought){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        int newStock = productToUpdate.getStock() - amountBought;
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        // Emitir y persistir SOLO el evento PUT: Actualizar stock
        inventoryEventPublisher.emitActualizarStock(productToUpdate);
        kafkaMockService.sendEvent("PUT: Actualizar stock", productToUpdate);

        return productToUpdate;
    }
    
    public Product updateStockPostCancelation(Integer productCode, int amountReturned){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        int newStock = productToUpdate.getStock() + amountReturned;

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        // Emitir y persistir SOLO el evento PUT: Actualizar stock
        inventoryEventPublisher.emitActualizarStock(productToUpdate);
        kafkaMockService.sendEvent("PUT: Actualizar stock", productToUpdate);

        return  productToUpdate;
    }
    public Product updateStock (Integer productCode, int newStock){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        // Emitir y persistir SOLO el evento PUT: Actualizar stock
        inventoryEventPublisher.emitActualizarStock(productToUpdate);
        kafkaMockService.sendEvent("PUT: Actualizar stock", productToUpdate);

        return productToUpdate;
    }

    public Product updateUnitPrice (Integer productCode, float newPrice){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setUnitPrice(newPrice);
        
        float priceWithDiscount = computePrice(newPrice, productToUpdate.getDiscount());

        productToUpdate.setPrice(priceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Precio unitario actualizado", productToUpdate);
        System.out.println(eventSent.toString());
        inventoryEventPublisher.emitProductoActualizado(productToUpdate);
        // Persistimos también el evento emitido al middleware
        kafkaMockService.sendEvent("PUT: Producto actualizado", productToUpdate);

        return productRepository.save(productToUpdate);
    }
    
    public Product updateDiscount(Integer productCode, float newDiscount){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setDiscount(newDiscount);

        float newPriceWithDiscount = computePrice(productToUpdate.getUnitPrice(), newDiscount);

        productToUpdate.setPrice(newPriceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Descuento actualizado", productToUpdate);
        System.out.println(eventSent.toString());
        inventoryEventPublisher.emitProductoActualizado(productToUpdate);
        // Persistimos también el evento emitido al middleware
        kafkaMockService.sendEvent("PUT: Producto actualizado", productToUpdate);

       return productRepository.save(productToUpdate);
    }

    public boolean deleteProduct(Integer productCode){
        try{
            Product productToDiactivate = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

            productToDiactivate.setActive(false);
            productRepository.save(productToDiactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Producto desactivado", productToDiactivate);
            System.out.println(eventSent.toString());
            inventoryEventPublisher.emitProductoDesactivado(productToDiactivate);

            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }

    public Product patchProduct(ProductPatchDTO patch) {
        if (patch.getProductCode() == null) {
            throw new EmptyResultDataAccessException("productCode es requerido para PATCH", 1);
        }
        Product product = productRepository.findByProductCode(patch.getProductCode())
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + patch.getProductCode(), 1));

        boolean wasActive = product.isActive();
        boolean priceRelatedChanged = false;

        if (patch.getName() != null) product.setName(patch.getName());
        if (patch.getDescription() != null) product.setDescription(patch.getDescription());
        if (patch.getUnitPrice() != null) { product.setUnitPrice(patch.getUnitPrice()); priceRelatedChanged = true; }
        if (patch.getDiscount() != null) { product.setDiscount(patch.getDiscount()); priceRelatedChanged = true; }
        if (patch.getStock() != null) product.setStock(patch.getStock());
        if (patch.getCategoryCodes() != null) {
            List<Category> cats = categoryService.geCategoriesForProductByCodes(patch.getCategoryCodes());
            product.setCategories(cats);
        } else if (patch.getCategories() != null) {
            List<Category> cats = categoryService.geCategoriesForProductByID(patch.getCategories());
            product.setCategories(cats);
        }
        if (patch.getBrandCode() != null) {
            Brand b = brandService.getBrandByCode(patch.getBrandCode());
            product.setBrand(b);
        } else if (patch.getBrand() != null) {
            Brand b = brandService.getBrandByID(patch.getBrand());
            product.setBrand(b);
        }
        if (patch.getCalification() != null) product.setCalification(patch.getCalification());
        if (patch.getImages() != null) product.setImages(patch.getImages());
        if (patch.getIsNew() != null) product.setNew(patch.getIsNew());
        if (patch.getIsBestSeller() != null) product.setBestSeller(patch.getIsBestSeller());
        if (patch.getIsFeatured() != null) product.setFeatured(patch.getIsFeatured());
        if (patch.getHero() != null) product.setHero(patch.getHero());
        if (patch.getActive() != null) product.setActive(patch.getActive());

        if (priceRelatedChanged) {
            float priceWithDiscount = computePrice(product.getUnitPrice(), product.getDiscount());
            product.setPrice(priceWithDiscount);
        }

        Product saved = productRepository.save(product);

        if (!wasActive && saved.isActive()) {
            inventoryEventPublisher.emitProductoActivado(saved);
            kafkaMockService.sendEvent("PATCH: Producto activado", saved);
        }
        inventoryEventPublisher.emitProductoActualizado(saved);

        var payload = buildProductModificationPayload(saved);
        kafkaMockService.sendEvent("PATCH: modificar un producto", payload);

        return saved;
    }

    public Product activateProduct(Integer productCode) {
        Product product = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        if (product.isActive()) {
            throw new IllegalStateException("El producto ya estaba activado");
        }
        product.setActive(true);
        Product saved = productRepository.save(product);

        // Notificar a Inventario y Middleware
        inventoryEventPublisher.emitProductoActivado(saved);
        var payload = buildProductModificationPayload(saved);
        kafkaMockService.sendEvent("PATCH: activar producto", payload);

        return saved;
    }

    public Product addReview(Integer productCode, String message, Float rateUpdated) {
        Product product = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        if (message != null && !message.isBlank()) {
            java.util.ArrayList<String> list = product.getReviews() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(product.getReviews());
            list.add(message);
            product.setReviews(list);
        }
        if (rateUpdated != null) {
            product.setCalification(rateUpdated);
        }
        return productRepository.save(product);
    }

    // Helpers para precio y descuento
    private float normalizeDiscount(float discount) {
        // Si viene 8 → 0.08; si ya viene 0.08, lo dejamos igual
        return discount > 1.0f ? (discount / 100.0f) : discount;
    }

    private float computePrice(float unitPrice, float discount) {
        float d = normalizeDiscount(discount);
        float price = unitPrice * (1.0f - d);
        // redondeo a 2 decimales
        return Math.round(price * 100.0f) / 100.0f;
    }
}
