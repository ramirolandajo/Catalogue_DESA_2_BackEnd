package ar.edu.uade.catalogue.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.uade.catalogue.model.DTO.ReviewDTO;
import ar.edu.uade.catalogue.model.Product;
import ar.edu.uade.catalogue.model.Review;
import ar.edu.uade.catalogue.repository.ReviewRepository;

@Service
public class ReviewService {

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    ProductService productService;

    public List<Review>getReviews(){
        List<Review>reviews = reviewRepository.findAll();
        return reviews;
    }

    public Review getReviewByID(Integer id){
        Optional<Review> reviewOptional = reviewRepository.findById(id);
        return reviewOptional.get();
    }
    
    public Review createReview(ReviewDTO reviewDTO){
        Product product = productService.getProductByID(reviewDTO.getProductID());

        Review reviewToSave = new Review();
        reviewToSave.setUser(reviewDTO.getUser());
        reviewToSave.setTitle(reviewDTO.getTitle());
        reviewToSave.setDescription(reviewDTO.getDescription());
        reviewToSave.setScore(reviewDTO.getScore());
        reviewToSave.setProduct(product);
        reviewToSave.setImages(reviewDTO.getImages());

        return reviewRepository.save(reviewToSave);
    }

    public List<Review>getReviewsByProductID(Integer id){
        List<Review> reviews = reviewRepository.findReviewsByProductID(id);
        return reviews;
    }

    public boolean deleteReview(Integer id){
        try{
            reviewRepository.deleteById(id);
            return true;
        }catch(EmptyResultDataAccessException e){
            return false;
        }

    }

}
