package ar.edu.uade.catalogue.repository;

import ar.edu.uade.catalogue.model.ConsumedEventLog;
import ar.edu.uade.catalogue.model.ConsumedEventLog.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumedEventLogRepository extends JpaRepository<ConsumedEventLog, Long> {
    Optional<ConsumedEventLog> findByEventId(String eventId);
    List<ConsumedEventLog> findByStatusAndUpdatedAtBefore(Status status, LocalDateTime threshold);
    List<ConsumedEventLog> findByStatus(Status status);
}

