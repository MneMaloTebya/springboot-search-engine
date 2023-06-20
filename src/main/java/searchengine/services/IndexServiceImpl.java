package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.PageValidator;
import searchengine.WebCrawler;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {
    
    private final SitesList sitesList;
    private final Map<SiteEntity, List<PageEntity>> sitesIndexing = Collections.synchronizedMap(new HashMap<>());
    private final String pageIndexing = "";

    @Getter
    private PageRepository pageRepository;
    
    private SiteRepository siteRepository;

    @Override
    public ResponseEntity startIndexing() {

        if (!sitesIndexing.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        if (!pageIndexing.isBlank()) {
            return ResponseEntity.ok(null);
        }
        
        for (Site site : sitesList.getSites()) {
            startIndexSite(site);
        }
        
        return null;
    }

    private void startIndexSite(Site site) {
        new Thread(() -> {
            SiteEntity siteEntity = siteRepository.findFirstByName(site.getName());
            String siteUrl = PageValidator.getHostFromUrl(site.getUrl());
            if (siteEntity != null) {
                deleteSiteEntity(siteEntity);
                siteEntity.setUrl(PageValidator.getHostFromUrl(siteUrl));
            } else {
                siteEntity = new SiteEntity(StatusType.INDEXING, LocalDateTime.now(), "", siteUrl, site.getName());
            }
            siteRepository.save(siteEntity);
            List<PageEntity> pageEntityList = new ArrayList<>(List.of(new PageEntity(siteEntity, "/", 0, "")));
            sitesIndexing.put(siteEntity, pageEntityList);
            new ForkJoinPool().invoke(new WebCrawler(pageEntityList.get(0), pageEntityList, this));

            if (siteEntity.getStatusType() == StatusType.INDEXING) {
                insetAllData(pageEntityList, siteEntity);
                siteEntity.setStatusType(StatusType.INDEXED);
                siteRepository.save(siteEntity);
            }
        }).start();
    }

    private void deleteSiteEntity(SiteEntity siteEntity) {
        siteEntity.setStatusType(StatusType.INDEXING);
        siteRepository.save(siteEntity);
        pageRepository.deleteAllBySite(siteEntity);

        // TODO: 18.06.2023 удалять леммы

        siteEntity.setStatusTime(LocalDateTime.now());
    }

    @Override
    public ResponseEntity stopIndexing() {
        return null;
    }

    @Override
    public ResponseEntity indexPage(String url) {
        return null;
    }

    

    public void insetAllData(List<PageEntity> pageEntityList, SiteEntity siteEntity) {
        
        for (PageEntity pageEntity : pageEntityList) {
            if (pageEntity.getCode() >= 400) {
                continue;
            }

            // TODO: 18.06.2023 логика лемматизации 
        }
        
        pageRepository.saveAll(pageEntityList);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

}
