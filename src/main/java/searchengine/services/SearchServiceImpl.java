package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.LemmaFinder;
import searchengine.Validator;
import searchengine.dto.response.ErrorResponse;
import searchengine.dto.search.PageInfo;
import searchengine.dto.search.SearchResponse;
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

    private final List<PageInfo> pageInfoList = new ArrayList<>();
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
        List<PageEntity> pageEntityList = getPages(sortedLemmas, siteEntity);
        fillPageInfo(sortedLemmas, pageEntityList);

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(pageEntityList.size());
        response.setData(getResponse(offset, Math.max(pageInfoList.size(), offset + limit)));
        return ResponseEntity.ok(response);
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

    private List<PageInfo> getResponse(int from, int to) {
        List<PageInfo> pageInfos = pageInfoList.subList(from, to);
        for (PageInfo pageInfo : pageInfos) {
            PageEntity pageEntity = pageInfo.getPageEntity();
            pageInfo.setSite(pageEntity.getSite().getUrl());
            pageInfo.setSiteName(pageEntity.getSite().getName());
            pageInfo.setUri(pageEntity.getPath());
            pageInfo.setTitle(Jsoup.parse(pageEntity.getContent()).title());
            pageInfo.setSnippet(getSnippet(pageEntity, lemmasQuery));

        }
        return pageInfos;
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
        float rABS;
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

    private String getSnippet(PageEntity pageEntity, Set<String> lemmasQuery) {
        StringBuilder builder = new StringBuilder();
        String content = pageEntity.getContent();
        Document document = Jsoup.parse(content);
        String text = document.select("title").text()
                .concat(document.select("body").text());

        List<String> rightSentences = Validator.getRightSentences(Validator.splitTextIntoSentences(text), lemmasQuery);
        for (String lemma : lemmasQuery) {
            for (String sentence : rightSentences) {
                int[] indexes = Validator.findLemmaIndex(lemma, sentence);
                builder.append(Validator.getFormattedSentence(indexes, sentence, lemma)).append("\n");
            }
        }
        return builder.toString();
    }
}
