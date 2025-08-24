package ar.edu.uade.catalogue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.repository.CategoryRepository;

@Service
public class CategoryService {
    
    @Autowired
    CategoryRepository categoryRepository;

    public List<Category>getCategories(){
        List<Category> categories = categoryRepository.findAll();
        return categories;
    }

    public List<Product> getAllProductsFromCategory(Integer id){
        Optional<Category> categoryOptional = categoryRepository.findById(id);
        Category category = categoryOptional.get();
        
        return category.getProducts();
    }

    public Category getCategoryByID(Integer id){
        Optional<Category> category = categoryRepository.findById(id);
        return category.orElse(null); //Mejorar para evitar los null?
        
    }

    public Category getCategoryByName(String name){
        Optional<Category> category = categoryRepository.findByName(name);
        return category.orElse(null);
    }

    public List<Category> geCategoriesByName(Set<String>categories){
        List<Category> categoriesFounded = new ArrayList<>();

        for (String name : categories) {
            categoriesFounded.add(categoryRepository.findByName(name).get());
        }
        return categoriesFounded;
    }
    
    public Category createCategory(Category category){
        //Asumimos sin dto xq es chico el objeto 
        return categoryRepository.save(category);
    }

    public boolean deleteCategory(Integer id){
        try{
            categoryRepository.deleteById(id);
            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}