# Sample Documentation Page

This is a sample markdown document to demonstrate the markdown rendering capabilities of the documentation website.

## Features

The markdown renderer supports **GitHub Flavored Markdown** (GFM) with beautiful styling:

- Lists with custom bullets
- **Bold** and *italic* text
- `inline code` snippets
- Syntax-highlighted code blocks
- Tables
- Blockquotes
- And more!

## Code Examples

Here's a Java example:

```java
@Getter
@Builder
@AllArgsConstructor
public class ApplicationService {
    private final String name;
    private final String namespace;

    public void validate() {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(namespace, "Namespace cannot be null");
    }
}
```

And a YAML example:

```yaml
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: my-app
  namespace: default
spec:
  name: my-app
  description: "Multi-tenant messaging platform"
```

## Tables

| CRD Type | Owner | Description |
|----------|-------|-------------|
| ApplicationService | None (root) | Represents a tenant |
| KafkaCluster | ApplicationService | Kafka cluster configuration |
| Topic | ApplicationService | Kafka topics |
| ACL | ApplicationService | Access control lists |

## Blockquotes

> "We shape our tools, and thereafter our tools shape us."
>
> — Marshall McLuhan

## Links

Check out the [Architecture page](/architecture) for more details about the system design.

Visit the [GitHub repository](https://github.com/framiere/conduktor_quick_start_in_a_single_container) for source code.

---

## Next Steps

1. Explore the CRD hierarchy
2. Review security model
3. Run integration tests
4. Deploy to Kubernetes

That's it! You can create any markdown file and render it beautifully with the `MarkdownPage` component.
