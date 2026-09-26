package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;

public record TopSectorItem(String sectorName, BigDecimal changeRate) {}
