#!/bin/bash

# Solr Collection Initialization Script for Legal Documents
# This script sets up the Solr collection with German text analysis

set -e

echo "Waiting for Solr to be ready..."
while ! curl -f http://solr:8983/solr/admin/ping 2>/dev/null; do
    echo "Waiting for Solr..."
    sleep 5
done

echo "Solr is ready. Initializing legal-documents collection..."

# Check if collection already exists
if curl -f "http://solr:8983/solr/admin/collections?action=LIST" | grep -q "legal-documents"; then
    echo "Collection 'legal-documents' already exists"
    exit 0
fi

echo "Creating legal-documents collection..."

# Create collection with basic configuration
curl -X POST "http://solr:8983/solr/admin/collections" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "action=CREATE" \
  -d "name=legal-documents" \
  -d "numShards=1" \
  -d "replicationFactor=1" \
  -d "collection.configName=legal-docs-config"

# Wait for collection to be created
sleep 10

echo "Adding German text analysis fields..."

# Add basic document fields
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "add-field": [
      {
        "name": "document_id",
        "type": "string",
        "stored": true,
        "indexed": true,
        "required": true,
        "multiValued": false
      },
      {
        "name": "court",
        "type": "string",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "ecli_identifier",
        "type": "string",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "source_url",
        "type": "string",
        "stored": true,
        "indexed": false,
        "multiValued": false
      },
      {
        "name": "title",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "summary",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "full_text",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "case_number",
        "type": "string",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "document_type",
        "type": "string",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "decision_date",
        "type": "pdate",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "crawled_at",
        "type": "pdate",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "indexed_at",
        "type": "pdate",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "status",
        "type": "string",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "file_path",
        "type": "string",
        "stored": true,
        "indexed": false,
        "multiValued": false
      },
      {
        "name": "year",
        "type": "pint",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "month",
        "type": "pint",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "leitsatz",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "tenor",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      },
      {
        "name": "gruende",
        "type": "text_de",
        "stored": true,
        "indexed": true,
        "multiValued": false
      }
    ]
  }'

echo "Adding German text field type..."

# Add German text analysis field type
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "add-field-type": {
      "name": "text_de",
      "class": "solr.TextField",
      "positionIncrementGap": "100",
      "analyzer": {
        "type": "index",
        "tokenizer": {
          "class": "solr.StandardTokenizerFactory"
        },
        "filters": [
          {
            "class": "solr.LowerCaseFilterFactory"
          },
          {
            "class": "solr.StopFilterFactory",
            "ignoreCase": "true",
            "words": "lang/stopwords_de.txt"
          },
          {
            "class": "solr.GermanNormalizationFilterFactory"
          },
          {
            "class": "solr.GermanLightStemFilterFactory"
          }
        ]
      }
    }
  }'

echo "Setting up copy fields for search..."

# Add copy fields for comprehensive search
curl -X POST "http://solr:8983/solr/legal-documents/schema" \
  -H "Content-Type: application/json" \
  -d '{
    "add-copy-field": [
      {
        "source": "title",
        "dest": "_text_"
      },
      {
        "source": "summary", 
        "dest": "_text_"
      },
      {
        "source": "full_text",
        "dest": "_text_"
      },
      {
        "source": "leitsatz",
        "dest": "_text_"
      },
      {
        "source": "tenor",
        "dest": "_text_"
      },
      {
        "source": "gruende",
        "dest": "_text_"
      }
    ]
  }'

echo "Configuring search request handler..."

# Configure search request handler with German-specific settings
curl -X POST "http://solr:8983/solr/legal-documents/config" \
  -H "Content-Type: application/json" \
  -d '{
    "add-requesthandler": {
      "name": "/legal-search",
      "class": "solr.SearchHandler",
      "defaults": {
        "defType": "edismax",
        "qf": "title^3.0 summary^2.0 full_text^1.0 case_number^4.0 ecli_identifier^4.0 leitsatz^2.5 tenor^1.5 gruende^1.0",
        "pf": "title^6.0 summary^4.0 full_text^2.0",
        "ps": 3,
        "qs": 3,
        "rows": 10,
        "start": 0,
        "hl": "true",
        "hl.fl": "title,summary,full_text,leitsatz",
        "hl.simple.pre": "<mark>",
        "hl.simple.post": "</mark>",
        "hl.fragsize": 200,
        "hl.maxAnalyzedChars": 500000,
        "facet": "true",
        "facet.field": ["court", "document_type", "year", "status"],
        "facet.mincount": 1,
        "facet.limit": 50
      }
    }
  }'

echo "Setting up suggester for autocomplete..."

# Configure suggester component
curl -X POST "http://solr:8983/solr/legal-documents/config" \
  -H "Content-Type: application/json" \
  -d '{
    "add-searchcomponent": {
      "name": "suggest",
      "class": "solr.SuggestComponent",
      "suggester": {
        "name": "legal_suggester",
        "lookupImpl": "AnalyzingInfixLookupFactory",
        "dictionaryImpl": "DocumentDictionaryFactory",
        "field": "title",
        "weightField": "_version_",
        "suggestAnalyzerFieldType": "text_de",
        "buildOnStartup": "false",
        "buildOnCommit": "true"
      }
    }
  }'

# Add suggest request handler
curl -X POST "http://solr:8983/solr/legal-documents/config" \
  -H "Content-Type: application/json" \
  -d '{
    "add-requesthandler": {
      "name": "/suggest",
      "class": "solr.SearchHandler",
      "defaults": {
        "suggest": "true",
        "suggest.count": "10",
        "suggest.dictionary": "legal_suggester"
      },
      "components": ["suggest"]
    }
  }'

echo "Configuring automatic commits..."

# Configure autocommit
curl -X POST "http://solr:8983/solr/legal-documents/config" \
  -H "Content-Type: application/json" \
  -d '{
    "set-property": {
      "updateHandler.autoCommit.maxTime": 5000,
      "updateHandler.autoCommit.openSearcher": true,
      "updateHandler.autoSoftCommit.maxTime": 1000
    }
  }'

echo "Collection setup completed successfully!"

# Verify collection is ready
curl -f "http://solr:8983/solr/legal-documents/admin/ping" && echo "Collection is ready for use"