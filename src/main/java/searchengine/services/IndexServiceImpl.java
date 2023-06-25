package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.MyConnectionAssistant;
import searchengine.WebCrawler;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ErrorResponse;
import searchengine.dto.response.OkResponse;
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

    @Autowired
    @Getter
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Override
    public ResponseEntity startIndexing() {
        if (!sitesIndexing.isEmpty()) return ResponseEntity.ok(new ErrorResponse("Предыдущая индексация еще не завершена"));
        if (!pageIndexing.isBlank()) return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Запущена индексация страницы"));
        sitesList.getSites().forEach(this::startIndexSite);
        return ResponseEntity.ok(new OkResponse());
    }

    private void startIndexSite(Site site) {
        new Thread(() -> {
            SiteEntity siteEntity = siteRepository.findFirstByName(site.getName());
            String siteUrl = MyConnectionAssistant.getSiteUrl(site);
            if (siteEntity != null) {
                deleteSiteEntity(siteEntity);
                siteEntity.setUrl(siteUrl);
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
            sitesIndexing.remove(siteEntity);
        }).start();
    }

    private void deleteSiteEntity(SiteEntity siteEntity) {
        sitesIndexing.put(siteEntity, null);
        siteEntity.setStatusType(StatusType.INDEXING);
        siteRepository.save(siteEntity);
        pageRepository.deleteAllBySite(siteEntity);

        // TODO: 18.06.2023 удалять леммы

        siteEntity.setStatusTime(LocalDateTime.now());
    }

    @Override
    public ResponseEntity stopIndexing() {
        if (sitesIndexing.isEmpty()) {
            return ResponseEntity.ok(new ErrorResponse("Индексация не запущена"));
        }
        for (SiteEntity siteEntity : sitesIndexing.keySet()) {
            siteEntity.setStatusType(StatusType.FAILED);
            siteEntity.setLastError("Индексация остановлена");
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
        return ResponseEntity.ok(new OkResponse());
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
