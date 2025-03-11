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

    // 🔍 Поиск страниц по содержимому и сайту
    @Query(value = """
        SELECT * FROM pages 
        WHERE site_id = :siteId AND content LIKE CONCAT('%', :query, '%') 
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Page> searchPagesByQueryAndSite(@Param("siteId") int siteId,
                                         @Param("query") String query,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    // 🔍 Поиск страниц по лемме внутри конкретного сайта
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

    // 🔍 Поиск страниц по лемме для всех сайтов
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

    // 🔍 Поиск страниц по пути и сайту (возвращает список)
    @Query("SELECT p FROM Page p WHERE p.path = :path AND p.site = :site")
    List<Page> findByPathAndSite(@Param("path") String path, @Param("site") Site site);

    // 🔍 Поиск страниц по uri и сайту (возвращает список)
    @Query("SELECT p FROM Page p WHERE p.uri = :uri AND p.site = :site")
    List<Page> findByUriAndSite(@Param("uri") String uri, @Param("site") Site site);
}

