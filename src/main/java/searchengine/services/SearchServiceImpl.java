package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.dto.response.ErrorResponse;
import searchengine.dto.search.PageInfo;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private IndexRepository indexRepository;

    private Set<String> lemmasQuery;
    private final List<PageInfo> pageInfoList = new ArrayList<>();

    @Override
    public ResponseEntity search(String query, String siteUrl, int offset, int limit) {
        if (query.isBlank()) {
            return ResponseEntity.ok(new ErrorResponse("Пустой запрос"));
        }
        SiteEntity siteEntity = siteRepository.findFirstByUrl(siteUrl);
        if (siteEntity == null || siteEntity.getStatusType() == StatusType.INDEXING) {
            return ResponseEntity.ok(new ErrorResponse("Сайт не проиндексирован"));
        }
        lemmasQuery = replaceQuery(query);
        List<LemmaEntity> sortedLemmas = dropPopularLemmasAndSort(lemmasQuery, siteEntity);
        List<PageEntity> pageEntityList = getPages(sortedLemmas, siteEntity);


        return null;
    }

    private Set<String> replaceQuery(String query) {
        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            return lemmaFinder.getLemmaSet(query);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<LemmaEntity> dropPopularLemmasAndSort(Set<String> lemmas, SiteEntity siteEntity) {
        List<LemmaEntity> lemmaEntityList = new ArrayList<>();
        int totalPageCountBySite = pageRepository.countBySite(siteEntity);
        for (String lemma : lemmas) {
            int pageCountByLemma;
            LemmaEntity lemmaEntity;
            if (siteEntity == null) {
                pageCountByLemma = pageRepository.findAllByLemma(lemma, PageRequest.of(0, 500)).size();
                List<LemmaEntity> lemmaEntities = lemmaRepository.findAllByLemma(lemma);
                if (!lemmaEntities.isEmpty()) {
                    int frequency = lemmaEntities.stream().map(LemmaEntity::getFrequency).reduce(Integer::sum).get();
                    lemmaEntity = new LemmaEntity(null, lemma, frequency);
                } else {
                    lemmaEntity = null;
                }
            } else {
                pageCountByLemma = pageRepository.findAllByLemmaAndSite(lemma, siteEntity, PageRequest.of(0, 500)).size();
                lemmaEntity = lemmaRepository.findFirstByLemmaAndSite(lemma, siteEntity);
            }
            if (totalPageCountBySite / pageCountByLemma >= 5 && lemma != null) { // TODO: 13.07.2023 тестовое значение
                lemmaEntityList.add(lemmaEntity);
            }
        }
        return lemmaEntityList.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency).reversed()).toList();
    }

    private List<PageEntity> getPages(List<LemmaEntity> sortedLemmas, SiteEntity siteEntity) {
        if (sortedLemmas.isEmpty()) {
            return new ArrayList<>();
        }
        String lemma = sortedLemmas.get(0).getLemma();
        List<PageEntity> pageEntityList;
        if (siteEntity == null) {
            pageEntityList = pageRepository.findAllByLemma(lemma, PageRequest.of(0, 500));
        } else {
            pageEntityList = pageRepository.findAllByLemmaAndSite(lemma, siteEntity, PageRequest.of(0, 500));
        }
        for (LemmaEntity sortedLemma : sortedLemmas) {
            int pageIndex = 0;
            lemma = sortedLemma.getLemma();
            while (pageIndex < pageEntityList.size()) {
                if (indexRepository.existsByLemma_LemmaAndPage(lemma, pageEntityList.get(pageIndex))) {
                    pageIndex++;
                } else {
                    pageEntityList.remove(pageIndex);
                }
            }
        }
        return pageEntityList;
    }

    private float calculateAbsRelevance(PageEntity pageEntity, List<LemmaEntity> sortedLemmas) {
        float rABS = 0.0f;
        for (LemmaEntity lemmaEntity : sortedLemmas) {
            rABS = rABS + indexRepository.findFirstByLemma_LemmaAndPage(lemmaEntity.getLemma(), pageEntity).getRank();
        }
        return rABS;
    }

    private void calculateRltRelevance() {
        pageInfoList.sort(Comparator.comparing(PageInfo::getRelevance).reversed());
        float rRLT = pageInfoList.get(0).getRelevance();
        pageInfoList.forEach(p -> p.setRelevance(p.getRelevance() / rRLT));
    }

    private void fillPageInfo(List<LemmaEntity> sortedLemmas, List<PageEntity> pageEntityList) {
        pageInfoList.clear();
        float rABS = 0.0f;
        for (PageEntity pageEntity : pageEntityList) {
            rABS = calculateAbsRelevance(pageEntity, sortedLemmas);
            PageInfo pageInfo = new PageInfo();
            pageInfo.setPageEntity(pageEntity);
            pageInfo.setRelevance(rABS);
        }
        if (pageInfoList.size() > 0) {
            calculateRltRelevance();
        }
    }

}
