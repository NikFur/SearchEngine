package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.Status;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    List<Site> findByUrl(String url);
    Optional<Site> findFirstByUrl(String url);
}
