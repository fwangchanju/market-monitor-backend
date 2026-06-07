package dev.eolmae.marketmonitor.common.enums;

public enum AmtQtyType {
    AMOUNT("1"), // amt_qty_tp: 1=금액
    QUANTITY("2"); // amt_qty_tp: 2=수량

    private final String code;

    AmtQtyType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
