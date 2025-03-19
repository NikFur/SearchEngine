package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);

    int countBySite(Site site);
}
