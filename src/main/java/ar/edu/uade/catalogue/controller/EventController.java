package ar.edu.uade.catalogue.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.service.KafkaMockService;
import org.springframework.web.bind.annotation.GetMapping;



@RestController
@RequestMapping(value = "/event")
public class EventController {

    @Autowired
    KafkaMockService kafkaMockService;

    @GetMapping(value = "/getAll",produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Event>> getAll() {
        try{
            List<Event> events = kafkaMockService.getAll();
            return new ResponseEntity<>(events,HttpStatus.OK);
        }catch(EmptyResultDataAccessException e){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }
    

}
