package com.leaseledger.maintenance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePhotoRepository extends JpaRepository<MaintenancePhoto, Long> {

    List<MaintenancePhoto> findByRequestIdOrderById(Long requestId);

    long countByRequestId(Long requestId);

    boolean existsByObjectKey(String objectKey);
}
