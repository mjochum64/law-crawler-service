# Apache Solr Konfiguration für Rechtsdokumente

Diese Konfiguration ermöglicht die Indizierung und Suche von XML-Rechtsdokumenten aus dem law-crawler-service.

## 📋 Übersicht der Dateien

### Core-Konfiguration
- **`schema.xml`** - Haupt-Schema für Feldtypen und -definitionen
- **`solrconfig.xml`** - Solr-Kernkonfiguration mit Request Handlers
- **`managed-schema.xml`** - Alternative managed Schema für Solr Cloud
- **`stopwords_de_legal.txt`** - Deutsche Stoppwörter inkl. Rechtsterminologie

### Datenimport
- **`data-import-config.xml`** - DIH-Konfiguration für XML-Import

## 🚀 Setup-Anleitung

### 1. Solr Installation

```bash
# Solr herunterladen (Version 9.x)
wget https://archive.apache.org/dist/solr/solr/9.6.1/solr-9.6.1.zip
unzip solr-9.6.1.zip
cd solr-9.6.1

# Solr starten
bin/solr start
```

### 2. Core erstellen

```bash
# Legal Documents Core erstellen
bin/solr create_core -c legal-documents

# Konfiguration kopieren
cp /path/to/law-crawler-service/solr-config/* server/solr/legal-documents/conf/

# Core neu laden
bin/solr reload -c legal-documents
```

### 3. Data Import Handler einrichten

```bash
# DIH Plugin herunterladen (falls nicht vorhanden)
# Für Solr 9.x ist DIH als separate Distribution verfügbar

# solrconfig.xml um DIH erweitern:
```

```xml
<!-- In solrconfig.xml hinzufügen -->
<requestHandler name="/dataimport" class="solr.DataImportHandler">
  <lst name="defaults">
    <str name="config">data-import-config.xml</str>
  </lst>
</requestHandler>
```

### 4. Dokumente indizieren

#### Option A: DIH verwenden
```bash
# Vollständigen Import starten
curl "http://localhost:8983/solr/legal-documents/dataimport?command=full-import&clean=true&commit=true&debug=true&dih.legal.documents.path=/path/to/legal-documents/**/*.xml"

# Import-Status prüfen
curl "http://localhost:8983/solr/legal-documents/dataimport?command=status"
```

#### Option B: JSON/XML direkt posten

```bash
# JSON-Format für einzelnes Dokument
curl -X POST "http://localhost:8983/solr/legal-documents/update?commit=true" \
     -H "Content-Type: application/json" \
     -d '[{
       "id": "legal_KORE712562025",
       "document_id": "KORE712562025",
       "court": "BGH",
       "decision_date": "2025-05-15T00:00:00Z",
       "case_number": "AK 30/25",
       "ecli_identifier": "ECLI:DE:BGH:2025:150525BAK30.25.0",
       "document_type": "Beschluss",
       "title": "BGH 3. Strafsenat - AK 30/25 - Beschluss",
       "full_text": "Dokument Volltext hier...",
       "year": 2025
     }]'
```

## 🔍 Such-Beispiele

### Standard-Suche
```bash
# Alle BGH-Entscheidungen
curl "http://localhost:8983/solr/legal-documents/select?q=court:BGH&rows=10"

# Suche nach ECLI
curl "http://localhost:8983/solr/legal-documents/select?q=ecli_identifier:ECLI:DE:BGH:2025*"

# Textsuche
curl "http://localhost:8983/solr/legal-documents/select?q=text:Untersuchungshaft&hl=true"
```

### Erweiterte Suche
```bash
# Rechtsdokument-Handler verwenden
curl "http://localhost:8983/solr/legal-documents/legal?q=Untersuchungshaft&facet=true"

# Datumsbereich
curl "http://localhost:8983/solr/legal-documents/select?q=*:*&fq=decision_date:[2025-01-01T00:00:00Z TO NOW]"

# Facetten nach Gericht und Jahr
curl "http://localhost:8983/solr/legal-documents/select?q=*:*&facet=true&facet.field=court&facet.field=year"
```

### More Like This
```bash
# Ähnliche Dokumente finden
curl "http://localhost:8983/solr/legal-documents/mlt?q=id:legal_KORE712562025&mlt.fl=title,leitsatz&rows=5"
```

### Autocomplete/Suggest
```bash
# Suggest-Index aufbauen
curl "http://localhost:8983/solr/legal-documents/suggest?suggest.build=true"

# Vorschläge abrufen  
curl "http://localhost:8983/solr/legal-documents/suggest?suggest.q=BGH"
```

## 📊 Monitoring & Performance

### Index-Statistiken
```bash
# Core-Informationen
curl "http://localhost:8983/solr/admin/cores?action=STATUS&core=legal-documents"

# Index-Größe und Dokument-Anzahl
curl "http://localhost:8983/solr/legal-documents/admin/luke"
```

### Performance-Optimierung
```bash
# Index optimieren (Segmente zusammenführen)
curl "http://localhost:8983/solr/legal-documents/update?optimize=true"

# Cache-Statistiken
curl "http://localhost:8983/solr/legal-documents/admin/stats.jsp"
```

## 🛠️ Anpassungen

### Schema erweitern
```bash
# Neue Felder über Schema API hinzufügen
curl -X POST "http://localhost:8983/solr/legal-documents/schema" \
     -H 'Content-type:application/json' \
     -d '{
       "add-field": {
         "name": "custom_field",
         "type": "text_de_legal",
         "indexed": true,
         "stored": true
       }
     }'
```

### Synonyme konfigurieren
```bash
# synonyms_de_legal.txt erstellen:
BGH,Bundesgerichtshof
BVerfG,Bundesverfassungsgericht
StGB,Strafgesetzbuch
BGB,Bürgerliches Gesetzbuch

# In schema.xml Synonym-Filter hinzufügen:
# <filter class="solr.SynonymGraphFilterFactory" synonyms="synonyms_de_legal.txt"/>
```

## 🚨 Troubleshooting

### Häufige Probleme
```bash
# Core lädt nicht
bin/solr restart

# DIH-Fehler prüfen
curl "http://localhost:8983/solr/legal-documents/dataimport?command=status"

# Schema-Validierung
curl "http://localhost:8983/solr/legal-documents/admin/luke?show=schema"

# Log-Dateien prüfen
tail -f server/logs/solr.log
```

### Performance-Issues
- **Zu langsame Suche**: filterCache und queryResultCache erhöhen
- **Hoher Memory-Verbrauch**: autoCommit-Intervall anpassen
- **Indexierung langsam**: ramBufferSizeMB erhöhen

## 📈 Produktive Konfiguration

Für produktive Umgebungen:
1. **Solr Cloud Setup** mit Zookeeper
2. **Sharding** für große Datenmengen
3. **Replikation** für Ausfallsicherheit
4. **SSL/TLS Verschlüsselung**
5. **Authentication & Authorization**

Beispiel solr.in.sh:
```bash
SOLR_HEAP="4g"
SOLR_JAVA_HOME="/usr/lib/jvm/java-11"
SOLR_TIMEZONE="Europe/Berlin"
```

## 🔗 Weitere Ressourcen

- [Apache Solr Dokumentation](https://solr.apache.org/guide/)
- [Data Import Handler](https://solr.apache.org/guide/uploading-structured-data-store-data-with-the-data-import-handler.html)
- [Schema Design](https://solr.apache.org/guide/schema-design.html)
- [German Analysis](https://solr.apache.org/guide/language-analysis.html#german)