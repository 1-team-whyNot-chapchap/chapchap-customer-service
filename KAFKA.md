# Kafka topic contract

Customer consumes the Subscription and Delivery topics with the `msa4-team1.` team prefix, aligned with their contracts as of 2026-09-08.

| Setting under `customer.kafka.topics` | Topic |
|---|---|
| payment | `msa4-team1.subscription.payment-events.v1` |
| refund | `msa4-team1.subscription.refund-events.v1` |
| subscription-notification | `msa4-team1.subscription.customer-notification-events.v1` |
| subscription | `msa4-team1.subscription.subscription-events.v1` |
| delivery-address | `msa4-team1.subscription.delivery-address-events.v1` |
| delivery | `msa4-team1.delivery.delivery-events.v1` |
| delivery-operation-notification | `msa4-team1.delivery.operation-notification-requests.v1` |

Main and test YAML use the same names. Existing `@RetryableTopic` annotations retain their retry policy and append `.DLT` to each original topic for dead letters. Consumer groups, event processing, envelopes and payloads are unchanged.

Before deployment, verify the original/retry/dead-letter topics and ACLs, align producer/consumer overrides, and plan old-topic backlog handling and initial offsets on the new topics. This configuration change does not move messages or offsets, subscribe to both namespaces, or replay events.

The contract test loads main and test YAML separately and resolves the actual listener annotations, so test configuration cannot hide a wrong production topic. Live Kafka delivery and deployment readiness require separate environment verification.
