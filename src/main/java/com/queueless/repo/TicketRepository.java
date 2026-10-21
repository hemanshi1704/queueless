package com.queueless.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.queueless.domain.Ticket;
import com.queueless.domain.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByToken(String token);

    Optional<Ticket> findFirstByShopIdAndStatus(Long shopId, TicketStatus status);

    Optional<Ticket> findFirstByShopIdAndStatusOrderByNumberAsc(Long shopId, TicketStatus status);

    List<Ticket> findByShopIdAndStatusOrderByNumberAsc(Long shopId, TicketStatus status);

    long countByShopIdAndStatus(Long shopId, TicketStatus status);

    long countByShopIdAndStatusAndNumberLessThan(Long shopId, TicketStatus status, int number);
}
