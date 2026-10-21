package com.queueless.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.queueless.domain.Owner;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByEmail(String email);

    boolean existsByEmail(String email);
}
