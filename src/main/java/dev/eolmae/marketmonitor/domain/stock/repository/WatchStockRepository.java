package dev.eolmae.marketmonitor.domain.stock.repository;
import dev.eolmae.marketmonitor.domain.stock.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchStockRepository extends JpaRepository<WatchStock, Long> {

	List<WatchStock> findByUserUserKey(String userKey);

	List<WatchStock> findByRegisterBy(RegisterBy registerBy);

	@Query("SELECT DISTINCT ws.stock.stockCode FROM WatchStock ws")
	List<String> findDistinctStockCodes();

	Optional<WatchStock> findByUserUserKeyAndStockStockCode(String userKey, String stockCode);

	boolean existsByUserUserKeyAndStockStockCode(String userKey, String stockCode);

	List<WatchStock> findByUserUserKeyAndIsPrimaryTrue(String userKey);
}
