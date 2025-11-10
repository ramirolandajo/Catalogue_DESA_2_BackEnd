package ar.edu.uade.catalogue.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
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

    public List<Product>getProducts(){
        return productRepository.findAll();
    }

    public Product getProductByProductCode(Integer productCode){
        Optional<Product> productOptional = productRepository.findByProductCode(productCode);
        return productOptional.orElse(null);
    }

    public Product createProduct(ProductDTO productDTO) throws IOException {
        return saveProduct(productDTO, false);
    }

    private Product saveProduct(ProductDTO productDTO, boolean suppressEvents) throws IOException {
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
        productToSave.setImages(urlToS3(productDTO.getImages()));
        productToSave.setNew(productDTO.isNew());
        productToSave.setBestSeller(productDTO.isBestSeller());
        productToSave.setFeatured(productDTO.isFeatured());
        productToSave.setHero(productDTO.isHero());
        productToSave.setActive(productDTO.isActive());

        productRepository.save(productToSave);

        // Agregamos el producto a las categorias (por códigos prioritario)
        if (productDTO.getCategoryCodes() != null && !productDTO.getCategoryCodes().isEmpty()) {
            categoryService.addProductToCategoriesByCodes(productDTO.getProductCode(), productDTO.getCategoryCodes());
        } else {
            categoryService.addProductToCategories(productDTO.getProductCode(), productDTO.getCategories());
        }
        // Marca (legacy por ID si vino)
        if (productDTO.getBrand() != null) {
            brandService.addProductToBrand(productDTO.getProductCode(), productDTO.getBrand());
        }

        // Emitir eventos individuales solo si no estamos en batch silencioso
        if (!suppressEvents) {
            inventoryEventPublisher.emitAgregarProducto(productToSave);
            kafkaMockService.sendEvent("POST: Agregar un producto", productToSave);
        }

        return productToSave;
    }

    public List<String>urlToS3(List<String>images) throws IOException{
        List<String> s3Images = new ArrayList<>();

        for (String url : images) {
            s3Images.add(s3ImageService.fromUrlToS3(url));
        }
        return s3Images;
    }

    public boolean loadBatchFromCSV(MultipartFile csvFile) throws Exception {
        // Leemos todo el contenido para poder intentar con múltiples separadores y normalizaciones
        String content = new String(csvFile.getBytes(), StandardCharsets.UTF_8);
        return loadBatchFromString(content);
    }

    public boolean loadBatchFromReader(Reader reader) throws Exception {
        try (CSVReader r = new CSVReader(reader)) {
            return loadBatchFromReaderInternal(r);
        }
    }

    public boolean loadBatchFromString(String content) throws Exception {
        if (content == null || content.isBlank()) return false;
        String normalized = normalizeCsvContent(content);
        // Intentar distintos separadores: coma, punto y coma, tab
        char[] seps = new char[]{',',';','\t'};
        for (char sep : seps) {
            try (CSVReader r = new CSVReaderBuilder(new StringReader(normalized))
                    .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                    .build()) {
                boolean ok = loadBatchFromReaderInternal(r);
                if (ok) return true; // éxito con este separador
            } catch (Exception e) {
                // intentar siguiente separador
            }
        }
        return false;
    }

    private String normalizeCsvContent(String content) {
        String s = content;
        if (s == null) return "";
        s = s.trim();
        // Remover BOM UTF-8 si existe
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        // Caso: archivo entero viene entrecomillado => des-encerrar y des-escapar ""
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            s = s.substring(1, s.length()-1);
            s = s.replace("\"\"", "\""); // "" -> "
        }
        // Normalizar saltos de línea a \n
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        // Si todo viene en una sola línea pero parece tener encabezado + datos pegados
        if (!s.contains("\n")) {
            String[] headerHints = new String[]{"productCode,","product_code,","codigo,","código,","name,","nombre,"};
            String sLower = s.toLowerCase();
            boolean looksLikeHeader = false;
            for (String h : headerHints) {
                if (sLower.startsWith(h.toLowerCase())) { looksLikeHeader = true; break; }
            }
            if (looksLikeHeader) {
                String[] endHeaderTokens = new String[]{"active","activo","enabled"};
                for (String token : endHeaderTokens) {
                    int idx = sLower.indexOf(token.toLowerCase() + " ");
                    if (idx > 0) {
                        int pos = idx + token.length() + 1;
                        if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                            s = s.substring(0, pos).trim() + "\n" + s.substring(pos).trim();
                            sLower = s.toLowerCase();
                            break;
                        }
                    }
                }
            }
        }
        // Pase adicional SIEMPRE: separar múltiples registros en una misma línea
        // Regla 1 (regex): (inicio|espacio|coma)(bool) + espacio + (digitos,)
        {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(^|[\\s,])(true|false|1|0|yes|no|si|sí)\\s+(\\d+,)", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.MULTILINE);
            java.util.regex.Matcher m = p.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String repl = m.group(1) + m.group(2) + "\n" + m.group(3);
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(repl));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }

    private boolean loadBatchFromReaderInternal(CSVReader r) throws Exception {
        List<ProductDTO> items = new ArrayList<>();
        String[] header = r.readNext();
        if (header == null) return false;

        // Detección de encabezados (ampliada a español y variantes)
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            if (header[i] == null) continue;
            idx.put(header[i].trim().toLowerCase(), i);
        }
        int headerLen = header.length;

        Set<String> headerKeys = new HashSet<>(List.of(
            // códigos
            "productcode","product_code","codigo","código","codigo_producto","código_producto","code",
            // nombre y descripción
            "name","nombre","description","descripcion","descripción",
            // precios y descuento
            "unitprice","unit_price","precio","precio_unitario","discount","descuento",
            // stock
            "stock",
            // categorías y marca por códigos
            "categorycodes","category_codes","codigos_categorias","códigos_categorias","categoria_codigos",
            "brandcode","brand_code","codigo_marca","código_marca",
            // legacy ids
            "categories","categorias","marca","brand",
            // otros
            "calification","rating","calificacion","calificación",
            "images","imagenes","imágenes",
            "new","isnew","nuevo","es_nuevo",
            "bestseller","isbestseller","is_bestseller","mas_vendido","más_vendido",
            "featured","isfeatured","is_featured","destacado",
            "hero",
            "active","enabled","activo","habilitado"
        ));
        boolean hasHeader = idx.keySet().stream().anyMatch(headerKeys::contains);

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
                    if (row.length > headerLen) {
                        // Dividir en chunks de tamaño headerLen
                        for (int start = 0; start + headerLen <= row.length; start += headerLen) {
                            String[] chunk = Arrays.copyOfRange(row, start, start + headerLen);
                            ProductDTO dto = parseByHeader(idx, chunk);
                            if (dto != null) items.add(dto);
                        }
                        // Si sobran columnas incompletas, las ignoramos
                    } else {
                        ProductDTO dto = parseByHeader(idx, row);
                        if (dto != null) items.add(dto);
                    }
                } else {
                    parseLegacyRow(items, row, line);
                }
            } catch (Exception e) {
                System.out.println("Error en línea " + line + ": " + java.util.Arrays.toString(row) + " -> " + e.getMessage());
            }
        }

        // Diagnóstico: listar cuántos items y sus productCode
        try {
            java.util.List<Integer> codes = items.stream().map(ProductDTO::getProductCode).filter(java.util.Objects::nonNull).toList();
            System.out.println("[BatchCSV] Filas parseadas=" + items.size() + ", productCodes=" + codes);
        } catch (Exception ignore) {}

        // Crear productos sin emitir eventos individuales; acumular para batch
        List<Product> created = new ArrayList<>();
        for (ProductDTO p : items) {
            try {
                Product saved = saveProduct(p, true);
                if (saved != null) created.add(saved);
            } catch (Exception ex) {
                System.out.println("Error creando productoCode=" + p.getProductCode() + ": " + ex.getMessage());
            }
        }
        // Emitir evento batch con todos los productos creados exitosamente
        if (!created.isEmpty()) {
            inventoryEventPublisher.emitAgregarProductosBatch(created);
        }
        // Éxito si al menos un producto fue creado
        return !created.isEmpty();
    }

    // Helpers de CSV
    private ProductDTO parseByHeader(Map<String, Integer> idx, String[] row) {
        ProductDTO dto = new ProductDTO();
        // Requeridos mínimos
        dto.setProductCode(getInt(idx, row, "productcode", "product_code", "codigo", "código", "codigo_producto", "código_producto", "code"));
        dto.setName(getStr(idx, row, "name", "nombre"));
        dto.setDescription(getStr(idx, row, "description", "descripcion", "descripción"));
        dto.setUnitPrice(getFloat(idx, row, "unitprice", "unit_price", "precio", "precio_unitario"));
        dto.setDiscount(getFloat(idx, row, "discount", "descuento"));
        Integer stockVal = getInt(idx, row, "stock");
        dto.setStock(stockVal != null ? stockVal : 0);

        // Códigos prioritarios
        dto.setCategoryCodes(getIntList(idx, row, ";", "categorycodes", "category_codes", "codigos_categorias", "códigos_categorías", "categoria_codigos"));
        Integer brandCode = getIntNullable(idx, row, "brandcode", "brand_code", "codigo_marca", "código_marca");
        if (brandCode != null) dto.setBrandCode(brandCode);

        // Compatibilidad legacy
        dto.setCategories(getIntList(idx, row, ";", "categories", "categorias"));
        Integer brandId = getIntNullable(idx, row, "brand", "marca");
        if (brandId != null) dto.setBrand(brandId);

        // Resto
        dto.setCalification(getFloat(idx, row, "calification", "rating", "calificacion", "calificación"));
        dto.setImages(getStrList(idx, row, ";", "images", "imagenes", "imágenes"));
        dto.setNew(getBool(idx, row, "new", "isnew", "nuevo", "es_nuevo"));
        dto.setBestSeller(getBool(idx, row, "bestseller", "isBestseller", "is_bestseller", "mas_vendido", "más_vendido"));
        dto.setFeatured(getBool(idx, row, "featured", "isfeatured", "is_featured", "destacado"));
        dto.setHero(getBool(idx, row, "hero"));
        dto.setActive(getBool(idx, row, "active", "enabled", "activo", "habilitado"));

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
            dto.setUnitPrice(parseFloatSafe(row[3]));
            dto.setDiscount(parseFloatSafe(row[4]));
            dto.setStock(Integer.parseInt(row[5].trim()));

            dto.setCategories(Arrays.stream(row[6].split(";"))
                    .filter(s -> !s.isBlank())
                    .map(Integer::parseInt)
                    .toList());

            dto.setBrand(Integer.parseInt(row[7].trim()));
            dto.setCalification(parseFloatSafe(row[8]));

            dto.setImages(Arrays.stream(row[9].split(";"))
                    .filter(s -> !s.isBlank())
                    .toList());

            dto.setNew(parseBoolSafe(row[10]));
            dto.setBestSeller(parseBoolSafe(row[11]));
            dto.setFeatured(parseBoolSafe(row[12]));
            dto.setHero(parseBoolSafe(row[13]));
            dto.setActive(parseBoolSafe(row[14]));
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
        try { return parseFloatSafe(row[i]); } catch (Exception e) { return 0f; }
    }
    private Boolean getBool(Map<String, Integer> idx, String[] row, String... keys) {
        Integer i = col(idx, keys);
        if (i == null || i >= row.length) return false;
        return parseBoolSafe(row[i]);
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

    private float parseFloatSafe(String s) {
        if (s == null) return 0f;
        String norm = s.trim().replace("%", "");
        // Soportar comas decimales
        norm = norm.replace(',', '.');
        try { return Float.parseFloat(norm); } catch (Exception e) { return 0f; }
    }
    private boolean parseBoolSafe(String s) {
        if (s == null) return false;
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y") || v.equals("si") || v.equals("sí");
    }

    public Product updateProduct(ProductDTO productUpdateDTO) throws IOException {
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
        productToUpdate.setImages(urlToS3(productUpdateDTO.getImages()));
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
        // Evitar notación científica y ceros de más
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
