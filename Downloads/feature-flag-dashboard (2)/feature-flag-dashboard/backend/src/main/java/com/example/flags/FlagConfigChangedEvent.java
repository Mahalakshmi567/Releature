package com.example.flags;

import java.util.List;

/** Published inside the transaction that changes a flag. The cached copies are dropped once it commits. */
public record FlagConfigChangedEvent(String flagKey, List<String> environmentKeys) {}
