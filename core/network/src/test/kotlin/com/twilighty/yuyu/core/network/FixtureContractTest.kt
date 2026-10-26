package com.twilighty.yuyu.core.network

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.network.error.ApiErrorParser
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.BroadcastMetaDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewDto
import com.twilighty.yuyu.core.network.model.GuestPageDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.core.network.model.OrgEventsDto
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.SessionDto
import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deserialises every server fixture (docs/mobile-api-fixtures, copied into
 * test resources) with the app's DTOs using a **strict** decoder: an unknown
 * or renamed field fails the build, so model drift is caught here.
 * When the backend adds a fixture, add it to [serializers].
 */
class FixtureContractTest {
    private val strict = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        isLenient = false
    }

    private val serializers: Map<String, KSerializer<*>> = mapOf(
        "broadcasts-meta.json" to ApiEnvelope.serializer(BroadcastMetaDto.serializer()),
        "check-in-preview.json" to ApiEnvelope.serializer(CheckInPreviewDto.serializer()),
        "error.json" to ApiEnvelope.serializer(JsonElement.serializer()),
        "guests.json" to ApiEnvelope.serializer(GuestPageDto.serializer()),
        "me.json" to ApiEnvelope.serializer(MeDto.serializer()),
        "org-events.json" to ApiEnvelope.serializer(OrgEventsDto.serializer()),
        "org-event-detail.json" to ApiEnvelope.serializer(OrgEventDetailResponseDto.serializer()),
        "registration-state.json" to ApiEnvelope.serializer(RegistrationStateDto.serializer()),
        "public-event.json" to ApiEnvelope.serializer(PublicEventResponseDto.serializer()),
        "registrations.json" to ApiEnvelope.serializer(RegistrationsDto.serializer()),
        "session.json" to ApiEnvelope.serializer(SessionDto.serializer()),
    )

    private fun fixtureDir(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource("fixtures/session.json")) { "fixtures missing" }
        return File(url.toURI()).parentFile
    }

    private fun read(name: String) = File(fixtureDir(), name).readText()

    @Test
    fun everyFixtureHasADtoMapping() {
        val files = fixtureDir().listFiles { f -> f.extension == "json" }!!.map { it.name }.toSet()
        assertEquals("Fixtures without a DTO mapping", emptySet<String>(), files - serializers.keys)
    }

    @Test
    fun everyFixtureDecodesStrictly() {
        serializers.forEach { (name, serializer) ->
            val decoded = runCatching { strict.decodeFromString(serializer, read(name)) }
            assertTrue("$name failed: ${decoded.exceptionOrNull()?.message}", decoded.isSuccess)
            val envelope = decoded.getOrThrow() as ApiEnvelope<*>
            if (name == "error.json") assertFalse(envelope.ok) else assertNotNull("$name data", envelope.data)
        }
    }

    @Test
    fun publicEventCarriesNewContractFields() {
        val data = strict.decodeFromString(ApiEnvelope.serializer(PublicEventResponseDto.serializer()), read("public-event.json")).data!!
        assertFalse(data.event.registrationFull)
        assertNull(data.event.onlineUrl)
        val dietary = data.registration.fields.first { it.key == "dietary" }
        assertEquals("attending_dinner", dietary.showIf?.fieldKey)
        assertEquals(listOf("Yes"), dietary.showIf?.values)
        assertEquals(3, dietary.settings.maxSelections)
        assertEquals(10, data.schedule.first().sessions[1].delayMinutes)
    }

    @Test
    fun sessionTokensAreOpaqueAndNeverPrinted() {
        val session = strict.decodeFromString(ApiEnvelope.serializer(SessionDto.serializer()), read("session.json")).data!!
        assertTrue(session.accessToken.startsWith("yma_"))
        assertTrue(session.refreshToken.startsWith("ymr_"))
        assertFalse(session.toString().contains("yma_"))
        assertFalse(session.toString().contains("ymr_"))
    }

    @Test
    fun errorFixtureParsesToTypedError() {
        val error = ApiErrorParser.parse(400, read("error.json"))
        assertTrue(error is AppError.InvalidRequest)
        error as AppError.InvalidRequest
        assertEquals("Invalid input.", error.message)
        assertEquals(listOf("Name is required"), error.fieldErrors["name"])
    }

    @Test
    fun registrationStateExposesManualClose() {
        val state = strict.decodeFromString(ApiEnvelope.serializer(RegistrationStateDto.serializer()), read("registration-state.json")).data!!
        assertTrue(state.registrationManuallyClosed)
        assertFalse(state.registrationOpen)
        val detail = strict.decodeFromString(ApiEnvelope.serializer(OrgEventDetailResponseDto.serializer()), read("org-event-detail.json")).data!!
        assertFalse(detail.event.registrationManuallyClosed)
        assertEquals(42, detail.counts?.confirmed)
    }

    @Test
    fun organiserCountsCarryCapacityUsed() {
        val detail = strict.decodeFromString(ApiEnvelope.serializer(OrgEventDetailResponseDto.serializer()), read("org-event-detail.json")).data!!
        assertEquals(42, detail.counts?.capacityUsed)
        assertEquals(42, detail.counts?.placesTaken)
        val events = strict.decodeFromString(ApiEnvelope.serializer(OrgEventsDto.serializer()), read("org-events.json")).data!!
        assertEquals(42, events.events.first().counts?.capacityUsed)
    }

    @Test
    fun checkInPreviewGateNeedsForce() {
        val preview = strict.decodeFromString(ApiEnvelope.serializer(CheckInPreviewDto.serializer()), read("check-in-preview.json")).data!!
        assertFalse(preview.gate.ok)
        assertTrue(preview.gate.needsForce)
    }
}
