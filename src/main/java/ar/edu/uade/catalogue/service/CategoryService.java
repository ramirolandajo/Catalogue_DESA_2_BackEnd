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

@Service
public class CategoryService {
    
    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired 
    KafkaMockService kafkaMockService;

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
            productsFound.add(productRepository.findByProductCode(productCode).get());
        }

        return productsFound;
    }

    public Category getCategoryByID(Integer id){
        Optional<Category> category = categoryRepository.findById(id);
        return category.orElse(null);         
    }

    public List<Category> geCategoriesForProductByID(List<Integer>categories){
        // No tiene endpoint porque es metodo interno que ayuda a la creacion del producto
        List<Category> categoriesFounded = new ArrayList<>();

        for (Integer id : categories) {
            categoriesFounded.add(categoryRepository.findById(id).get());
        }
        return categoriesFounded;
    }
    
    public Category createCategory(CategoryDTO categoryDTO){
        Category categoryToSave = new Category();

        categoryToSave.setName(categoryDTO.getName());
        categoryToSave.setProducts(new ArrayList<Integer>());
        categoryToSave.setActive(categoryDTO.isActive());

        Event eventSent = kafkaMockService.sendEvent("POST: Cateogria creada", categoryToSave);
        System.out.println(eventSent.toString());

        return categoryRepository.save(categoryToSave);
    }

    public void addProductToCategories(Integer productCode, List<Integer>categories){
        // Cambiar a boolean el return para validar cuando se asigna en Product?
        for(Integer id : categories){
            Optional<Category> categoryOptinal = categoryRepository.findById(id);
            Category c = categoryOptinal.get();
            
            List<Integer> prodcutsFromCategory = c.getProducts();
            prodcutsFromCategory.add(productCode);
            
            Event eventSent = kafkaMockService.sendEvent
            ("PATCH: producto " + String.valueOf(productCode)
             + " agregado a las categorias: + " + categories.toString() , categories);

            System.out.println(eventSent.toString());

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

            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}