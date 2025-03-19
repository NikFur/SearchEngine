package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;

import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionalPersistenceService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaIndexerService lemmaIndexerService;

    @Transactional
    public synchronized Page savePageWithLemmas(Site site, String uri, String content, int code, String text) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(uri);
        page.setUri(uri);
        page.setContent(content);
        page.setCode(code);
        pageRepository.save(page);

        lemmaIndexerService.saveLemmas(page, text);

        return page;
    }

    @Transactional(readOnly = true)
    public boolean existsByPathAndSite(String path, Site site) {
        return pageRepository.existsByPathAndSite(path, site);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSiteStatus(Site site, Status status, String message) {
        site.setStatus(status);
        site.setLastError(message);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }
}

