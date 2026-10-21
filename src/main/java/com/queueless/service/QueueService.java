package com.queueless.service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueless.domain.Owner;
import com.queueless.domain.Shop;
import com.queueless.domain.Ticket;
import com.queueless.domain.TicketStatus;
import com.queueless.repo.OwnerRepository;
import com.queueless.repo.ShopRepository;
import com.queueless.repo.TicketRepository;
import com.queueless.web.ApiException;
import com.queueless.web.Dtos.CreateShopRequest;
import com.queueless.web.Dtos.OwnerSnapshot;
import com.queueless.web.Dtos.PublicStatus;
import com.queueless.web.Dtos.ServingEntry;
import com.queueless.web.Dtos.ShopSummary;
import com.queueless.web.Dtos.TicketView;
import com.queueless.web.Dtos.WaitingEntry;

@Service
public class QueueService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RNG = new SecureRandom();

    private final ShopRepository shops;
    private final TicketRepository tickets;
    private final OwnerRepository owners;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QueueService(ShopRepository shops, TicketRepository tickets, OwnerRepository owners,
                        ApplicationEventPublisher events, Clock clock) {
        this.shops = shops;
        this.tickets = tickets;
        this.owners = owners;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ owner: shops

    @Transactional
    public ShopSummary createShop(Long ownerId, CreateShopRequest req) {
        Owner owner = owners.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown account"));
        Shop shop = shops.save(new Shop(owner, req.name().trim(), uniqueSlug(req.name()),
                req.avgServiceMinutes() * 60.0));
        return summary(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopSummary> listShops(Long ownerId) {
        return shops.findByOwnerIdOrderByIdAsc(ownerId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public OwnerSnapshot snapshot(Long ownerId, Long shopId) {
        Shop shop = shops.findById(shopId).orElseThrow(QueueService::shopNotFound);
        assertOwner(shop, ownerId);
        return snapshotOf(shop);
    }

    @Transactional
    public OwnerSnapshot callNext(Long ownerId, Long shopId) {
        Shop shop = lockOwned(ownerId, shopId);
        advance(shop, true);
        publish(shop);
        return snapshotOf(shop);
    }

    @Transactional
    public OwnerSnapshot skipCurrent(Long ownerId, Long shopId) {
        Shop shop = lockOwned(ownerId, shopId);
        advance(shop, false);
        publish(shop);
        return snapshotOf(shop);
    }

    @Transactional
    public OwnerSnapshot toggleOpen(Long ownerId, Long shopId) {
        Shop shop = lockOwned(ownerId, shopId);
        shop.setOpen(!shop.isOpen());
        publish(shop);
        return snapshotOf(shop);
    }

    // ------------------------------------------------------------------ public: customers

    @Transactional(readOnly = true)
    public PublicStatus publicStatus(String slug) {
        Shop shop = shops.findBySlug(slug).orElseThrow(QueueService::shopNotFound);
        return statusOf(shop);
    }

    @Transactional
    public TicketView join(String slug, String customerName) {
        Shop shop = shops.lockBySlug(slug).orElseThrow(QueueService::shopNotFound);
        if (!shop.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This queue is closed right now");
        }
        Ticket ticket = tickets.save(new Ticket(shop, shop.nextNumber(), newToken(),
                customerName.trim(), Instant.now(clock)));
        publish(shop);
        return view(ticket);
    }

    @Transactional(readOnly = true)
    public TicketView ticket(String token) {
        return view(tickets.findByToken(token).orElseThrow(QueueService::ticketNotFound));
    }

    @Transactional
    public TicketView cancel(String token) {
        Ticket found = tickets.findByToken(token).orElseThrow(QueueService::ticketNotFound);
        // Lock the shop first so a cancel can't race with the owner calling this ticket.
        Shop shop = shops.lockById(found.getShop().getId()).orElseThrow(QueueService::shopNotFound);
        Ticket ticket = tickets.findByToken(token).orElseThrow(QueueService::ticketNotFound);
        if (ticket.getStatus() != TicketStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only waiting tickets can be cancelled");
        }
        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setFinishedAt(Instant.now(clock));
        publish(shop);
        return view(ticket);
    }

    // ------------------------------------------------------------------ internals

    /** Finishes (or skips) whoever is being served, then promotes the next waiting ticket. */
    private void advance(Shop shop, boolean completed) {
        Instant now = Instant.now(clock);
        tickets.findFirstByShopIdAndStatus(shop.getId(), TicketStatus.SERVING).ifPresent(current -> {
            current.setFinishedAt(now);
            if (completed) {
                current.setStatus(TicketStatus.DONE);
                long took = Duration.between(current.getStartedAt(), now).toSeconds();
                shop.setAvgServiceSeconds(EtaCalculator.updateAverage(shop.getAvgServiceSeconds(), took));
            } else {
                current.setStatus(TicketStatus.SKIPPED);
            }
        });
        tickets.findFirstByShopIdAndStatusOrderByNumberAsc(shop.getId(), TicketStatus.WAITING)
                .ifPresent(next -> {
                    next.setStatus(TicketStatus.SERVING);
                    next.setStartedAt(now);
                });
    }

    private TicketView view(Ticket t) {
        Shop shop = t.getShop();
        Optional<Ticket> serving = tickets.findFirstByShopIdAndStatus(shop.getId(), TicketStatus.SERVING);
        Integer position = null;
        Long eta = null;
        if (t.getStatus() == TicketStatus.WAITING) {
            int ahead = (int) tickets.countByShopIdAndStatusAndNumberLessThan(
                    shop.getId(), TicketStatus.WAITING, t.getNumber());
            position = ahead + 1;
            eta = EtaCalculator.etaSeconds(ahead, shop.getAvgServiceSeconds(), elapsed(serving));
        }
        return new TicketView(t.getToken(), shop.getName(), shop.getSlug(), t.getNumber(),
                t.getCustomerName(), t.getStatus(), serving.map(Ticket::getNumber).orElse(null),
                position, eta);
    }

    private PublicStatus statusOf(Shop shop) {
        Optional<Ticket> serving = tickets.findFirstByShopIdAndStatus(shop.getId(), TicketStatus.SERVING);
        int waiting = (int) tickets.countByShopIdAndStatus(shop.getId(), TicketStatus.WAITING);
        long eta = EtaCalculator.etaSeconds(waiting, shop.getAvgServiceSeconds(), elapsed(serving));
        return new PublicStatus(shop.getName(), shop.isOpen(), serving.map(Ticket::getNumber).orElse(null),
                waiting, Math.round(shop.getAvgServiceSeconds()), eta);
    }

    private OwnerSnapshot snapshotOf(Shop shop) {
        Instant now = Instant.now(clock);
        ServingEntry serving = tickets.findFirstByShopIdAndStatus(shop.getId(), TicketStatus.SERVING)
                .map(t -> new ServingEntry(t.getNumber(), t.getCustomerName(),
                        Duration.between(t.getStartedAt(), now).toSeconds()))
                .orElse(null);
        List<WaitingEntry> waiting = tickets
                .findByShopIdAndStatusOrderByNumberAsc(shop.getId(), TicketStatus.WAITING).stream()
                .map(t -> new WaitingEntry(t.getNumber(), t.getCustomerName(),
                        Duration.between(t.getCreatedAt(), now).toSeconds()))
                .toList();
        long served = tickets.countByShopIdAndStatus(shop.getId(), TicketStatus.DONE);
        return new OwnerSnapshot(summary(shop), serving, waiting, served,
                Math.round(shop.getAvgServiceSeconds()));
    }

    private ShopSummary summary(Shop shop) {
        int waiting = (int) tickets.countByShopIdAndStatus(shop.getId(), TicketStatus.WAITING);
        return new ShopSummary(shop.getId(), shop.getName(), shop.getSlug(), shop.isOpen(), waiting);
    }

    private Long elapsed(Optional<Ticket> serving) {
        Instant now = Instant.now(clock);
        return serving.map(t -> Duration.between(t.getStartedAt(), now).toSeconds()).orElse(null);
    }

    private void publish(Shop shop) {
        events.publishEvent(new QueueChanged(shop.getSlug(), statusOf(shop)));
    }

    private Shop lockOwned(Long ownerId, Long shopId) {
        Shop shop = shops.lockById(shopId).orElseThrow(QueueService::shopNotFound);
        assertOwner(shop, ownerId);
        return shop;
    }

    private static void assertOwner(Shop shop, Long ownerId) {
        if (!shop.getOwner().getId().equals(ownerId)) {
            // Same response as "missing" so shop ids can't be probed.
            throw shopNotFound();
        }
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "queue";
        }
        if (base.length() > 30) {
            base = base.substring(0, 30).replaceAll("-+$", "");
        }
        String slug;
        do {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                suffix.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
            }
            slug = base + "-" + suffix;
        } while (shops.existsBySlug(slug));
        return slug;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static ApiException shopNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Queue not found");
    }

    private static ApiException ticketNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Ticket not found");
    }
}
