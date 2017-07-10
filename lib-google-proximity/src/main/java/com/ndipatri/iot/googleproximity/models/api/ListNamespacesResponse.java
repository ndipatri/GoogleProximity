package com.ndipatri.iot.googleproximity.models.api;

import java.util.List;

public class ListNamespacesResponse {

    public List<Namespace> namespaces;

    public static class Namespace {
        public String namespaceName;
        public String servingVisibility;
    }
}
