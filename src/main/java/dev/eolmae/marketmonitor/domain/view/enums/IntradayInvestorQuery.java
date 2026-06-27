package dev.eolmae.marketmonitor.domain.view.enums;

import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import java.util.List;

public enum IntradayInvestorQuery {
    FOREIGNER,
    FOREIGN_COMPANY,
    INSTITUTION,
    PENSION_FUND,
    TRUST,
    FOREIGN_COMBINED;

    public List<IntradayInvestorType> toInvestorTypes() {
        return switch (this) {
            case FOREIGN_COMBINED -> List.of(IntradayInvestorType.FOREIGNER, IntradayInvestorType.FOREIGN_COMPANY);
            default -> List.of(IntradayInvestorType.valueOf(name()));
        };
    }
}
