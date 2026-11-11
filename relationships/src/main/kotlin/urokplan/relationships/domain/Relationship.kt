package urokplan.relationships.domain

enum class ConnectionType {
    LEARNING,
    GUARDIAN,
}

enum class ConnectionRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
}

enum class ConnectionStatus {
    ACTIVE,
    TERMINATED,
}
