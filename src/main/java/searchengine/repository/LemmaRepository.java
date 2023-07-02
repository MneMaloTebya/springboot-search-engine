package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    void deleteAllBySite(SiteEntity siteEntity);
    List<LemmaEntity> findAllBySite(SiteEntity siteEntity);
    LemmaEntity findByLemma(String lemma);
}
