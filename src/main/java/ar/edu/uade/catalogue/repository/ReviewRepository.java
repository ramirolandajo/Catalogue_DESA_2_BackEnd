package ar.edu.uade.catalogue.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.uade.catalogue.model.Review;

@Repository
public interface  ReviewRepository extends  JpaRepository<Review,Integer>{

    List<Review>findReviewsByProductID(Integer id);
}
