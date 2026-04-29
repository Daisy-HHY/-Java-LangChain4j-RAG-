# kgdrug RDF Export

The project uses the kgdrug dataset only. The source currently available in
`apache jena/tdb_drug_new` is a Jena TDB binary store, not an original readable
TTL/RDF source file.

To recover readable RDF from the TDB store, use a SPARQL `CONSTRUCT` export:

```powershell
.\util\export_kgdrug_rdf.ps1
```

Generated files:

- `kgdrug_export/kgdrug_dump.nt`: N-Triples RDF backup, one triple per line.
- `kgdrug_export/kgdrug_dump.ttl`: Turtle RDF backup generated from the `.nt`.

Validation performed during export:

```powershell
.\apache jena\apache-jena-3.6.0\bat\riot.bat --validate .\kgdrug_export\kgdrug_dump.nt
.\apache jena\apache-jena-3.6.0\bat\riot.bat --validate .\kgdrug_export\kgdrug_dump.ttl
```

To rebuild a TDB directory from the exported RDF:

```powershell
.\util\rebuild_kgdrug_tdb.ps1 -TargetTdbPath E:\Github_project\LangChain4j-KGQA\kgdrug_export\tdb_rebuilt
```

Current export note:

- Original TDB query count: `528089`.
- Exported valid RDF triples: `528032`.
- Difference: `57` records. Jena reported null-binding warnings during export,
  which indicates a small number of abnormal internal TDB nodes that cannot be
  safely serialized as RDF.
