package com.elderhive.dtos.Emergency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Backs AdminDashboardController's own open-alerts stat (Prompt 94) - {@code overdue} is
 *  always &lt;= {@code open} (every overdue alert is, by definition, still open). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlertOpenCounts {
    private long open;
    private long overdue;
}
