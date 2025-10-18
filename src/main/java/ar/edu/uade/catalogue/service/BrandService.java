package ar.edu.uade.catalogue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.Brand;
import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.DTO.BrandDTO;
import ar.edu.uade.catalogue.repository.BrandRepository;
import ar.edu.uade.catalogue.repository.ProductRepository;
import ar.edu.uade.catalogue.messaging.InventoryEventPublisher;

@Service
public class BrandService {

    @Autowired
    BrandRepository brandRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    KafkaMockService kafkaMockService;

    @Autowired
    InventoryEventPublisher inventoryEventPublisher;

    public List<Brand>getBrands(){
        return brandRepository.findAll().stream().toList();
    }

    public List<Product>getProductsFromBrand(Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        Brand brand = brandOptional.orElseThrow(() -> new EmptyResultDataAccessException("Marca no encontrada id=" + id, 1));
        List<Integer>products = brand.getProducts();
        List<Product>productsFound = new ArrayList<>();
        if (products != null) {
            for (Integer productCode : products) {
                productRepository.findByProductCode(productCode).ifPresent(productsFound::add);
            }
        }
        return productsFound;
    }

    public Brand getBrandByID(Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        return brandOptional.orElse(null);
    }

    public Brand getBrandByCode(Integer brandCode){
        return brandRepository.findByBrandCode(brandCode).orElse(null);
    }

    public void addProductToBrand(Integer productCode, Integer id){
        Optional<Brand> brandOptional = brandRepository.findById(id);
        Brand brandToUpdate = brandOptional.orElseThrow(() -> new EmptyResultDataAccessException("Marca no encontrada id=" + id, 1));

        List<Integer>productsFromBrand = brandToUpdate.getProducts();
        if (productsFromBrand == null) {
            productsFromBrand = new ArrayList<>();
            brandToUpdate.setProducts(productsFromBrand);
        }
        if (!productsFromBrand.contains(productCode)) {
            productsFromBrand.add(productCode);
        }

        Event eventSent = kafkaMockService.sendEvent
            ("PATCH: producto " + productCode +
             " agregado a la marca: + " + (brandToUpdate.getBrandCode() != null ? brandToUpdate.getBrandCode() : String.valueOf(id)) , brandToUpdate);
        System.out.println(eventSent.toString());

        brandRepository.save(brandToUpdate);
    }
    
    public Brand createBrand(BrandDTO brandDTO){
        if (brandDTO.getBrandCode() == null) {
            throw new IllegalArgumentException("brandCode es requerido para crear marca");
        }
        if (brandRepository.existsByBrandCode(brandDTO.getBrandCode())) {
            throw new IllegalArgumentException("brandCode ya existe: " + brandDTO.getBrandCode());
        }

        Brand brandToSave = new Brand();
        brandToSave.setBrandCode(brandDTO.getBrandCode());
        brandToSave.setName(brandDTO.getName());
        brandToSave.setProducts(new ArrayList<>());
        brandToSave.setActive(brandDTO.isActive());
        
        Event eventSent = kafkaMockService.sendEvent("POST: Marca creada", brandToSave);
        System.out.println(eventSent.toString());

        Brand saved = brandRepository.save(brandToSave);
        // Emisión hacia middleware
        inventoryEventPublisher.emitMarcaCreada(saved);
        return saved;
    }

    // Nuevo: activar marca por brandCode
    public Brand activateBrandByCode(Integer brandCode) {
        Brand brand = brandRepository.findByBrandCode(brandCode)
            .orElseThrow(() -> new EmptyResultDataAccessException("Marca no encontrada brandCode=" + brandCode, 1));
        if (brand.isActive()) {
            throw new IllegalStateException("La marca ya estaba activada");
        }
        brand.setActive(true);
        Brand saved = brandRepository.save(brand);
        // Persistir y emitir evento de activación
        kafkaMockService.sendEvent("PATCH: Marca activada", saved);
        inventoryEventPublisher.emitMarcaActivada(saved);
        return saved;
    }

    // Cambiado: desactivar por brandCode (ya no por id)
    public boolean deleteBrandByCode(Integer brandCode){
        try{
            Brand brandToDeactivate = brandRepository.findByBrandCode(brandCode)
                .orElseThrow(() -> new EmptyResultDataAccessException("Marca no encontrada brandCode=" + brandCode, 1));

            brandToDeactivate.setActive(false);
            brandRepository.save(brandToDeactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Marca desactivada", brandToDeactivate);
            System.out.println(eventSent.toString());
            // Emisión hacia middleware
            inventoryEventPublisher.emitMarcaDesactivada(brandToDeactivate);

            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }

    // Método legacy por id (se mantiene por compatibilidad, pero preferir deleteBrandByCode)
    public boolean deleteBrand(Integer id){
        try{
            Optional<Brand> brandOptional = brandRepository.findById(id);
            Brand brandToDeactivate = brandOptional.orElseThrow(() -> new EmptyResultDataAccessException("Marca no encontrada id=" + id, 1));

            brandToDeactivate.setActive(false);
            brandRepository.save(brandToDeactivate);

            Event eventSent = kafkaMockService.sendEvent("PATCH: Marca desactivada", brandToDeactivate);
            System.out.println(eventSent.toString());
            // Emisión hacia middleware
            inventoryEventPublisher.emitMarcaDesactivada(brandToDeactivate);

            return true; 
        }catch(EmptyResultDataAccessException e){
            return false;
        }
    }
}
