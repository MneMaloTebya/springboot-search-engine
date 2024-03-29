package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.MyConnector;
import searchengine.MyAssistant;
import searchengine.WebCrawler;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ErrorResponse;
import searchengine.dto.response.OkResponse;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

    private final SitesList sitesList;
    private final Map<SiteEntity, List<PageEntity>> sitesIndexing = Collections.synchronizedMap(new HashMap<>());
    private String pageIndexing = "";

    @Autowired
    @Getter
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Override
    public ResponseEntity startIndexing() {
        if (!sitesIndexing.isEmpty())
            return ResponseEntity.ok(new ErrorResponse("Предыдущая индексация еще не завершена"));
        if (!pageIndexing.isBlank())
            return ResponseEntity.ok(new ErrorResponse("Индексация невозможна. Запущена индексация страницы"));
        sitesList.getSites().forEach(this::startIndexSite);
        return ResponseEntity.ok(new OkResponse());
    }

    private void startIndexSite(Site site) {
        new Thread(() -> {
            SiteEntity siteEntity = siteRepository.findFirstByName(site.getName());
            String siteUrl = MyAssistant.getSiteUrl(site);
            if (siteEntity != null) {
                deleteSiteEntity(siteEntity);
                siteEntity.setUrl(siteUrl);
                siteEntity.setLastError("");
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
        lemmaRepository.deleteAllBySite(siteEntity);
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
        if (!sitesIndexing.isEmpty())
            return ResponseEntity.ok(new ErrorResponse("Предыдущая индексация еще не завершена"));
        if (!MyAssistant.urlIsLocatedConfig(url, sitesList))
            return ResponseEntity.ok(new ErrorResponse("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        Document document;
        try {
            document = MyConnector.getDocument(url);
        } catch (IOException e) {
            return ResponseEntity.ok(new ErrorResponse("Страница недоступна"));
        }
        if (pageIndexing.equals(document.location())) {
            return ResponseEntity.ok(new ErrorResponse("Эта станица уже индексируется"));
        }
        pageIndexing = document.location();
        String siteUrl = MyAssistant.getSiteUrl(url);
        SiteEntity siteEntity = siteRepository.findFirstByUrl(siteUrl);
        PageEntity pageEntity = pageRepository.findByPathAndSite(MyAssistant.getPathSite(url, siteEntity.getUrl()), siteEntity);
        if (pageEntity != null) {
            deletePageData(pageEntity, siteEntity);
        }
        String pageUrl = MyAssistant.getPathSite(url, siteUrl);
        int code = document.connection().response().statusCode();
        String content = document.outerHtml();
        pageEntity = new PageEntity(siteEntity, pageUrl, code, content);
        insetAllData(new ArrayList<>(List.of(pageEntity)), siteEntity);
        pageIndexing = "";
        return ResponseEntity.ok(new OkResponse());
    }

    private void deletePageData(PageEntity pageEntity, SiteEntity siteEntity) {
        List<LemmaEntity> lemmas = lemmaRepository.findAllBySite(siteEntity);
        List<IndexEntity> indexes = indexRepository.findAllByPage(pageEntity);
        if (lemmas.isEmpty() || indexes.isEmpty()) {
            lemmaRepository.deleteAllBySite(siteEntity);
            indexRepository.deleteAllByPage(pageEntity);
        }
        pageRepository.delete(pageEntity);
    }

    public void insetAllData(List<PageEntity> pageEntityList, SiteEntity siteEntity) {
        List<LemmaEntity> lemmasToInsert = new ArrayList<>();
        List<IndexEntity> indexesToInsert = new ArrayList<>();
        List<LemmaEntity> lemmaEntityList = lemmaRepository.findAllBySite(siteEntity);
        for (PageEntity pageEntity : pageEntityList) {
            if (pageEntity.getCode() >= 400) {
                continue;
            }
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Map<String, Integer> lemmas = lemmaFinder.collectLemmas(pageEntity.getContent());
                lemmas.forEach((key, value) -> {
                    LemmaEntity lemma = MyAssistant.findLemmaInList(lemmasToInsert, key);
                    boolean isLemmaInListToInsert = true;
                    if (lemma == null) {
                        lemma = MyAssistant.findLemmaInList(lemmaEntityList, key);
                        isLemmaInListToInsert = false;
                    }
                    if (lemma == null) {
                        lemma = new LemmaEntity(siteEntity, key, 1);
                    } else {
                        lemma.setFrequency(lemma.getFrequency() + 1);
                    }
                    if (!isLemmaInListToInsert) {
                        lemmasToInsert.add(lemma);
                    }
                    indexesToInsert.add(new IndexEntity(pageEntity, lemma, value));
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        pageRepository.saveAll(pageEntityList);
        lemmaRepository.saveAll(lemmasToInsert);
        indexRepository.saveAll(indexesToInsert);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }
}