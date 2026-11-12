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
     * Produces the visible subscription prefix, e.g. "[机场A]".
     * Keeping this prefix stable lets NAME groups follow subscription updates.
     */
    fun subscriptionTag(subscriptionName: String): String {
        val name = subscriptionName.trim()
        require(name.isNotEmpty()) { "Subscription name must not be blank" }
        return if (name.startsWith("[") && name.endsWith("]")) name else "[$name]"
    }

    /**
     * Adds a subscription tag without duplicating it when a node is imported
     * or processed more than once. The caller must retain the original name
     * separately so the user can restore it.
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
     * STATIC membership is maintained explicitly and is never inferred here.
     * NAME_REGION requires a verified exit-region result; unknown or failed
     * lookups never fall back to the node name or server address.
     */
    fun matches(group: NodeGroup, candidate: NodeGroupCandidate): Boolean {
        return when (group.mode) {
            NodeGroup.MODE_STATIC -> false
            NodeGroup.MODE_NAME -> {
                val tag = group.nameTag ?: return false
                hasSubscriptionTag(candidate.displayName, tag)
            }
            NodeGroup.MODE_NAME_REGION -> {
                val tag = group.nameTag ?: return false
                val region = group.regionCode ?: return false
                candidate.exitRegionVerified &&
                    !candidate.exitRegionCode.isNullOrBlank() &&
                    candidate.exitRegionCode.equals(region, ignoreCase = true) &&
                    hasSubscriptionTag(candidate.displayName, tag)
            }
            else -> false
        }
    }
}
