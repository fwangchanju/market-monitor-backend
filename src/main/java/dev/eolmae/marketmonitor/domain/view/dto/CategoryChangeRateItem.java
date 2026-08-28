package dev.eolmae.marketmonitor.domain.view.dto;

public record CategoryChangeRateItem(Long categoryId, SnapshotAverages now, SnapshotAverages before) {

    public static CategoryChangeRateItem withoutBefore(Long categoryId, SnapshotAverages now) {
        return new CategoryChangeRateItem(categoryId, now, null);
    }

    public static CategoryChangeRateItem withBefore(Long categoryId, SnapshotAverages now, SnapshotAverages before) {
        return new CategoryChangeRateItem(categoryId, now, before);
    }
}
