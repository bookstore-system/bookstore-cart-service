package com.notfound.cartservice.repository;

import com.notfound.cartservice.model.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    Optional<CartEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("update CartEntity cart set cart.updatedAt = :updatedAt where cart.cartId = :cartId")
    int updateUpdatedAt(@Param("cartId") UUID cartId, @Param("updatedAt") Instant updatedAt);
}
