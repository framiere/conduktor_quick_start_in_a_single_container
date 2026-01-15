# Plan: Intégration CLI Conduktor avec Opérateur

## Objectif

Permettre à l'opérateur Kubernetes d'appliquer les ressources transformées (VirtualCluster, Topic, ACL, etc.) sur Console et Gateway via la CLI Conduktor, en utilisant un token d'authentification stocké dans un Secret Kubernetes.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 1 : BOOTSTRAP                         │
│                                                                 │
│  bootstrap.sh  ───► POST /api/login ───► access_token          │
│                              │                                  │
│                              ▼                                  │
│              kubectl create secret generic conduktor-cli-token  │
│                     --from-literal=token=<access_token>         │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 2 : OPÉRATEUR                         │
│                                                                 │
│  KafkaCluster CRD ──► VirtualClusterTransformer ──► YAML       │
│  ServiceAccount CRD ──► ServiceAccountTransformer ──► YAML     │
│  Topic CRD ──► TopicTransformer ──► YAML                       │
│                              │                                  │
│                              ▼                                  │
│              ConduktorCliExecutor (lit Secret, exécute CLI)     │
│                              │                                  │
│                    conduktor apply -f <yaml>                    │
│                              │                                  │
│              ┌───────────────┴───────────────┐                  │
│              ▼                               ▼                  │
│          Console                         Gateway                │
└─────────────────────────────────────────────────────────────────┘
```

## Composants à créer

### 1. Script de Bootstrap (`scripts/bootstrap-conduktor-token.sh`)

```bash
#!/bin/bash
# Authentifie sur Console et crée le Secret Kubernetes avec le token

CONSOLE_URL="${CONSOLE_URL:-http://localhost:8080}"
CONSOLE_USER="${CONSOLE_USER:-admin@demo.dev}"
CONSOLE_PASSWORD="${CONSOLE_PASSWORD:-123_ABC_abc}"
SECRET_NAME="${SECRET_NAME:-conduktor-cli-credentials}"
SECRET_NAMESPACE="${SECRET_NAMESPACE:-default}"

# 1. Authentification
TOKEN=$(curl -s -X POST "$CONSOLE_URL/api/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$CONSOLE_USER\",\"password\":\"$CONSOLE_PASSWORD\"}" \
  | jq -r '.access_token')

# 2. Créer le Secret
kubectl create secret generic "$SECRET_NAME" \
  --namespace="$SECRET_NAMESPACE" \
  --from-literal=console-url="$CONSOLE_URL" \
  --from-literal=console-token="$TOKEN" \
  --from-literal=gateway-url="${GATEWAY_URL:-http://localhost:8888}" \
  --from-literal=gateway-user="${GATEWAY_USER:-admin}" \
  --from-literal=gateway-password="${GATEWAY_PASSWORD:-conduktor}" \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 2. Manifest Secret (`k8s/conduktor-cli-secret.yaml`)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: conduktor-cli-credentials
  namespace: default
type: Opaque
stringData:
  console-url: "http://console:8080"
  console-token: "<populated-by-bootstrap>"
  gateway-url: "http://gateway:8888"
  gateway-user: "admin"
  gateway-password: "conduktor"
```

### 3. Classe Java `ConduktorCliExecutor`

```java
public class ConduktorCliExecutor {
    private final String consoleUrl;
    private final String consoleToken;
    private final String gatewayUrl;
    private final String gatewayUser;
    private final String gatewayPassword;

    // Lit les credentials depuis le Secret K8s (via env vars ou fichiers montés)
    public ConduktorCliExecutor() {
        this.consoleUrl = System.getenv("CONDUKTOR_CONSOLE_URL");
        this.consoleToken = readSecretFile("/var/run/secrets/conduktor/console-token");
        // ...
    }

    public void apply(String yamlContent) {
        // Écrit le YAML dans un fichier temp
        // Exécute: conduktor apply -f <file> --console-url=... --token=...
    }
}
```

### 4. Intégration dans le Reconciler

```java
@Override
public UpdateControl<Topic> reconcile(Topic topic, Context<Topic> context) {
    // 1. Transformer CRD → Conduktor YAML
    String conduktorYaml = topicTransformer.transform(topic);

    // 2. Appliquer via CLI
    cliExecutor.apply(conduktorYaml);

    return UpdateControl.noUpdate();
}
```

## Étapes d'implémentation

### Phase 1 : Bootstrap (Priorité haute)

- [ ] Créer `scripts/bootstrap-conduktor-token.sh`
- [ ] Créer `k8s/conduktor-cli-secret.yaml`
- [ ] Tester le script manuellement avec Console+Gateway local

### Phase 2 : Opérateur (Priorité haute)

- [ ] Créer `ConduktorCliExecutor.java`
- [ ] Ajouter lecture du Secret (env vars ou volume mount)
- [ ] Intégrer dans les transformers existants
- [ ] Ajouter tests unitaires

### Phase 3 : Tests d'intégration (Priorité moyenne)

- [ ] Créer `docker-compose.yml` avec Console+Gateway+Operator
- [ ] Script de test end-to-end
- [ ] CI/CD pipeline

## Configuration Helm/Deployment

```yaml
# values.yaml
conduktor:
  console:
    url: "http://console:8080"
  gateway:
    url: "http://gateway:8888"
  secretName: "conduktor-cli-credentials"

# deployment.yaml
env:
  - name: CONDUKTOR_CONSOLE_URL
    valueFrom:
      secretKeyRef:
        name: {{ .Values.conduktor.secretName }}
        key: console-url
volumes:
  - name: conduktor-credentials
    secret:
      secretName: {{ .Values.conduktor.secretName }}
```

## Questions ouvertes

1. **Refresh token** : Le token Console expire-t-il ? Faut-il un mécanisme de refresh ?
2. **CLI installation** : Comment installer la CLI dans l'image Docker de l'opérateur ?
3. **Dry-run vs Apply** : L'opérateur doit-il faire dry-run d'abord puis apply ?

## Prochaines étapes

1. Valider ce plan
2. Implémenter le script de bootstrap
3. Tester avec l'environnement Docker existant
