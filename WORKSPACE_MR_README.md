# StormVR Workspace MR

Veja o [README principal](README.md), o [guia de configuração](docs/WORKSPACE_MR.md) e o [relatório de testes](docs/TEST_RESULTS.md).

A versão alpha preserva a implementação existente e o commit `dbc19ad`. O pacote próprio é `com.stormvr.workspace.mr`. A compilação usa Java 17, Gradle 8.7 e NDK 27.0.12077973, com ferramentas e caches no `/home`.

```bash
python tools/workspace/bootstrap-android.py --accept-licenses
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
```

Os testes em aparelho e a confirmação da correção do congelamento Moonlight permanecem pendentes. O serviço Linux alternativo usa JPEG/HTTPS, sem entrada remota, e mantém a seleção explícita de fontes pelo portal.
