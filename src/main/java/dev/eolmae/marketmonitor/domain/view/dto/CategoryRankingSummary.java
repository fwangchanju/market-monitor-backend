package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;
import java.util.List;

/** 마켓별 지수 등락률과 대분류 TOP3 카테고리 랭킹 — 텔레그램 캡션용으로 확정된 결과. */
public record CategoryRankingSummary(Market market, BigDecimal indexChangeRate, List<TopCategoryItem> topCategories) {}
