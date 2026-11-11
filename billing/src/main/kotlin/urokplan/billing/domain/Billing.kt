package urokplan.billing.domain

enum class PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
}

enum class LedgerEntryType {
    TOP_UP,
    CONSUMPTION,
    REFUND,
}

enum class ChargeStatus {
    OPEN,
    COVERED,
    WAIVED,
}
