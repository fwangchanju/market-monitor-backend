package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.util.MarketLabels;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 카테고리 등락률 TOP3 랭킹 텍스트 조립 — 랭킹 확정(필터·정렬·TOP3)은 전부 MarketMapQueryService가
// 끝낸 뒤 넘겨주므로, 여기는 헤더 조립과 퍼센트 포맷만 한다. 조회 의존성은 없다.
@Component
public class CategoryRankingTextBuilder {

    /** summaries가 담고 있는 마켓 각각을 "#코스피 +x.xx%\n카테고리 +x.xx%\n\n#코스닥 +x.xx%\n.." 형태로
     * 이어붙인다(헤더 옆 퍼센트는 그 마켓 지수의 등락률). 데이터 없는 마켓은 summaries에 이미 없는 상태라
     * 자동으로 텍스트에서도 빠진다. 헤더("Custom Sector" 등)는 안 붙이므로 호출부가 자기 맥락에 맞는
     * 헤더를 붙여 쓴다. */
    public String buildRankingText(List<CategoryRankingSummary> summaries) {
        return summaries.stream()
                .map(summary -> buildHeader(summary.market(), summary.indexChangeRate()) + "\n"
                        + summary.topCategories().stream()
                                .map(this::formatTopCategory)
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    private String formatTopCategory(TopCategoryItem top) {
        return top.categoryName() + " " + formatPercent(top.changeRate());
    }

    private String buildHeader(Market market, BigDecimal indexChangeRate) {
        String header = "#" + MarketLabels.toKorean(market);
        if (indexChangeRate != null) {
            header += " " + formatPercent(indexChangeRate);
        }
        return header;
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String sign = rounded.signum() > 0 ? "+" : "";
        return sign + rounded.toPlainString() + "%";
    }
}
