package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Transactional
    void deleteAllByPage(PageEntity pageEntity);
    List<IndexEntity> findAllByPage(PageEntity pageEntity);
    boolean existsByLemma_LemmaAndPage(String lemma, PageEntity pageEntity);
}
