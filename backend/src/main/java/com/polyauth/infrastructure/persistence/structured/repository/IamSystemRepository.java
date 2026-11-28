package com.polyauth.infrastructure.persistence.structured.repository;

import com.polyauth.infrastructure.persistence.structured.entity.IamSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IamSystemRepository extends JpaRepository<IamSystem, Long> {
    Optional<IamSystem> findBySystemCode(String systemCode);
}
