#!/bin/bash

# Script to generate Kotlin HTTP client from OpenAPI specs

set -e  # Exit on any error

echo "🔄 Generating Kotlin HTTP client from OpenAPI specs..."

API_MODULE_DIR="api"

# Remove the previous client if it exists to ensure a clean generation
if [ -d "$API_MODULE_DIR" ]; then
    echo "🗑️  Removing previous client..."
    rm -rf "$API_MODULE_DIR"
fi

# Generate the client using openapi-generator
# We use the kotlin generator with the jvm-okhttp4 library.
# This generates a client that uses OkHttp for networking.
# We also specify package names for better organization.
echo "📝 Running openapi-generator..."
openapi-generator generate \
    -i openapi/corbado_public_api.yml \
    -g kotlin \
    -o "$API_MODULE_DIR" \
    --additional-properties="library=jvm-okhttp4,dateLibrary=java8,packageName=com.corbado.api,apiPackage=com.corbado.api.v1,modelPackage=com.corbado.api.models,invokerPackage=com.corbado.api.invoker"

echo "✅ Client generation complete!" 