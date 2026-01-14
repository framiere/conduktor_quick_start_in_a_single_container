package org.openapitools.functional;

import org.junit.jupiter.api.*;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.*;
import org.openapitools.client.model.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive CRUD tests for Conduktor Console SDK.
 * Each test is fully independent with no shared state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SdkCrudTest {

    private ApiClient client;
    private String cluster;

    @BeforeAll
    void setupClient() throws ApiException {
        String baseUrl = env("BASE_URL", "http://localhost:8080");
        // go to http://localhost:8080/settings/public-api-keys to setup one
        String token = env("TOKEN", "QJklTZh8O6Y=.Ey+uvCruR3CILMM7MiSxiBbX82+gQGp7W1qdnHhHrOlyOw5yZaKjHoaNqoQjplYvix9Wuwxg5ftxVmgjcofezGdp/Fonq/azaXqSFtam+RMjkwqX7H8L3n9zOl1u8cGOg7I8ztfZe+FAZS4lTPxcCsNPms4h2/eTCM6KeLU8GFmR5TmbQN124+sz/OTNi/DRhGVbicC9jyMDzRrD2xA4UC85coZFaiZo26+XSYQJgpKssqJsglAfTvRqaSEWRPP+LHy/AcdW20HVAxDpkbk5pK+gxtwzL51Z6l25vObcG+sE3U3JSfobrh77aIwrkEzloiVKSd3EMl1GNx1r7E6u0w==");

        client = Configuration.getDefaultApiClient();
        client.setBasePath(baseUrl);
        client.setBearerToken(token);

        var clusters = new ClustersApi(client).listAllClusters();
        assertThat(clusters).isNotEmpty();
        cluster = clusters.get(0).getClusterSlug();
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) value = System.getProperty(name.toLowerCase());
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    private String randomName() {
        return "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @FunctionalInterface
    interface ThrowingCallable {
        void call() throws Exception;
    }

    private void assert404(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(404);
    }

    // ========================================================================
    // Groups
    // ========================================================================

    @Nested
    @DisplayName("Groups")
    class GroupTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliGroupIamV21Api(client).getGroupByNameV2(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliGroupIamV21Api(client).deleteGroupV2(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliGroupIamV21Api(client);
            String name = uniqueName("group");

            try {
                var created = api.createOrUpdateGroupV2(group(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getGroupByNameV2(name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listGroupResourcesV2();
                assertThat(list).extracting(g -> g.getMetadata().getName()).contains(name);

                api.deleteGroupV2(name);

                var listAfter = api.listGroupResourcesV2();
                assertThat(listAfter).extracting(g -> g.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteGroupV2(name);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name)
                    )
                    .spec(new GroupSpec()
                            .displayName("Test group"));
        }
    }

    // ========================================================================
    // Topics
    // ========================================================================

    @Nested
    @DisplayName("Topics")
    class TopicTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliTopicKafkaV212Api(client).getTopicByNameV2(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliTopicKafkaV212Api(client).deleteTopicV2(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliTopicKafkaV212Api(client);
            String name = uniqueName("topic");

            try {
                var created = api.createOrUpdateTopicV2(cluster, topic(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getTopicByNameV2(cluster, name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listTopicResourcesV2(cluster);
                assertThat(list).extracting(t -> t.getMetadata().getName()).contains(name);

                api.deleteTopicV2(cluster, name);

                var listAfter = api.listTopicResourcesV2(cluster);
                assertThat(listAfter).extracting(t -> t.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteTopicV2(cluster, name);
                } catch (Exception ignored) {
                }
            }
        }

        private TopicResourceV2 topic(String name) {
            return new TopicResourceV2()
                    .apiVersion("v2")
                    .kind(TopicKind.TOPIC)
                    .metadata(new TopicMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new TopicSpec()
                            .partitions(1)
                            .replicationFactor(1)
                    );
        }
    }

    // ========================================================================
    // Subjects
    // ========================================================================

    @Nested
    @DisplayName("Subjects")
    class SubjectTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliSubjectKafkaV213Api(client).getSubjectByNameV2(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliSubjectKafkaV213Api(client).deleteSubjectV2(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliSubjectKafkaV213Api(client);
            String name = uniqueName("subject");

            try {
                var created = api.createOrUpdateSubjectV2(cluster, subject(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getSubjectByNameV2(cluster, name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listSubjectResourcesV2(cluster);
                assertThat(list).extracting(s -> s.getMetadata().getName()).contains(name);

                api.deleteSubjectV2(cluster, name);

                var listAfter = api.listSubjectResourcesV2(cluster);
                assertThat(listAfter).extracting(s -> s.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteSubjectV2(cluster, name);
                } catch (Exception ignored) {
                }
            }
        }

        private SubjectResourceV2 subject(String name) {
            return new SubjectResourceV2()
                    .apiVersion("v2")
                    .kind(SubjectKind.SUBJECT)
                    .metadata(new SubjectMetadata()
                            .name(name)
                            .cluster(name))
                    .spec(new SubjectSpec()
                            .format(SchemaFormat.AVRO)
                            .schema("{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}")
                    );
        }
    }

    // ========================================================================
    // IndexedTopics
    // ========================================================================

    @Nested
    @DisplayName("IndexedTopics")
    class IndexedTopicTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliIndexedTopicSqlV115Api(client).getIndexedTopicByNameV1(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliIndexedTopicSqlV115Api(client).deleteIndexedTopicV1(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with topic dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliIndexedTopicSqlV115Api(client);
            var topicApi = new CliTopicKafkaV212Api(client);
            String name = uniqueName("indexed");

            try {
                // Create dependency
                topicApi.createOrUpdateTopicV2(cluster, topic(name), null);

                var created = api.createOrUpdateIndexedTopicV1(cluster, indexedTopic(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getIndexedTopicByNameV1(cluster, name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listIndexedTopicResourcesV1(cluster);
                assertThat(list).extracting(i -> i.getMetadata().getName()).contains(name);

                api.deleteIndexedTopicV1(cluster, name);

                var listAfter = api.listIndexedTopicResourcesV1(cluster);
                assertThat(listAfter).extracting(i -> i.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteIndexedTopicV1(cluster, name);
                } catch (Exception ignored) {
                }
                try {
                    topicApi.deleteTopicV2(cluster, name);
                } catch (Exception ignored) {
                }
            }
        }

        private TopicResourceV2 topic(String name) {
            return new TopicResourceV2()
                    .apiVersion("v2")
                    .kind(TopicKind.TOPIC)
                    .metadata(new TopicMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new TopicSpec()
                            .partitions(1)
                            .replicationFactor(1));
        }

        private IndexedTopicResourceV1 indexedTopic(String name) {
            return new IndexedTopicResourceV1()
                    .apiVersion("v1")
                    .kind(IndexedTopicKind.INDEXED_TOPIC)
                    .metadata(new IndexedTopicMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new IndexedTopicSpec()
                            .retentionTimeInSecond(3600L)
                            .enabled(true));
        }
    }

    // ========================================================================
    // Applications
    // ========================================================================

    @Nested
    @DisplayName("Applications")
    class ApplicationTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliApplicationSelfServeV17Api(client).getApplicationByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliApplicationSelfServeV17Api(client).deleteApplicationV1(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with group dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliApplicationSelfServeV17Api(client);
            var groupApi = new CliGroupIamV21Api(client);
            String groupName = uniqueName("app-owner");
            String appName = uniqueName("app");

            try {
                // Create dependency
                groupApi.createOrUpdateGroupV2(group(groupName), null);

                var created = api.createOrUpdateApplicationV1(application(appName, groupName), null);
                assertThat(created).isNotNull();

                var fetched = api.getApplicationByNameV1(appName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(appName);

                var list = api.listApplicationResourcesV1();
                assertThat(list).extracting(a -> a.getMetadata().getName()).contains(appName);

                api.deleteApplicationV1(appName);

                var listAfter = api.listApplicationResourcesV1();
                assertThat(listAfter).extracting(a -> a.getMetadata().getName()).doesNotContain(appName);
            } finally {
                try {
                    api.deleteApplicationV1(appName);
                } catch (Exception ignored) {
                }
                try {
                    groupApi.deleteGroupV2(groupName);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name))
                    .spec(new GroupSpec()
                            .displayName("Test Group"));
        }

        private ApplicationResourceV1 application(String name, String owner) {
            return new ApplicationResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationKind.APPLICATION)
                    .metadata(new ApplicationMetadata()
                            .name(name))
                    .spec(new InputApplicationSpec()
                            .title("Test App")
                            .owner(owner));
        }
    }

    // ========================================================================
    // ApplicationInstances
    // ========================================================================

    @Nested
    @DisplayName("ApplicationInstances")
    class ApplicationInstanceTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliApplicationInstanceSelfServeV18Api(client).getApplicationInstanceByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliApplicationInstanceSelfServeV18Api(client).deleteApplicationInstanceV1(randomName()));
        }

        @Test
        void listApplicationInstanceFromNonExistent404() {
            assert404(() -> new CliApplicationInstanceSelfServeV18Api(client).listApplicationInstanceResourcesV1(uniqueName("foo")));
        }

        @Test
        @DisplayName("CRUD lifecycle with application dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliApplicationInstanceSelfServeV18Api(client);
            var appApi = new CliApplicationSelfServeV17Api(client);
            var groupApi = new CliGroupIamV21Api(client);
            String groupName = uniqueName("inst-owner");
            String appName = uniqueName("inst-app");
            String instanceName = uniqueName("instance");

            try {
                // Create dependencies
                groupApi.createOrUpdateGroupV2(group(groupName), null);
                appApi.createOrUpdateApplicationV1(application(appName, groupName), null);

                var created = api.createOrUpdateApplicationInstanceV1(applicationInstance(instanceName, appName), null);
                assertThat(created).isNotNull();

                var fetched = api.getApplicationInstanceByNameV1(instanceName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(instanceName);


                var list = api.listApplicationInstanceResourcesV1(null);
                assertThat(list).extracting(i -> i.getMetadata().getName()).contains(instanceName);

                api.deleteApplicationInstanceV1(instanceName);

                var listAfter = api.listApplicationInstanceResourcesV1(null);
                assertThat(listAfter).extracting(i -> i.getMetadata().getName()).doesNotContain(instanceName);
            } finally {
                try {
                    api.deleteApplicationInstanceV1(instanceName);
                } catch (Exception ignored) {
                }
                try {
                    appApi.deleteApplicationV1(appName);
                } catch (Exception ignored) {
                }
                try {
                    groupApi.deleteGroupV2(groupName);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name))
                    .spec(new GroupSpec()
                            .displayName("Test Group"));
        }

        private ApplicationResourceV1 application(String name, String owner) {
            return new ApplicationResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationKind.APPLICATION)
                    .metadata(new ApplicationMetadata()
                            .name(name))
                    .spec(new InputApplicationSpec()
                            .title("Test App")
                            .owner(owner));
        }

        private ApplicationInstanceResourceV1 applicationInstance(String name, String appName) {
            return new ApplicationInstanceResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationInstanceKind.APPLICATION_INSTANCE)
                    .metadata(new ApplicationInstanceMetadata()
                            .name(name)
                            .application(appName))
                    .spec(new ApplicationInstanceSpec()
                            .cluster(cluster));
        }
    }

    // ========================================================================
    // ApplicationGroups
    // ========================================================================

    @Nested
    @DisplayName("ApplicationGroups")
    class ApplicationGroupTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliApplicationGroupSelfServeV110Api(client).getApplicationGroupByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliApplicationGroupSelfServeV110Api(client).deleteApplicationGroupV1(randomName()));
        }


        @Test
        void listApplicationGroupResourcesFromNonExistent404() {
            assert404(() -> new CliApplicationGroupSelfServeV110Api(client).listApplicationGroupResourcesV1(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with application dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliApplicationGroupSelfServeV110Api(client);
            var appApi = new CliApplicationSelfServeV17Api(client);
            var groupApi = new CliGroupIamV21Api(client);
            String groupName = uniqueName("agrp-owner");
            String appName = uniqueName("agrp-app");
            String appGroupName = uniqueName("appgroup");

            try {
                // Create dependencies
                groupApi.createOrUpdateGroupV2(group(groupName), null);
                appApi.createOrUpdateApplicationV1(application(appName, groupName), null);

                var created = api.createOrUpdateApplicationGroupV1(applicationGroup(appGroupName, appName), null);
                assertThat(created).isNotNull();

                var fetched = api.getApplicationGroupByNameV1(appGroupName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(appGroupName);

                var list = api.listApplicationGroupResourcesV1(appName);
                assertThat(list).extracting(g -> g.getMetadata().getName()).contains(appGroupName);

                api.deleteApplicationGroupV1(appGroupName);

                var listAfter = api.listApplicationGroupResourcesV1(null);
                assertThat(listAfter).extracting(g -> g.getMetadata().getName()).doesNotContain(appGroupName);
            } finally {
                try {
                    api.deleteApplicationGroupV1(appGroupName);
                } catch (Exception ignored) {
                }
                try {
                    appApi.deleteApplicationV1(appName);
                } catch (Exception ignored) {
                }
                try {
                    groupApi.deleteGroupV2(groupName);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name))
                    .spec(new GroupSpec()
                            .displayName("Test Group"));
        }

        private ApplicationResourceV1 application(String name, String owner) {
            return new ApplicationResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationKind.APPLICATION)
                    .metadata(new ApplicationMetadata()
                            .name(name))
                    .spec(new InputApplicationSpec()
                            .title("Test App")
                            .owner(owner));
        }

        private ApplicationGroupResourceV1 applicationGroup(String name, String appName) {
            return new ApplicationGroupResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationGroupKind.APPLICATION_GROUP)
                    .metadata(new ApplicationGroupMetadata()
                            .name(name)
                            .application(appName))
                    .spec(new ApplicationGroupSpec()
                            .displayName("Test App Group"));
        }
    }

    // ========================================================================
    // TopicPolicies - with polymorphic constraint types
    // ========================================================================

    @Nested
    @DisplayName("TopicPolicies")
    class TopicPolicyTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliTopicPolicySelfServeV15Api(client).getTopicPolicyByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliTopicPolicySelfServeV15Api(client).deleteTopicPolicyV1(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with all polymorphic constraint types")
        void crudWithAllConstraintTypes() throws ApiException {
            var api = new CliTopicPolicySelfServeV15Api(client);
            String name = uniqueName("policy");

            try {
                // Create policy with ALL constraint types to test polymorphic serialization
                var policy = topicPolicyWithAllConstraints(name);

                var created = api.createOrUpdateTopicPolicyV1(policy, null);
                assertThat(created).isNotNull();

                // GET and verify each polymorphic constraint type deserializes correctly
                var fetched = api.getTopicPolicyByNameV1(name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                Map<String, PolicyConstraintDto> policies = fetched.getSpec().getPolicies();
                var constraints = policies;

                // Verify Range constraint
                var partitions = constraints.get("partitions");
                assertThat(partitions).isNotNull();
                assertThat(partitions.getActualInstance()).isInstanceOf(Range.class);
                var range = (Range) partitions.getActualInstance();
                assertThat(range.getConstraint()).isEqualTo(ConstraintKind.RANGE);
                assertThat(range.getMin()).isEqualTo(1L);
                assertThat(range.getMax()).isEqualTo(100L);

                // Verify OneOf constraint
                var cleanupPolicy = constraints.get("cleanup.policy");
                assertThat(cleanupPolicy).isNotNull();
                assertThat(cleanupPolicy.getActualInstance()).isInstanceOf(OneOf.class);
                var oneOf = (OneOf) cleanupPolicy.getActualInstance();
                assertThat(oneOf.getConstraint()).isEqualTo(ConstraintKind.ONE_OF);
                assertThat(oneOf.getValues()).containsExactlyInAnyOrder("delete", "compact");

                // Verify NoneOf constraint
                var compressionType = constraints.get("compression.type");
                assertThat(compressionType).isNotNull();
                assertThat(compressionType.getActualInstance()).isInstanceOf(NoneOf.class);
                var noneOf = (NoneOf) compressionType.getActualInstance();
                assertThat(noneOf.getConstraint()).isEqualTo(ConstraintKind.NONE_OF);
                assertThat(noneOf.getValues()).containsExactlyInAnyOrder("uncompressed");

                // Verify Match constraint (pattern matching)
                var topicNameMatch = constraints.get("name");
                assertThat(topicNameMatch).isNotNull();
                assertThat(topicNameMatch.getActualInstance()).isInstanceOf(Match.class);
                var match = (Match) topicNameMatch.getActualInstance();
                assertThat(match.getConstraint()).isEqualTo(ConstraintKind.MATCH);
                assertThat(match.getPattern()).isEqualTo("^[a-z][a-z0-9-]*$");

                // LIST contains our policy
                var list = api.listTopicPolicyResourcesV1(null);
                assertThat(list).extracting(p -> p.getMetadata().getName()).contains(name);

                // DELETE
                api.deleteTopicPolicyV1(name);

                // LIST after delete
                var listAfter = api.listTopicPolicyResourcesV1(null);
                assertThat(listAfter).extracting(p -> p.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteTopicPolicyV1(name);
                } catch (Exception ignored) {
                }
            }
        }

        private TopicPolicyResourceV1 topicPolicyWithAllConstraints(String name) {
            return new TopicPolicyResourceV1()
                    .apiVersion("v1")
                    .kind(TopicPolicyKind.TOPIC_POLICY)
                    .metadata(new ApplicationPolicyMetadata()
                            .name(name))
                    .spec(new ApplicationPolicySpec()
                            .policies(Map.of(
                                    "partitions", new PolicyConstraintDto(new Range()
                                            .constraint(ConstraintKind.RANGE)
                                            .min(1L)
                                            .max(100L)),
                                    "cleanup.policy", new PolicyConstraintDto(new OneOf()
                                            .constraint(ConstraintKind.ONE_OF)
                                            .values(List.of("delete", "compact"))),
                                    "compression.type", new PolicyConstraintDto(new NoneOf()
                                            .constraint(ConstraintKind.NONE_OF)
                                            .values(List.of("uncompressed"))),
                                    "name", new PolicyConstraintDto(new Match()
                                            .constraint(ConstraintKind.MATCH)
                                            .pattern("^[a-z][a-z0-9-]*$"))
                            )));
        }
    }

    // ========================================================================
    // KafkaClusters - with polymorphic schema registry types
    // ========================================================================

    @Nested
    @DisplayName("KafkaClusters")
    class KafkaClusterTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliKafkaClusterConsoleV22Api(client).getKafkaClusterByNameV2(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliKafkaClusterConsoleV22Api(client).deleteKafkaClusterV2(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with all polymorphic schema registry types")
        void crudWithAllSchemaRegistryTypes() throws ApiException {
            var api = new CliKafkaClusterConsoleV22Api(client);
            String confluentName = uniqueName("cluster-confluent");
            String glueName = uniqueName("cluster-glue");

            try {
                // Create BOTH cluster types
                var createdConfluent = api.createOrUpdateKafkaClusterV2(
                        kafkaClusterWithConfluentLikeSchemaRegistry(confluentName), null);
                assertThat(createdConfluent).isNotNull();

                var createdGlue = api.createOrUpdateKafkaClusterV2(
                        kafkaClusterWithGlueSchemaRegistry(glueName), null);
                assertThat(createdGlue).isNotNull();

                // LIST and verify BOTH types deserialize correctly from the same response
                var list = api.listKafkaClusterResourcesV2();
                assertThat(list).extracting(c -> c.getMetadata().getName())
                        .contains(confluentName, glueName);

                // Find and verify ConfluentLike cluster from list
                var confluentCluster = list.stream()
                        .filter(c -> c.getMetadata().getName().equals(confluentName))
                        .findFirst()
                        .orElseThrow();
                var confluentSr = confluentCluster.getSpec().getSchemaRegistry();
                assertThat(confluentSr).isNotNull();
                assertThat(confluentSr.getActualInstance()).isInstanceOf(ConfluentLike.class);
                var confluentLike = (ConfluentLike) confluentSr.getActualInstance();
                assertThat(confluentLike.getType()).isEqualTo(ConfluentLike.TypeEnum.CONFLUENT_LIKE);
                assertThat(confluentLike.getUrl()).isEqualTo("http://schema-registry:8081");

                // Find and verify Glue cluster from list
                var glueCluster = list.stream()
                        .filter(c -> c.getMetadata().getName().equals(glueName))
                        .findFirst()
                        .orElseThrow();
                var glueSr = glueCluster.getSpec().getSchemaRegistry();
                assertThat(glueSr).isNotNull();
                assertThat(glueSr.getActualInstance()).isInstanceOf(Glue.class);
                var glue = (Glue) glueSr.getActualInstance();
                assertThat(glue.getType()).isEqualTo(Glue.TypeEnum.GLUE);
                assertThat(glue.getRegion()).isEqualTo("us-east-1");
                assertThat(glue.getRegistryName()).isEqualTo("test-registry");

                // Verify nested AmazonSecurity polymorphic type
                var security = glue.getSecurity();
                assertThat(security).isNotNull();
                assertThat(security.getActualInstance()).isInstanceOf(Credentials.class);
                var credentials = (Credentials) security.getActualInstance();
                assertThat(credentials.getAccessKeyId()).isEqualTo("AKIAIOSFODNN7EXAMPLE");

                // DELETE both
                api.deleteKafkaClusterV2(confluentName);
                api.deleteKafkaClusterV2(glueName);

                // Verify both are gone
                var listAfter = api.listKafkaClusterResourcesV2();
                assertThat(listAfter).extracting(c -> c.getMetadata().getName())
                        .doesNotContain(confluentName, glueName);
            } finally {
                try {
                    api.deleteKafkaClusterV2(confluentName);
                } catch (Exception ignored) {
                }
                try {
                    api.deleteKafkaClusterV2(glueName);
                } catch (Exception ignored) {
                }
            }
        }

        private KafkaClusterResourceV2 kafkaClusterWithConfluentLikeSchemaRegistry(String name) {
            return new KafkaClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KafkaClusterKind.KAFKA_CLUSTER)
                    .metadata(new KafkaClusterMetadata()
                            .name(name))
                    .spec(new KafkaClusterSpec()
                            .displayName("Test Cluster with ConfluentLike SR")
                            .bootstrapServers("localhost:9092")
                            .schemaRegistry(new SchemaRegistry(new ConfluentLike()
                                    .type(ConfluentLike.TypeEnum.CONFLUENT_LIKE)
                                    .url("http://schema-registry:8081")
                                    .security(new ConfluentLikeSchemaRegistrySecurity(new NoSecurity()
                                            .type(NoSecurity.TypeEnum.NO_SECURITY)))
                                    .ignoreUntrustedCertificate(false))));
        }

        private KafkaClusterResourceV2 kafkaClusterWithGlueSchemaRegistry(String name) {
            return new KafkaClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KafkaClusterKind.KAFKA_CLUSTER)
                    .metadata(new KafkaClusterMetadata()
                            .name(name))
                    .spec(new KafkaClusterSpec()
                            .displayName("Test Cluster with Glue SR")
                            .bootstrapServers("localhost:9092")
                            .schemaRegistry(new SchemaRegistry(new Glue()
                                    .type(Glue.TypeEnum.GLUE)
                                    .registryName("test-registry")
                                    .region("us-east-1")
                                    .security(new AmazonSecurity(new Credentials()
                                            .type(Credentials.TypeEnum.CREDENTIALS)
                                            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                                            .secretKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"))))));
        }
    }

    // ========================================================================
    // Authentication
    // ========================================================================

    @Nested
    @DisplayName("Authentication")
    class AuthTests {

        @Test
        @DisplayName("whoami returns current user")
        void whoami() throws ApiException {
            var whoami = new TokensApi(client).whoami();
            assertThat(whoami).isNotNull();
        }

        @Test
        @DisplayName("listAdminTokens returns tokens")
        void listTokens() throws ApiException {
            TokensApi tokensApi = new TokensApi(client);
            var tokens = tokensApi.listAdminTokens();
            assertThat(tokens).isNotEmpty();
        }
    }

    // ========================================================================
    // KafkaConnectClusters
    // ========================================================================

    @Nested
    @DisplayName("KafkaConnectClusters")
    class KafkaConnectClusterTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliKafkaConnectClusterConsoleV23Api(client).getKafkaConnectClusterByNameV2(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliKafkaConnectClusterConsoleV23Api(client).deleteKafkaConnectClusterV2(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliKafkaConnectClusterConsoleV23Api(client);
            String name = uniqueName("kconnect");

            try {
                var created = api.createOrUpdateKafkaConnectClusterV2(cluster, kafkaConnectCluster(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getKafkaConnectClusterByNameV2(cluster, name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listKafkaConnectClusterResourcesV2(cluster);
                assertThat(list).extracting(c -> c.getMetadata().getName()).contains(name);

                api.deleteKafkaConnectClusterV2(cluster, name);

                var listAfter = api.listKafkaConnectClusterResourcesV2(cluster);
                assertThat(listAfter).extracting(c -> c.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteKafkaConnectClusterV2(cluster, name);
                } catch (Exception ignored) {
                }
            }
        }

        private KafkaConnectClusterResourceV2 kafkaConnectCluster(String name) {
            return new KafkaConnectClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KafkaConnectClusterKind.KAFKA_CONNECT_CLUSTER)
                    .metadata(new KafkaConnectMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new KafkaConnectSpec()
                            .displayName("Test Kafka Connect")
                            .urls("http://kafka-connect:8083"));
        }
    }

    // ========================================================================
    // KsqlDBClusters
    // ========================================================================

    @Nested
    @DisplayName("KsqlDBClusters")
    class KsqlDBClusterTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliKsqlDBClusterConsoleV24Api(client).getKsqlDBClusterByNameV2(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliKsqlDBClusterConsoleV24Api(client).deleteKsqlDBClusterV2(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliKsqlDBClusterConsoleV24Api(client);
            String name = uniqueName("ksqldb");

            try {
                var created = api.createOrUpdateKsqlDBClusterV2(cluster, ksqlDBCluster(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getKsqlDBClusterByNameV2(cluster, name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listKsqlDBClusterResourcesV2(cluster);
                assertThat(list).extracting(c -> c.getMetadata().getName()).contains(name);

                api.deleteKsqlDBClusterV2(cluster, name);

                var listAfter = api.listKsqlDBClusterResourcesV2(cluster);
                assertThat(listAfter).extracting(c -> c.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteKsqlDBClusterV2(cluster, name);
                } catch (Exception ignored) {
                }
            }
        }

        private KsqlDBClusterResourceV2 ksqlDBCluster(String name) {
            return new KsqlDBClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KsqlDBClusterKind.KSQL_DB_CLUSTER)
                    .metadata(new KsqlDBMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new KsqlDBSpec()
                            .displayName("Test ksqlDB")
                            .url("http://ksqldb:8088"));
        }
    }

    // ========================================================================
    // Connectors
    // ========================================================================

    @Nested
    @DisplayName("Connectors")
    class ConnectorTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliConnectorKafkaV214Api(client).getConnectorByNameV2(cluster, "nonexistent-connect", randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliConnectorKafkaV214Api(client).deleteConnectorV2(cluster, "nonexistent-connect", randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with kafka connect dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliConnectorKafkaV214Api(client);
            var connectApi = new CliKafkaConnectClusterConsoleV23Api(client);
            String connectName = uniqueName("connect");
            String connectorName = uniqueName("connector");

            try {
                // Create kafka connect cluster dependency
                connectApi.createOrUpdateKafkaConnectClusterV2(cluster, kafkaConnectCluster(connectName), null);

                var created = api.createOrUpdateConnectorV2(cluster, connectName, connector(connectorName, connectName), null);
                assertThat(created).isNotNull();

                var fetched = api.getConnectorByNameV2(cluster, connectName, connectorName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(connectorName);

                var list = api.listConnectorResourcesV2(cluster, connectName);
                assertThat(list).extracting(c -> c.getMetadata().getName()).contains(connectorName);

                api.deleteConnectorV2(cluster, connectName, connectorName);

                var listAfter = api.listConnectorResourcesV2(cluster, connectName);
                assertThat(listAfter).extracting(c -> c.getMetadata().getName()).doesNotContain(connectorName);
            } finally {
                try {
                    api.deleteConnectorV2(cluster, connectName, connectorName);
                } catch (Exception ignored) {
                }
                try {
                    connectApi.deleteKafkaConnectClusterV2(cluster, connectName);
                } catch (Exception ignored) {
                }
            }
        }

        private KafkaConnectClusterResourceV2 kafkaConnectCluster(String name) {
            return new KafkaConnectClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KafkaConnectClusterKind.KAFKA_CONNECT_CLUSTER)
                    .metadata(new KafkaConnectMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new KafkaConnectSpec()
                            .displayName("Test Kafka Connect")
                            .urls("http://kafka-connect:8083"));
        }

        private ConnectorResourceV2 connector(String name, String connectCluster) {
            return new ConnectorResourceV2()
                    .apiVersion("v2")
                    .kind(ConnectorKind.CONNECTOR)
                    .metadata(new ConnectorMetadata()
                            .name(name)
                            .cluster(cluster)
                            .connectCluster(connectCluster))
                    .spec(new ConnectorSpec()
                            .config(Map.of(
                                    "connector.class", "org.apache.kafka.connect.file.FileStreamSourceConnector",
                                    "tasks.max", "1",
                                    "file", "/tmp/test.txt",
                                    "topic", "test-topic"
                            )));
        }
    }

    // ========================================================================
    // Users
    // ========================================================================

    @Nested
    @DisplayName("Users")
    class UserTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliUserIamV20Api(client).getUserByNameV2(randomName() + "@example.com"));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliUserIamV20Api(client).deleteUserV2(randomName() + "@example.com"));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliUserIamV20Api(client);
            String email = uniqueName("user") + "@test.conduktor.io";

            try {
                var created = api.createOrUpdateUserV2(user(email), null);
                assertThat(created).isNotNull();

                var fetched = api.getUserByNameV2(email);
                assertThat(fetched.getMetadata().getName()).isEqualTo(email);

                var list = api.listUserResourcesV2();
                assertThat(list).extracting(u -> u.getMetadata().getName()).contains(email);

                api.deleteUserV2(email);

                var listAfter = api.listUserResourcesV2();
                assertThat(listAfter).extracting(u -> u.getMetadata().getName()).doesNotContain(email);
            } finally {
                try {
                    api.deleteUserV2(email);
                } catch (Exception ignored) {
                }
            }
        }

        private UserResourceV2 user(String email) {
            return new UserResourceV2()
                    .apiVersion("v2")
                    .kind(UserKind.USER)
                    .metadata(new UserMetadata()
                            .name(email))
                    .spec(new UserSpec()
                            .firstName("Test")
                            .lastName("User"));
        }
    }

    // ========================================================================
    // Alerts
    // ========================================================================

    @Nested
    @DisplayName("Alerts")
    class AlertTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliAlertMonitoringV220Api(client).getAlertByNameV2(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliAlertMonitoringV220Api(client).deleteAlertV2(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with topic alert type")
        void crudLifecycle() throws ApiException {
            var api = new CliAlertMonitoringV220Api(client);
            var topicApi = new CliTopicKafkaV212Api(client);
            String topicName = uniqueName("alert-topic");
            String alertName = uniqueName("alert");

            try {
                // Create topic dependency
                topicApi.createOrUpdateTopicV2(cluster, topic(topicName), null);

                var created = api.createOrUpdateAlertV2(cluster, alert(alertName, topicName), null);
                assertThat(created).isNotNull();

                var fetched = api.getAlertByNameV2(cluster, alertName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(alertName);

                var list = api.listAlertResourcesV2(cluster, null, null, null, null, null);
                assertThat(list).extracting(a -> a.getMetadata().getName()).contains(alertName);

                api.deleteAlertV2(cluster, alertName);

                var listAfter = api.listAlertResourcesV2(cluster, null, null, null, null, null);
                assertThat(listAfter).extracting(a -> a.getMetadata().getName()).doesNotContain(alertName);
            } finally {
                try {
                    api.deleteAlertV2(cluster, alertName);
                } catch (Exception ignored) {
                }
                try {
                    topicApi.deleteTopicV2(cluster, topicName);
                } catch (Exception ignored) {
                }
            }
        }

        private TopicResourceV2 topic(String name) {
            return new TopicResourceV2()
                    .apiVersion("v2")
                    .kind(TopicKind.TOPIC)
                    .metadata(new TopicMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new TopicSpec()
                            .partitions(1)
                            .replicationFactor(1));
        }

        private AlertResourceV2 alert(String name, String topicName) {
            return new AlertResourceV2()
                    .apiVersion("v2")
                    .kind(AlertKind.ALERT)
                    .metadata(new AlertMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new AlertSpecV2(new TopicAlertV2()
                            .type(TopicAlertV2.TypeEnum.TOPIC_ALERT)
                            .topicName(topicName)
                            .metric(TopicMetricType.MESSAGE_IN)
                            .operator(Operator.GREATER_THAN)
                            .threshold(1000L)));
        }
    }

    // ========================================================================
    // Integrations
    // ========================================================================

    @Nested
    @DisplayName("Integrations")
    class IntegrationTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            // Integrations use predefined names (Slack, Email, Teams, Webhook)
            // Getting a non-existent one returns 404
            assert404(() -> new CliIntegrationMonitoringV319Api(client).getIntegrationByNameV3(IntegrationType.SLACK));
        }

        @Test
        @DisplayName("CRUD lifecycle with Slack integration")
        void crudLifecycle() throws ApiException {
            var api = new CliIntegrationMonitoringV319Api(client);

            try {
                var created = api.createOrUpdateIntegrationV3(slackIntegration(), null);
                assertThat(created).isNotNull();

                var fetched = api.getIntegrationByNameV3(IntegrationType.SLACK);
                assertThat(fetched.getMetadata().getName()).isEqualTo(IntegrationType.SLACK);

                var list = api.listIntegrationResourcesV3();
                assertThat(list).extracting(i -> i.getMetadata().getName()).contains(IntegrationType.SLACK);

                api.deleteIntegrationV3(IntegrationType.SLACK);

                var listAfter = api.listIntegrationResourcesV3();
                assertThat(listAfter).extracting(i -> i.getMetadata().getName()).doesNotContain(IntegrationType.SLACK);
            } finally {
                try {
                    api.deleteIntegrationV3(IntegrationType.SLACK);
                } catch (Exception ignored) {
                }
            }
        }

        private IntegrationResourceV3 slackIntegration() {
            return new IntegrationResourceV3()
                    .apiVersion("v3")
                    .kind(IntegrationKind.INTEGRATION)
                    .metadata(new IntegrationMetadata()
                            .name(IntegrationType.SLACK))
                    .spec(new IntegrationSpec()
                            .config(new IntegrationConfig(new Slack()
                                    .type(Slack.TypeEnum.SLACK)
                                    .token("xoxb-test-token-12345"))));
        }
    }

    // ========================================================================
    // ResourcePolicies
    // ========================================================================

    @Nested
    @DisplayName("ResourcePolicies")
    class ResourcePolicyTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliResourcePolicySelfServeV16Api(client).getResourcePolicyByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliResourcePolicySelfServeV16Api(client).deleteResourcePolicyV1(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle")
        void crudLifecycle() throws ApiException {
            var api = new CliResourcePolicySelfServeV16Api(client);
            String name = uniqueName("res-policy");

            try {
                var created = api.createOrUpdateResourcePolicyV1(resourcePolicy(name), null);
                assertThat(created).isNotNull();

                var fetched = api.getResourcePolicyByNameV1(name);
                assertThat(fetched.getMetadata().getName()).isEqualTo(name);

                var list = api.listResourcePolicyResourcesV1(null, null);
                assertThat(list).extracting(p -> p.getMetadata().getName()).contains(name);

                api.deleteResourcePolicyV1(name);

                var listAfter = api.listResourcePolicyResourcesV1(null, null);
                assertThat(listAfter).extracting(p -> p.getMetadata().getName()).doesNotContain(name);
            } finally {
                try {
                    api.deleteResourcePolicyV1(name);
                } catch (Exception ignored) {
                }
            }
        }

        private ResourcePolicyResourceV1 resourcePolicy(String name) {
            return new ResourcePolicyResourceV1()
                    .apiVersion("v1")
                    .kind(ResourcePolicyKind.RESOURCE_POLICY)
                    .metadata(new ResourcePolicyMetadata()
                            .name(name))
                    .spec(new ResourcePolicySpec()
                            .targetKind(PolicyKind.TOPIC)
                            .description("Test resource policy")
                            .rules(List.of(new CelRule()
                                    .condition("resource.name.startsWith('team-')")
                                    .errorMessage("should start with team-"))));
        }
    }

    // ========================================================================
    // ServiceAccounts
    // ========================================================================

    @Nested
    @DisplayName("ServiceAccounts")
    class ServiceAccountTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliServiceAccountSelfServeV111Api(client).getServiceAccountByNameV1(cluster, randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliServiceAccountSelfServeV111Api(client).deleteServiceAccountV1(cluster, randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with application instance dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliServiceAccountSelfServeV111Api(client);
            var appApi = new CliApplicationSelfServeV17Api(client);
            var instanceApi = new CliApplicationInstanceSelfServeV18Api(client);
            var groupApi = new CliGroupIamV21Api(client);

            String groupName = uniqueName("sa-owner");
            String appName = uniqueName("sa-app");
            String instanceName = uniqueName("sa-instance");
            String saName = uniqueName("sa");

            try {
                // Create dependencies
                groupApi.createOrUpdateGroupV2(group(groupName), null);
                appApi.createOrUpdateApplicationV1(application(appName, groupName), null);
                instanceApi.createOrUpdateApplicationInstanceV1(applicationInstance(instanceName, appName), null);

                var created = api.createOrUpdateServiceAccountV1(cluster, serviceAccount(saName, instanceName), null);
                assertThat(created).isNotNull();

                var fetched = api.getServiceAccountByNameV1(cluster, saName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(saName);

                var list = api.listServiceAccountResourcesV1(cluster);
                assertThat(list).extracting(s -> s.getMetadata().getName()).contains(saName);

                api.deleteServiceAccountV1(cluster, saName);

                var listAfter = api.listServiceAccountResourcesV1(cluster);
                assertThat(listAfter).extracting(s -> s.getMetadata().getName()).doesNotContain(saName);
            } finally {
                try {
                    api.deleteServiceAccountV1(cluster, saName);
                } catch (Exception ignored) {
                }
                try {
                    instanceApi.deleteApplicationInstanceV1(instanceName);
                } catch (Exception ignored) {
                }
                try {
                    appApi.deleteApplicationV1(appName);
                } catch (Exception ignored) {
                }
                try {
                    groupApi.deleteGroupV2(groupName);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name))
                    .spec(new GroupSpec()
                            .displayName("Test Group"));
        }

        private ApplicationResourceV1 application(String name, String owner) {
            return new ApplicationResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationKind.APPLICATION)
                    .metadata(new ApplicationMetadata()
                            .name(name))
                    .spec(new InputApplicationSpec()
                            .title("Test App")
                            .owner(owner));
        }

        private ApplicationInstanceResourceV1 applicationInstance(String name, String appName) {
            return new ApplicationInstanceResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationInstanceKind.APPLICATION_INSTANCE)
                    .metadata(new ApplicationInstanceMetadata()
                            .name(name)
                            .application(appName))
                    .spec(new ApplicationInstanceSpec()
                            .applicationManagedServiceAccount(true)
                            .cluster(cluster));
        }

        private ServiceAccountResourceV1 serviceAccount(String name, String appInstance) {
            return new ServiceAccountResourceV1()
                    .apiVersion("v1")
                    .kind(ServiceAccountKind.SERVICE_ACCOUNT)
                    .metadata(new ServiceAccountMetadata()
                            .name(name)
                            .cluster(cluster)
                            .appInstance(appInstance))
                    .spec(new ServiceAccountSpec()
                            .authorization(new ServiceAccountAuthorization(new KAFKAACL()
                                    .type(KAFKAACL.TypeEnum.KAFKA_ACL)
                                    .acls(List.of()))));
        }
    }

    // ========================================================================
    // ApplicationInstancePermissions
    // ========================================================================

    @Nested
    @DisplayName("ApplicationInstancePermissions")
    class ApplicationInstancePermissionTests {

        @Test
        @DisplayName("GET non-existent returns 404")
        void getNonExistent404() {
            assert404(() -> new CliApplicationInstancePermissionSelfServeV19Api(client).getApplicationInstancePermissionByNameV1(randomName()));
        }

        @Test
        @DisplayName("DELETE non-existent returns 404")
        void deleteNonExistent404() {
            assert404(() -> new CliApplicationInstancePermissionSelfServeV19Api(client).deleteApplicationInstancePermissionV1(randomName()));
        }

        @Test
        @DisplayName("CRUD lifecycle with application instance dependency")
        void crudLifecycle() throws ApiException {
            var api = new CliApplicationInstancePermissionSelfServeV19Api(client);
            var appApi = new CliApplicationSelfServeV17Api(client);
            var instanceApi = new CliApplicationInstanceSelfServeV18Api(client);
            var groupApi = new CliGroupIamV21Api(client);
            var topicApi = new CliTopicKafkaV212Api(client);

            String groupName = uniqueName("perm-owner");
            String appName = uniqueName("perm-app");
            String instanceName = uniqueName("perm-instance");
            String grantedToInstanceName = uniqueName("granted-instance");
            String permName = uniqueName("perm");
            String topicName = uniqueName("perm-topic");

            try {
                // Create dependencies in order
                groupApi.createOrUpdateGroupV2(group(groupName), null);
                appApi.createOrUpdateApplicationV1(application(appName, groupName), null);

                // Create topic first
                topicApi.createOrUpdateTopicV2(cluster, topic(topicName), null);

                // Create instance WITH ownership of the topic
                instanceApi.createOrUpdateApplicationInstanceV1(
                        applicationInstanceWithResources(instanceName, appName, topicName), null);

                // Create the instance that will receive permissions
                instanceApi.createOrUpdateApplicationInstanceV1(
                        applicationInstance(grantedToInstanceName, appName), null);

                var created = api.createOrUpdateApplicationInstancePermissionV1(
                        applicationInstancePermission(permName, appName, instanceName, grantedToInstanceName, topicName), null);
                assertThat(created).isNotNull();

                var fetched = api.getApplicationInstancePermissionByNameV1(permName);
                assertThat(fetched.getMetadata().getName()).isEqualTo(permName);

                var list = api.listApplicationInstancePermissionResourcesV1(null, null, null);
                assertThat(list).extracting(p -> p.getMetadata().getName()).contains(permName);

                api.deleteApplicationInstancePermissionV1(permName);

                var listAfter = api.listApplicationInstancePermissionResourcesV1(null, null, null);
                assertThat(listAfter).extracting(p -> p.getMetadata().getName()).doesNotContain(permName);
            } finally {
                try {
                    api.deleteApplicationInstancePermissionV1(permName);
                } catch (Exception ignored) {
                }
                try {
                    instanceApi.deleteApplicationInstanceV1(grantedToInstanceName);
                } catch (Exception ignored) {
                }
                try {
                    instanceApi.deleteApplicationInstanceV1(instanceName);
                } catch (Exception ignored) {
                }
                try {
                    topicApi.deleteTopicV2(cluster, topicName);
                } catch (Exception ignored) {
                }
                try {
                    appApi.deleteApplicationV1(appName);
                } catch (Exception ignored) {
                }
                try {
                    groupApi.deleteGroupV2(groupName);
                } catch (Exception ignored) {
                }
            }
        }

        private GroupResourceV2 group(String name) {
            return new GroupResourceV2()
                    .apiVersion("v2")
                    .kind(GroupKind.GROUP)
                    .metadata(new GroupMetadata()
                            .name(name))
                    .spec(new GroupSpec()
                            .displayName("Test Group"));
        }

        private ApplicationResourceV1 application(String name, String owner) {
            return new ApplicationResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationKind.APPLICATION)
                    .metadata(new ApplicationMetadata()
                            .name(name))
                    .spec(new InputApplicationSpec()
                            .title("Test App")
                            .owner(owner));
        }

        private ApplicationInstanceResourceV1 applicationInstance(String name, String appName) {
            return new ApplicationInstanceResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationInstanceKind.APPLICATION_INSTANCE)
                    .metadata(new ApplicationInstanceMetadata()
                            .name(name)
                            .application(appName))
                    .spec(new ApplicationInstanceSpec()
                            .cluster(cluster));
        }

        private ApplicationInstanceResourceV1 applicationInstanceWithResources(String name, String appName, String topicName) {
            return new ApplicationInstanceResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationInstanceKind.APPLICATION_INSTANCE)
                    .metadata(new ApplicationInstanceMetadata()
                            .name(name)
                            .application(appName))
                    .spec(new ApplicationInstanceSpec()
                            .cluster(cluster)
                            .resources(Set.of(new ResourceWithOwnership()
                                    .type(ResourceType.TOPIC)
                                    .name(topicName)
                                    .patternType(ResourcePatternType.LITERAL)
                                    .ownershipMode(OwnershipMode.ALL))));
        }

        private TopicResourceV2 topic(String name) {
            return new TopicResourceV2()
                    .apiVersion("v2")
                    .kind(TopicKind.TOPIC)
                    .metadata(new TopicMetadata()
                            .name(name)
                            .cluster(cluster))
                    .spec(new TopicSpec()
                            .partitions(1)
                            .replicationFactor(1));
        }

        private ApplicationInstancePermissionResourceV1 applicationInstancePermission(
                String name, String appName, String appInstance, String grantedTo, String topicName) {
            return new ApplicationInstancePermissionResourceV1()
                    .apiVersion("v1")
                    .kind(ApplicationInstancePermissionKind.APPLICATION_INSTANCE_PERMISSION)
                    .metadata(new ApplicationInstancePermissionMetadata()
                            .name(name)
                            .application(appName)
                            .appInstance(appInstance))
                    .spec(new ApplicationInstancePermissionSpec()
                            .resource(new Resource()
                                    .type(ResourceType.TOPIC)
                                    .name(topicName)
                                    .patternType(ResourcePatternType.LITERAL))
                            .permission(ResourcePermission.READ)
                            .grantedTo(grantedTo));
        }
    }

    // ========================================================================
    // Read-only APIs
    // ========================================================================

    @Nested
    @DisplayName("Read-only APIs")
    class ReadOnlyTests {

        @Test
        @DisplayName("list users")
        void listUsers() throws ApiException {
            var users = new UsersApi(client).getTheListOfUsers();
            assertThat(users).isNotEmpty();
        }

        @Test
        @DisplayName("list clusters")
        void listClusters() throws ApiException {
            var clusters = new ClustersApi(client).listAllClusters();
            assertThat(clusters).isNotEmpty();
        }

        @Test
        @DisplayName("list loggers")
        void listLoggers() throws ApiException {
            var loggers = new LoggingApi(client).listLoggers();
            assertThat(loggers).isNotNull();
        }

        @Test
        @DisplayName("list indexer results")
        void listIndexerResults() throws ApiException {
            var results = new IndexingApi(client).listIndexerTaskResults();
            assertThat(results).isNotNull();
        }

        @Test
        @DisplayName("data catalog")
        void dataCatalog() throws ApiException {
            var catalog = new CatalogApi(client).dataCatalog(null, null, null);
            assertThat(catalog).isNotNull();
        }
    }
}
