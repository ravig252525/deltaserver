package com.delta.server.delta.repo;

import com.delta.server.delta.model.Watchlist;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

import reactor.core.publisher.Mono;

public interface WatchlistRepository
        extends ReactiveCrudRepository<Watchlist, Long> {

    /**
     * एक specific user के लिए specific symbol वाली entry खोजे
     *
     * @param symbol the trading symbol, e.g. "BTCUSDT"
     * @param userId the user identifier
     * @return Mono.empty() अगर कोई नहीं मिला, वरना Mono<Watchlist>
     */
    Mono<Watchlist> findBySymbolAndUserId(String symbol, String userId);

    /**
     * एक user की सारी watchlist entries लाए
     *
     * @param userId the user identifier
     * @return Flux of Watchlist entries
     */
    Flux<Watchlist> findAllByUserId(String userId);

    /**
     * (Optional) अगर आप एक ही symbol पर सभी users की entries चाहते हैं:
     */
    Flux<Watchlist> findAllBySymbol(String symbol);

    /**
     * (Optional) delete by symbol और userId:
     */
    Mono<Void> deleteBySymbolAndUserId(String symbol, String userId);
}

