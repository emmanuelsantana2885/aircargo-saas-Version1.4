package com.aircargo.common.event;

import java.util.UUID;

public record ReceiptCreatedEvent(UUID receiptId, UUID mawbId, String mawbNumber, boolean completed) {}
