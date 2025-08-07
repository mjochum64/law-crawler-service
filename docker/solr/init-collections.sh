#!/bin/bash

# Simplified Solr Collection Initialization Script
set -e

echo "Waiting for Solr to be ready..."
while ! curl -f http://solr:8983/solr/admin/ping 2>/dev/null; do
    echo "Waiting for Solr..."
    sleep 5
done

echo "Solr is ready. Checking if legal-documents collection exists..."

# Check if collection already exists
if curl -sf "http://solr:8983/solr/admin/collections?action=LIST" | grep -q "legal-documents"; then
    echo "Collection 'legal-documents' already exists"
    exit 0
fi

echo "Creating legal-documents collection..."

# Create collection using the configset approach
curl -X POST "http://solr:8983/solr/admin/collections" \
  -d "action=CREATE&name=legal-documents&numShards=1&replicationFactor=1&configName=_default"

if [ $? -eq 0 ]; then
    echo "Collection created successfully"
else
    echo "Failed to create collection, trying alternative method..."
    
    # Alternative: Create collection without configName parameter
    curl -X POST "http://solr:8983/solr/admin/collections" \
      -d "action=CREATE&name=legal-documents&numShards=1&replicationFactor=1"
fi

# Wait for collection to be created
sleep 10

echo "Adding required fields to schema..."

# Add document_id field (unique key)
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "add-field": {
      "name": "document_id",
      "type": "string",
      "stored": true,
      "indexed": true,
      "required": true,
      "multiValued": false
    }
  }' || echo "Field document_id might already exist"

# Add basic text fields
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "add-field": [
      {"name": "court", "type": "string", "stored": true, "indexed": true},
      {"name": "ecli_identifier", "type": "string", "stored": true, "indexed": true},
      {"name": "title", "type": "text_general", "stored": true, "indexed": true},
      {"name": "summary", "type": "text_general", "stored": true, "indexed": true},
      {"name": "full_text", "type": "text_general", "stored": true, "indexed": true},
      {"name": "case_number", "type": "string", "stored": true, "indexed": true},
      {"name": "document_type", "type": "string", "stored": true, "indexed": true},
      {"name": "status", "type": "string", "stored": true, "indexed": true},
      {"name": "source_url", "type": "string", "stored": true, "indexed": false},
      {"name": "file_path", "type": "string", "stored": true, "indexed": false},
      {"name": "decision_date", "type": "pdate", "stored": true, "indexed": true},
      {"name": "crawled_at", "type": "pdate", "stored": true, "indexed": true},
      {"name": "indexed_at", "type": "pdate", "stored": true, "indexed": true},
      {"name": "year", "type": "pint", "stored": true, "indexed": true},
      {"name": "month", "type": "pint", "stored": true, "indexed": true}
    ]
  }' || echo "Some fields might already exist"

# Set unique key if not already set
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "replace-field": {
      "name": "id",
      "type": "string",
      "stored": true,
      "indexed": true,
      "required": true,
      "multiValued": false
    }
  }' || echo "ID field configuration might already be correct"

echo "Schema configuration completed!"

# Test the collection
echo "Testing collection..."
curl -f "http://solr:8983/solr/legal-documents/admin/ping" && echo "Collection is ready for use"

echo "Solr collection setup completed successfully!"