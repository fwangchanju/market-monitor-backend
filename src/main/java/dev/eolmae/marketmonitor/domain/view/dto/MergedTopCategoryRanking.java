package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.util.List;

/** 맵 발송(코스피+코스닥 앨범) 캡션용 — 두 마켓의 카테고리별 breakdown을 합친 뒤 가중평균 기준 TOP2.
 * markets는 그 시각 데이터가 있어 캡처 대상이 되는 마켓 목록이다. 병합 랭킹 자체엔 마켓 구분이 없어
 * 이 조회에서 함께 내려준다. */
public record MergedTopCategoryRanking(List<Market> markets, List<TopCategoryItem> topCategories) {}
