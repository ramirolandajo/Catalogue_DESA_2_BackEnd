package ar.edu.uade.catalogue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.CategoryDTO;
import ar.edu.uade.catalogue.repository.CategoryRepository;
import ar.edu.uade.catalogue.repository.ProductRepository;
import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;

@Service
public class CategoryService {
    
    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired 
    KafkaMockService kafkaMockService;

    @Autowired
    InventoryEventPublisher inventoryEventPublisher;

    public List<Category>getCategories(){
        List<Category> categories = categoryRepository.findAll();
        return categories;
    }

    public List<Product> getAllProductsFromCategory(Integer id){
        Optional<Category> categoryOptional = categoryRepository.findById(id);
        Category category = categoryOptional.get();

        List<Integer> productsToFind = category.getProducts();

        List<Product>productsFound = new ArrayList<>();

        for (Integer productCode : productsToFind) {
            productRepository.findByProductCode(productCode).ifPresent(productsFound::add);
        }

        return productsFound;
    }

    public Category getCategoryByID(Integer id){
        Optional<Category> category = categoryRepository.findById(id);
        return category.orElse(null);         
    }

    public Category getCategoryByCode(Integer code){
        return categoryRepository.findByCategoryCode(code).orElse(null);
    }

    public List<Category> geCategoriesForProductByID(List<Integer>categories){
        // Compatibilidad: método antiguo por IDs internos
        List<Category> categoriesFounded = new ArrayList<>();
        if (categories == null) return categoriesFounded;
        for ( Integer id : categories) {
            categoryRepository.findById(id).ifPresent(categoriesFounded::add);
        }
        return categoriesFounded;
    }

    public List<Category> geCategoriesForProductByCodes(List<Integer> codes) {
        List<Category> out = new ArrayList<>();
        if (codes == null) return out;
        for (Integer code : codes) {
            categoryRepository.findByCategoryCode(code).ifPresent(out::add);
        }
        return out;
    }

    public Category createCategory(CategoryDTO categoryDTO){
        if (categoryDTO.getCategoryCode() == null) {
            throw new IllegalArgumentException("categoryCode es requerido para crear categoría");
        }
        if (categoryRepository.existsByCategoryCode(categoryDTO.getCategoryCode())) {
            throw new IllegalArgumentException("categoryCode ya existe: " + categoryDTO.getCategoryCode());
        }

        Category categoryToSave = new Category();
        categoryToSave.setCategoryCode(categoryDTO.getCategoryCode());
        categoryToSave.setName(categoryDTO.getName());
        categoryToSave.setProducts(new ArrayList<Integer>());
        categoryToSave.setActive(categoryDTO.isActive());

        Event eventSent = kafkaMockService.sendEvent("POST: Categoría creada", categoryToSave);
        System.out.println(eventSent.toString());

        Category saved = categoryRepository.save(categoryToSave);
        // Emisión hacia middleware
        inventoryEventPublisher.emitCategoriaCreada(saved);
        return saved;
    }

    public void addProductToCategories(Integer productCode, List<Integer>categories){
        // Método legacy por IDs internos
        if (categories == null) return;
        for(Integer id : categories){
            Optional<Category> categoryOptinal = categoryRepository.findById(id);
            if (categoryOptinal.isEmpty()) continue;
            Category c = categoryOptinal.get();
            List<Integer> prodcutsFromCategory = c.getProducts();
            if (prodcutsFromCategory == null) {
                prodcutsFromCategory = new ArrayList<>();
                c.setProducts(prodcutsFromCategory);
            }
            if (!prodcutsFromCategory.contains(productCode)) {
                prodcutsFromCategory.add(productCode);
            }
            Event eventSent = kafkaMockService.sendEvent
            ("PATCH: producto " + productCode +
             " agregado a las categorias: + " + categories.toString() , categories);
            System.out.println(eventSent.toString());
            categoryRepository.save(c);
        }

    }

    public void addProductToCategoriesByCodes(Integer productCode, List<Integer> codes){
        if (codes == null) return;
        for (Integer code : codes) {
            Optional<Category> catOpt = categoryRepository.findByCategoryCode(code);
            if (catOpt.isEmpty()) continue;
            Category c = catOpt.get();
            List<Integer> list = c.getProducts();
            if (list == null) { list = new ArrayList<>(); c.setProducts(list);}
            if (!list.contains(productCode)) list.add(productCode);
            categoryRepository.save(c);
        }
    }

    public boolean deleteCategory(Integer id){
        try{
            Optional<Category> categoryOptional = categoryRepository.findById(id);
            Category categoryToDeactivate = categoryOptional.get();

            categoryToDeactivate.setActive(false);
            categoryRepository.save(categoryToDeactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Categoria desactivada", categoryToDeactivate);
            System.out.println(eventSent.toString());
            // Emisión hacia middleware
            inventoryEventPublisher.emitCategoriaDesactivada(categoryToDeactivate);

            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }

    // Nuevo: baja lógica por category_code
    public boolean deleteCategoryByCode(Integer categoryCode){
        try{
            Category categoryToDeactivate = categoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new EmptyResultDataAccessException("Categoría no encontrada categoryCode=" + categoryCode, 1));

            categoryToDeactivate.setActive(false);
            categoryRepository.save(categoryToDeactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Categoria desactivada", categoryToDeactivate);
            System.out.println(eventSent.toString());
            inventoryEventPublisher.emitCategoriaDesactivada(categoryToDeactivate);
            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }

    // Nuevo: activar categoría por categoryCode
    public Category activateCategoryByCode(Integer categoryCode) {
        Category category = categoryRepository.findByCategoryCode(categoryCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Categoría no encontrada categoryCode=" + categoryCode, 1));
        if (category.isActive()) {
            throw new IllegalStateException("La categoría ya estaba activada");
        }
        category.setActive(true);
        Category saved = categoryRepository.save(category);
        kafkaMockService.sendEvent("PATCH: Categoria activada", saved);
        inventoryEventPublisher.emitCategoriaActivada(saved);
        return saved;
    }
}