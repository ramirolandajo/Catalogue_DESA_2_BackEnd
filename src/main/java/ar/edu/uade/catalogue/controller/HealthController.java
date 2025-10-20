package ar.edu.uade.catalogue.controller;

import ar.edu.uade.catalogue.model.ConsumedEventLog;
import ar.edu.uade.catalogue.repository.ConsumedEventLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health/consumidores")
public class HealthController {

    private final ConsumedEventLogRepository repo;

    public HealthController(ConsumedEventLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/ventas")
    public ResponseEntity<Map<String, Object>> ventasHealth() {
        Map<String, Object> map = new HashMap<>();
        long total = repo.count();
        long ok = repo.findByStatus(ConsumedEventLog.Status.PROCESSED).size();
        long err = repo.findByStatus(ConsumedEventLog.Status.ERROR).size();
        long pend = repo.findByStatus(ConsumedEventLog.Status.PENDING).size();
        map.put("total", total);
        map.put("processed", ok);
        map.put("error", err);
        map.put("pending", pend);
        return ResponseEntity.ok(map);
    }
}

