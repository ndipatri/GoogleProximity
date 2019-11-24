package com.ndipatri.iot.googleproximity.models.api

class ListNamespacesResponse {

    var namespaces: List<Namespace>? = null

    class Namespace {
        var namespaceName: String? = null
        var servingVisibility: String? = null
    }
}
