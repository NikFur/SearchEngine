package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SearchIndex;
import searchengine.model.Page;
import searchengine.model.Lemma;

import java.util.List;

public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    List<SearchIndex> findByPage(Page page);
    List<SearchIndex> findByLemma(Lemma lemma);
}
