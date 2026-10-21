package com.queueless.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.queueless.domain.Shop;

import jakarta.persistence.LockModeType;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Shop> findByOwnerIdOrderByIdAsc(Long ownerId);

    /** Serialises ticket numbering and queue advancement per shop. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.id = :id")
    Optional<Shop> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.slug = :slug")
    Optional<Shop> lockBySlug(@Param("slug") String slug);
}
