package com.github.kr328.clash.service.data

/**
 * Minimal node metadata used by the node-group matcher.
 *
 * exitRegionCode must come from a successful query through that node, not
 * from the proxy server hostname or the region written in its display name.
 */
data class NodeGroupCandidate(
    val profileUuid: String,
    val proxyKey: String,
    val displayName: String,
    val exitRegionCode: String? = null,
    val exitRegionVerified: Boolean = false,
)

object NodeGroupMatcher {
    /**
     * Creates a normalized display prefix for subscription-tagged node labels.
     * Dynamic subscription groups now bind to the source profile UUID instead.
     */
    fun subscriptionTag(subscriptionName: String): String {
        val name = subscriptionName.trim()
        require(name.isNotEmpty()) { "Subscription name must not be blank" }
        return if (name.startsWith("[") && name.endsWith("]")) name else "[$name]"
    }

    /**
     * Adds a display-only subscription tag without duplicating it. The caller
     * must retain the original node name separately so it can be restored.
     */
    fun taggedNodeName(subscriptionName: String, originalNodeName: String): String {
        val tag = subscriptionTag(subscriptionName)
        val original = originalNodeName.trim()
        require(original.isNotEmpty()) { "Node name must not be blank" }

        return if (hasSubscriptionTag(original, tag)) original else "$tag $original"
    }

    fun hasSubscriptionTag(nodeName: String, subscriptionTag: String): Boolean {
        val name = nodeName.trim()
        val tag = subscriptionTag.trim()
        if (name == tag) return true
        return name.startsWith("$tag ")
    }

    /**
     * STATIC membership is maintained explicitly. NAME groups follow a source
     * subscription by its stable profile UUID. NAME_REGION additionally requires
     * a verified exit-region result; unknown lookups never infer a region.
     */
    fun matches(group: NodeGroup, candidate: NodeGroupCandidate): Boolean {
        return when (group.mode) {
            NodeGroup.MODE_STATIC -> false
            NodeGroup.MODE_NAME -> {
                val sourceProfile = group.nameTag ?: return false
                candidate.profileUuid.equals(sourceProfile, ignoreCase = true)
            }
            NodeGroup.MODE_NAME_REGION -> {
                val sourceProfile = group.nameTag ?: return false
                val region = group.regionCode ?: return false
                candidate.profileUuid.equals(sourceProfile, ignoreCase = true) &&
                    candidate.exitRegionVerified &&
                    !candidate.exitRegionCode.isNullOrBlank() &&
                    candidate.exitRegionCode.equals(region, ignoreCase = true)
            }
            else -> false
        }
    }
}
