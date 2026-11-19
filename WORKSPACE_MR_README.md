# StormVR Workspace MR

Extensão experimental do StormVR existente. Leia o [guia de configuração e limitações](docs/WORKSPACE_MR.md) e o [relatório de testes](docs/TEST_RESULTS.md) antes de instalar.

A versão Android desta entrega ainda depende de compilação e testes físicos; não há APK validado. Há código real para telas independentes, integração OES do Moonlight, controles do workspace, persistência, fallback Camera2 e serviço Linux de captura por portal/PipeWire.

```bash
python tools/workspace/bootstrap-android.py --accept-licenses
source "$HOME/Android/stormvr/environment.sh"
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug
```

O wrapper oficial Gradle 8.7 também está incluído em `Source/android`. `build-android.sh` usa a distribuição verificada instalada pelo bootstrap, evitando baixar a mesma distribuição novamente.

Linux:

```bash
python -m unittest discover -s tools/workspace -p 'test_*.py' -v
bash tools/workspace/check-host.sh
bash tools/workspace/install-service.sh
```

Nenhum serviço inicia automaticamente. Configure TLS, token e seleção explícita de fontes conforme o guia. A captura alternativa usa JPEG/HTTPS; entrada remota e H.264 multistream ainda não estão implementados nesse serviço. Uma única sessão Moonlight é preservada.

Os arquivos LICENSE e avisos de terceiros originais foram preservados. Há divergência no upstream entre README (GPL-3.0) e LICENSE (AGPL-3.0), documentada no guia.
