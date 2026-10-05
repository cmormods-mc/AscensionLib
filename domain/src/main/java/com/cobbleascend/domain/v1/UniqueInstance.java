package com.cobbleascend.domain.v1;

import java.util.Objects;
import java.util.UUID;

public record UniqueInstance(String uniqueId, int definitionVersion, UUID installedOperationId) {
    public UniqueInstance {
        Objects.requireNonNull(uniqueId);
        Objects.requireNonNull(installedOperationId);
        if (!uniqueId.matches("[a-z][a-z0-9_]{0,63}") || definitionVersion < 1)
            throw new IllegalArgumentException("Invalid unique instance");
    }
}
