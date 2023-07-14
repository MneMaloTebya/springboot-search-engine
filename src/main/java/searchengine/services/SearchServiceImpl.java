package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.dto.response.ErrorResponse;
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
        for (int i = 0; i < sortedLemmas.size(); i++) {
            int pageIndex = 0;
            lemma = sortedLemmas.get(i).getLemma();
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

}
