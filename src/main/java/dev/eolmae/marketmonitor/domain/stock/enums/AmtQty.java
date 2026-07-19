package dev.eolmae.marketmonitor.domain.stock.enums;

public enum AmtQty {
    AMOUNT("1"), // amt_qty_tp: 1=금액
    QUANTITY("2"); // amt_qty_tp: 2=수량

    private final String code;

    AmtQty(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
