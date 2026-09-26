package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;
import java.util.List;

/** 마켓별 지수 등락률과 대분류 TOP3 섹터 랭킹 — 텔레그램 캡션용으로 확정된 결과. */
public record SectorRankingSummary(Market market, BigDecimal indexChangeRate, List<TopSectorItem> topSectors) {}
