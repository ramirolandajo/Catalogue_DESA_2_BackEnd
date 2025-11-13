package ar.edu.uade.catalogue.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.model.DTO.ProductDTO;
import ar.edu.uade.catalogue.model.DTO.ProductPatchDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.ReviewEntry;
import ar.edu.uade.catalogue.repository.ProductRepository;
import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;

@Service
public class ProductService {

    // Estructuras de resultado batch accesibles desde el Controller
    public static record BatchError(int line, String message) {}
    public static record BatchResult(boolean success, int totalRows, int created, List<BatchError> errors) {}

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

    @Autowired
    S3ImageService s3ImageService;

    private static final Pattern NAME_ALLOWED = Pattern.compile(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*");

    public List<Product>getProducts(){
        return productRepository.findAll();
    }

    public Product getProductByProductCode(Integer productCode){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        return productOptional.orElse(null);
    }

    public Product createProduct(ProductDTO productDTO) throws IOException {
        validateProductDTOForCreate(productDTO);
        // Convertir URLs a S3 ANTES de guardar
        List<String> s3Images = urlToS3(productDTO.getImages());
        productDTO.setImages(s3Images);
        return saveProduct(productDTO, false);
    }

    private void validateProductDTOForCreate(ProductDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Body requerido");
        if (dto.getProductCode() == null) throw new IllegalArgumentException("productCode es obligatorio");
        if (productRepository.findByProductCode(dto.getProductCode()).isPresent()) {
            throw new IllegalArgumentException("productCode ya existe: " + dto.getProductCode());
        }
        if (productRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("El nombre del producto ya existe: " + dto.getName());
        }
        validateCommon(dto);
    }

    private void validateCommon(ProductDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) throw new IllegalArgumentException("El nombre no puede ser vacío");
        if (!NAME_ALLOWED.matcher(dto.getName()).matches()) throw new IllegalArgumentException("El nombre debe contener al menos una letra");
        if (dto.getDescription() == null || dto.getDescription().isBlank()) throw new IllegalArgumentException("La descripción no puede ser vacía");
        if (dto.getUnitPrice() < 0) throw new IllegalArgumentException("El precio no puede ser negativo");
        if (dto.getStock() < 0) throw new IllegalArgumentException("El stock no puede ser negativo");
        if (dto.getDiscount() < 0) throw new IllegalArgumentException("El descuento no puede ser negativo");
        float normalized = normalizeDiscount(dto.getDiscount());
        if (normalized < 0f || normalized >= 1f) throw new IllegalArgumentException("El descuento debe estar entre 0 y 1 (ej: 0.2 = 20%)");

        // Validar categorías/marca existentes si se informan
        if (dto.getCategoryCodes() != null && !dto.getCategoryCodes().isEmpty()) {
            List<Category> cats = categoryService.geCategoriesForProductByCodes(dto.getCategoryCodes());
            if (cats.size() != dto.getCategoryCodes().size()) throw new IllegalArgumentException("Alguna categoría no existe por code");
        } else if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            List<Category> cats = categoryService.geCategoriesForProductByID(dto.getCategories());
            if (cats.size() != dto.getCategories().size()) throw new IllegalArgumentException("Alguna categoría no existe por id");
        }
        if (dto.getBrandCode() != null) {
            if (brandService.getBrandByCode(dto.getBrandCode()) == null) throw new IllegalArgumentException("La marca no existe (brandCode)");
        } else if (dto.getBrand() != null) {
            if (brandService.getBrandByID(dto.getBrand()) == null) throw new IllegalArgumentException("La marca no existe (id)");
        }
        // Validar imágenes: URLs y extensiones soportadas
        if (dto.getImages() != null) {
            for (String u : dto.getImages()) {
                if (u != null && !u.isBlank() && !(u.startsWith("http://") || u.startsWith("https://"))) {
                    throw new IllegalArgumentException("URL de imagen inválida: " + u);
                }
            }
        }
    }

    private Product saveProduct(ProductDTO productDTO, boolean suppressEvents) throws IOException {
        // Este método ahora asume que las URLs de imágenes ya son de S3
        List<Category> categoriesToSave = (productDTO.getCategoryCodes() != null && !productDTO.getCategoryCodes().isEmpty())
                ? categoryService.geCategoriesForProductByCodes(productDTO.getCategoryCodes())
                : categoryService.geCategoriesForProductByID(productDTO.getCategories());
        Brand brandToSave = (productDTO.getBrandCode() != null)
                ? brandService.getBrandByCode(productDTO.getBrandCode())
                : brandService.getBrandByID(productDTO.getBrand());

        float priceWithDiscount = computePrice(productDTO.getUnitPrice(), productDTO.getDiscount());
        validateImageLengths(productDTO.getImages(), 2048);

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
        productToSave.setImages(productDTO.getImages()); // Guardar las URLs ya procesadas
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());
        productToSave.setActive(productDTO.isActive());

        productRepository.save(productToSave);

        if (productDTO.getCategoryCodes() != null && !productDTO.getCategoryCodes().isEmpty()) {
            categoryService.addProductToCategoriesByCodes(productDTO.getProductCode(), productDTO.getCategoryCodes());
        } else {
            categoryService.addProductToCategories(productDTO.getProductCode(), productDTO.getCategories());
        }
        if (productDTO.getBrand() != null) {
            brandService.addProductToBrand(productDTO.getProductCode(), productDTO.getBrand());
        }

        if (!suppressEvents) {
            inventoryEventPublisher.emitAgregarProducto(productToSave);
            kafkaMockService.sendEvent("POST: Agregar un producto", productToSave);
        }

        return productToSave;
    }

    private void validateImageLengths(List<String> images, int max) {
        if (images == null) return;
        for (String u : images) {
            if (u != null && u.length() > max) {
                throw new IllegalArgumentException("La URL de imagen excede la longitud máxima permitida (" + max + ")");
            }
        }
    }

    public List<String> urlToS3(List<String> images) throws IOException {
        List<String> s3Images = new ArrayList<>();
        if (images == null) return s3Images;
        for (String url : images) {
            // Ignorar URLs nulas o vacías
            if (url != null && !url.isBlank()) {
                s3Images.add(s3ImageService.fromUrlToS3(url));
            }
        }
        return s3Images;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean loadBatchFromCSV(MultipartFile csvFile) throws Exception {
        String content = new String(csvFile.getBytes(), StandardCharsets.UTF_8);
        return loadBatchFromString(content);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean loadBatchFromReader(Reader reader) throws Exception {
        try (CSVReader r = new CSVReader(reader)) {
            return loadBatchFromReaderInternal(r);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean loadBatchFromString(String content) {
        if (content == null || content.isBlank()) return false;
        String normalized = normalizeCsvContent(content);
        char[] seps = new char[]{',',';','\t'};
        for (char sep : seps) {
            try (CSVReader r = new CSVReaderBuilder(new StringReader(normalized))
                    .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                    .build()) {
                boolean ok = loadBatchFromReaderInternal(r);
                if (ok) return true;
            } catch (Exception e) {
                // intentar siguiente separador
            }
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchResult loadBatchFromCSVDetailed(MultipartFile csvFile) throws Exception {
        String content = new String(csvFile.getBytes(), StandardCharsets.UTF_8);
        return loadBatchFromStringDetailed(content);
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchResult loadBatchFromStringDetailed(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return new BatchResult(false, 0, 0, List.of(new BatchError(1, "Contenido CSV vacío")));
        }
        String normalized = normalizeCsvContent(content);
        try (CSVReader r = new CSVReaderBuilder(new StringReader(normalized))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {
            return parseCsv(r);
        } catch (Exception e) {
            return new BatchResult(false, 0, 0, List.of(new BatchError(-1, e.getMessage() == null ? "Error de parseo con punto y coma" : e.getMessage())));
        }
    }

    private BatchResult parseCsv(CSVReader r) throws Exception {
        List<ProductDTO> items = new ArrayList<>();
        List<BatchError> errors = new ArrayList<>();
        String[] header = r.readNext();
        if (header == null) return new BatchResult(false, 0, 0, List.of(new BatchError(1, "CSV sin encabezado ni filas")));

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null) idx.put(header[i].trim().toLowerCase(), i);
        }

        String[] row;
        int line = 1;

        while ((row = r.readNext()) != null) {
            line++;
            try {
                ProductDTO dto = parseByHeader(idx, row);
                validateProductDTOForCreate(dto);
                
                // VALIDACIÓN DE IMÁGENES DURANTE EL PARSEO
                List<String> s3Urls = urlToS3(dto.getImages());
                dto.setImages(s3Urls); // Reemplazar con URLs de S3

                items.add(dto);
            } catch (Exception e) {
                errors.add(new BatchError(line, e.getMessage() == null ? "Fila inválida" : e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new BatchResult(false, items.size() + errors.size(), 0, errors);
        }

        List<Product> created = new ArrayList<>();
        for (ProductDTO p : items) {
            Product saved = saveProduct(p, true);
            created.add(saved);
        }
        
        if (!created.isEmpty()) {
            inventoryEventPublisher.emitAgregarProductosBatch(created);
        }
        return new BatchResult(true, items.size(), created.size(), List.of());
    }

    private boolean loadBatchFromReaderInternal(CSVReader r) throws Exception {
        BatchResult res = parseCsv(r);
        if (!res.success()) {
            String msg = res.errors().stream().limit(3)
                .map(e -> "linea=" + e.line() + ": " + e.message())
                .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalArgumentException(msg.isBlank() ? "Errores en CSV" : msg);
        }
        return res.created() > 0;
    }

    private String normalizeCsvContent(String content) {
        String s = content;
        if (s == null) return "";
        s = s.trim();
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            s = s.substring(1, s.length()-1);
            s = s.replace("\"\"", "\"");
        }
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        return s;
    }

    private ProductDTO parseByHeader(Map<String, Integer> idx, String[] row) {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode(getInt(idx, row, "productcode", "product_code", "codigo_producto", "codigo", "código", "code"));
        dto.setName(getStr(idx, row, "name", "nombre"));
        dto.setDescription(getStr(idx, row, "description", "descripcion", "descripción"));
        dto.setUnitPrice(getFloat(idx, row, "unitprice", "unit_price", "preciounitario", "precio", "precio_unitario"));
        dto.setDiscount(getFloat(idx, row, "discount", "descuento"));
        Integer stockVal = getInt(idx, row, "stock");
        dto.setStock(stockVal != null ? stockVal : 0);
        dto.setCategoryCodes(getIntList(idx, row, ";", "categorycodes", "category_codes", "codigosdecategoria", "codigos_categorias", "códigos_categorías", "categoria_codigos"));
        Integer brandCode = getIntNullable(idx, row, "brandcode", "brand_code", "codigodemarca", "codigo_marca", "código_marca");
        if (brandCode != null) dto.setBrandCode(brandCode);
        dto.setCategories(getIntList(idx, row, ";", "categories", "categorias"));
        Integer brandId = getIntNullable(idx, row, "brand", "marca");
        if (brandId != null) dto.setBrand(brandId);
        dto.setCalification(getFloat(idx, row, "calification", "calificacion", "rating", "calificación"));
        dto.setImages(getStrList(idx, row, ";", "images", "imágenes", "imagenes"));
        dto.setNew(getBool(idx, row, "new", "isnew", "nuevo", "es_nuevo"));
        dto.setBestSeller(getBool(idx, row, "bestseller", "isbestseller", "is_bestseller", "mas_vendido", "más_vendido"));
        dto.setFeatured(getBool(idx, row, "featured", "isfeatured", "is_featured", "destacado"));
        dto.setHero(getBool(idx, row, "hero"));
        dto.setActive(getBool(idx, row, "active", "enabled", "activo", "habilitado"));
        if (dto.getProductCode() == null || dto.getName() == null) throw new IllegalArgumentException("productCode y name son requeridos");
        return dto;
    }

    private void parseLegacyRow(List<ProductDTO> items, String[] row, int line) {
        // ... (Este método queda deprecado pero se mantiene por si acaso)
    }

    private Integer col(Map<String, Integer> idx, String... keys) {
        for (String k : keys) { Integer i = idx.get(k.toLowerCase()); if (i != null) return i; }
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
        try { return parseFloatSafe(row[i]); } catch (Exception e) { return 0f; }
    }
    private Boolean getBool(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return false;
        return parseBoolSafe(row[i]);
    }
    private List<Integer> getIntList(Map<String, Integer> idx, String[] row, String sep, String... keys) {
        String v = getStr(idx, row, keys);
        if (v == null || v.isBlank()) return java.util.List.of();
        return Arrays.stream(v.split(sep)).map(String::trim).filter(s->!s.isBlank()).map(Integer::parseInt).toList();
    }
    private List<String> getStrList(Map<String, Integer> idx, String[] row, String sep, String... keys) {
        String v = getStr(idx, row, keys);
        if (v == null || v.isBlank()) return java.util.List.of();
        return Arrays.stream(v.split(sep)).map(String::trim).filter(s->!s.isBlank()).toList();
    }
    private String safeStr(String s) { return s == null ? "" : s.trim(); }
    private float parseFloatSafe(String s) {
        if (s == null) return 0f;
        String norm = s.trim().replace("%", "");
        norm = norm.replace(',', '.');
        try { return Float.parseFloat(norm); } catch (Exception e) { return 0f; }
    }
    private boolean parseBoolSafe(String s) {
        if (s == null) return false;
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y") || v.equals("si") || v.equals("sí");
    }

    public Product updateProduct(ProductDTO productUpdateDTO) throws IOException {
        if (productUpdateDTO == null || productUpdateDTO.getProductCode() == null) {
            throw new IllegalArgumentException("productCode es obligatorio para actualizar");
        }
        Product productToUpdate = productRepository.findByProductCode(productUpdateDTO.getProductCode())
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productUpdateDTO.getProductCode(), 1));

        if (!productToUpdate.getProductCode().equals(productUpdateDTO.getProductCode())) {
            throw new IllegalArgumentException("No se puede modificar el productCode de un producto existente.");
        }
        if (productUpdateDTO.getName() != null && !productUpdateDTO.getName().equals(productToUpdate.getName())) {
            productRepository.findByName(productUpdateDTO.getName()).ifPresent(p -> {
                if (!p.getProductCode().equals(productToUpdate.getProductCode())) {
                    throw new IllegalArgumentException("El nombre de producto '" + productUpdateDTO.getName() + "' ya está en uso por otro producto.");
                }
            });
        }

        validateCommon(productUpdateDTO);

        List<String> s3Images = urlToS3(productUpdateDTO.getImages());
        
        List<Category>categoriesToUpdate = (productUpdateDTO.getCategoryCodes() != null && !productUpdateDTO.getCategoryCodes().isEmpty())
                ? categoryService.geCategoriesForProductByCodes(productUpdateDTO.getCategoryCodes())
                : categoryService.geCategoriesForProductByID(productUpdateDTO.getCategories());
        Brand brandToUpdate = (productUpdateDTO.getBrandCode() != null)
                ? brandService.getBrandByCode(productUpdateDTO.getBrandCode())
                : brandService.getBrandByID(productUpdateDTO.getBrand());

        boolean wasActive = productToUpdate.isActive();

        productToUpdate.setName(productUpdateDTO.getName());
        productToUpdate.setDescription(productUpdateDTO.getDescription());
        productToUpdate.setUnitPrice(productUpdateDTO.getUnitPrice());
        productToUpdate.setDiscount(productUpdateDTO.getDiscount());
        productToUpdate.setPrice(computePrice(productToUpdate.getUnitPrice(), productToUpdate.getDiscount()));
        productToUpdate.setStock(productUpdateDTO.getStock());
        productToUpdate.setCategories(categoriesToUpdate);
        productToUpdate.setBrand(brandToUpdate);
        productToUpdate.setCalification(productUpdateDTO.getCalification());
        productToUpdate.setImages(s3Images);
        validateImageLengths(productToUpdate.getImages(), 2048);
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

        var payload = buildProductModificationPayload(saved);
        kafkaMockService.sendEvent("PATCH: modificar un producto", payload);

        return saved;
    }

    private java.util.Map<String, Object> buildProductModificationPayload(Product p) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("productCode", p.getProductCode());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("discount", p.getDiscount());
        map.put("stock", p.getStock());
        List<Integer> categoryCodes = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getCategoryCode).toList();
        List<Integer> categoryIds = p.getCategories() == null ? List.of() : p.getCategories().stream().map(Category::getId).toList();
        map.put("categories", categoryCodes);
        map.put("categoryIds", categoryIds);
        Integer brandCode = p.getBrand() == null ? null : p.getBrand().getBrandCode();
        map.put("brand", brandCode);
        map.put("calification", p.getCalification());
        map.put("images", p.getImages() == null ? List.of() : new java.util.ArrayList<>(p.getImages()));
        map.put("new", p.isNew());
        map.put("bestSeller", p.isBestSeller());
        map.put("featured", p.isFeatured());
        map.put("hero", p.isHero());
        map.put("active", p.isActive());
        map.put("unitPrice", p.getUnitPrice());
        map.put("unit_price", p.getUnitPrice());
        map.put("price", p.getPrice());
        return map;
    }

    public Product updateStockPostSale(Integer productCode, int amountBought){
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        int newStock = productToUpdate.getStock() - amountBought;
        if (newStock < 0) throw new IllegalArgumentException("Stock no puede ser negativo");
        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

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

        inventoryEventPublisher.emitActualizarStock(productToUpdate);
        kafkaMockService.sendEvent("PUT: Actualizar stock", productToUpdate);

        return  productToUpdate;
    }
    public Product updateStock (Integer productCode, int newStock){
        if (newStock < 0) throw new IllegalArgumentException("Stock no puede ser negativo");
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setStock(newStock);

        productRepository.save(productToUpdate);

        inventoryEventPublisher.emitActualizarStock(productToUpdate);
        kafkaMockService.sendEvent("PUT: Actualizar stock", productToUpdate);

        return productToUpdate;
    }

    public Product updateUnitPrice (Integer productCode, float newPrice){
        if (newPrice < 0) throw new IllegalArgumentException("El precio no puede ser negativo");
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setUnitPrice(newPrice);
        
        float priceWithDiscount = computePrice(newPrice, productToUpdate.getDiscount());

        productToUpdate.setPrice(priceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Precio unitario actualizado", productToUpdate);
        System.out.println(eventSent.toString());
        inventoryEventPublisher.emitProductoActualizado(productToUpdate);
        kafkaMockService.sendEvent("PUT: Producto actualizado", productToUpdate);

        return productRepository.save(productToUpdate);
    }
    
    public Product updateDiscount(Integer productCode, float newDiscount){
        float normalized = normalizeDiscount(newDiscount);
        if (normalized < 0f || normalized >= 1f) throw new IllegalArgumentException("El descuento debe estar entre 0 y 1");
        Product productToUpdate = productRepository.findByProductCode(productCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        productToUpdate.setDiscount(newDiscount);

        float newPriceWithDiscount = computePrice(productToUpdate.getUnitPrice(), newDiscount);

        productToUpdate.setPrice(newPriceWithDiscount);

        Event eventSent = kafkaMockService.sendEvent("PATCH: Descuento actualizado", productToUpdate);
        System.out.println(eventSent.toString());
        inventoryEventPublisher.emitProductoActualizado(productToUpdate);
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

    public Product patchProduct(ProductPatchDTO patch) throws IOException {
        if (patch.getProductCode() == null) {
            throw new EmptyResultDataAccessException("productCode es requerido para PATCH", 1);
        }
        Product product = productRepository.findByProductCode(patch.getProductCode())
            .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + patch.getProductCode(), 1));

        boolean wasActive = product.isActive();
        boolean priceRelatedChanged = false;

        if (patch.getName() != null) {
            if (patch.getName().isBlank() || !NAME_ALLOWED.matcher(patch.getName()).matches()) {
                throw new IllegalArgumentException("Nombre inválido (no vacío y con letras)");
            }
            if (!patch.getName().equals(product.getName())) {
                productRepository.findByName(patch.getName()).ifPresent(p -> {
                    if (!p.getProductCode().equals(product.getProductCode())) {
                        throw new IllegalArgumentException("El nombre de producto '" + patch.getName() + "' ya está en uso por otro producto.");
                    }
                });
            }
            product.setName(patch.getName());
        }
        if (patch.getDescription() != null) {
            if (patch.getDescription().isBlank()) throw new IllegalArgumentException("Descripción no puede ser vacía");
            product.setDescription(patch.getDescription());
        }
        if (patch.getUnitPrice() != null) {
            if (patch.getUnitPrice() < 0) throw new IllegalArgumentException("El precio no puede ser negativo");
            product.setUnitPrice(patch.getUnitPrice()); priceRelatedChanged = true;
        }
        if (patch.getDiscount() != null) {
            float n = normalizeDiscount(patch.getDiscount());
            if (n < 0f || n >= 1f) throw new IllegalArgumentException("El descuento debe estar entre 0 y 1");
            product.setDiscount(patch.getDiscount()); priceRelatedChanged = true;
        }
        if (patch.getStock() != null) {
            if (patch.getStock() < 0) throw new IllegalArgumentException("Stock no puede ser negativo");
            product.setStock(patch.getStock());
        }
        if (patch.getCategoryCodes() != null) {
            List<Category> cats = categoryService.geCategoriesForProductByCodes(patch.getCategoryCodes());
            if (cats.size() != patch.getCategoryCodes().size()) throw new IllegalArgumentException("Alguna categoría no existe por code");
            product.setCategories(cats);
        } else if (patch.getCategories() != null) {
            List<Category> cats = categoryService.geCategoriesForProductByID(patch.getCategories());
            if (cats.size() != patch.getCategories().size()) throw new IllegalArgumentException("Alguna categoría no existe por id");
            product.setCategories(cats);
        }
        if (patch.getBrandCode() != null) {
            Brand b = brandService.getBrandByCode(patch.getBrandCode());
            if (b == null) throw new IllegalArgumentException("Marca inexistente (brandCode)");
            product.setBrand(b);
        } else if (patch.getBrand() != null) {
            Brand b = brandService.getBrandByID(patch.getBrand());
            if (b == null) throw new IllegalArgumentException("Marca inexistente (id)");
            product.setBrand(b);
        }
        if (patch.getCalification() != null) product.setCalification(patch.getCalification());
        if (patch.getImages() != null) {
            List<String> s3 = urlToS3(patch.getImages());
            validateImageLengths(s3, 2048);
            product.setImages(s3);
        }
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

        inventoryEventPublisher.emitProductoActivado(saved);
        var payload = buildProductModificationPayload(saved);
        kafkaMockService.sendEvent("PATCH: activar producto", payload);

        return saved;
    }

    public Product addReview(Integer productCode, String message, Float rateUpdated) {
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new EmptyResultDataAccessException("Producto no encontrado para productCode=" + productCode, 1));

        if (message != null && !message.isBlank()) {
            java.util.ArrayList<ReviewEntry> list = product.getReviews() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(product.getReviews());
            list.add(new ReviewEntry(productCode, message));
            product.setReviews(list);
        }
        if (rateUpdated != null) {
            product.setCalification(rateUpdated);
        }
        return productRepository.save(product);
    }

    /**
     * Exporta todos los productos a CSV usando el mismo formato de importación:
     * Header: productCode,name,description,unitPrice,discount,stock,categoryCodes,brandCode,calification,images,new,bestSeller,featured,hero,active
     * - Separador de columnas: coma
     * - Listas (categorías, imágenes): separadas por ';'
     * - Campos con coma, salto de línea o comillas: se envuelven en comillas dobles y se escapan comillas como "".
     */
    public byte[] exportProductsCsv() {
        List<ar.edu.uade.catalogue.model.Product> products = getProducts();
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("productCode,name,description,unitPrice,discount,stock,categoryCodes,brandCode,calification,images,new,bestSeller,featured,hero,active\r\n");
        for (ar.edu.uade.catalogue.model.Product p : products) {
            Integer productCode = p.getProductCode();
            String name = nullToEmpty(p.getName());
            String description = nullToEmpty(p.getDescription());
            // unitPrice y discount en Locale ROOT con punto decimal
            String unitPrice = formatFloat(p.getUnitPrice());
            String discount = formatFloat(p.getDiscount());
            String stock = String.valueOf(p.getStock());
            // categoryCodes separados por ';'
            String categoryCodes = p.getCategories() == null ? "" : p.getCategories().stream()
                    .map(c -> c.getCategoryCode() == null ? "" : String.valueOf(c.getCategoryCode()))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(";"));
            String brandCode = p.getBrand() != null && p.getBrand().getBrandCode() != null ? String.valueOf(p.getBrand().getBrandCode()) : "";
            String calification = formatFloat(p.getCalification());
            String images = p.getImages() == null ? "" : p.getImages().stream().filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(";"));
            String isNew = String.valueOf(p.isNew());
            String bestSeller = String.valueOf(p.isBestSeller());
            String featured = String.valueOf(p.isFeatured());
            String hero = String.valueOf(p.isHero());
            String active = String.valueOf(p.isActive());

            // Escribir fila con escape CSV
            sb.append(csv(productCode))
                    .append(',').append(csv(name))
                    .append(',').append(csv(description))
                    .append(',').append(csv(unitPrice))
                    .append(',').append(csv(discount))
                    .append(',').append(csv(stock))
                    .append(',').append(csv(categoryCodes))
                    .append(',').append(csv(brandCode))
                    .append(',').append(csv(calification))
                    .append(',').append(csv(images))
                    .append(',').append(csv(isNew))
                    .append(',').append(csv(bestSeller))
                    .append(',').append(csv(featured))
                    .append(',').append(csv(hero))
                    .append(',').append(csv(active))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String formatFloat(float f) { return String.format(Locale.ROOT, "%s", stripTrailingZeros(f)); }
    private static String stripTrailingZeros(float f) {
        java.math.BigDecimal bd = new java.math.BigDecimal(Float.toString(f));
        bd = bd.stripTrailingZeros();
        return bd.toPlainString();
    }
    private static String csv(Object raw) {
        String s = raw == null ? "" : String.valueOf(raw);
        boolean mustQuote = s.contains(",") || s.contains("\n") || s.contains("\"");
        if (mustQuote) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private float normalizeDiscount(float discount) {
        return discount > 1.0f ? (discount / 100.0f) : discount;
    }

    private float computePrice(float unitPrice, float discount) {
        float d = normalizeDiscount(discount);
        float price = unitPrice * (1.0f - d);
        return Math.round(price * 100.0f) / 100.0f;
    }

    // --- Excel batch (único) ---
    @Transactional(rollbackFor = Exception.class)
    public BatchResult loadBatchFromExcel(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Archivo Excel vacío");
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try (InputStream is = file.getInputStream()) {
            Workbook wb;
            if (name.endsWith(".xlsx")) wb = new XSSFWorkbook(is);
            else if (name.endsWith(".xls")) wb = new HSSFWorkbook(is);
            else throw new IllegalArgumentException("Formato no soportado (use .xlsx o .xls)");

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("Sin hoja 0 en Excel");

            Map<String,Integer> idx = new HashMap<>();
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Excel sin encabezado");
            for (int c=0; c<header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String key = cell.getStringCellValue();
                    if (key != null) idx.put(key.trim().toLowerCase(), c);
                }
            }

            List<ProductDTO> items = new ArrayList<>();
            List<BatchError> errors = new ArrayList<>();

            for (int r=1; r<=sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                try {
                    ProductDTO dto = parseByHeaderExcel(idx, row);
                    validateProductDTOForCreate(dto);

                    List<String> s3Urls = urlToS3(dto.getImages());
                    dto.setImages(s3Urls);

                    items.add(dto);
                } catch (Exception ex) {
                    errors.add(new BatchError(r+1, ex.getMessage() == null ? "Fila inválida" : ex.getMessage()));
                }
            }

            if (!errors.isEmpty()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return new BatchResult(false, items.size() + errors.size(), 0, errors);
            }

            List<Product> created = new ArrayList<>();
            for (ProductDTO dto : items) {
                Product saved = saveProduct(dto, true);
                created.add(saved);
            }
            if (!created.isEmpty()) {
                inventoryEventPublisher.emitAgregarProductosBatch(created);
            }
            return new BatchResult(true, items.size(), created.size(), List.of());
        }
    }
    
    private ProductDTO parseByHeaderExcel(Map<String, Integer> idx, Row row) {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode(getNumericNullable(row, idx, "productcode", "product_code", "codigo_producto").intValue());
        dto.setName(getString(row, idx, "name", "nombre"));
        dto.setDescription(getString(row, idx, "description", "descripcion"));
        dto.setUnitPrice(getNumericNullable(row, idx, "unitprice", "unit_price", "preciounitario").floatValue());
        dto.setDiscount(getNumericNullable(row, idx, "discount", "descuento").floatValue());
        dto.setStock(getNumericNullable(row, idx, "stock").intValue());
        dto.setCategoryCodes(getIntListExcel(row, idx, "categorycodes", "category_codes", "codigosdecategoria"));
        dto.setBrandCode(getNumericNullable(row, idx, "brandcode", "brand_code", "codigodemarca").intValue());
        dto.setCalification(getNumericNullable(row, idx, "calification", "calificacion").floatValue());
        dto.setImages(getStrListExcel(row, idx, "images", "imágenes", "imagenes"));
        dto.setNew(getBoolExcel(row, idx, "new", "isnew", "nuevo"));
        dto.setBestSeller(getBoolExcel(row, idx, "bestseller", "isbestseller"));
        dto.setFeatured(getBoolExcel(row, idx, "featured", "isfeatured", "destacado"));
        dto.setHero(getBoolExcel(row, idx, "hero"));
        dto.setActive(getBoolExcel(row, idx, "active", "activo"));
        return dto;
    }

    private static Double getNumericNullable(Row row, Map<String,Integer> idx, String... keys) {
        Integer c = first(idx, keys);
        if (c == null) return null;
        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                if (s == null || s.isBlank()) yield null;
                s = s.replace('%', ' ').trim().replace(',', '.');
                try { yield Double.parseDouble(s); } catch (Exception e) { throw new IllegalArgumentException("Valor numérico inválido en columna " + (c+1)); }
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? 1.0 : 0.0;
            default -> null;
        };
    }
    private static String getString(Row row, Map<String,Integer> idx, String... keys) {
        Integer c = first(idx, keys);
        if (c == null) return null;
        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String s = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : (cell.getCellType()==CellType.NUMERIC ? String.valueOf((long)cell.getNumericCellValue()) : cell.toString());
        return s != null ? s.trim() : null;
    }
    private static boolean getBoolExcel(Row row, Map<String,Integer> idx, String... keys) {
        Integer c = first(idx, keys);
        if (c == null) return false;
        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return false;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> cell.getNumericCellValue() != 0.0;
            case STRING -> {
                String v = cell.getStringCellValue();
                yield v != null && v.matches("(?i)true|1|yes|y|si|sí");
            }
            default -> false;
        };
    }
    private static List<Integer> getIntListExcel(Row row, Map<String,Integer> idx, String... keys) {
        String s = getString(row, idx, keys);
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(";")).map(String::trim).filter(t -> !t.isBlank()).map(Integer::parseInt).collect(Collectors.toList());
    }
    private static List<String> getStrListExcel(Row row, Map<String,Integer> idx, String... keys) {
        String s = getString(row, idx, keys);
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(";")).map(String::trim).filter(t -> !t.isBlank()).collect(Collectors.toList());
    }
    private static Integer first(Map<String,Integer> idx, String... keys) {
        for (String k : keys) { Integer i = idx.get(k.toLowerCase()); if (i != null) return i; }
        return null;
    }
}
