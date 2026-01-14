package com.example.messaging.operator.crd;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple validation tests to verify uniqueness constraints in the CRD YAML.
 */
class SimpleCrdValidationTest {

    private static final String CRD_PATH = "target/classes/META-INF/fabric8/messagingdeclarations.messaging.example.com-v1.yml";

    @Test
    void testCrdFileExists() {
        assertTrue(Files.exists(Paths.get(CRD_PATH)), "CRD file should exist");
    }

    @Test
    void testTopicsHasMapListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        // Find topics section in spec
        assertTrue(crdContent.contains("topics:"), "CRD should contain topics property");
        assertTrue(crdContent.contains("x-kubernetes-list-type: map"),
                "CRD should contain 'x-kubernetes-list-type: map' constraint");

        // Check that the map constraint appears after topics definition
        int topicsIndex = crdContent.indexOf("topics:");
        int mapTypeIndex = crdContent.indexOf("x-kubernetes-list-type: map", topicsIndex);
        assertTrue(mapTypeIndex > topicsIndex && mapTypeIndex < topicsIndex + 500,
                "x-kubernetes-list-type: map should appear near topics definition");

        assertTrue(crdContent.contains("x-kubernetes-list-map-keys:"),
                "CRD should contain x-kubernetes-list-map-keys");
    }

    @Test
    void testAclsHasMapListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        assertTrue(crdContent.contains("acls:"), "CRD should contain acls property");

        // Check that acls has map type constraint
        int aclsIndex = crdContent.indexOf("acls:");
        String aclsSection = crdContent.substring(aclsIndex, Math.min(crdContent.length(), aclsIndex + 1000));

        assertTrue(aclsSection.contains("x-kubernetes-list-type: map"),
                "acls should have x-kubernetes-list-type: map");
        assertTrue(aclsSection.contains("x-kubernetes-list-map-keys"),
                "acls should have x-kubernetes-list-map-keys");
    }

    @Test
    void testOperationsHasSetListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        assertTrue(crdContent.contains("operations:"), "CRD should contain operations property");

        // Find operations section within acls
        int operationsIndex = crdContent.indexOf("operations:");
        String operationsSection = crdContent.substring(operationsIndex,
                Math.min(crdContent.length(), operationsIndex + 500));

        assertTrue(operationsSection.contains("x-kubernetes-list-type: set"),
                "operations should have x-kubernetes-list-type: set");
    }

    @Test
    void testOwnedTopicsHasSetListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        assertTrue(crdContent.contains("ownedTopics:"), "CRD should contain ownedTopics property");

        int ownedTopicsIndex = crdContent.indexOf("ownedTopics:");
        String ownedTopicsSection = crdContent.substring(ownedTopicsIndex,
                Math.min(crdContent.length(), ownedTopicsIndex + 300));

        assertTrue(ownedTopicsSection.contains("x-kubernetes-list-type: set"),
                "ownedTopics should have x-kubernetes-list-type: set");
    }

    @Test
    void testReferencedTopicsHasSetListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        assertTrue(crdContent.contains("referencedTopics:"), "CRD should contain referencedTopics property");

        int refTopicsIndex = crdContent.indexOf("referencedTopics:");
        String refTopicsSection = crdContent.substring(refTopicsIndex,
                Math.min(crdContent.length(), refTopicsIndex + 300));

        assertTrue(refTopicsSection.contains("x-kubernetes-list-type: set"),
                "referencedTopics should have x-kubernetes-list-type: set");
    }

    @Test
    void testConditionsHasMapListType() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        assertTrue(crdContent.contains("conditions:"), "CRD should contain conditions property");

        int conditionsIndex = crdContent.lastIndexOf("conditions:"); // Find in status section
        String conditionsSection = crdContent.substring(conditionsIndex,
                Math.min(crdContent.length(), conditionsIndex + 400));

        assertTrue(conditionsSection.contains("x-kubernetes-list-type: map"),
                "conditions should have x-kubernetes-list-type: map");
        assertTrue(conditionsSection.contains("x-kubernetes-list-map-keys"),
                "conditions should have x-kubernetes-list-map-keys");
    }

    @Test
    void testAllUniquenessConstraintsPresent() throws IOException {
        String crdContent = Files.readString(Paths.get(CRD_PATH));

        // Count all x-kubernetes-list-type occurrences
        int mapCount = countOccurrences(crdContent, "x-kubernetes-list-type: map");
        int setCount = countOccurrences(crdContent, "x-kubernetes-list-type: set");

        // We expect:
        // - 3 map types: topics, acls, conditions
        // - 3 set types: operations, ownedTopics, referencedTopics
        assertEquals(3, mapCount, "Should have exactly 3 'map' list types");
        assertEquals(3, setCount, "Should have exactly 3 'set' list types");
    }

    private int countOccurrences(String str, String findStr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(findStr, index)) != -1) {
            count++;
            index += findStr.length();
        }
        return count;
    }
}
