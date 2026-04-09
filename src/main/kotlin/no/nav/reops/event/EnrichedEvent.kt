package no.nav.reops.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EnrichedEvent(
    val websiteId: UUID,
    val sessionId: String? = null,
    val createdAt: Instant,
    val websiteEvent: WebsiteEvent,
    val session: Session
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class WebsiteEvent(
    val eventId: UUID,
    val urlPath: String? = null,
    val urlQuery: String? = null,
    val referrerPath: String? = null,
    val referrerQuery: String? = null,
    val referrerDomain: String? = null,
    val pageTitle: String? = null,
    /** 1 = page view, 2 = custom event */
    val eventType: Int,
    val eventName: String? = null,
    val visitId: UUID,
    val hostname: String? = null,
    val eventData: List<EventDataEntry>? = null
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Session(
    val browser: String? = null,
    val os: String? = null,
    val device: String? = null,
    val screen: String? = null,
    val language: String? = null,
    val country: String? = null,
    val distinctId: String? = null,
    val sessionData: List<SessionDataEntry>? = null
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EventDataEntry(
    val eventDataId: UUID,
    val dataKey: String,
    val stringValue: String? = null,
    val numberValue: Double? = null,
    val dateValue: Instant? = null,
    /** 1 = string, 2 = number, 4 = date */
    val dataType: Int,
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class SessionDataEntry(
    val sessionDataId: UUID,
    val sessionId: String,
    val dataKey: String,
    val stringValue: String? = null,
    val numberValue: Double? = null,
    val dateValue: Instant? = null,
    /** 1 = string, 2 = number, 4 = date */
    val dataType: Int,
    val distinctId: String? = null
)
