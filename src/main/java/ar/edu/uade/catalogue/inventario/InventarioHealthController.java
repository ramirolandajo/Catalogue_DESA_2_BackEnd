package ar.edu.uade.catalogue.inventario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health/consumidores")
public class InventarioHealthController {

    private final InventarioConsumerMonitorService monitorService;

    public InventarioHealthController(InventarioConsumerMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public Map<String, Object> getHealth() {
        return monitorService.healthPayload();
    }
}

