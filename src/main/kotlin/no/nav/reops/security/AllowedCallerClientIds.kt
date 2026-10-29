package no.nav.reops.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Parses the Nais-injected `AZURE_APP_PRE_AUTHORIZED_APPS` JSON and extracts the client
 * id of the single expected caller: the Innblikk BFF in the same cluster and namespace.
 *
 * Shape (from azurerator, marshalled `Valid` list of `resource.Resource`):
 *   `[ {"name":"<cluster>:<namespace>:<application>","clientId":"<uuid>"}, ... ]`
 *
 * The app name is derived from Nais `UniformResourceName` = `cluster:namespace:name`.
 * We match the exact same-cluster/same-namespace entry for `reops-innblikk-frontend`.
 * Fails closed (empty) on a missing/blank/malformed value or when no matching entry
 * exists — never trust a partial parse.
 */
@Component
class AllowedCallerClientIds(
    objectMapper: ObjectMapper,
    @param:Value("\${app.security.pre-authorized-apps-json:}") preAuthorizedAppsJson: String,
    @param:Value("\${NAIS_CLUSTER_NAME:}") clusterName: String,
    @param:Value("\${NAIS_NAMESPACE:}") namespace: String,
) {
    data class PreAuthorizedApp(
        val name: String? = null,
        val clientId: String? = null,
    )

    val clientIds: Set<String> = parse(objectMapper, preAuthorizedAppsJson, clusterName, namespace)

    private fun parse(
        objectMapper: ObjectMapper,
        json: String,
        clusterName: String,
        namespace: String,
    ): Set<String> {
        if (json.isBlank()) {
            LOG.warn("AZURE_APP_PRE_AUTHORIZED_APPS is empty — no callers will be allowed (fail closed)")
            return emptySet()
        }
        if (clusterName.isBlank() || namespace.isBlank()) {
            LOG.warn("NAIS_CLUSTER_NAME/NAIS_NAMESPACE not set — cannot resolve expected caller, failing closed")
            return emptySet()
        }
        val expectedName = "$clusterName:$namespace:$CALLER_APPLICATION"
        return try {
            val apps = objectMapper.readValue(json, Array<PreAuthorizedApp>::class.java).toList()
            val matches = apps.filter { it.name == expectedName }.mapNotNull { it.clientId }.toSet()
            if (matches.isEmpty()) {
                LOG.warn(
                    "No pre-authorized app named '{}' found in AZURE_APP_PRE_AUTHORIZED_APPS — failing closed",
                    expectedName,
                )
            } else {
                LOG.info("Resolved allowed caller client id(s) for '{}': {} entr(ies)", expectedName, matches.size)
            }
            matches
        } catch (e: Exception) {
            LOG.error("Failed to parse AZURE_APP_PRE_AUTHORIZED_APPS — failing closed", e)
            emptySet()
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(AllowedCallerClientIds::class.java)
        private const val CALLER_APPLICATION = "reops-innblikk-frontend"
    }
}
