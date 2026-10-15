package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    List<Environment> findAllByOrderBySortOrderAsc();
    Optional<Environment> findByKey(String key);
}
