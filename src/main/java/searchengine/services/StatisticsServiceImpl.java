package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.*;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        List<Site> sites = siteRepository.findAll();

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.size());
        total.setIndexing(false);

        int totalPages = 0;
        int totalLemmas = 0;

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for (Site site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTimeInMillis());
            item.setError(site.getLastError());

            int pagesCount = pageRepository.countBySite(site);
            int lemmasCount = lemmaRepository.countBySite(site);

            item.setPages(pagesCount);
            item.setLemmas(lemmasCount);

            totalPages += pagesCount;
            totalLemmas += lemmasCount;

            detailed.add(item);
        }

        total.setPages(totalPages);
        total.setLemmas(totalLemmas);

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
