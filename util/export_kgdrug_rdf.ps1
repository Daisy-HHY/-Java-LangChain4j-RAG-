param(
    [string]$JenaHome = "E:\Github_project\LangChain4j-KGQA\apache jena\apache-jena-3.6.0",
    [string]$TdbPath = "E:\Github_project\LangChain4j-KGQA\apache jena\tdb_drug_new",
    [string]$OutputDir = "E:\Github_project\LangChain4j-KGQA\kgdrug_export"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$env:JENA_HOME = $JenaHome

$tdbQuery = Join-Path $JenaHome "bat\tdbquery.bat"
$riot = Join-Path $JenaHome "bat\riot.bat"

$ntPath = Join-Path $OutputDir "kgdrug_dump.nt"
$ttlPath = Join-Path $OutputDir "kgdrug_dump.ttl"

& $tdbQuery --loc $TdbPath --results=N-TRIPLES "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }" |
    Set-Content -Encoding UTF8 $ntPath

& $riot --validate $ntPath

& $riot --output=TURTLE $ntPath |
    Set-Content -Encoding UTF8 $ttlPath

& $riot --validate $ttlPath

Write-Host "Exported kgdrug RDF:"
Write-Host "  N-Triples: $ntPath"
Write-Host "  Turtle:    $ttlPath"
