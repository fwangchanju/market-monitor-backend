package dev.eolmae.marketmonitor.common.util;

import dev.eolmae.marketmonitor.common.enums.Zone;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// KST 기준 "지금" — 여기저기서 LocalDateTime.now(Zone.KST.zoneId())를 직접 부르면 나중에 한꺼번에
// 바꿔야 할 때 호출부를 다 찾아다녀야 해서 모아둔다.
public final class KstClock {

    private KstClock() {}

    public static LocalDateTime getNowTruncateMinute() {
        return LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.MINUTES);
    }
}
