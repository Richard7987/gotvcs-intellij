# gotvcs-intellij

Plugin de IntelliJ que integra [Game of Trees](https://gameoftrees.org/) (`got`)
como proveedor de control de versiones nativo: detección de work trees `.got/`,
estado de archivos en el panel de Commit, diffs, historial y commits desde la UI.

## Estado actual

Fases 1-4 del plan: esqueleto del plugin, detección de raíces `got`, estado
de archivos, diff nativo (gutter de líneas) y commit/rollback desde la UI.
Historial y update quedan para fases posteriores.

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

## Tags firmados

Cada build validado se marca con un tag `got` firmado por SSH (mismo esquema
que `/nixdots`: `got tag` no soporta GPG, solo SSH vía `ssh-keygen -Y sign`).

```
got tag -S ~/.ssh/yubikey.pub -m "mensaje" nombre-del-tag
got tag -V nombre-del-tag   # verifica la firma
```

`got.conf` (con `allowed_signers "/home/ale/.ssh/allowed_signers"`) y el propio
`~/.ssh/allowed_signers` viven fuera del work tree y **no están versionados**.
En una PC nueva, antes de poder verificar tags (`got tag -V`):

```
echo "ale_bnes@tuta.com $(cat ~/.ssh/yubikey.pub)" > ~/.ssh/allowed_signers
echo 'allowed_signers "/home/ale/.ssh/allowed_signers"' > /home/ale/gotvcs-intellij.git/got.conf
```
