package urokplan.app

import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthApiTest {
    @Test
    fun `health reports ready database`() = testApplication {
        application {
            configureHttp { true }
        }
        val response = client.get("/api/health")

        assertEquals(200, response.status.value)
        assertEquals("{\"status\":\"UP\",\"database\":\"UP\"}", response.bodyAsText())
    }

    @Test
    fun `health reports unavailable database`() = testApplication {
        application {
            configureHttp { false }
        }

        val response = client.get("/api/health")

        assertEquals(503, response.status.value)
    }

    @Test
    fun `swagger UI and specification are available`() = testApplication {
        application {
            configureHttp { true }
        }

        assertEquals(200, client.get("/swagger").status.value)
        assertEquals(200, client.get("/swagger/documentation.yaml").status.value)
    }
}
