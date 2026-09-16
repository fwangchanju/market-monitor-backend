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

// 카테고리 등락률 TOP2 랭킹 텍스트 조립 — 랭킹 확정(필터·정렬·TOP2)은 전부 MarketMapQueryService가
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

    /** summary 하나(마켓 하나)를 "[#코스피 15분 전 대비]\n카테고리 +x.xx%p\n.." 형태로 만든다. 마켓별로
     * 개별 메시지를 보내는 섹터 전용 발송(SectorTelegramReportSender)에서만 쓴다 — 지수 등락률 대신
     * beforeMinutes로 "N분 전 대비" 라벨을 헤더에 붙이고, 본문 값도 그 라벨에 맞는 %p 차이다
     * (buildRankingText의 현재 등락률 %와 단위가 다르다). */
    public String buildSectorCaption(CategoryRankingSummary summary, int beforeMinutes) {
        String header = "[#" + MarketLabels.toKorean(summary.market()) + " " + beforeMinutes + "분 전 대비]";
        // before가 없는 카테고리는 MarketMapQueryService가 순위에서 빼므로, 그 시각 스냅샷이 통째로
        // 없으면 여기가 빈 목록이 된다(매일 첫 발송이 그렇다 — 08:10의 before는 07:55인데 수집은
        // 08:00부터다). 헤더만 덜렁 내보내지 않고 없다고 적는다.
        if (summary.topCategories().isEmpty()) {
            return header + "\n" + beforeMinutes + "분 전 데이터가 없습니다";
        }
        String body =
                summary.topCategories().stream().map(this::formatTopCategory).collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    // TopCategoryItem.changeRate는 "N분 전 대비 변화"라 단위가 %가 아니라 %p다. 헤더의 지수 등락률은
    // 현재값 그대로라 %를 쓴다 — 한 블록 안에서 단위가 갈리는 것이 의도다.
    private String formatTopCategory(TopCategoryItem top) {
        return top.categoryName() + " " + formatPercent(top.changeRate()) + "p";
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
