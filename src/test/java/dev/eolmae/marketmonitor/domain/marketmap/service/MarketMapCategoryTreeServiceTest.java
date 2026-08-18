package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryTreeNode;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapCategoryTreeServiceTest {

    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final MarketMapCategoryTreeService service = new MarketMapCategoryTreeService(
            marketMapCategoryRepository, marketMapStockCategoryRepository, new ObjectMapper());

    @Test
    void buildTree_대분류_세부카테고리_직속종목_정확히_조립된다() {
        MarketMapCategory electronics = category(1L, null, "전기/전자");
        MarketMapCategory semiconductor = category(2L, 1L, "반도체");
        MarketMapCategory chemical = category(3L, null, "화학");

        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor, chemical));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("005930", 2L),
                        MarketMapStockCategory.create("000660", 2L),
                        MarketMapStockCategory.create("009150", 1L)));

        List<CategoryTreeNode> tree = service.buildTree();

        assertThat(tree).hasSize(2);
        CategoryTreeNode electronicsNode =
                tree.stream().filter(node -> node.categoryName().equals("전기/전자")).findFirst().orElseThrow();
        assertThat(electronicsNode.stockCodes()).containsExactly("009150");
        assertThat(electronicsNode.children()).hasSize(1);
        assertThat(electronicsNode.children().get(0).categoryName()).isEqualTo("반도체");
        assertThat(electronicsNode.children().get(0).stockCodes()).containsExactly("005930", "000660");

        CategoryTreeNode chemicalNode =
                tree.stream().filter(node -> node.categoryName().equals("화학")).findFirst().orElseThrow();
        assertThat(chemicalNode.stockCodes()).isEmpty();
        assertThat(chemicalNode.children()).isEmpty();
    }

    @Test
    void toJson_parseJson_왕복해도_구조가_동일하다() {
        List<CategoryTreeNode> original = List.of(new CategoryTreeNode(
                "전기/전자",
                List.of(new CategoryTreeNode("반도체", List.of(), List.of("005930", "000660"))),
                List.of("009150")));

        String json = service.toJson(original);
        List<CategoryTreeNode> parsed = service.parseJson(json);

        assertThat(parsed).isEqualTo(original);
    }

    private MarketMapCategory category(Long id, Long parentId, String name) {
        MarketMapCategory category =
                parentId == null ? MarketMapCategory.createParent(name) : categoryWithParent(parentId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder");
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent);
    }
}
