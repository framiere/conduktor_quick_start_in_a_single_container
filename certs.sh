#!/bin/bash
# Certificate generation for mTLS setup
# Generates CA, server certificates for redpanda, gateway, and console

set -euo pipefail

CERT_DIR="${CERT_DIR:-/var/lib/conduktor/certs}"
PASSWORD="${CERT_PASSWORD:-conduktor}"

mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

echo "Generating certificates in $CERT_DIR..."

# Clean up any existing certificates
rm -f *.crt *.csr *_creds *.jks *.srl *.key *.pem *.der *.p12 *.log 2>/dev/null || true

# Generate CA certificate and key
echo "Creating CA certificate..."
openssl req -new -x509 \
    -keyout ca.key \
    -out ca.crt \
    -days 365 \
    -subj '/CN=ca.conduktor.local/OU=TEST/O=CONDUKTOR/L=LONDON/C=UK' \
    -passin pass:$PASSWORD \
    -passout pass:$PASSWORD

# Generate truststore with CA certificate
echo "Creating truststore..."
keytool -noprompt \
    -keystore truststore.jks \
    -alias ca-root \
    -import -file ca.crt \
    -storepass $PASSWORD \
    -keypass $PASSWORD

# Function to create certificate for a service
create_certificate() {
    local name=$1
    shift
    local extra_sans=("$@")

    echo "Creating certificate for: $name"

    # Build SAN list
    local san_list="DNS:$name,DNS:localhost,DNS:127.0.0.1"
    for san in "${extra_sans[@]}"; do
        san_list+=",DNS:$san"
    done

    local san_config="
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = $name
[v3_req]
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = $san_list
"

    # Generate keystore with private key
    keytool -genkey -noprompt \
        -alias $name \
        -dname "CN=$name,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK" \
        -ext "SAN=$san_list" \
        -keystore $name.keystore.jks \
        -keyalg RSA \
        -storepass $PASSWORD \
        -keypass $PASSWORD \
        -storetype pkcs12

    # Create certificate signing request
    keytool -keystore $name.keystore.jks \
        -alias $name \
        -certreq -file $name.csr \
        -storepass $PASSWORD \
        -keypass $PASSWORD \
        -ext "SAN=$san_list"

    # Sign with CA
    CERT_SERIAL=$(awk -v seed="$RANDOM" 'BEGIN { srand(seed); printf("0x%.4x%.4x%.4x%.4x\n", rand()*65535 + 1, rand()*65535 + 1, rand()*65535 + 1, rand()*65535 + 1) }')
    openssl x509 -req \
        -CA ca.crt \
        -CAkey ca.key \
        -in $name.csr \
        -out $name-signed.crt \
        -sha256 \
        -days 365 \
        -set_serial $CERT_SERIAL \
        -passin pass:$PASSWORD \
        -extensions v3_req \
        -extfile <(echo "$san_config")

    # Import CA cert into keystore
    keytool -noprompt \
        -keystore $name.keystore.jks \
        -alias ca-root \
        -import -file ca.crt \
        -storepass $PASSWORD \
        -keypass $PASSWORD

    # Import signed certificate into keystore
    keytool -noprompt \
        -keystore $name.keystore.jks \
        -alias $name \
        -import -file $name-signed.crt \
        -storepass $PASSWORD \
        -keypass $PASSWORD

    # Create individual truststore (some services need it)
    keytool -noprompt \
        -keystore $name.truststore.jks \
        -alias ca-root \
        -import -file ca.crt \
        -storepass $PASSWORD \
        -keypass $PASSWORD

    # Save credential files
    echo "$PASSWORD" > ${name}_sslkey_creds
    echo "$PASSWORD" > ${name}_keystore_creds
    echo "$PASSWORD" > ${name}_truststore_creds

    # Export to PEM format (for Redpanda and curl testing)
    keytool -export \
        -alias $name \
        -file $name.der \
        -keystore $name.keystore.jks \
        -storepass $PASSWORD

    openssl x509 -inform der -in $name.der -out $name.crt

    keytool -importkeystore \
        -srckeystore $name.keystore.jks \
        -destkeystore $name.p12 \
        -deststoretype PKCS12 \
        -deststorepass $PASSWORD \
        -srcstorepass $PASSWORD \
        -noprompt

    openssl pkcs12 \
        -in $name.p12 \
        -nodes \
        -nocerts \
        -out $name.key \
        -passin pass:$PASSWORD

    echo "  Created: $name.keystore.jks, $name.truststore.jks, $name.crt, $name.key"
}


create_certificate "gateway"
create_certificate "admin"
create_certificate "user1"

# Cleanup intermediate files
rm -f *.csr *.der *.p12 *-signed.crt 2>/dev/null || true

echo ""
echo "Certificate generation complete!"
echo "Files created in: $CERT_DIR"
ls -la "$CERT_DIR"
