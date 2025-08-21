package ar.edu.uade.catalogue.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Category;
import ar.edu.uade.catalogue.repository.CategoryRepository;

@Service
public class CategoryService {
    
    @Autowired
    CategoryRepository categoryRepository;

    public List<Category>getCategories(){
        List<Category> categories = categoryRepository.findAll();
        return categories;
    }

    public Category getCategoryByID(Integer id){
        Optional<Category> category = categoryRepository.findById(id);
        return category.orElse(null); //Mejorar para evitar los null?
        
    }

    public Category getCategoryByName(String name){
        Optional<Category> category = categoryRepository.findByName(name);
        return category.orElse(null);
    }
    
    public Category createCategory(String name){
        //Asumimos sin dto xq es chico el objeto 
        //id 0 xq es identity autoincrement
        Category category = new Category(0,name); 
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