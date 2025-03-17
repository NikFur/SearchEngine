package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Page;
import searchengine.model.Site;
import java.util.List;

public interface PageRepository extends JpaRepository<Page, Integer> {

    int countBySite(Site site);

    boolean existsByPathAndSite(String path, Site site);

    @Query(value = """
        SELECT p.*
        FROM pages p
        JOIN search_index si ON si.page_id = p.id
        JOIN lemma l ON l.id = si.lemma_id
        WHERE l.lemma = :query AND p.site_id = :siteId
        GROUP BY p.id
        ORDER BY MAX(si.rank_value) DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Page> findPagesByLemma(@Param("siteId") Integer siteId,
                                @Param("query") String query,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    @Query(value = """
        SELECT p.*
        FROM pages p
        JOIN search_index si ON si.page_id = p.id
        JOIN lemma l ON l.id = si.lemma_id
        WHERE l.lemma = :query
        GROUP BY p.id
        ORDER BY MAX(si.rank_value) DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Page> findPagesByLemmaForAllSites(@Param("query") String query,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Query("SELECT p FROM Page p WHERE p.path = :path AND p.site = :site")
    List<Page> findByPathAndSite(@Param("path") String path, @Param("site") Site site);
}

