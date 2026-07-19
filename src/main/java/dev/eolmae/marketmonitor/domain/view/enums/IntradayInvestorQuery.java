package dev.eolmae.marketmonitor.domain.view.enums;

import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestor;
import java.util.List;

public enum IntradayInvestorQuery {
    FOREIGNER,
    FOREIGN_COMPANY,
    INSTITUTION,
    PENSION_FUND,
    TRUST,
    FOREIGN_COMBINED;

    public List<IntradayInvestor> toInvestors() {
        return switch (this) {
            case FOREIGN_COMBINED -> List.of(IntradayInvestor.FOREIGNER, IntradayInvestor.FOREIGN_COMPANY);
            default -> List.of(IntradayInvestor.valueOf(name()));
        };
    }
}
