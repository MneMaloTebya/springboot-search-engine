package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
       for (Site site : sitesList) {
           DetailedStatisticsItem item = new DetailedStatisticsItem();
           item.setName(site.getName());
           item.setUrl(site.getUrl());
           SiteEntity siteEntity = siteRepository.findFirstByUrl(site.getUrl());
           if (siteEntity != null) {
               int pages = pageRepository.countBySite(siteEntity);
               int lemmas = lemmaRepository.countBySite(siteEntity);
               item.setPages(pages);
               item.setLemmas(lemmas);
               item.setStatus(siteEntity.getStatusType().toString());
               if (siteEntity.getLastError() == null) {
                   item.setError("При индексации ошибок не возникло");
               } else {
                   item.setError(siteEntity.getLastError());
               }
               item.setStatusTime(siteEntity.getStatusTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
               total.setPages(total.getPages() + pages);
               total.setLemmas(total.getLemmas() + lemmas);
               detailed.add(item);
           }
       }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
