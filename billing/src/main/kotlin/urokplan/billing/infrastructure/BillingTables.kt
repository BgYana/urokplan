package urokplan.billing.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.billing.domain.ChargeStatus
import urokplan.billing.domain.LedgerEntryType
import urokplan.billing.domain.PaymentStatus

internal object BalancesTable : Table("billing.balances") {
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val availableUnits = integer("available_units")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(courseId, studentAccountId)
}

internal object PaymentsTable : Table("billing.payments") {
    val id = uuid("id")
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val payerAccountId = uuid("payer_account_id")
    val units = integer("units")
    val unitPriceMinor = long("unit_price_minor")
    val amountMinor = long("amount_minor")
    val status = enumerationByName<PaymentStatus>("status", 20)
    val providerName = varchar("provider_name", 50)
    val providerPaymentId = varchar("provider_payment_id", 200)
    val confirmationUrl = varchar("confirmation_url", 1000).nullable()
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
    val failureCode = varchar("failure_code", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object LedgerEntriesTable : Table("billing.ledger_entries") {
    val id = uuid("id")
    val sequenceNumber = long("sequence_number").autoIncrement()
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val type = enumerationByName<LedgerEntryType>("type", 20)
    val unitsDelta = integer("units_delta")
    val paymentId = uuid("payment_id").nullable()
    val lessonId = uuid("lesson_id").nullable()
    val createdAt = timestamp("created_at")
    val actorAccountId = uuid("actor_account_id").nullable()
    val reason = varchar("reason", 500).nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object LessonChargesTable : Table("billing.lesson_charges") {
    val lessonId = uuid("lesson_id")
    val studentAccountId = uuid("student_account_id")
    val courseId = uuid("course_id")
    val lessonStartsAt = timestamp("lesson_starts_at")
    val lessonEndsAt = timestamp("lesson_ends_at")
    val status = enumerationByName<ChargeStatus>("status", 20)
    val createdAt = timestamp("created_at")
    val coveredAt = timestamp("covered_at").nullable()
    val waivedAt = timestamp("waived_at").nullable()
    override val primaryKey = PrimaryKey(lessonId, studentAccountId)
}
