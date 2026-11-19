# Build v0.1.4 — Zoom & Hand Stability

Validação local em 19/11/2026, na branch `fix/zoom-out-hand-visual-stability`.
A árvore estava limpa antes do fetch e da troca de branch. O commit solicitado
`9588602a3dc5e6a7755a10178245f70209663e4c` era o HEAD remoto e é a base desta entrega.

## Correções necessárias durante a validação

A primeira execução compilou o APK, mas falhou em 1 dos 63 testes Android/JVM:
`CameraZoomTest.zoomRejectsNonFiniteValuesAndClampsToSafeDigitalRange` esperava
zoom neutro de 1,0x para NaN/infinito; a implementação retornava o mínimo de 0,4x.
Foi corrigido o fallback para 1,0x, mantendo os valores finitos entre 0,4x e 2,0x.

A revisão também encontrou o botão menos desabilitado em 1,0x. Ele agora usa
`CameraZoom.MIN`, permitindo reduzir até 0,4x; o botão mais usa `CameraZoom.MAX`.
O teste Android do controle foi ampliado para verificar habilitação, limite inferior,
limite superior, tela cheia e preservação do zoom do WebView e FOV das lentes.
Nenhuma outra alteração de produção foi feita nesta rodada.

## Resultado executado

```sh
bash tools/workspace/build-android.sh :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
python -m unittest discover -s tools/workspace -p test_server.py
python tools/workspace/verify-hand-model.py
```

- **63 testes Android/JVM aprovados**, sem falhas, erros ou testes ignorados.
- **8 testes Linux aprovados**.
- Modelo de mãos embarcado: integridade/tamanho/SHA-256 aprovados.
- Build final: **BUILD SUCCESSFUL**, com Gradle 8.7 e JDK 17 na toolchain instalada.
- Moonlight compilado para ARM64 e ARMv7; a revisão do diff não encontrou remoção de funcionalidades.
- Existem avisos de APIs obsoletas/variáveis não usadas; não há erro de compilação final.
- Assinatura debug do APK verificada com `apksigner`; pacote confirmado com `aapt`.
- APKs permanecem fora do histórico Git; não foi feito merge na main.

Os testes de zoom verificam intervalo 0,4x–2,0x e projeção com câmera descentralizada/nos dois olhos.
`HandRenderRecoveryTest` verifica retenção visual até 220 ms e reinicialização para
intervalos antigos/inválidos. `VrRenderer` mantém a suavização visual durante pequenas
perdas; validade de entrada e cliques continuam seguindo `HandSamplePolicy`.
Não foi alterado o algoritmo de recuperação visual nesta rodada.

## APK gerado

Arquivo local: `Source/android/app/build/outputs/apk/debug/app-debug.apk`.
Nome publicado: `StormVR-Workspace-MR-Zoom-Hand-Stability-Debug.apk`.
Tamanho: **100264715 bytes**.
SHA-256: `79327d2384897993a9b201af89335786166ab4b0dc383efb13575a75e2f69338`.
Pacote: **com.stormvr.workspace.mr**.
Metadados do manifest: `package: name='com.stormvr.workspace.mr' versionCode='1' versionName='0.1.0-alpha' platformBuildVersionName='14' platformBuildVersionCode='34' compileSdkVersion='34' compileSdkVersionCodename='14'`.

## Limitações

`adb devices -l` não encontrou dispositivo conectado. A suavização foi validada por
políticas/testes sintéticos e inspeção do caminho de renderização, não em câmera real.
A versão ainda precisa de validação física de câmera, mãos, headset, Bluetooth,
Moonlight e desempenho/consumo. Zoom abaixo de 1,0x reduz visualmente a imagem e pode
mostrar bordas vazias; não amplia o campo óptico capturado pela lente.

A release é experimental. Sua tag aponta para o novo commit que contém exatamente
estas correções e o código compilado, descendente do commit solicitado. O hash efetivo,
checksum, tamanho e links dos arquivos constam também na descrição da release.
