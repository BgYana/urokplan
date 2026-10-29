package urokplan.billing

import urokplan.billing.application.BillingAttendanceUpdater
import urokplan.billing.application.BillingProcessor
import urokplan.billing.application.BillingService
import urokplan.billing.application.BillingUseCases
import urokplan.billing.application.MockPaymentProviderControl
import urokplan.billing.application.MockPaymentService
import urokplan.billing.application.MockPaymentUseCases
import urokplan.billing.application.PaymentProvider
import urokplan.billing.application.PaymentResultHandler
import urokplan.billing.infrastructure.ExposedBillingRepository
import urokplan.billing.infrastructure.MockPaymentProvider
import urokplan.courses.application.CoursesApi
import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.application.AttendanceBillingUpdater
import urokplan.scheduling.application.SchedulingApi
import java.time.Clock

class BillingModule private constructor(
    val useCases: BillingUseCases,
    val paymentResults: PaymentResultHandler,
    val mockPayments: MockPaymentUseCases?,
) {
    companion object {
        fun createAttendanceUpdater(
            transactions: TransactionRunner,
            clock: Clock,
        ): AttendanceBillingUpdater {
            val repository = ExposedBillingRepository()
            return BillingAttendanceUpdater(transactions, BillingProcessor(repository, clock))
        }

        fun create(
            transactions: TransactionRunner,
            identity: IdentityApi,
            relationships: RelationshipsApi,
            courses: CoursesApi,
            scheduling: SchedulingApi,
            clock: Clock,
            configuredPaymentProvider: PaymentProvider? = null,
            configuredMockControl: MockPaymentProviderControl? = null,
        ): BillingModule {
            val repository = ExposedBillingRepository()
            val processor = BillingProcessor(repository, clock)
            val paymentProvider = configuredPaymentProvider ?: MockPaymentProvider()
            val mockControl = configuredMockControl ?: paymentProvider as? MockPaymentProviderControl
            val service = BillingService(
                transactions,
                repository,
                processor,
                identity,
                relationships,
                courses,
                scheduling,
                paymentProvider,
                clock,
            )
            val mockPayments = mockControl?.let {
                MockPaymentService(transactions, repository, it, service, paymentProvider.name)
            }
            return BillingModule(service, service, mockPayments)
        }
    }
}
