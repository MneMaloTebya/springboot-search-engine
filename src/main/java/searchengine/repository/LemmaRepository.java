package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Transactional
    void deleteAllBySite(SiteEntity siteEntity);
    List<LemmaEntity> findAllBySite(SiteEntity siteEntity);
    LemmaEntity findByLemma(String lemma);
    int countBySite(SiteEntity siteEntity);
}
