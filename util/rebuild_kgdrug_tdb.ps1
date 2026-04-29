param(
    [string]$JenaHome = "E:\Github_project\LangChain4j-KGQA\apache jena\apache-jena-3.6.0",
    [string]$RdfFile = "E:\Github_project\LangChain4j-KGQA\kgdrug_export\kgdrug_dump.nt",
    [string]$TargetTdbPath = "E:\Github_project\LangChain4j-KGQA\kgdrug_export\tdb_rebuilt"
)

$ErrorActionPreference = "Stop"

$env:JENA_HOME = $JenaHome
$tdbLoader = Join-Path $JenaHome "bat\tdbloader.bat"
$tdbQuery = Join-Path $JenaHome "bat\tdbquery.bat"

if (Test-Path $TargetTdbPath) {
    throw "Target TDB path already exists: $TargetTdbPath. Choose an empty path to avoid overwriting data."
}

& $tdbLoader --loc $TargetTdbPath $RdfFile
& $tdbQuery --loc $TargetTdbPath "SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }"

Write-Host "Rebuilt kgdrug TDB: $TargetTdbPath"
