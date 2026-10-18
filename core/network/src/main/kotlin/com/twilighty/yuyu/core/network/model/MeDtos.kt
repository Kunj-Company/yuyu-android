package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class MeDto(
    val user: UserDto,
    val organisations: List<OrgSummaryDto> = emptyList(),
    val collaborations: List<CollaborationDto> = emptyList(),
)

@Serializable
data class OrgSummaryDto(
    val id: String,
    val slug: String,
    val name: String,
    val logoUrl: String? = null,
    /** OWNER | ADMIN | MEMBER, see [OrgRole]. */
    val role: String,
)

@Serializable
data class CollaborationDto(
    val organisation: OrgRefDto,
    val eventId: String,
    val eventTitle: String,
    /** Event permission names, e.g. CHECK_IN, MANAGE_REGISTRATIONS. */
    val permissions: List<String> = emptyList(),
)

@Serializable
data class OrgRefDto(
    val slug: String,
    val name: String,
    val logoUrl: String? = null,
)

@Serializable
data class UpdateMeRequest(val name: String)

@Serializable
data class RegistrationsDto(
    val upcoming: List<RegistrationDto> = emptyList(),
    val past: List<RegistrationDto> = emptyList(),
    val certificates: List<CertificateDto> = emptyList(),
)

@Serializable
data class RegistrationDto(
    val rsvpId: String,
    val status: String,
    val checkedInAt: String? = null,
    val registeredAt: String,
    val canCancel: Boolean = false,
    /** Only for CONFIRMED registrations. Secret: never log it. */
    val ticketToken: String? = null,
    val event: EventSummaryDto,
    val instanceId: String? = null,
) {
    override fun toString(): String = "RegistrationDto(rsvpId=$rsvpId, status=$status, event=${event.id})"
}

@Serializable
data class CertificateDto(
    val eventTitle: String,
    val organisationName: String,
    val issuedAt: String,
    val certificateUrl: String,
)
