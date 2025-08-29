package ar.edu.uade.catalogue.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.catalogue.model.DTO.ReviewDTO;
import ar.edu.uade.catalogue.model.Review;
import ar.edu.uade.catalogue.service.ReviewService;

@RestController
@RequestMapping(value="/review")
public class ReviewController {

    @Autowired
    ReviewService reviewService;

    @GetMapping(value="/getAll",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Review>>getReviews(){
        try {
            List<Review> reviews = reviewService.getReviews();
            return new ResponseEntity<>(reviews, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }

    @GetMapping(value="/getReviewByID/{id}",produces={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Review>getReviewByID(@PathVariable("id") Integer id){
        try {
            Review review = reviewService.getReviewByID(id);
            return new ResponseEntity<>(review,HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(value="/create",consumes={MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?>createReview(@RequestPart ReviewDTO reviewDTO){
        try {
            Review reviewSaved = reviewService.createReview(reviewDTO);
            return new ResponseEntity<>(reviewSaved,HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(),HttpStatus.CONFLICT);
        }
    }

    @DeleteMapping(value="/delete/{id}")
    public ResponseEntity<Void>delteReview(@PathVariable("id") Integer id){
        boolean deleted = reviewService.deleteReview(id);

        if(deleted){
            return new ResponseEntity<>(HttpStatus.OK);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


}
