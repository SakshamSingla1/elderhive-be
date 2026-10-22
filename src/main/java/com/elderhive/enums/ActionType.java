package com.elderhive.enums;

/**
 * Additive only, same rule as ExceptionCodeEnum/EntityTypeEnum.
 */
public enum ActionType {
    CREATE,
    UPDATE,
    DELETE,
    GRANT,
    REVOKE,
    // A notification fan-out to one recipient (Prompt 83's emergency alert trigger logs
    // one of these per caretaker/family member notified) - distinct from CREATE/UPDATE
    // since nothing about the audited entity itself changed, only that someone was told
    // about it.
    NOTIFY
}
