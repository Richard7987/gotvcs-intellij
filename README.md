# gotvcs-intellij

Plugin de IntelliJ que integra [Game of Trees](https://gameoftrees.org/) (`got`)
como proveedor de control de versiones nativo: detección de work trees `.got/`,
estado de archivos en el panel de Commit, diffs, historial y commits desde la UI.

## Estado actual

Fase 1 + 2 del plan: esqueleto del plugin, detección de raíces `got` y estado
de archivos (read-only) vía `got status`. Diff completo, commit/rollback,
historial y update quedan para fases posteriores.

## Build

Requiere JDK 21 y Gradle, provistos por el `flake.nix` de este repo:

```
nix develop -c gradle buildPlugin
```

El ZIP resultante queda en `build/distributions/` y se instala manualmente en
IntelliJ vía *Settings > Plugins > Install Plugin from Disk*.

Compila contra una instalación local de IntelliJ Ultimate (ver `gradle.properties`,
clave `ideLocalPath`) en vez de descargar un IDE, ya que un IntelliJ descargado
por Gradle normalmente no ejecuta en NixOS sin `nix-ld`/FHS.
